package com.gateless.listening;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import androidx.core.app.NotificationCompat;
import java.util.HashMap;
import java.util.Locale;

public class TtsService extends Service {

    private static final String CHANNEL_ID = "listening_tts";
    private static final int NOTIF_ID = 1;

    private TextToSpeech tts;
    private boolean ttsReady = false;
    private boolean speaking = false;
    private boolean paused = false;
    private Runnable onDone;

    private final IBinder binder = new TtsBinder();

    public class TtsBinder extends Binder {
        TtsService getService() { return TtsService.this; }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, buildNotification("Ready"), 1073741824); // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        } else {
            startForeground(NOTIF_ID, buildNotification("Ready"));
        }

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.KOREAN);
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { speaking = true; updateNotification("Playing"); }
                    @Override public void onDone(String id) {
                        speaking = false;
                        if (!paused && onDone != null) onDone.run();
                    }
                    @Override public void onError(String id) {
                        speaking = false;
                        if (!paused && onDone != null) onDone.run();
                    }
                });
                ttsReady = true;
            }
        });
    }

    public void speak(String text, float rate, float pitch) {
        if (!ttsReady) return;
        paused = false;
        tts.setSpeechRate(rate);
        tts.setPitch(pitch);
        HashMap<String, String> params = new HashMap<>();
        params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utt");
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        updateNotification("Playing");
    }

    public void stop() {
        if (!ttsReady) return;
        paused = false;
        tts.stop();
        speaking = false;
        updateNotification("Stopped");
    }

    public void pause() {
        if (!ttsReady) return;
        paused = true;
        tts.stop();
        speaking = false;
        updateNotification("Paused");
    }

    public boolean isSpeaking() { return speaking; }

    public void setOnDone(Runnable r) { onDone = r; }

    private void createNotificationChannel() {
        NotificationChannel ch = new NotificationChannel(
            CHANNEL_ID, "Listening TTS", NotificationManager.IMPORTANCE_LOW);
        ch.setDescription("Background TTS");
        getSystemService(NotificationManager.class).createNotificationChannel(ch);
    }

    private Notification buildNotification(String status) {
        Intent intent = new Intent(this, MainActivity.class);
        PendingIntent pi = PendingIntent.getActivity(this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Listening")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build();
    }

    private void updateNotification(String status) {
        Notification n = buildNotification(status);
        getSystemService(NotificationManager.class).notify(NOTIF_ID, n);
    }

    @Override
    public IBinder onBind(Intent intent) { return binder; }

    @Override
    public void onDestroy() {
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}