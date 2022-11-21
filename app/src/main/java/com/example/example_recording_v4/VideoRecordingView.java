package com.example.example_recording_v4;

import android.content.Context;
import android.util.AttributeSet;
import android.view.TextureView;

//set Video View(custom textureview)
public class VideoRecordingView extends TextureView {
    int mRatioWidth = 0;
    int mRatioHeight=0;

    // VideoRecordingView ()
    public VideoRecordingView(Context context) {
        this(context, null);
    }
    public VideoRecordingView(Context context, AttributeSet attrs) {
        this(context, attrs, 0);
    }
    public VideoRecordingView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
    }

    public void setAspectRatio(int width, int height) throws IllegalAccessException {
        if(width<0 || height<0){
            throw new IllegalAccessException("Size cannot be negative");
        }
        mRatioWidth = width;
        mRatioHeight = height;
        requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        int width= MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        if(0==mRatioWidth||0==mRatioHeight){
            setMeasuredDimension(width, height);
        }else{
            if(width<height*mRatioWidth/mRatioHeight){  //check ratio and reset dimension
                setMeasuredDimension(width,width*mRatioHeight/mRatioWidth);
            }else {
                setMeasuredDimension(height*mRatioWidth/mRatioHeight,height);
            }
        }
    }
}
