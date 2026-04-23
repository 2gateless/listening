package com.gateless.listening;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.os.PowerManager;
import android.content.Context;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import java.util.HashMap;
import java.util.Locale;

public class MainActivity extends Activity {

    private WebView webView;
    private TextToSpeech tts;
    private boolean ttsReady = false;
    private ValueCallback<Uri[]> fileChooserCallback;
    private PowerManager.WakeLock wakeLock;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON |
                        WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED |
                        WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
        );

        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null) {
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Listening::WakeLock");
        }

        WebView.setWebContentsDebuggingEnabled(true);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setMediaPlaybackRequiresUserGesture(false);

        webView.addJavascriptInterface(new JsBridge(), "AndroidTTS");

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onPermissionRequest(PermissionRequest request) {
                request.grant(request.getResources());
            }

            @Override
            public boolean onShowFileChooser(WebView wv,
                                             ValueCallback<Uri[]> filePathCallback,
                                             FileChooserParams fileChooserParams) {
                fileChooserCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, 1001);
                } catch (Exception e) {
                    fileChooserCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl("file:///android_asset/public/index.html");

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(Locale.KOREAN);
                if (result == TextToSpeech.LANG_MISSING_DATA ||
                        result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault());
                }
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {}

                    @Override
                    public void onDone(String id) {
                        runOnUiThread(() ->
                                webView.evaluateJavascript("onNativeTtsDone()", null)
                        );
                    }

                    @Override
                    public void onError(String id) {
                        runOnUiThread(() ->
                                webView.evaluateJavascript("onNativeTtsDone()", null)
                        );
                    }
                });
                ttsReady = true;
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1001 && fileChooserCallback != null) {
            Uri[] results = null;
            if (resultCode == RESULT_OK && data != null) {
                results = new Uri[]{data.getData()};
            }
            fileChooserCallback.onReceiveValue(results);
            fileChooserCallback = null;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    public class JsBridge {
        @JavascriptInterface
        public void speak(String text, float rate, float pitch) {
            if (!ttsReady) return;
            tts.setSpeechRate(rate);
            tts.setPitch(pitch);
            HashMap<String, String> params = new HashMap<>();
            params.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "utt");
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, params);
        }

        @JavascriptInterface
        public void stop() {
            if (!ttsReady) return;
            tts.stop();
        }

        @JavascriptInterface
        public void pause() {
            if (!ttsReady) return;
            tts.stop();
        }

        @JavascriptInterface
        public boolean isSpeaking() {
            return ttsReady && tts.isSpeaking();
        }

        @JavascriptInterface
        public void setOnDoneCallback() {}

        @JavascriptInterface
        public void acquireCpuWakeLock() {
            runOnUiThread(() -> {
                if (wakeLock != null && !wakeLock.isHeld()) {
                    wakeLock.acquire(4 * 60 * 60 * 1000L);
                }
            });
        }

        @JavascriptInterface
        public void releaseCpuWakeLock() {
            runOnUiThread(() -> {
                if (wakeLock != null && wakeLock.isHeld()) {
                    wakeLock.release();
                }
            });
        }

        @JavascriptInterface
        public void startForeground() {
            Intent intent = new Intent(MainActivity.this, TtsService.class);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                startForegroundService(intent);
            } else {
                startService(intent);
            }
        }

        @JavascriptInterface
        public void stopForeground() {
            Intent intent = new Intent(MainActivity.this, TtsService.class);
            stopService(intent);
        }
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }

    @Override
    protected void onDestroy() {
        if (wakeLock != null && wakeLock.isHeld()) {
            wakeLock.release();
        }
        if (tts != null) { tts.stop(); tts.shutdown(); }
        super.onDestroy();
    }
}