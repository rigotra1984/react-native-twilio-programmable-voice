package com.hoxfon.react.RNTwilioVoice;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.PowerManager;
import android.util.Log;

import com.facebook.react.bridge.ReactApplicationContext;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ProximityViaPowerManager {
    private static final String TAG = "ProximityViaPowerManager";
    private static final String ERROR_PROXIMITY_LOCK_NOT_SUPPORTED = "Proximity lock is not supported.";

    private PowerManager.WakeLock proximityWakeLock;
    private PowerManager powerManager;

    public ProximityViaPowerManager(ReactApplicationContext context) {
        powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        initProximityWakeLock();
    }

    @SuppressLint({"InvalidWakeLockTag", "LongLogTag"})
    private void initProximityWakeLock() {
        // Check if PROXIMITY_SCREEN_OFF_WAKE_LOCK is implemented, not part of public api.
        // PROXIMITY_SCREEN_OFF_WAKE_LOCK and isWakeLockLevelSupported are available from api 21
        try {
            boolean isSupported;
            int proximityScreenOffWakeLock;
            if (android.os.Build.VERSION.SDK_INT < 21) {
                Field field = PowerManager.class.getDeclaredField("PROXIMITY_SCREEN_OFF_WAKE_LOCK");
                proximityScreenOffWakeLock = (Integer) field.get(null);

                Method method = powerManager.getClass().getDeclaredMethod("getSupportedWakeLockFlags");
                int powerManagerSupportedFlags = (Integer) method.invoke(powerManager);
                isSupported = ((powerManagerSupportedFlags & proximityScreenOffWakeLock) != 0x0);
            } else {
                proximityScreenOffWakeLock = powerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK;
                isSupported = powerManager.isWakeLockLevelSupported(proximityScreenOffWakeLock);
            }
            if (isSupported) {
                proximityWakeLock = powerManager.newWakeLock(proximityScreenOffWakeLock, TAG);
                proximityWakeLock.setReferenceCounted(false);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to get proximity screen locker.");
        }
    }

    @SuppressLint("LongLogTag")
    public void turnScreenOn() {
        if (proximityWakeLock == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, ERROR_PROXIMITY_LOCK_NOT_SUPPORTED);
            }
            return;
        }

        synchronized (proximityWakeLock) {
            if (proximityWakeLock.isHeld()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "turnScreenOn()");
                }
                if (android.os.Build.VERSION.SDK_INT >= 21) {
                    proximityWakeLock.release(PowerManager.RELEASE_FLAG_WAIT_FOR_NO_PROXIMITY);
                }
            }
        }
    }

    @SuppressLint("LongLogTag")
    public void turnScreenOff() {
        if (proximityWakeLock == null) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, ERROR_PROXIMITY_LOCK_NOT_SUPPORTED);
            }
            return;
        }

        synchronized (proximityWakeLock) {
            if (!proximityWakeLock.isHeld()) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "turnScreenOff()");
                }
                proximityWakeLock.acquire();
            }
        }
    }

}
