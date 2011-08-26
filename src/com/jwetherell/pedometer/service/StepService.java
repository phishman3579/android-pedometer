package com.jwetherell.pedometer.service;

import java.lang.reflect.Method;
import java.util.ArrayList;
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
 * This class extends the Service class. it is in charge of starting and stopping the power, notification,
 * and sensor managers. It also passes information received from the sensor to the StepDetector.
 * 
 * @author Justin Wetherell <phishman3579@gmail.com>
 */
public class StepService extends Service implements StepListener {
    private static final Logger logger = Logger.getLogger(StepService.class.getSimpleName());
    
    private static int NOTIFY = 0x1001;
    private static boolean running = false;
    private static boolean updating = false;
    
    private static SensorManager sensorManager = null;
    private static StepDetector stepDetector = null;
    
    private static PowerManager powerManager = null;
    private static WakeLock wakeLock = null;
    private static NotificationManager notificatioManager = null;
    private static Notification notification = null;
    private static Intent passedIntent = null;
    private static ArrayList<IStepServiceCallback> mCallbacks = new ArrayList<IStepServiceCallback>();;
    private static int mSteps = 0;

    @Override
    public void onCreate() {
        super.onCreate();
        logger.info("onCreate");

        notificatioManager = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
        initNotification();

        powerManager = (PowerManager) getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "StepService");
        if (!wakeLock.isHeld()) wakeLock.acquire();

        stepDetector = new StepDetector();
        stepDetector.addStepListener(this);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        sensorManager.registerListener( stepDetector, 
                                        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), 
                                        SensorManager.SENSOR_DELAY_GAME);
    }

    @Override
    public void onStart(Intent intent, int startId) {        
        super.onStart(intent, startId);
        logger.info("onStart");
        
        passedIntent = intent;
        Bundle extras = passedIntent.getExtras();
        if (extras!=null) {
            NOTIFY = extras.getInt("int");
        }

        updateNotification(1);
        startForegroundCompat(NOTIFY,notification);
        
        running=true;
        mSteps = 0;
    }
    
    @Override
    public void onDestroy() {        
        super.onDestroy();
        logger.info("onDestroy");
        
        notificatioManager.cancel(NOTIFY);
        if (wakeLock.isHeld()) wakeLock.release();
        sensorManager.unregisterListener(stepDetector);

        stopForegroundCompat(NOTIFY);
        
        running=false;
    }
    
    /**
     * This is a wrapper around the new startForeground method, using the older
     * APIs if it is not available.
     */
    public void startForegroundCompat(int id, Notification notif) {
        Method mStartForeground = null;
        try {
            mStartForeground = getClass().getMethod("startForeground", new Class[]{int.class, Notification.class});
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
        notificatioManager.notify(id,notif);
    }
    
    /**
     * This is a wrapper around the new stopForeground method, using the older
     * APIs if it is not available.
     */
    public void stopForegroundCompat(int id) {
        Method mStopForeground = null;
        try {
            mStopForeground = getClass().getMethod("stopForeground", new Class[]{boolean.class});
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
        
        // Fall back on the old API.  Note to cancel BEFORE changing the
        // foreground state, since we could be killed at that point.
        notificatioManager.cancel(id);
        setForeground(false);
    }

    private void initNotification() {
        notification = new Notification(R.drawable.icon, "Pedometer started.", System.currentTimeMillis());
        notification.flags = Notification.FLAG_NO_CLEAR | Notification.FLAG_ONGOING_EVENT;
    }
    
    private void updateNotification(int steps) {
        updating = true;
        
        notification.number = steps;
        notification.when = System.currentTimeMillis();
        PendingIntent contentIntent = PendingIntent.getActivity(this, 0, passedIntent, 0);
        notification.setLatestEventInfo(this, getText(R.string.app_name), "Total steps: "+steps, contentIntent);
        notificatioManager.notify(NOTIFY, notification);
        
        updating = false;
    }

    @Override
    public void onStep() {
        mSteps++;

        if (!updating) { 
            UpdateNotificationAsyncTask update = new UpdateNotificationAsyncTask();
            update.doInBackground(mSteps);
        }
        
        if (mCallbacks != null) {
        	for (IStepServiceCallback mCallback : mCallbacks) {
        		try {
					mCallback.stepsChanged(mSteps);
				} catch (RemoteException e) {
					logger.info("Exception: "+e.getMessage());
				}
        	}
        }
    }
    
    private class UpdateNotificationAsyncTask extends AsyncTask<Integer, Integer, Boolean> {
        @Override
        protected Boolean doInBackground(Integer... params) {
            updateNotification(params[0]);
            return true;
        }
    }

    private final IStepService.Stub mBinder = new IStepService.Stub(){
        @Override
        public boolean isRunning() throws RemoteException {
            return running;
        }

		@Override
		public void setSensitivity(int sens) throws RemoteException {
			logger.info("setSensitivity: "+sens);
		    StepDetector.setSensitivity(sens);
		}  
		
        @Override
        public void registerCallback(IStepServiceCallback cb) throws RemoteException {
        	logger.info("registerCallback: "+cb.toString());
            if (cb != null) mCallbacks.add(cb);
        }

        @Override
        public void unregisterCallback(IStepServiceCallback cb) throws RemoteException {
        	logger.info("unregisterCallback: "+cb.toString());
            if (cb != null) mCallbacks.remove(cb);
        }
    };

    @Override
    public IBinder onBind(Intent intent) {
        logger.info("onBind()");
        return mBinder;
    }
}
