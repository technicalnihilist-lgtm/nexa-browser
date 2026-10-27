package com.rajok.glassbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.provider.MediaStore;
import android.util.Base64;
import android.view.Gravity;
import android.view.PixelCopy;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.GeolocationPermissions;
import android.webkit.JavascriptInterface;
import android.webkit.PermissionRequest;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends Activity {

    private FrameLayout root;
    private WebView content;
    private WebView chrome;
    private float density = 1f;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private ValueCallback<Uri[]> filePathCallback;
    private Uri lastShot = null;
    private boolean pendingShot = false;
    private String pendingUrl = null;
    private boolean userNav = false;
    private static final int REQ_FILE = 101;
    private static final int REQ_PERM_SHOT = 102;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;

        Window w = getWindow();
        w.setStatusBarColor(Color.TRANSPARENT);
        w.setNavigationBarColor(Color.TRANSPARENT);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        View dec = w.getDecorView();
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        boolean night = (getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (!night) flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        dec.setSystemUiVisibility(flags);

        root = new FrameLayout(this);
        root.setBackgroundColor(Color.WHITE);

        content = new WebView(this);
        content.setBackgroundColor(Color.WHITE);
        root.addView(content, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        chrome = new WebView(this);
        chrome.setBackgroundColor(Color.TRANSPARENT);
        chrome.setVerticalScrollBarEnabled(false);
        chrome.setHorizontalScrollBarEnabled(false);
        chrome.setOverScrollMode(View.OVER_SCROLL_NEVER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.round(132 * density));
        clp.gravity = Gravity.BOTTOM;
        root.addView(chrome, clp);

        setContentView(root);
        root.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                v.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(),
                        insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
                return insets;
            }
        });

        setupContent();
        setupChrome();
        pendingUrl = urlFrom(getIntent());
        chrome.loadUrl("file:///android_asset/index.html");
    }

    private void setupContent() {
        WebSettings s = content.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setSupportMultipleWindows(true);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(true);
        } else if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(WebSettings.FORCE_DARK_AUTO);
        }
        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(content, true);

        content.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                if (r != null && r.isForMainFrame()) userNav = true;
                return handleUrl(r.getUrl().toString());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String u) {
                userNav = true;
                return handleUrl(u);
            }

            @Override
            public void onPageStarted(WebView v, String u, android.graphics.Bitmap f) {
                js("window.BN&&window.BN.on({t:'start'})");
            }

            @Override
            public void onPageFinished(WebView v, String u) {
                js("window.BN&&window.BN.on({t:'done'})");
            }

            @Override
            public void doUpdateVisitedHistory(WebView v, String u, boolean reload) {
                if (!reload && u != null && !u.startsWith("about:")) {
                    boolean push = userNav;
                    userNav = false;
                    js("window.BN&&window.BN.on({t:'visited',push:" + push + ",url:" + quote(u) + "})");
                }
            }

            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
                if (r != null && r.isForMainFrame()) {
                    js("window.BN&&window.BN.on({t:'error',desc:" + quote(String.valueOf(e.getDescription())) + "})");
                }
            }

            @Override
            public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError err) {
                h.cancel();
                js("window.BN&&window.BN.on({t:'error',desc:'This site’s security certificate isn’t valid, so the connection was blocked.'})");
            }
        });

        content.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView v, int p) {
                js("window.BN&&window.BN.on({t:'prog',p:" + p + "})");
            }

            @Override
            public void onReceivedTitle(WebView v, String t) {
            }

            @Override
            public void onPermissionRequest(final PermissionRequest request) {
                ui.post(new Runnable() {
                    public void run() { request.deny(); }
                });
            }

            @Override
            public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback cb) {
                cb.invoke(origin, false, false);
            }

            @Override
            public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb, FileChooserParams params) {
                if (filePathCallback != null) filePathCallback.onReceiveValue(null);
                filePathCallback = cb;
                try {
                    Intent i = params.createIntent();
                    startActivityForResult(Intent.createChooser(i, "Choose file"), REQ_FILE);
                    return true;
                } catch (Exception e) {
                    filePathCallback = null;
                    return false;
                }
            }

            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
                final WebView tmp = new WebView(MainActivity.this);
                tmp.getSettings().setJavaScriptEnabled(true);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                        openPopup(r.getUrl().toString());
                        return true;
                    }
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, String u) {
                        openPopup(u);
                        return true;
                    }
                    @Override
                    public void onPageStarted(WebView v, String u, android.graphics.Bitmap f) {
                        if (u != null && !u.startsWith("about:")) openPopup(u);
                    }
                });
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
                root.addView(tmp, lp);
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(tmp);
                resultMsg.sendToTarget();
                return true;
            }
        });

        content.setDownloadListener(new DownloadListener() {
            @Override
            public void onDownloadStart(String url, String ua, String cd, String mime, long len) {
                try {
                    String name = URLUtil.guessFileName(url, cd, mime);
                    DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
                    r.setMimeType(mime);
                    r.addRequestHeader("User-Agent", ua);
                    String cookie = CookieManager.getInstance().getCookie(url);
                    if (cookie != null) r.addRequestHeader("Cookie", cookie);
                    r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
                    r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
                    DownloadManager dm = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
                    if (dm != null) dm.enqueue(r);
                    toast("Downloading " + name);
                } catch (Exception e) {
                    toast("Couldn’t start that download");
                }
            }
        });
    }

    private void openPopup(String u) {
        js("window.BN&&window.BN.newTab(" + quote(u) + ")");
    }

    private boolean handleUrl(String u) {
        if (u == null) return false;
        if (u.startsWith("http://") || u.startsWith("https://") || u.startsWith("about:")) return false;
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (Exception ignored) { }
        return true;
    }

    private void setupChrome() {
        WebSettings s = chrome.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(false);
        s.setAllowFileAccess(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        chrome.addJavascriptInterface(new Bridge(), "Android");
        chrome.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String u) {
                return true;
            }
            @Override
            public void onPageFinished(WebView v, String u) {
                if (pendingUrl != null) {
                    String t = pendingUrl;
                    pendingUrl = null;
                    js("window.BN&&window.BN.newTab(" + quote(t) + ")");
                }
            }
        });
        chrome.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(android.webkit.ConsoleMessage m) {
                android.util.Log.d("GlassChrome", m.message() + " @" + m.lineNumber());
                return true;
            }
        });
    }

    private void js(final String code) {
        ui.post(new Runnable() {
            public void run() {
                if (chrome != null) chrome.evaluateJavascript(code, null);
            }
        });
    }

    private static String quote(String s) {
        if (s == null) return "''";
        StringBuilder sb = new StringBuilder("'");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' || c == '\'') sb.append('\\').append(c);
            else if (c == '\n') sb.append("\\n");
            else if (c == '\r') sb.append("\\r");
            else if (c == '<') sb.append("\\u003c");
            else if (c == '>') sb.append("\\u003e");
            else sb.append(c);
        }
        return sb.append("'").toString();
    }

    private void toast(final String m) {
        ui.post(new Runnable() {
            public void run() { Toast.makeText(MainActivity.this, m, Toast.LENGTH_SHORT).show(); }
        });
    }

    /* ---------------- JS bridge ---------------- */
    public class Bridge {
        @JavascriptInterface
        public void nav(String u) {
            if (u == null || u.isEmpty()) return;
            ui.post(new Runnable() {
                public void run() { content.loadUrl(u); }
            });
        }

        @JavascriptInterface
        public void blank() {
            ui.post(new Runnable() {
                public void run() {
                    content.stopLoading();
                    content.loadUrl("about:blank");
                }
            });
        }

        @JavascriptInterface
        public void stop() {
            ui.post(new Runnable() { public void run() { content.stopLoading(); } });
        }

        @JavascriptInterface
        public void setChrome(final int h) {
            ui.post(new Runnable() {
                public void run() {
                    ViewGroup.LayoutParams lp = chrome.getLayoutParams();
                    ViewGroup.MarginLayoutParams cp = (ViewGroup.MarginLayoutParams) content.getLayoutParams();
                    if (h < 0) {
                        lp.height = ViewGroup.LayoutParams.MATCH_PARENT;
                        cp.bottomMargin = 0;
                    } else {
                        lp.height = Math.round(h * density);
                        cp.bottomMargin = lp.height;
                    }
                    chrome.setLayoutParams(lp);
                    content.setLayoutParams(cp);
                }
            });
        }

        @JavascriptInterface
        public void setPopups(final boolean on) {
            ui.post(new Runnable() {
                public void run() {
                    content.getSettings().setJavaScriptCanOpenWindowsAutomatically(on);
                }
            });
        }

        @JavascriptInterface
        public void share(final String u) {
            ui.post(new Runnable() {
                public void run() {
                    try {
                        Intent i = new Intent(Intent.ACTION_SEND);
                        i.setType("text/plain");
                        i.putExtra(Intent.EXTRA_TEXT, u);
                        startActivity(Intent.createChooser(i, "Share link"));
                    } catch (Exception e) {
                        toast("Nothing to share with");
                    }
                }
            });
        }

        @JavascriptInterface
        public void openExternal(final String u) {
            ui.post(new Runnable() {
                public void run() {
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                    } catch (Exception e) {
                        toast("No other browser installed");
                    }
                }
            });
        }

        @JavascriptInterface
        public void screenshot() {
            ui.post(new Runnable() { public void run() { capture(); } });
        }

        @JavascriptInterface
        public void exit() {
            ui.post(new Runnable() { public void run() { finish(); } });
        }

        @JavascriptInterface
        public void ready() { }
    }

    /* ---------------- screenshot ---------------- */
    private void capture() {
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingShot = true;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM_SHOT);
            return;
        }
        View v = getWindow().getDecorView();
        final int w = v.getWidth(), h = v.getHeight();
        if (w <= 0 || h <= 0) { shotResult(false, "Couldn’t capture the screen"); return; }
        final Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                PixelCopy.request(getWindow(), bmp, new PixelCopy.OnPixelCopyFinishedListener() {
                    @Override
                    public void onPixelCopyFinished(int result) {
                        if (result == PixelCopy.SUCCESS) saveShot(bmp);
                        else shotResult(false, "Couldn’t capture the screen");
                    }
                }, ui);
                return;
            } catch (Exception e) {
                // fall through to draw()
            }
        }
        try {
            Canvas c = new Canvas(bmp);
            v.draw(c);
            saveShot(bmp);
        } catch (Exception e) {
            shotResult(false, "Couldn’t capture the screen");
        }
    }

    private void saveShot(Bitmap bmp) {
        String name = "GlassBrowser_" + new SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(new Date()) + ".png";
        try {
            if (Build.VERSION.SDK_INT >= 29) {
                ContentValues cv = new ContentValues();
                cv.put(MediaStore.Images.Media.DISPLAY_NAME, name);
                cv.put(MediaStore.Images.Media.MIME_TYPE, "image/png");
                cv.put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Glass Browser");
                Uri uri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv);
                if (uri == null) throw new Exception("no uri");
                OutputStream os = getContentResolver().openOutputStream(uri);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, os);
                os.close();
                lastShot = uri;
                shotResult(true, "Screenshot saved to Pictures");
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "Glass Browser");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                FileOutputStream fo = new FileOutputStream(f);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
                fo.close();
                lastShot = Uri.fromFile(f);
                android.media.MediaScannerConnection.scanFile(this, new String[]{f.getAbsolutePath()}, null, null);
                shotResult(true, "Screenshot saved to Pictures");
            }
        } catch (Exception e) {
            shotResult(false, "Couldn’t save the screenshot");
        }
    }

    private void shotResult(boolean ok, String msg) {
        js("window.BN&&window.BN.on({t:'shot',ok:" + ok + ",msg:" + quote(msg) + "})");
        if (ok) {
            // offer to share right away from the native side too
            toast(msg);
        }
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] res) {
        super.onRequestPermissionsResult(req, perms, res);
        if (req == REQ_PERM_SHOT) {
            boolean granted = res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingShot) {
                pendingShot = false;
                capture();
            } else {
                pendingShot = false;
                shotResult(false, "Storage permission is needed to save screenshots");
            }
        }
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        if (req == REQ_FILE) {
            if (filePathCallback != null) {
                Uri[] out = null;
                if (res == RESULT_OK && data != null) {
                    out = WebChromeClient.FileChooserParams.parseResult(res, data);
                }
                filePathCallback.onReceiveValue(out);
                filePathCallback = null;
            }
            return;
        }
        super.onActivityResult(req, res, data);
    }

    private String urlFrom(Intent i) {
        if (i == null || i.getData() == null) return null;
        String s = i.getData().toString();
        if (s.startsWith("http://") || s.startsWith("https://")) return s;
        return null;
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        String u = urlFrom(intent);
        if (u != null) openPopup(u);
    }

    @Override
    public void onBackPressed() {
        js("window.BN&&window.BN.on({t:'back'})");
    }

    @Override
    protected void onPause() {
        super.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        if (content != null) {
            content.destroy();
        }
        super.onDestroy();
    }
}
