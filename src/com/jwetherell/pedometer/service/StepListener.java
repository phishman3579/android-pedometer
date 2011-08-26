package com.jwetherell.pedometer.service;

/**
 * This interface provides a callback mechanism for the StepDetector.
 * 
 * This code is losely based on http://code.google.com/p/pedometer/
 * 
 * @author bagilevi <bagilevi@gmail.com>
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public interface StepListener {
    public void onStep();
}

