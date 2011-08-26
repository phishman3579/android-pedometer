package com.jwetherell.pedometer.service;

/**
 * This interface provides a callback mechanism for the StepDetector.
 * 
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public interface StepListener {
    public void onStep();
}

