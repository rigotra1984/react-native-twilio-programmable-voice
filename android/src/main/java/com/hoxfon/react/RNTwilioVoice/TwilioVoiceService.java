package com.hoxfon.react.RNTwilioVoice;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.NonNull;

import androidx.annotation.Nullable;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import com.facebook.react.bridge.ReactApplicationContext;
import com.twilio.voice.*;

import java.util.HashMap;

import static android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE;
import static com.hoxfon.react.RNTwilioVoice.Constants.*;

public class TwilioVoiceService extends Service {

    private static final String TAG = "TwilioVoiceService";

    private static HashMap<String, String> params = new HashMap<>();
    private static Call activeCall;
    private static CallInvite activeCallInvite;
    private Call.Listener callListener = callListener();

    private static AudioFocusRequest focusRequest;
    private static HeadsetManager headsetManager;
    private static AudioManager audioManager;
    private static int originalAudioMode = AudioManager.MODE_NORMAL;
    private static ReactApplicationContext reactContext;
    private static CallNotificationManager callNotificationManager;

    private static ProximityViaPowerManager proximityViaPowerManager;

    private static String toNumber = "";
    private static String toName = "";
    private static String progressText;
    private static String progressAction;

    private static Boolean inForegroundService = false;

    public TwilioVoiceService() {
    }

    public static void setReactApplicationContext(ReactApplicationContext reactContext) {
        TwilioVoiceService.reactContext = reactContext;
    }

    public static ReactApplicationContext getReactApplicationContext() {
        return TwilioVoiceService.reactContext;
    }

    @Override
    public IBinder onBind(Intent intent) {
        //TODO for communication return IBinder implementation
        return null;
    }

    @Override
    public void onCreate() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Servicio creado...");
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Servicio iniciado...");
        }

        if(intent != null) {
            startCallService(intent);
        } else {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Servicio reiniciado probablemente por el sistema...");
            }
        }


        return START_NOT_STICKY;
    }

    @Override
    public void onDestroy() {
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "Servicio destruido...");
        }

        unSetAudioFocus();
    }

    private void startForegroundService(String callSid) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Foreground services not required before SDK 28
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Servicio not started required before SDK 28");
            }
            return;
        }

        String caller = TwilioVoiceService.toNumber;
        if (TwilioVoiceService.toName != null && !TwilioVoiceService.toName.equals("")) {
            caller = TwilioVoiceService.toName;
        }

        startForeground(FOREGROUND_SERVICE_TYPE_MICROPHONE, TwilioVoiceService.callNotificationManager.getHangupLocalNotification(getReactApplicationContext(), callSid, caller, TwilioVoiceService.progressText, TwilioVoiceService.progressAction));
        TwilioVoiceService.inForegroundService = true;
    }

    private void stopForegroundService() {
        if(TwilioVoiceService.inForegroundService) {
            if (BuildConfig.DEBUG) {
                Log.d(TAG, "Servicio stoped required before SDK 28");
            }
            stopForeground(true);
            TwilioVoiceService.inForegroundService = false;
        }

        stopSelf();
    }

    private void startCallService(Intent intent) {
        TwilioVoiceService.proximityViaPowerManager = new ProximityViaPowerManager(getReactApplicationContext());

        TwilioVoiceService.headsetManager = new HeadsetManager(null);
        TwilioVoiceService.audioManager = (AudioManager) getReactApplicationContext().getSystemService(Context.AUDIO_SERVICE);
        TwilioVoiceService.callNotificationManager = new CallNotificationManager();

        TwilioVoiceService.toNumber = intent.getStringExtra(CALLER_NUMBER);
        TwilioVoiceService.toName = intent.getStringExtra(CALLER_NAME);
        String accessToken = intent.getStringExtra(ACCESS_TOKEN);
        TwilioVoiceService.progressText = intent.getStringExtra(NOTIFICATION_CALL_PROGRESS_TEXT);
        TwilioVoiceService.progressAction = intent.getStringExtra(NOTIFICATION_CALL_PROGRESS_ACTION);
        startCall(accessToken);
    }

    private void startCall(String accessToken) {

        if(TwilioVoiceService.activeCallInvite != null) {
            TwilioVoiceService.toNumber = TwilioVoiceService.activeCallInvite.getFrom();
            TwilioVoiceService.toName = TwilioVoiceService.activeCallInvite.getFrom();

            AcceptOptions acceptOptions = new AcceptOptions.Builder()
                    .enableDscp(true)
                    .build();
            TwilioVoiceService.activeCall = TwilioVoiceService.activeCallInvite.accept(getReactApplicationContext(), acceptOptions, callListener);
            TwilioVoiceService.activeCallInvite = null;
            return;
        }

        TwilioVoiceService.params.put("To", TwilioVoiceService.toNumber);
        ConnectOptions connectOptions = new ConnectOptions.Builder(accessToken)
                .params(TwilioVoiceService.params)
                .build();

        TwilioVoiceService.activeCall = Voice.connect(this, connectOptions, callListener);
    }

    private Call.Listener callListener() {
        return new Call.Listener() {

            @Override
            public void onConnectFailure(@NonNull Call call, @NonNull CallException callException) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CALL FAILURE callListener().onConnectFailure call state = "+call.getState());
                }
                TwilioVoiceService.unSetAudioFocus();
                TwilioVoiceService.proximityViaPowerManager.desactivate();
                Bundle extras = new Bundle();
                String callSid = "";
                if (call != null) {
                    callSid = call.getSid();
                    extras.putString("call_sid", callSid);
                    extras.putString("call_state", call.getState().name());
                    extras.putString("call_from", call.getFrom());
                    extras.putString("call_to", call.getTo());
                }
                if (callException != null) {
                    Log.e(TAG, String.format("CallListener onConnectFailure error: %d, %s",
                            callException.getErrorCode(), callException.getMessage()));
                    extras.putString("err", callException.getMessage());
                }
                if (callSid != null && TwilioVoiceService.activeCall != null && TwilioVoiceService.activeCall.getSid() != null && TwilioVoiceService.activeCall.getSid().equals(callSid)) {
                    TwilioVoiceService.activeCall = null;
                }

                stopForegroundService();
                TwilioVoiceService.toNumber = "";
                TwilioVoiceService.toName = "";
                publishEvent(ACTION_CALL_CONNECTION_DID_DISCONNECT, extras);
            }

            @Override
            public void onRinging(@NonNull Call call) {
                // TODO test this with JS app
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CALL RINGING callListener().onRinging call state = "+call.getState());
                    Log.d(TAG, call.toString());
                }
                Bundle extras = new Bundle();
                if (call != null) {
                    extras.putString("call_sid",   call.getSid());
                    extras.putString("call_from",  call.getFrom());
                }

                publishEvent(ACTION_CALL_STATE_RINGING, extras);
            }

            @Override
            public void onConnected(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CALL CONNECTED callListener().onConnected call state = "+call.getState());
                }
                TwilioVoiceService.setAudioFocus();
                TwilioVoiceService.proximityViaPowerManager.activate();
                TwilioVoiceService.headsetManager.startWiredHeadsetEvent(getReactApplicationContext());

                Bundle extras = new Bundle();
                if (call != null) {
                    extras.putString("call_sid",   call.getSid());
                    extras.putString("call_state", call.getState().name());
                    extras.putString("call_from", call.getFrom());
                    extras.putString("call_to", call.getTo());
                    TwilioVoiceService.activeCall = call;
                    startForegroundService(call.getSid());
                }

                publishEvent(ACTION_CALL_CONNECTION_DID_CONNECT, extras);
            }

            @Override
            public void onReconnecting(@NonNull Call call, @NonNull CallException callException) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CALL RECONNECTING callListener().onReconnecting call state = "+call.getState());
                }
                Bundle extras = new Bundle();
                if (call != null) {
                    extras.putString("call_sid",   call.getSid());
                    extras.putString("call_from", call.getFrom());
                    extras.putString("call_to", call.getTo());
                }

                publishEvent(ACTION_CALL_CONNECTION_IS_RECONNECTING, extras);
            }

            @Override
            public void onReconnected(@NonNull Call call) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CALL RECONNECTED callListener().onReconnected call state = "+call.getState());
                }
                Bundle extras = new Bundle();
                if (call != null) {
                    extras.putString("call_sid",   call.getSid());
                    extras.putString("call_from", call.getFrom());
                    extras.putString("call_to", call.getTo());
                }

                publishEvent(ACTION_CALL_CONNECTION_DID_RECONNECT, extras);
            }

            @Override
            public void onDisconnected(@NonNull Call call, @Nullable CallException callException) {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "CALL DISCONNECTED callListener().onDisconnected call state = "+call.getState());
                }
                TwilioVoiceService.unSetAudioFocus();
                TwilioVoiceService.proximityViaPowerManager.desactivate();
                TwilioVoiceService.headsetManager.stopWiredHeadsetEvent(getReactApplicationContext());

                Bundle extras = new Bundle();
                String callSid = "";
                if (call != null) {
                    callSid = call.getSid();
                    extras.putString("call_sid", callSid);
                    extras.putString("call_state", call.getState().name());
                    extras.putString("call_from", call.getFrom());
                    extras.putString("call_to", call.getTo());
                }
                if (callException != null) {
                    Log.e(TAG, String.format("CallListener onDisconnected error: %d, %s",
                            callException.getErrorCode(), callException.getMessage()));
                    extras.putString("err", callException.getMessage());
                }
                if (callSid != null && TwilioVoiceService.activeCall != null && TwilioVoiceService.activeCall.getSid() != null && TwilioVoiceService.activeCall.getSid().equals(callSid)) {
                    TwilioVoiceService.activeCall = null;
                }

                stopForegroundService();
                TwilioVoiceService.toNumber = "";
                TwilioVoiceService.toName = "";
                publishEvent(ACTION_CALL_CONNECTION_DID_DISCONNECT, extras);
            }
        };
    }

    private static void setAudioFocus() {
        if (TwilioVoiceService.audioManager == null) {
            TwilioVoiceService.audioManager.setMode(TwilioVoiceService.originalAudioMode);
            TwilioVoiceService.audioManager.abandonAudioFocus(null);
            return;
        }
        TwilioVoiceService.originalAudioMode = TwilioVoiceService.audioManager.getMode();
        // Request audio focus before making any device switch
        if (Build.VERSION.SDK_INT >= 26) {
            AudioAttributes playbackAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            TwilioVoiceService.focusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                    .setAudioAttributes(playbackAttributes)
                    .setAcceptsDelayedFocusGain(true)
                    .setOnAudioFocusChangeListener(i -> { })
                    .build();
            TwilioVoiceService.audioManager.requestAudioFocus(TwilioVoiceService.focusRequest);
        } else {
            int focusRequestResult = TwilioVoiceService.audioManager.requestAudioFocus(focusChange -> {},
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        }
        /*
         * Start by setting MODE_IN_COMMUNICATION as default audio mode. It is
         * required to be in this mode when playout and/or recording starts for
         * best possible VoIP performance. Some devices have difficulties with speaker mode
         * if this is not set.
         */
        TwilioVoiceService.audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
    }

    private static void unSetAudioFocus() {
        if (TwilioVoiceService.audioManager == null) {
            TwilioVoiceService.audioManager.setMode(TwilioVoiceService.originalAudioMode);
            TwilioVoiceService.audioManager.abandonAudioFocus(null);
            return;
        }
        TwilioVoiceService.audioManager.setMode(TwilioVoiceService.originalAudioMode);
        if (Build.VERSION.SDK_INT >= 26) {
            if (TwilioVoiceService.focusRequest != null) {
                TwilioVoiceService.audioManager.abandonAudioFocusRequest(TwilioVoiceService.focusRequest);
            }
        } else {
            TwilioVoiceService.audioManager.abandonAudioFocus(null);
        }
    }

    private void publishEvent(String action, Bundle extras) {
        Intent intent = new Intent(action);

        if(extras != null && !extras.isEmpty()) {
            intent.putExtras(extras);
        }

        LocalBroadcastManager.getInstance(getReactApplicationContext()).sendBroadcast(intent);

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "TwilioVoiceCallService.publishEvent "+action+". Intent "+ intent.getExtras());
        }
    }

    public static Call getActiveCall() {
        return TwilioVoiceService.activeCall;
    }

    public static String getToNumber() {
        return TwilioVoiceService.toNumber;
    }

    public static void setSpeakerPhone(Boolean value) {
        TwilioVoiceService.setAudioFocus();
        TwilioVoiceService.audioManager.setSpeakerphoneOn(value);
    }

    public static CallInvite getActiveCallInvite() {
        return activeCallInvite;
    }

    public static void setActiveCallInvite(CallInvite activeCallInvite) {
        TwilioVoiceService.activeCallInvite = activeCallInvite;
    }

}