package com.rajok.glassbrowser;

import android.Manifest;
import android.app.Activity;
import android.app.ActivityManager;
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
import android.provider.MediaStore;
import android.util.Log;
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
import android.webkit.RenderProcessGoneDetail;
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
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Glass Browser.
 *
 * Two layers: a transparent "chrome" WebView (the HTML UI) sitting on top of one
 * content WebView per tab. Each tab gets its own WebView so switching tabs keeps
 * scroll position, form state and video position, and so a heavy page in one tab
 * cannot take the others down with it. Live WebViews are capped by device memory
 * and the least recently used one is evicted (its URL lives in the JS tab list, so
 * it reloads on return).
 */
public class MainActivity extends Activity {

    private static final String TAG = "GlassBrowser";
    private static final int REQ_FILE = 101;
    private static final int REQ_PERM_SHOT = 102;

    private FrameLayout root;
    private WebView chrome;
    private float density = 1f;
    private final Handler ui = new Handler(Looper.getMainLooper());

    /** tab id -> live WebView, access-ordered so the eldest entry is the LRU. */
    private final LinkedHashMap<Long, WebView> live = new LinkedHashMap<>(8, 0.75f, true);
    private long activeId = -1L;
    private int maxLive = 4;
    private int chromePx = 0;

    /** Tabs whose current navigation came from a tap rather than a redirect. */
    private final Set<Long> userNav = new HashSet<>();

    /** last progress value sent per tab, so we can throttle updates. */
    private final java.util.HashMap<Long, Integer> progressCache = new java.util.HashMap<>();

    private ValueCallback<Uri[]> filePathCallback;
    private boolean pendingShot = false;
    private String pendingUrl = null;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        density = getResources().getDisplayMetrics().density;
        chromePx = Math.round(132 * density);

        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        int memClass = am != null ? am.getMemoryClass() : 128;
        // Each live WebView costs real memory; scale the cap to the device.
        maxLive = memClass >= 384 ? 6 : memClass >= 256 ? 4 : memClass >= 192 ? 3 : 2;
        Log.d(TAG, "memoryClass=" + memClass + "MB maxLive=" + maxLive);

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

        chrome = new WebView(this);
        chrome.setBackgroundColor(Color.TRANSPARENT);
        chrome.setVerticalScrollBarEnabled(false);
        chrome.setHorizontalScrollBarEnabled(false);
        chrome.setOverScrollMode(View.OVER_SCROLL_NEVER);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, chromePx);
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

        setupChrome();
        chrome.loadUrl("file:///android_asset/index.html");
        pendingUrl = urlFrom(getIntent());
    }

    /* ------------------------------------------------------------------ chrome */

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
                Log.d(TAG + "/js", m.message() + " @" + m.lineNumber());
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

    private void jsNow(String code) {
        if (chrome != null) chrome.evaluateJavascript(code, null);
    }

    /* ------------------------------------------------------------------- tabs */

    private WebView createTab(final long id) {
        WebView w = new WebView(this);
        w.setBackgroundColor(Color.WHITE);

        WebSettings s = w.getSettings();
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
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        s.setUserAgentString(s.getUserAgentString().replace("; wv", ""));
        if (Build.VERSION.SDK_INT >= 33) {
            s.setAlgorithmicDarkeningAllowed(true);
        } else if (Build.VERSION.SDK_INT >= 29) {
            s.setForceDark(WebSettings.FORCE_DARK_AUTO);
        }

        CookieManager cm = CookieManager.getInstance();
        cm.setAcceptCookie(true);
        cm.setAcceptThirdPartyCookies(w, true);

        w.setWebViewClient(new TabClient(id));
        w.setWebChromeClient(new TabChrome(id));
        w.setDownloadListener(new TabDownload(id));

        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
        lp.bottomMargin = chromePx;
        // index 0 keeps every tab underneath the chrome overlay
        root.addView(w, 0, lp);
        w.setVisibility(View.GONE);

        live.put(id, w);
        return w;
    }

    private void showTab(long id, String url) {
        if (id <= 0) return;
        WebView w = live.get(id);
        boolean fresh = false;
        if (w == null) {
            w = createTab(id);
            fresh = true;
        }
        if (activeId != id) {
            WebView old = live.get(activeId);
            if (old != null && old != w) {
                old.setVisibility(View.GONE);
                old.onPause();
            }
            activeId = id;
        }
        w.setVisibility(View.VISIBLE);
        w.onResume();
        if (fresh && url != null && !url.isEmpty()) w.loadUrl(url);
        trimTabs();
    }

    private void trimTabs() {
        if (live.size() <= maxLive) return;
        Iterator<Map.Entry<Long, WebView>> it = live.entrySet().iterator();
        while (it.hasNext() && live.size() > maxLive) {
            Map.Entry<Long, WebView> e = it.next();
            if (e.getKey() == activeId) continue;
            WebView v = e.getValue();
            try {
                root.removeView(v);
                v.stopLoading();
                v.destroy();
            } catch (Exception ignored) { }
            it.remove();
        }
    }

    private void closeTab(long id) {
        WebView w = live.remove(id);
        if (w != null) {
            try {
                root.removeView(w);
                w.stopLoading();
                w.destroy();
            } catch (Exception ignored) { }
        }
        userNav.remove(id);
        progressCache.remove(id);
        if (activeId == id) activeId = -1L;
    }

    private WebView activeView() {
        return live.get(activeId);
    }

    /** The renderer died (usually OOM on a heavy page). Rebuild instead of crashing. */
    private void recoverFromCrash() {
        for (WebView v : new ArrayList<>(live.values())) {
            try {
                root.removeView(v);
                v.destroy();
            } catch (Exception ignored) { }
        }
        live.clear();
        activeId = -1L;
        userNav.clear();
        progressCache.clear();
        jsNow("window.BN&&window.BN.on({t:'crash'})");
    }

    /* --------------------------------------------------------------- clients */

    private class TabClient extends WebViewClient {
        final long id;
        int lastProg = -1;

        TabClient(long id) { this.id = id; }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
            if (r != null && r.isForMainFrame()) userNav.add(id);
            return handleUrl(r.getUrl().toString());
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView v, String u) {
            userNav.add(id);
            return handleUrl(u);
        }

        @Override
        public void onPageStarted(WebView v, String u, Bitmap f) {
            lastProg = -1;
            js("window.BN&&window.BN.on({t:'start',id:" + id + "})");
        }

        @Override
        public void onPageFinished(WebView v, String u) {
            js("window.BN&&window.BN.on({t:'done',id:" + id + "})");
        }

        @Override
        public void doUpdateVisitedHistory(WebView v, String u, boolean reload) {
            if (reload || u == null || u.startsWith("about:")) return;
            boolean push = userNav.remove(id);
            js("window.BN&&window.BN.on({t:'visited',id:" + id + ",push:" + push
                    + ",url:" + quote(u) + "})");
        }

        @Override
        public void onReceivedError(WebView v, WebResourceRequest r, WebResourceError e) {
            if (r != null && r.isForMainFrame()) {
                js("window.BN&&window.BN.on({t:'error',id:" + id + ",desc:"
                        + quote(String.valueOf(e.getDescription())) + "})");
            }
        }

        @Override
        public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h,
                                       android.net.http.SslError err) {
            h.cancel();
            js("window.BN&&window.BN.on({t:'error',id:" + id
                    + ",desc:'This site\u2019s security certificate isn\u2019t valid, so the connection was blocked.'})");
        }

        @Override
        public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
            Log.w(TAG, "renderer gone, didCrash=" + (d != null && d.didCrash()));
            ui.post(new Runnable() {
                public void run() { recoverFromCrash(); }
            });
            return true; // handled: do not let the system kill the app
        }
    }

    private class TabChrome extends WebChromeClient {
        final long id;
        TabChrome(long id) { this.id = id; }

        @Override
        public void onProgressChanged(WebView v, int p) {
            // throttled: heavy pages fire this hundreds of times per load
            int last = progressCache.containsKey(id) ? progressCache.get(id) : -1;
            if (p < 100 && p - last < 5) return;
            progressCache.put(id, p);
            js("window.BN&&window.BN.on({t:'prog',id:" + id + ",p:" + p + "})");
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
        public boolean onShowFileChooser(WebView v, ValueCallback<Uri[]> cb,
                                         FileChooserParams params) {
            if (filePathCallback != null) filePathCallback.onReceiveValue(null);
            filePathCallback = cb;
            try {
                startActivityForResult(Intent.createChooser(params.createIntent(), "Choose file"), REQ_FILE);
                return true;
            } catch (Exception e) {
                filePathCallback = null;
                return false;
            }
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture,
                                      Message resultMsg) {
            final WebView tmp = new WebView(MainActivity.this);
            tmp.getSettings().setJavaScriptEnabled(true);
            final boolean[] used = {false};

            final Runnable cleanup = new Runnable() {
                public void run() {
                    try {
                        root.removeView(tmp);
                        tmp.stopLoading();
                        tmp.destroy();
                    } catch (Exception ignored) { }
                }
            };

            tmp.setWebViewClient(new WebViewClient() {
                private void grab(String u) {
                    if (used[0] || u == null || u.startsWith("about:")) return;
                    used[0] = true;
                    ui.removeCallbacks(cleanup);
                    cleanup.run();
                    openPopup(u);
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                    grab(r.getUrl().toString());
                    return true;
                }

                @Override
                public boolean shouldOverrideUrlLoading(WebView v, String u) {
                    grab(u);
                    return true;
                }

                @Override
                public void onPageStarted(WebView v, String u, Bitmap f) {
                    grab(u);
                }
            });

            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(1, 1);
            root.addView(tmp, 0, lp);
            // if the popup never navigates, don't leave a live WebView behind
            ui.postDelayed(cleanup, 4000);

            WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
            transport.setWebView(tmp);
            resultMsg.sendToTarget();
            return true;
        }
    }

    private class TabDownload implements DownloadListener {
        final long id;
        TabDownload(long id) { this.id = id; }

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
                toast("Couldn\u2019t start that download");
            }
        }
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

    /* ---------------------------------------------------------------- bridge */

    public class Bridge {

        @JavascriptInterface
        public void show(final String tabId, final String url) {
            final long id = parseId(tabId);
            ui.post(new Runnable() {
                public void run() { showTab(id, url); }
            });
        }

        @JavascriptInterface
        public void nav(final String tabId, final String u) {
            final long id = parseId(tabId);
            if (u == null || u.isEmpty()) return;
            ui.post(new Runnable() {
                public void run() {
                    showTab(id, null);
                    WebView w = live.get(id);
                    if (w != null) w.loadUrl(u);
                }
            });
        }

        @JavascriptInterface
        public void newTab(final String tabId, final String u) {
            final long id = parseId(tabId);
            ui.post(new Runnable() {
                public void run() {
                    showTab(id, null);
                    WebView w = live.get(id);
                    if (w != null && u != null && !u.isEmpty()) w.loadUrl(u);
                }
            });
        }

        @JavascriptInterface
        public void close(final String tabId) {
            final long id = parseId(tabId);
            ui.post(new Runnable() {
                public void run() { closeTab(id); }
            });
        }

        @JavascriptInterface
        public void reload(final String tabId) {
            final long id = parseId(tabId);
            ui.post(new Runnable() {
                public void run() {
                    WebView w = live.get(id);
                    if (w != null) w.reload();
                }
            });
        }

        @JavascriptInterface
        public void stop(final String tabId) {
            final long id = parseId(tabId);
            ui.post(new Runnable() {
                public void run() {
                    WebView w = live.get(id);
                    if (w != null) w.stopLoading();
                }
            });
        }

        @JavascriptInterface
        public void blank(final String tabId) {
            final long id = parseId(tabId);
            ui.post(new Runnable() {
                public void run() {
                    showTab(id, null);
                    WebView w = live.get(id);
                    if (w != null) {
                        w.stopLoading();
                        w.loadUrl("about:blank");
                    }
                }
            });
        }

        @JavascriptInterface
        public void setChrome(final int h) {
            ui.post(new Runnable() {
                public void run() {
                    chromePx = h < 0 ? 0 : Math.round(h * density);
                    ViewGroup.LayoutParams lp = chrome.getLayoutParams();
                    lp.height = h < 0 ? ViewGroup.LayoutParams.MATCH_PARENT : chromePx;
                    chrome.setLayoutParams(lp);
                    for (WebView w : live.values()) {
                        ViewGroup.MarginLayoutParams mp = (ViewGroup.MarginLayoutParams) w.getLayoutParams();
                        if (mp.bottomMargin != chromePx) {
                            mp.bottomMargin = chromePx;
                            w.setLayoutParams(mp);
                        }
                    }
                }
            });
        }

        @JavascriptInterface
        public void setPopups(final boolean on) {
            ui.post(new Runnable() {
                public void run() {
                    for (WebView w : live.values()) {
                        w.getSettings().setJavaScriptCanOpenWindowsAutomatically(on);
                    }
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

    private long parseId(String s) {
        try {
            return Long.parseLong(s);
        } catch (Exception e) {
            return -1L;
        }
    }

    /* ------------------------------------------------------------ screenshot */

    private void capture() {
        if (Build.VERSION.SDK_INT < 29
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingShot = true;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_PERM_SHOT);
            return;
        }
        View v = getWindow().getDecorView();
        final int w = v.getWidth(), h = v.getHeight();
        if (w <= 0 || h <= 0) { shotResult(false, "Couldn\u2019t capture the screen"); return; }
        final Bitmap bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        if (Build.VERSION.SDK_INT >= 26) {
            try {
                PixelCopy.request(getWindow(), bmp, new PixelCopy.OnPixelCopyFinishedListener() {
                    @Override
                    public void onPixelCopyFinished(int result) {
                        if (result == PixelCopy.SUCCESS) saveShot(bmp);
                        else shotResult(false, "Couldn\u2019t capture the screen");
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
            shotResult(false, "Couldn\u2019t capture the screen");
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
                shotResult(true, "Screenshot saved to Pictures");
            } else {
                File dir = new File(Environment.getExternalStoragePublicDirectory(
                        Environment.DIRECTORY_PICTURES), "Glass Browser");
                if (!dir.exists()) dir.mkdirs();
                File f = new File(dir, name);
                FileOutputStream fo = new FileOutputStream(f);
                bmp.compress(Bitmap.CompressFormat.PNG, 100, fo);
                fo.close();
                android.media.MediaScannerConnection.scanFile(this,
                        new String[]{f.getAbsolutePath()}, null, null);
                shotResult(true, "Screenshot saved to Pictures");
            }
        } catch (Exception e) {
            shotResult(false, "Couldn\u2019t save the screenshot");
        }
    }

    private void shotResult(boolean ok, String msg) {
        js("window.BN&&window.BN.on({t:'shot',ok:" + ok + ",msg:" + quote(msg) + "})");
        if (ok) toast(msg);
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
        WebView w = activeView();
        if (w != null) w.onPause();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onResume() {
        super.onResume();
        WebView w = activeView();
        if (w != null) w.onResume();
    }

    @Override
    protected void onDestroy() {
        for (WebView v : new ArrayList<>(live.values())) {
            try {
                root.removeView(v);
                v.destroy();
            } catch (Exception ignored) { }
        }
        live.clear();
        super.onDestroy();
    }
}
