package com.jwetherell.pedometer.service;

import java.util.ArrayList;
import java.util.List;

import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;


/**
 * This class handles SensorEvents and determines if they are a "step" or not.
 * 
 * This code is losely based on http://code.google.com/p/pedometer/
 * 
 * @author bagilevi <bagilevi@gmail.com>
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public class StepDetector implements SensorEventListener {
    private static boolean detecting = false;
    
    private static int mLimit = 100;

    private static float mLastValues[] = new float[3*2];
    private static float mScale[] = new float[2];
    private static float mYOffset = 0;

    private static float mLastDirections[] = new float[3*2];
    private static float mLastExtremes[][] = { new float[3*2], new float[3*2] };
    private static float mLastDiff[] = new float[3*2];
    private static int mLastMatch = -1;
    
    private List<StepListener> mStepListeners = new ArrayList<StepListener>();

    /**
     * Add a StepListener;
     * @param sl StepListener to add.
     */
    public void addStepListener(StepListener sl) {
        if (!mStepListeners.contains(sl)) mStepListeners.add(sl);
    }
    /**
     * Remove a StepListener.
     * @param sl StepListener to remove.
     */
    public void removeStepListener(StepListener sl) {
        if (mStepListeners.contains(sl)) mStepListeners.remove(sl);
    }
    
    public StepDetector() {
        int h = 480;
        mYOffset = h * 0.5f;
        mScale[0] = - (h * 0.5f * (1.0f / (SensorManager.STANDARD_GRAVITY * 2)));
        mScale[1] = - (h * 0.5f * (1.0f / (SensorManager.MAGNETIC_FIELD_EARTH_MAX)));
    }
    
    /**
     * Set sensitivity of the StepDector.
     * @param sensitivity Sensitivity of the StepDector.
     */
    public static void setSensitivity(int sensitivity) {
        mLimit = sensitivity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onSensorChanged(SensorEvent event) {
    	if (event==null) return;

        if (!detecting) detectStep(event);
    }

    private void detectStep(SensorEvent event) {
    	if (event==null) return;

        detecting=true;
        
        float vSum = 0;
        for (int i=0 ; i<3 ; i++) {
            final float v = mYOffset + event.values[i] * mScale[0];
            vSum += v;
        }
        int k = 0;
        float v = vSum / 3;
        
        float direction = (v > mLastValues[k] ? 1 : (v < mLastValues[k] ? -1 : 0));
        if (direction == - mLastDirections[k]) {
            // Direction changed
            int extType = (direction > 0 ? 0 : 1); // minumum or maximum?
            mLastExtremes[extType][k] = mLastValues[k];
            float diff = Math.abs(mLastExtremes[extType][k] - mLastExtremes[1 - extType][k]);

            if (diff > mLimit) {
                boolean isAlmostAsLargeAsPrevious = diff > (mLastDiff[k]*2/3);
                boolean isPreviousLargeEnough = mLastDiff[k] > (diff/3);
                boolean isNotContra = (mLastMatch != 1 - extType);
                
                if (isAlmostAsLargeAsPrevious && isPreviousLargeEnough && isNotContra) {
                    for (StepListener stepListener : mStepListeners) {
                        stepListener.onStep();
                    }
                    mLastMatch = extType;
                } else {
                    mLastMatch = -1;
                }
            }
            mLastDiff[k] = diff;
        }
        mLastDirections[k] = direction;
        mLastValues[k] = v;
        
        detecting=false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAccuracyChanged(Sensor arg0, int arg1) {
        // Not used
    }
}
