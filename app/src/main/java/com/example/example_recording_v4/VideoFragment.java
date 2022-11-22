package com.example.example_recording_v4;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.graphics.Matrix;
import android.graphics.RectF;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;

import com.example.example_recording_v4.databinding.FragmentVideoBinding;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import io.reactivex.Observable;
import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;

public class VideoFragment extends Fragment implements View.OnClickListener {

    private static final String TAG = "VideoFragment";
    private static final String FILE_PATH = "DCIM/Choco/";

    FragmentVideoBinding binding;

    //Camera lens
    private static final String CAM_FRONT = "1";
    private static final String CAM_REAR = "0";

    private String mCameraId;
    private int mCameraFacing;  //Front or back camera state
    CameraCaptureSession mCameraCaptureSession;
    CameraDevice mCameraDevice;
    CameraManager mCameraManager;
    MediaRecorder mMediaRecorder;

    Size mVideoSize;
    Size mPreviewSize;
    CaptureRequest.Builder mCaptureRequestBuilder;

    int mSensorOrientation;

    Semaphore mSemaphore = new Semaphore(1);

    HandlerThread mBackgroundThread;
    Handler mBackgroundHandler;

    private String mNextVideoAbsolutePath;
    private boolean mIsRecordingVideo;
    CompositeDisposable mCompositeDisposable; //for timer--dependency add: io.reactivex.rxjava2...

    public static VideoFragment newInstance(){
        return new VideoFragment();
    }


    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState){
        binding = DataBindingUtil.inflate(inflater,R.layout.fragment_video,container,false);
        mCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;   //init- not setting (11/21)
        mCompositeDisposable = new CompositeDisposable();
        return binding.getRoot();
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState){
        binding.pictureBtn.setOnClickListener(this);
        binding.switchImgBtn.setOnClickListener(this);
        binding.modeBtn.setOnClickListener(this);
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState){
        super.onActivityCreated(savedInstanceState);
        assert getActivity() != null;
    }

    @Override
    public void onResume(){
        super.onResume();
        startBackgroundThread();
        if (binding.preview.isAvailable()){
            openCamera(binding.preview.getWidth(),binding.preview.getHeight(),mCameraFacing);
        }else{
            binding.preview.setSurfaceTextureListener(mSurfaceTextureListener);
        }
    }

    @Override
    public void onPause(){
        closeCamera();
        stopBackgroundThread();
        super.onPause();
    }
    //========== BACKGROUNDTHREAD START/STOP==========//
    private void startBackgroundThread(){
        mBackgroundThread = new HandlerThread("CameraBackground");
        mBackgroundThread.start();
        mBackgroundHandler = new android.os.Handler(mBackgroundThread.getLooper());
    }
    private void stopBackgroundThread(){
        mBackgroundThread.quitSafely();
        try {
            mBackgroundThread.join();
            mBackgroundThread = null;
            mBackgroundHandler = null;
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    //========== TEXTUREVIEW ==========//
    private TextureView.SurfaceTextureListener mSurfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            openCamera(binding.preview.getWidth(), binding.preview.getHeight(),mCameraFacing);
        }
        @Override
        public void onSurfaceTextureSizeChanged(@NonNull SurfaceTexture surfaceTexture, int width, int height) {
            if (!mIsRecordingVideo) configureTransform(width,height);
        }
        @Override
        public boolean onSurfaceTextureDestroyed(@NonNull SurfaceTexture surface) {
            return true;
        }
        @Override
        public void onSurfaceTextureUpdated(@NonNull SurfaceTexture surface) {

        }
    };
    private void configureTransform(int viewWidth, int viewHeight){
        Activity activity = getActivity();
        if (null == binding.preview || null ==mPreviewSize || null == activity){
            return;
        }
        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        Matrix matrix = new Matrix();
        RectF viewRect = new RectF(0,0,viewWidth, viewHeight);
        RectF bufferRect = new RectF(0, 0, mPreviewSize.getWidth(), mPreviewSize.getHeight());

        float centerX = viewRect.centerX();
        float centerY = viewRect.centerY();

        if (Surface.ROTATION_90 == rotation || Surface.ROTATION_270 == rotation){
            bufferRect.offset(centerX - bufferRect.centerX(),centerY - bufferRect.centerY());
            matrix.setRectToRect(viewRect,bufferRect,Matrix.ScaleToFit.FILL);
            float scale = Math.max(
                    (float) viewHeight / mPreviewSize.getHeight(),
                    (float) viewWidth / mPreviewSize.getWidth()
            );
            matrix.postScale(scale,scale,centerX,centerY);
            matrix.postRotate(90*(rotation -2),centerX,centerY);
        }
        activity.runOnUiThread(()->binding.preview.setTransform(matrix));
    }

    //========== CAMERADEVICE STATE CALLBACK ==========//
    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            startPreview();
            mSemaphore.release();
            if(null != binding.preview){
                configureTransform(binding.preview.getWidth(), binding.preview.getHeight());
            }
        }
        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mSemaphore.release();
            camera.close();
            mCameraDevice = null;
        }
        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mSemaphore.release();
            camera.close();
            mCameraDevice = null;
            Activity activity = getActivity();
            assert activity != null;
            activity.finish();
        }
    };

    //========== CAMERA OPEN/CLOSE, SIZE==========//
    //open camera : call camera feature
    private void openCamera(int width, int height, int mCameraFacing ){
        Activity activity = getActivity();
        assert activity != null;
        mCameraManager = (CameraManager) activity.getSystemService(Context.CAMERA_SERVICE);
        Log.e(TAG, "openCamera()");
        try {
            if (!mSemaphore.tryAcquire(2500, TimeUnit.MILLISECONDS)){
                throw new RuntimeException("Time out waiting to lock camera opening.");
            }
            mCameraId = mCameraManager.getCameraIdList()[mCameraFacing];
            CameraCharacteristics cc = mCameraManager.getCameraCharacteristics(mCameraId);
            StreamConfigurationMap scm = cc.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            mSensorOrientation = cc.get(CameraCharacteristics.SENSOR_ORIENTATION);
            if (scm == null){   //scm has information all about HW camera characteristics
                throw new RuntimeException("Cannot get available preview/video sizes");
            }

            mVideoSize = chooseVideoSize(scm.getOutputSizes(MediaRecorder.class));
            mPreviewSize = chooseOptimalSize(scm.getOutputSizes(SurfaceTexture.class), width, height, mVideoSize);

            int orientation = getResources().getConfiguration().orientation;
            if (orientation == Configuration.ORIENTATION_LANDSCAPE){
                binding.preview.setAspectRatio(mPreviewSize.getWidth(),mPreviewSize.getHeight());
            }else{
                binding.preview.setAspectRatio(mPreviewSize.getHeight(),mPreviewSize.getWidth());
            }

            configureTransform(width,height);
            mMediaRecorder = new MediaRecorder();
            mCameraManager.openCamera(mCameraId, mStateCallback, mBackgroundHandler);
        }catch (CameraAccessException | SecurityException | NullPointerException | InterruptedException | IllegalAccessException e){
            e.printStackTrace();
            activity.finish();
        }
    }

    //close Camera
    private void closeCamera(){
        try {
            mSemaphore.acquire();
            closePreviewSession();
            if (null != mCameraDevice) {    //close camera device
                mCameraDevice.close();
                mCameraDevice = null;
            }
            if (null != mMediaRecorder) {   //close media recorder
                mMediaRecorder.release();
                mMediaRecorder = null;
            }
        }catch (InterruptedException ie){
            ie.printStackTrace();
        }finally {
            mSemaphore.release();
        }
    }

    // set Sizes
    private static Size chooseVideoSize(Size[] choices){
        for (Size size:choices){
            if (size.getWidth() == size.getHeight() *4/3 && size.getWidth()<=1080){
                return size;
            }
        }
        return choices[choices.length -1];
    }
    private static Size chooseOptimalSize(Size[] choices, int width, int height, Size aspectRatio){
        List<Size> bigEnough = new ArrayList<>();
        int w = aspectRatio.getWidth();
        int h = aspectRatio.getHeight();
        for (Size ops : choices){
            if (ops.getHeight() == ops.getWidth()*h/w && ops.getWidth()>=width && ops.getHeight() >=height){
                bigEnough.add(ops);
            }
        }
        if (bigEnough.size()>0){
            return Collections.min(bigEnough, new CompareSizesByArea());
        }else {
            return choices[0];
        }
    }
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

    //========== PREVIEW START/UPDATE/CLOSE ==========//
    //start preview
    private void startPreview(){
        if (null == mCameraDevice || !binding.preview.isAvailable() || null == mPreviewSize){
            return;
        }
        try {
            closePreviewSession();
            SurfaceTexture surfaceTexture = binding.preview.getSurfaceTexture();
            assert surfaceTexture != null;
            surfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);

            Surface previewSurface = new Surface(surfaceTexture);
            mCaptureRequestBuilder.addTarget(previewSurface);
            mCameraDevice.createCaptureSession(Collections.singletonList(previewSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCameraCaptureSession = session;
                    updatePreview();
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Activity activity = getActivity();
                    assert activity != null;
                    Toast.makeText(activity,"Failed",Toast.LENGTH_SHORT).show();
                }
            },mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    //update preview
    private void updatePreview(){
        if (null == mCameraDevice){
            return;
        }
        try {
            setUpCaptureRequestBuilder(mCaptureRequestBuilder);
            HandlerThread thread = new HandlerThread("CameraPreview");
            thread.start();
            mCameraCaptureSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, mBackgroundHandler);
        }catch (CameraAccessException e){
            e.printStackTrace();
        }
    }
    private void setUpCaptureRequestBuilder(CaptureRequest.Builder builder){
        builder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO);
    }
    //close preview session
    private void closePreviewSession(){
        if (mCameraCaptureSession != null){
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        }
    }
    //orientation settings
    private static final int SENSOR_ORIENTATION_DEFAULT_DEGREES = 90;
    private static final int SENSOR_ORIENTATION_INVERSE_DEGREES = 270;
    private static final SparseIntArray DEFAULT_ORIENTATIONS = new SparseIntArray();
    private static final SparseIntArray INVERSE_ORIENTATIONS = new SparseIntArray();
    static {
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_0, 90);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_90, 0);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_180, 270);
        DEFAULT_ORIENTATIONS.append(Surface.ROTATION_270, 180);
    }

    static {
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_0, 270);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_90, 180);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_180, 90);
        INVERSE_ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }

    //========== VIDEO RECORDING ==========//
    private void setUpMediaRecorder() throws IOException{
        final Activity activity = getActivity();
        if (null == activity) return;

        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        if (mNextVideoAbsolutePath == null || mNextVideoAbsolutePath.isEmpty()){
            mNextVideoAbsolutePath = getVideoFilePath();
        }
        mMediaRecorder.setOutputFile(mNextVideoAbsolutePath);
        mMediaRecorder.setVideoEncodingBitRate(10000000);
        mMediaRecorder.setVideoFrameRate(30);
        mMediaRecorder.setVideoSize(mVideoSize.getWidth(), mVideoSize.getHeight());
        mMediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mMediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);

        int rotation = activity.getWindowManager().getDefaultDisplay().getRotation();
        switch (mSensorOrientation){
            case SENSOR_ORIENTATION_DEFAULT_DEGREES:
                mMediaRecorder.setOrientationHint(DEFAULT_ORIENTATIONS.get(rotation));
                break;
            case SENSOR_ORIENTATION_INVERSE_DEGREES:
                mMediaRecorder.setOrientationHint(INVERSE_ORIENTATIONS.get(rotation));
                break;
        }
        mMediaRecorder.prepare();
    }
    //set file name & file path
    private String getVideoFilePath(){
        final File dir = Environment.getExternalStorageDirectory().getAbsoluteFile();
        String path = dir.getPath() + "/" + FILE_PATH;
        File dst = new File(path);
        if (!dst.exists()) dst.mkdirs();
        return path + System.currentTimeMillis() + ".mp4";
    }
    //start recording
    private void startRecordingVideo(){
        if (null == mCameraDevice || !binding.preview.isAvailable() || null == mPreviewSize){
            return;
        }
        assert getActivity() != null;

        try {
            closePreviewSession();
            setUpMediaRecorder();
            SurfaceTexture texture = binding.preview.getSurfaceTexture();
            assert texture != null;
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);

            List<Surface> surfaces = new ArrayList<>();     //surface listed

            Surface previewSurface = new Surface(texture);
            surfaces.add(previewSurface);
            mCaptureRequestBuilder.addTarget(previewSurface);

            Surface recordSurface = mMediaRecorder.getSurface();
            surfaces.add(recordSurface);
            mCaptureRequestBuilder.addTarget(recordSurface);

            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    mCameraCaptureSession = session;
                    updatePreview();
                    getActivity().runOnUiThread(() -> {
                        binding.pictureBtn.setText("stop");
                        mIsRecordingVideo = true;

                        mMediaRecorder.start();
                    });
                }
                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                    Activity activity = getActivity();
                    if (null != activity) {
                        Toast.makeText(activity, "Failed", Toast.LENGTH_SHORT).show();
                    }
                }
            },mBackgroundHandler);
            timer();
        }catch (CameraAccessException | IOException e){
            e.printStackTrace();
        }
    }
    //stop recording
    private void stopRecordingVideo(){
        mIsRecordingVideo = false;
        binding.pictureBtn.setText("Record");
        mMediaRecorder.stop();
        mMediaRecorder.reset();

        Activity activity = getActivity();
        if (null != activity){
            Toast.makeText(activity,"Video saved: "+ mNextVideoAbsolutePath,Toast.LENGTH_SHORT).show();
            Log.d(TAG,"Video saved: "+mNextVideoAbsolutePath);
            File file = new File(mNextVideoAbsolutePath);
            if (!file.exists()) file.mkdirs();
            getActivity().getApplicationContext().sendBroadcast(new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)));
        }
        mNextVideoAbsolutePath = null;
        stop();
        startPreview();
    }
    //CHANGE CAMERA LENSE
    //onclick()
    @Override
    public void onClick(View view) {
        int id = view.getId();
        switch (id){
            case R.id.pictureBtn:
                if (mIsRecordingVideo) stopRecordingVideo();
                else startRecordingVideo();
                break;
            case R.id.switchImgBtn:
                switch (mCameraFacing){
                    case Camera.CameraInfo.CAMERA_FACING_BACK:
                        mCameraFacing = Camera.CameraInfo.CAMERA_FACING_FRONT;
                        break;
                    case Camera.CameraInfo.CAMERA_FACING_FRONT:
                        mCameraFacing = Camera.CameraInfo.CAMERA_FACING_BACK;
                        break;
                }
                closeCamera();
                openCamera(binding.preview.getWidth(),binding.preview.getHeight(),mCameraFacing);
                break;
            case R.id.modeBtn:
                ((MainActivity)getActivity()).replaceFragment(Camera2BasicFragment.newInstance());
                break;
        }
    }
    static class CompareSizeByArea implements Comparator<Size>{
        @Override
        public int compare(Size lhs, Size rhs){
            return Long.signum((long) lhs.getWidth()*lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }


    //========== TIME COUNT ==========//
    private void timer(){
        binding.recordTimeTxtView.setVisibility(View.VISIBLE);
        Log.e(TAG,"Timer start");
        Observable<Long> duration = Observable.interval(1, TimeUnit.SECONDS)
                .map(sec -> sec += 1);
        Disposable disposable = duration.subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(timeout -> {
                    long min = timeout/60;
                    long sec = timeout % 60;
                    String sMin;
                    String sSec;
                    if (min<10) sMin = "0" + min;
                    else sMin = String.valueOf(min);

                    if (sec<10) sSec = "0" + sec;
                    else sSec = String.valueOf(sec);

                    String elapseTime = sMin + ":" + sSec;
                    binding.recordTimeTxtView.setText(elapseTime);
                });
        mCompositeDisposable.add(disposable);
    }

    private void stop(){
        binding.recordTimeTxtView.setVisibility(View.GONE);
        if(!mCompositeDisposable.isDisposed()){
            mCompositeDisposable.dispose();
        }
    }



    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (!mCompositeDisposable.isDisposed())
            mCompositeDisposable.dispose();
    }

}
