package ua.pp.povitryanavarta.radar;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.content.ContentValues;
import android.os.Build;
import android.util.Base64;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import java.io.OutputStream;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String URL = "https://radar.povitryanavarta.pp.ua/";
    private WebView web;
    private static final String DOWNLOAD_SCRIPT = "(function(){if(window.__pvDownloadHook)return;window.__pvDownloadHook=true;document.addEventListener('click',async function(e){let a=e.target.closest('a[download]');if(!a)return;let url=a.href;if(!url.startsWith('blob:')&&!url.startsWith('data:image/png'))return;e.preventDefault();try{let b=await(await fetch(url)).blob();if(b.type!=='image/png')return;let r=new FileReader();r.onload=function(){window.PVAndroid.savePNG(String(r.result))};r.readAsDataURL(b);}catch(err){console.error(err)}},true)})()";
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setStatusBarColor(Color.rgb(7,19,33));
        getWindow().setNavigationBarColor(Color.rgb(7,19,33));
        web = new WebView(this);
        setContentView(web);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setBackgroundColor(Color.rgb(7,19,33));
        CookieManager.getInstance().setAcceptCookie(true);
        web.addJavascriptInterface(new PNGBridge(), "PVAndroid");
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String h = u.getHost();
                if ("https".equals(u.getScheme()) && ("radar.povitryanavarta.pp.ua".equalsIgnoreCase(h))) return false;
                if (request.isForMainFrame()) {
                    try { startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                    return true;
                }
                return false;
            }
            @Override public void onPageFinished(WebView view, String url) {
                if (url.startsWith(URL)) view.evaluateJavascript(DOWNLOAD_SCRIPT, null);
            }
        });
        web.setDownloadListener((url, userAgent, contentDisposition, mimeType, contentLength) -> {
            if (!url.startsWith("https://")) return;
            try {
                DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
                request.setMimeType(mimeType);
                request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                request.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, "povitryana-varta-download");
                ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
                Toast.makeText(this, "Завантаження почалося", Toast.LENGTH_SHORT).show();
            } catch (Exception ex) { Toast.makeText(this, "Помилка завантаження", Toast.LENGTH_SHORT).show(); }
        });
        if (state != null) web.restoreState(state); else web.loadUrl(URL);
    }
    private class PNGBridge {
        @JavascriptInterface public void savePNG(String dataUrl) {
            if (dataUrl == null || !dataUrl.startsWith("data:image/png;base64,") || dataUrl.length() > 21_000_000) return;
            try {
                byte[] bytes = Base64.decode(dataUrl.substring("data:image/png;base64,".length()), Base64.DEFAULT);
                if (bytes.length < 8 || bytes[0] != (byte)137 || bytes[1] != 80 || bytes[2] != 78 || bytes[3] != 71) return;
                ContentValues values = new ContentValues();
                values.put(MediaStore.Images.Media.DISPLAY_NAME, "PovitryanaVarta-" + System.currentTimeMillis() + ".png");
                values.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PovitryanaVarta");
                // Scoped MediaStore writes are supported without storage permissions on Android 10+.
                if (Build.VERSION.SDK_INT < 29) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this, "Для PNG потрібен Android 10+", Toast.LENGTH_LONG).show());
                    return;
                }
                Uri result = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);
                if (result == null) throw new IllegalStateException("Insert failed");
                try (OutputStream out = getContentResolver().openOutputStream(result)) { if (out == null) throw new IllegalStateException("Stream failed"); out.write(bytes); }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "PNG збережено у Галерею", Toast.LENGTH_SHORT).show());
            } catch (Exception e) { runOnUiThread(() -> Toast.makeText(MainActivity.this, "Не вдалося зберегти PNG", Toast.LENGTH_LONG).show()); }
        }
    }
    @Override public void onBackPressed() { if (web.canGoBack()) web.goBack(); else super.onBackPressed(); }
    @Override protected void onSaveInstanceState(Bundle out) { web.saveState(out); super.onSaveInstanceState(out); }
    @Override protected void onDestroy() { web.destroy(); super.onDestroy(); }
}
