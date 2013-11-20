package com.jwetherell.pedometer.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import com.jwetherell.pedometer.R;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.PowerManager;
import android.os.RemoteException;
import android.os.PowerManager.WakeLock;


/**
 * This class extends the Service class. it is in charge of starting and
 * stopping the power, notification, and sensor managers. It also passes
 * information received from the sensor to the StepDetector.
 * 
 * This code is losely based on http://code.google.com/p/pedometer/
 * 
 * @author bagilevi <bagilevi@gmail.com>
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public class StepService extends Service implements StepListener {

    private static final Logger logger = Logger.getLogger(StepService.class.getSimpleName());

    private static int NOTIFY = 0x1001;
    private static AtomicBoolean updating = new AtomicBoolean(false);

    private static SensorManager sensorManager = null;
    private static StepDetector stepDetector = null;

    private static PowerManager powerManager = null;
    private static WakeLock wakeLock = null;
    private static NotificationManager notificatioManager = null;
    private static Notification notification = null;
    private static Intent passedIntent = null;
    private static List<IStepServiceCallback> mCallbacks = new ArrayList<IStepServiceCallback>();;

    private static int mSteps = 0;
    private static boolean running = false;

    /**
     * {@inheritDoc}
     */
    @Override
    public void onCreate() {
        super.onCreate();
        logger.info("onCreate");

        notificatioManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        initNotification();

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StepService");
        if (!wakeLock.isHeld()) wakeLock.acquire();

        if (stepDetector == null) {
            stepDetector = StepDetector.getInstance();
            stepDetector.addStepListener(this);
        }

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener(stepDetector, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_GAME);
        running = true;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDestroy() {
        super.onDestroy();
        logger.info("onDestroy");
        running = false;
        mSteps = 0;

        notificatioManager.cancel(NOTIFY);
        if (wakeLock.isHeld()) wakeLock.release();
        sensorManager.unregisterListener(stepDetector);

        stopForegroundCompat(NOTIFY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStart(Intent intent, int startId) {
        super.onStart(intent, startId);
        logger.info("onStart");

        passedIntent = intent;
        Bundle extras = passedIntent.getExtras();
        if (extras != null) {
            NOTIFY = extras.getInt("int");
        }

        // Work around a bug where notif number has to be > 0
        updateNotification((mSteps > 0) ? mSteps : 1);
        startForegroundCompat(NOTIFY, notification);
    }

    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     * 
     * @param id
     *            Integer representing the service to start.
     * @param notif
     *            Notification to display when service is running.
     */
    public void startForegroundCompat(int id, Notification notif) {
        Method mStartForeground = null;
        try {
            mStartForeground = getClass().getMethod("startForeground", new Class[] { int.class, Notification.class });
        } catch (Exception e) {
            // Should happen in Android OS < 2.0
        }

        // If we have the new startForeground API, then use it.
        if (mStartForeground != null) {
            try {
                mStartForeground.invoke(this, id, notif);
            } catch (Exception e) {
                // Should not happen.
            }
            return;
        }

        // Fall back on the old API.
        setForeground(true);
        notificatioManager.notify(id, notif);
    }

    /**
     * This is a wrapper around the new stopForeground method, using the older
     * APIs if it is not available.
     * 
     * @param id
     *            Integer of the service to stop.
     */
    public void stopForegroundCompat(int id) {
        Method mStopForeground = null;
        try {
            mStopForeground = getClass().getMethod("stopForeground", new Class[] { boolean.class });
        } catch (Exception e) {
            // Should happen in Android OS < 2.0
        }

        // If we have the new stopForeground API, then use it.
        if (mStopForeground != null) {
            try {
                mStopForeground.invoke(this, true);
            } catch (Exception e) {
                // Should not happen.
            }
            return;
        }

        // Fall back on the old API. Note to cancel BEFORE changing the
        // foreground state, since we could be killed at that point.
        notificatioManager.cancel(id);
        setForeground(false);
    }

    private void initNotification() {
        notification = new Notification(R.drawable.icon, "Pedometer started.", System.currentTimeMillis());
        notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
    }

    private void updateNotification(int steps) {
        if (!updating.compareAndSet(false, true)) return;

        notification.number = steps;
        notification.when = System.currentTimeMillis();
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, passedIntent, 0);
        notification.setLatestEventInfo(this, getText(R.string.app_name), "Total steps: " + steps, contentIntent);
        notificatioManager.notify(NOTIFY, notification);

        updating.set(false);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onStep() {
        logger.info("onStep()");
        mSteps++;

        if (!updating.get()) {
            UpdateNotificationAsyncTask update = new UpdateNotificationAsyncTask();
            update.doInBackground(mSteps);
        }

        if (mCallbacks != null) {
            List<IStepServiceCallback> callbacksToRemove = null;
            for (IStepServiceCallback mCallback : mCallbacks) {
                try {
                    mCallback.stepsChanged(mSteps);
                } catch (RemoteException e) {
                    // Remove old callbacks if they failed to unbind
                    callbacksToRemove = new ArrayList<IStepServiceCallback>();
                    callbacksToRemove.add(mCallback);
                    e.printStackTrace();
                }
            }
            if (callbacksToRemove != null) {
                for (IStepServiceCallback mCallback : callbacksToRemove) {
                    mCallbacks.remove(mCallback);
                }
            }
        }
    }

    private class UpdateNotificationAsyncTask extends AsyncTask<Integer, Integer, Boolean> {

        /**
         * {@inheritDoc}
         */
        @Override
        protected Boolean doInBackground(Integer... params) {
            updateNotification(params[0]);
            return true;
        }
    }

    private final IStepService.Stub mBinder = new IStepService.Stub() {

        /**
         * {@inheritDoc}
         */
        @Override
        public boolean isRunning() throws RemoteException {
            return running;
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void setSensitivity(int sens) throws RemoteException {
            logger.info("setSensitivity: " + sens);
            StepDetector.setSensitivity(sens);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void registerCallback(IStepServiceCallback cb) throws RemoteException {
            if (cb == null) return;

            logger.info("registerCallback: " + cb.toString());
            cb.stepsChanged(mSteps);
            if (!mCallbacks.contains(cb)) mCallbacks.add(cb);
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public void unregisterCallback(IStepServiceCallback cb) throws RemoteException {
            if (cb == null) return;

            logger.info("unregisterCallback: " + cb.toString());
            if (mCallbacks.contains(cb)) mCallbacks.remove(cb);
        }
    };

    /**
     * {@inheritDoc}
     */
    @Override
    public IBinder onBind(Intent intent) {
        logger.info("onBind()");
        return mBinder;
    }
}
