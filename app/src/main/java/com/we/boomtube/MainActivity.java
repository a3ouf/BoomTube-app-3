package com.we.boomtube;

import android.Manifest;
import android.app.Activity;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.security.crypto.EncryptedSharedPreferences;
import androidx.security.crypto.MasterKey;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {

    // ---- Change these two lines if the server address ever changes ----
    private static final String HOST = "213.158.167.154";
    private static final String START_URL = "https://213.158.167.154:4042/boom2";
    // -------------------------------------------------------------------

    private static final int REQ_LOCATION = 1;
    private static final int REQ_STORAGE = 2;

    private WebView web;
    private SharedPreferences prefs;          // encrypted; may be null if keystore fails
    private String injectJs = "";
    private boolean autoLoginConsumed = false;

    private GeolocationPermissions.Callback geoCallback;
    private String geoOrigin;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.parseColor("#2A2A28"));

        prefs = openPrefs();
        injectJs = readAsset("inject.js");

        if (Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
        }

        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // localStorage / sessionStorage persist between launches
        s.setGeolocationEnabled(true);
        s.setAllowFileAccess(false);
        s.setSupportMultipleWindows(false);
        s.setMediaPlaybackRequiresUserGesture(true);

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(web, true);
        restoreCookies(cm);

        web.addJavascriptInterface(new Bridge(), "VASApp");

        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) { }
                return true;
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                // The server uses a self-signed certificate: trust it for OUR host only.
                String h = Uri.parse(error.getUrl()).getHost();
                if (HOST.equals(h)) handler.proceed(); else handler.cancel();
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript(injectJs, null);
                CookieManager.getInstance().flush();
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    String html = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>"
                            + "<body style='font-family:sans-serif;text-align:center;padding:48px 24px;color:#333'>"
                            + "<h2 style='color:#2A2A28'>Can't reach the server</h2>"
                            + "<p>Check your network / VPN and try again.</p>"
                            + "<p><a href='" + START_URL + "' style='display:inline-block;padding:12px 28px;"
                            + "background:#2A2A28;color:#fff;border-radius:8px;text-decoration:none'>Retry</a></p>"
                            + "</body></html>";
                    view.loadDataWithBaseURL(null, html, "text/html", "utf-8", null);
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
                if (hasLocationPermission()) {
                    callback.invoke(origin, true, true);
                } else {
                    geoOrigin = origin;
                    geoCallback = callback;
                    requestPermissions(new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION}, REQ_LOCATION);
                }
            }
        });

        // Downloads (Excel reports etc.): re-fetch inside the page so cookies / self-signed cert just work
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            String name = URLUtil.guessFileName(url, contentDisposition, mimeType);
            String mime = (mimeType == null || mimeType.isEmpty()) ? "application/octet-stream" : mimeType;
            String js = "(function(){fetch(" + JSONObject.quote(url) + ",{credentials:'include'})"
                    + ".then(function(r){return r.blob();})"
                    + ".then(function(b){var fr=new FileReader();fr.onload=function(){"
                    + "VASApp.saveFile(fr.result," + JSONObject.quote(name) + "," + JSONObject.quote(mime) + ");};"
                    + "fr.readAsDataURL(b);})"
                    + ".catch(function(e){VASApp.toast('Download failed');});})();";
            web.evaluateJavascript(js, null);
        });

        web.loadUrl(START_URL);
    }

    // ------------------------------------------------------------------ permissions
    private boolean hasLocationPermission() {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_LOCATION && geoCallback != null) {
            boolean ok = false;
            for (int r : grantResults) if (r == PackageManager.PERMISSION_GRANTED) ok = true;
            geoCallback.invoke(geoOrigin, ok, ok);
            geoCallback = null;
            geoOrigin = null;
        }
    }

    // ------------------------------------------------------------------ session (cookies)
    private void saveCookies() {
        if (prefs == null) return;
        CookieManager cm = CookieManager.getInstance();
        cm.flush();
        String c = cm.getCookie(START_URL);
        if (c == null || c.isEmpty()) prefs.edit().remove("cookies").apply();   // logged out
        else prefs.edit().putString("cookies", c).apply();
    }

    /** Session cookies (no expiry) are normally lost when Android kills the app; re-install them with a 30-day lifetime. */
    private void restoreCookies(CookieManager cm) {
        if (prefs == null) return;
        String now = cm.getCookie(START_URL);
        if (now != null && !now.isEmpty()) return;
        String saved = prefs.getString("cookies", null);
        if (saved == null) return;
        for (String part : saved.split(";")) {
            String p = part.trim();
            if (p.isEmpty() || !p.contains("=")) continue;
            cm.setCookie(START_URL, p + "; Max-Age=2592000; Path=/; Secure");
        }
        cm.flush();
    }

    @Override
    protected void onPause() {
        saveCookies();
        super.onPause();
    }

    @Override
    protected void onStop() {
        saveCookies();
        super.onStop();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }

    // ------------------------------------------------------------------ helpers
    private SharedPreferences openPrefs() {
        try {
            MasterKey mk = new MasterKey.Builder(this)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build();
            return EncryptedSharedPreferences.create(this, "vas_secure", mk,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM);
        } catch (Exception e) {
            return null;
        }
    }

    private String readAsset(String name) {
        try (InputStream in = getAssets().open(name)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            return "";
        }
    }

    private void toastUi(final String msg) {
        runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_LONG).show());
    }

    // ------------------------------------------------------------------ JS bridge
    private class Bridge {

        @JavascriptInterface
        public String getCreds() {
            if (prefs == null) return "";
            String p = prefs.getString("p", null);
            if (p == null) return "";
            try {
                JSONObject o = new JSONObject();
                o.put("u", prefs.getString("u", ""));
                o.put("p", p);
                return o.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void saveCreds(String user, String pass) {
            if (prefs == null || pass == null || pass.isEmpty()) return;
            prefs.edit().putString("u", user == null ? "" : user).putString("p", pass).apply();
        }

        /** True only once per app launch, so a failed/logged-out login never loops. */
        @JavascriptInterface
        public boolean consumeAutoLogin() {
            if (autoLoginConsumed) return false;
            autoLoginConsumed = true;
            return true;
        }

        @JavascriptInterface
        public void toast(String msg) {
            toastUi(msg);
        }

        @JavascriptInterface
        public void saveFile(String dataUrl, String name, String mime) {
            try {
                byte[] bytes = Base64.decode(dataUrl.substring(dataUrl.indexOf(',') + 1), Base64.DEFAULT);
                if (Build.VERSION.SDK_INT >= 29) {
                    ContentValues v = new ContentValues();
                    v.put(MediaStore.Downloads.DISPLAY_NAME, name);
                    v.put(MediaStore.Downloads.MIME_TYPE, mime);
                    v.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS);
                    Uri uri = getContentResolver().insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
                    try (OutputStream os = getContentResolver().openOutputStream(uri)) {
                        os.write(bytes);
                    }
                } else {
                    File dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
                    dir.mkdirs();
                    try (FileOutputStream os = new FileOutputStream(new File(dir, name))) {
                        os.write(bytes);
                    }
                }
                toastUi("Saved to Downloads: " + name);
            } catch (Exception e) {
                toastUi("Could not save file");
            }
        }
    }
}
