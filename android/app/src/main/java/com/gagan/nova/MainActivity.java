package com.gagan.nova;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.DownloadListener;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private static final int MAX_TABS = 3;
    private static final int REQ_FILE = 411;
    private static final int REQ_STORAGE = 410;
    private static final String PREFS = "nova_tv_browser";
    private static final String GOOGLE_HOME = "https://www.google.com/";

    private final ArrayList<Tab> tabs = new ArrayList<>();
    private int activeIndex = -1;
    private LinearLayout root, toolbar, tabsRow;
    private FrameLayout content;
    private PointerView pointerView;
    private float pointerX, pointerY;
    private boolean pointerInitialized = false;
    private HorizontalScrollView tabScroll;
    private String mode = "HOME";
    private EditText address;
    private TextView backButton, forwardButton, reloadButton;
    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean fullscreen = false;
    private boolean desktopMode = false;
    private String searchEngine = "Google";
    private boolean adBlockEnabled = false;
    private boolean pointerEnabled = true;
    private volatile Set<String> allowedAdHosts = new HashSet<>();
    private String panelReturnMode = "HOME";
    private String pendingDownload;
    private String pendingDownloadAgent, pendingDownloadDisposition, pendingDownloadMime;
    private SharedPreferences prefs;

    private static class Tab {
        final WebView web;
        String title = "New tab";
        String url = "";
        Tab(WebView web) { this.web = web; }
    }

    // A visible mouse-style cursor drawn above the WebView for D-pad remotes.
    private final class PointerView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();

        PointerView(Context context) {
            super(context);
            setFocusable(false);
            setClickable(false);
            setWillNotDraw(false);
            fill.setColor(Color.WHITE);
            fill.setStyle(Paint.Style.FILL);
            outline.setColor(Color.rgb(10, 14, 24));
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeWidth(dp(2));
            outline.setStrokeJoin(Paint.Join.ROUND);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getVisibility() != View.VISIBLE) return;
            arrow.reset();
            float x = pointerX;
            float y = pointerY;
            arrow.moveTo(x, y);
            arrow.lineTo(x + dp(5), y + dp(29));
            arrow.lineTo(x + dp(12), y + dp(22));
            arrow.lineTo(x + dp(20), y + dp(39));
            arrow.lineTo(x + dp(26), y + dp(36));
            arrow.lineTo(x + dp(18), y + dp(19));
            arrow.lineTo(x + dp(31), y + dp(18));
            arrow.close();
            fill.setShadowLayer(dp(4), 0, dp(2), 0x99000000);
            canvas.drawPath(arrow, fill);
            fill.clearShadowLayer();
            canvas.drawPath(arrow, outline);
        }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(9, 13, 22));
        getWindow().setNavigationBarColor(Color.rgb(9, 13, 22));
        enterImmersive();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        desktopMode = prefs.getBoolean("desktop_mode", false);
        searchEngine = prefs.getString("search_engine", "Google");
        adBlockEnabled = prefs.getBoolean("ad_block_enabled", false);
        pointerEnabled = prefs.getBoolean("pointer_enabled", true);
        allowedAdHosts = new HashSet<>(prefs.getStringSet("ad_allowlist", new HashSet<>()));
        buildShell();
        createTab(false);
        showHome();
    }

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }

    private int dp(float n) {
        return (int)(n * getResources().getDisplayMetrics().density + 0.5f);
    }

    private GradientDrawable rounded(int color, int radius) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(color);
        d.setCornerRadius(dp(radius));
        return d;
    }

    private android.graphics.drawable.Drawable focusBackground(int normal, int focused) {
        android.graphics.drawable.StateListDrawable d = new android.graphics.drawable.StateListDrawable();
        d.addState(new int[]{android.R.attr.state_focused}, rounded(focused, 12));
        d.addState(new int[]{android.R.attr.state_pressed}, rounded(focused, 12));
        d.addState(new int[]{}, rounded(normal, 12));
        return d;
    }

    private TextView label(String value, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(value);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return t;
    }

    private TextView button(String value, String description) {
        TextView b = label(value, 18, Color.rgb(233, 237, 248), true);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        b.setContentDescription(description);
        b.setBackground(focusBackground(Color.rgb(27, 36, 54), Color.rgb(76, 66, 153)));
        return b;
    }

    private LinearLayout.LayoutParams controlParams() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(40), dp(40));
        p.leftMargin = dp(2);
        p.rightMargin = dp(2);
        return p;
    }

    private void buildShell() {
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(9, 13, 22));
        setContentView(root);

        toolbar = new LinearLayout(this);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(dp(13), dp(7), dp(13), dp(7));
        toolbar.setBackgroundColor(Color.rgb(13, 19, 32));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, dp(62)));

        TextView brand = label("NOVA", 20, Color.WHITE, true);
        brand.setLetterSpacing(0.12f);
        toolbar.addView(brand, new LinearLayout.LayoutParams(dp(76), -2));

        backButton = button("‹", "Back");
        forwardButton = button("›", "Forward");
        reloadButton = button("↻", "Reload");
        TextView home = button("⌂", "Home");
        toolbar.addView(backButton, controlParams());
        toolbar.addView(forwardButton, controlParams());
        toolbar.addView(reloadButton, controlParams());
        toolbar.addView(home, controlParams());
        backButton.setOnClickListener(v -> goBack());
        forwardButton.setOnClickListener(v -> goForward());
        reloadButton.setOnClickListener(v -> reload());
        home.setOnClickListener(v -> showHome());

        address = new EditText(this);
        address.setSingleLine(true);
        address.setTextColor(Color.WHITE);
        address.setHintTextColor(Color.rgb(142, 154, 176));
        address.setHint("Search Google or enter website");
        address.setTextSize(14);
        address.setPadding(dp(14), 0, dp(10), 0);
        address.setSelectAllOnFocus(true);
        address.setImeOptions(EditorInfo.IME_ACTION_GO);
        address.setInputType(android.text.InputType.TYPE_CLASS_TEXT |
                android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        address.setBackground(focusBackground(Color.rgb(27, 36, 54), Color.rgb(63, 56, 120)));
        LinearLayout.LayoutParams addressParams = new LinearLayout.LayoutParams(0, dp(42), 1f);
        addressParams.leftMargin = dp(8);
        addressParams.rightMargin = dp(8);
        toolbar.addView(address, addressParams);
        address.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER &&
                    event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_GO ||
                    actionId == EditorInfo.IME_ACTION_DONE || enter) {
                navigateFromAddress();
                return true;
            }
            return false;
        });

        TextView go = button("GO", "Search or open website");
        go.setTextSize(12);
        go.setBackground(rounded(Color.rgb(100, 85, 213), 11));
        go.setPadding(dp(13), 0, dp(13), 0);
        toolbar.addView(go, new LinearLayout.LayoutParams(-2, dp(40)));
        go.setOnClickListener(v -> navigateFromAddress());

        TextView addTab = button("+", "New tab");
        toolbar.addView(addTab, controlParams());
        addTab.setOnClickListener(v -> createTab(true));

        TextView menu = button("⋮", "Browser menu");
        toolbar.addView(menu, controlParams());
        menu.setOnClickListener(v -> showMenu(menu));

        tabScroll = new HorizontalScrollView(this);
        tabScroll.setHorizontalScrollBarEnabled(false);
        tabScroll.setBackgroundColor(Color.rgb(10, 15, 25));
        tabsRow = new LinearLayout(this);
        tabsRow.setGravity(Gravity.CENTER_VERTICAL);
        tabsRow.setPadding(dp(12), dp(4), dp(12), dp(4));
        tabScroll.addView(tabsRow);
        root.addView(tabScroll, new LinearLayout.LayoutParams(-1, dp(42)));

        content = new FrameLayout(this);
        content.setBackgroundColor(Color.rgb(9, 13, 22));
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1f));
    }

    private Tab activeTab() {
        return activeIndex >= 0 && activeIndex < tabs.size() ? tabs.get(activeIndex) : null;
    }

    private WebView activeWeb() {
        Tab t = activeTab();
        return t == null ? null : t.web;
    }

    private void createTab(boolean focusAddress) {
        if (tabs.size() >= MAX_TABS) {
            toast("NOVA supports 3 tabs on this TV to protect memory.");
            return;
        }
        WebView web = createWebView();
        Tab tab = new Tab(web);
        tabs.add(tab);
        activeIndex = tabs.size() - 1;
        refreshTabs();
        showHome();
        if (focusAddress) focusAddress();
    }

    private WebView createWebView() {
        WebView web = new WebView(this);
        web.setBackgroundColor(Color.WHITE);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setLoadsImagesAutomatically(true);
        s.setBlockNetworkImage(false);
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setTextZoom(desktopMode ? 100 : 110);
        web.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        s.setUseWideViewPort(true);
        s.setLoadWithOverviewMode(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setSupportZoom(true);
        s.setSaveFormData(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(true);
        if (Build.VERSION.SDK_INT >= 21) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
            CookieManager.getInstance().setAcceptThirdPartyCookies(web, true);
        }
        CookieManager.getInstance().setAcceptCookie(true);
        applyUserAgent(s);

        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                // Keep normal website links inside the current browser tab.
                return handleExternalScheme(url);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                String scheme = request.getUrl().getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
                return handleExternalScheme(url);
            }
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, String url) {
                return interceptAdRequest(url, view);
            }
            @Override public android.webkit.WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                return interceptAdRequest(request.getUrl().toString(), view);
            }
            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                Tab tab = findTab(view);
                if (tab != null) tab.url = url;
                if (view == activeWeb() && !address.hasFocus()) address.setText(url);
                updateNavigationButtons();
            }
            @Override public void onPageFinished(WebView view, String url) {
                Tab tab = findTab(view);
                if (tab != null) {
                    tab.url = url;
                    String title = view.getTitle();
                    if (title != null && !title.trim().isEmpty()) tab.title = title;
                    else tab.title = hostName(url);
                    saveHistory(url, tab.title);
                }
                // Expose native HTML5 controls when a site's player supports them.
                view.evaluateJavascript("(function(){document.querySelectorAll('video').forEach(function(v){v.setAttribute('controls','controls');v.setAttribute('playsinline','');v.style.maxWidth='100%';});})();", null);
                if (view == activeWeb()) {
                    if (!address.hasFocus()) address.setText(url);
                    updateNavigationButtons();
                    refreshTabs();
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= 23 && request.isForMainFrame() && view == activeWeb()) {
                    toast("Page failed to load. Check connection, then press Reload.");
                }
            }
        });

        web.setWebChromeClient(new WebChromeClient() {
            @Override public void onReceivedTitle(WebView view, String title) {
                Tab t = findTab(view);
                if (t != null && title != null && !title.trim().isEmpty()) t.title = title;
                if (view == activeWeb()) refreshTabs();
            }
            @Override public void onProgressChanged(WebView view, int progress) {
                if (view == activeWeb() && progress == 100) updateNavigationButtons();
            }
            @Override public void onShowCustomView(View view, CustomViewCallback callback) {
                enterFullscreen(view, callback);
            }
            @Override public void onHideCustomView() {
                exitFullscreen();
            }
            @Override public boolean onCreateWindow(WebView view, boolean dialog, boolean userGesture,
                                                    android.os.Message resultMsg) {
                if (tabs.size() >= MAX_TABS) {
                    toast("Close a tab before opening another website.");
                    return false;
                }
                // Many search results use target=_blank; create an actual browser tab for them.
                WebView newWeb = createWebView();
                Tab newTab = new Tab(newWeb);
                tabs.add(newTab);
                activeIndex = tabs.size() - 1;
                refreshTabs();
                showWebContent();
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(newWeb);
                resultMsg.sendToTarget();
                return true;
            }
            @Override public void onPermissionRequest(PermissionRequest request) {
                request.deny();
            }
            @Override public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback,
                                                       FileChooserParams params) {
                if (fileCallback != null) fileCallback.onReceiveValue(null);
                fileCallback = callback;
                Intent pick = new Intent(Intent.ACTION_GET_CONTENT);
                pick.addCategory(Intent.CATEGORY_OPENABLE);
                pick.setType("*/*");
                try {
                    startActivityForResult(Intent.createChooser(pick, "Choose file"), REQ_FILE);
                    return true;
                } catch (ActivityNotFoundException e) {
                    fileCallback = null;
                    callback.onReceiveValue(null);
                    return false;
                }
            }
        });

        web.setDownloadListener((url, userAgent, disposition, mimeType, length) ->
                startDownload(url, userAgent, disposition, mimeType));
        return web;
    }

    private void applyUserAgent(WebSettings settings) {
        String base = settings.getUserAgentString();
        String chrome = "120.0.0.0";
        if (base != null) {
            Matcher matcher = Pattern.compile("Chrome/([0-9.]+)").matcher(base);
            if (matcher.find()) chrome = matcher.group(1);
        }
        if (desktopMode) {
            settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chrome + " Safari/537.36");
            settings.setLoadWithOverviewMode(false);
            settings.setTextZoom(100);
        } else {
            settings.setUserAgentString("Mozilla/5.0 (Linux; Android 9; Android TV) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chrome + " Safari/537.36");
            settings.setLoadWithOverviewMode(true);
            settings.setTextZoom(110);
        }
        settings.setUseWideViewPort(true);
    }

    private boolean hostMatches(String host, String domain) {
        return host.equals(domain) || host.endsWith("." + domain);
    }

    private android.webkit.WebResourceResponse interceptAdRequest(String rawUrl, WebView source) {
        if (!adBlockEnabled) return null;
        Uri uri = Uri.parse(rawUrl);
        String host = uri.getHost();
        if (host == null) return null;
        host = host.toLowerCase(Locale.ROOT);
        if (source != null && source.getUrl() != null) {
            String pageHost = Uri.parse(source.getUrl()).getHost();
            if (pageHost != null) {
                pageHost = pageHost.toLowerCase(Locale.ROOT);
                for (String allowed : allowedAdHosts) {
                    if (hostMatches(pageHost, allowed)) return null;
                }
            }
        }
        String[] adHosts = {
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "adnxs.com", "adform.net", "taboola.com",
            "outbrain.com", "criteo.com", "pubmatic.com", "openx.net",
            "rubiconproject.com", "smartadserver.com", "yieldmo.com",
            "adsrvr.org", "casalemedia.com", "contextweb.com", "media.net"
        };
        for (String domain : adHosts) {
            if (hostMatches(host, domain)) {
                return new android.webkit.WebResourceResponse("text/plain", "UTF-8",
                        new ByteArrayInputStream(new byte[0]));
            }
        }
        return null;
    }

    private Tab findTab(WebView view) {
        for (Tab t : tabs) if (t.web == view) return t;
        return null;
    }

    private boolean handleExternalScheme(String raw) {
        if (raw == null) return false;
        Uri uri = Uri.parse(raw);
        String scheme = uri.getScheme();
        if (scheme == null || "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) return false;
        try {
            Intent intent;
            if (raw.startsWith("intent:")) intent = Intent.parseUri(raw, Intent.URI_INTENT_SCHEME);
            else intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);
        } catch (Exception e) {
            toast("This link needs another app that is not installed.");
        }
        return true;
    }

    private String resolveAddress(String raw) {
        String q = raw == null ? "" : raw.trim();
        if (q.isEmpty()) return GOOGLE_HOME;
        if (q.matches("(?i)^[a-z][a-z0-9+.-]*://.*")) return q;
        if (q.matches("(?i)^([a-z0-9-]+\\.)+[a-z]{2,}(:[0-9]+)?(/[^ ]*)?$")) return "https://" + q;
        try {
            String encoded = URLEncoder.encode(q, "UTF-8");
            if ("Bing".equals(searchEngine)) return "https://www.bing.com/search?q=" + encoded;
            if ("DuckDuckGo".equals(searchEngine)) return "https://duckduckgo.com/?q=" + encoded;
            return "https://www.google.com/search?q=" + encoded;
        } catch (UnsupportedEncodingException e) {
            return GOOGLE_HOME;
        }
    }

    private String hostName(String url) {
        try { return Uri.parse(url).getHost(); }
        catch (Exception e) { return url; }
    }

    private void navigateFromAddress() {
        String value = address.getText().toString();
        hideKeyboard();
        loadInActiveTab(value);
    }

    private void loadInActiveTab(String input) {
        Tab tab = activeTab();
        if (tab == null) return;
        String url = resolveAddress(input);
        tab.url = url;
        tab.title = hostName(url);
        address.setText(url);
        showWebContent();
        tab.web.loadUrl(url);
        refreshTabs();
        updateNavigationButtons();
    }

    private void focusAddress() {
        address.requestFocus();
        address.setSelection(address.getText().length());
        address.postDelayed(() -> {
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.showSoftInput(address, InputMethodManager.SHOW_IMPLICIT);
        }, 180);
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(address.getWindowToken(), 0);
        address.clearFocus();
    }

    private void showWebContent() {
        mode = "BROWSER";
        content.removeAllViews();
        WebView web = activeWeb();
        if (web != null) {
            if (web.getParent() != null) ((ViewGroup)web.getParent()).removeView(web);
            content.addView(web, new FrameLayout.LayoutParams(-1, -1));
            web.requestFocus();
            if (pointerView == null) pointerView = new PointerView(this);
            content.addView(pointerView, new FrameLayout.LayoutParams(-1, -1));
            pointerView.setVisibility(pointerEnabled ? View.VISIBLE : View.GONE);
            pointerView.bringToFront();
            pointerView.post(() -> {
                if (!pointerInitialized && pointerView.getWidth() > 0 && pointerView.getHeight() > 0) {
                    pointerX = pointerView.getWidth() * 0.48f;
                    pointerY = pointerView.getHeight() * 0.42f;
                    pointerInitialized = true;
                }
                clampPointer();
                pointerView.invalidate();
                sendPointerHover();
            });
        }
        updateNavigationButtons();
    }

    private void clampPointer() {
        if (pointerView == null) return;
        float min = dp(3);
        float maxX = Math.max(min, pointerView.getWidth() - dp(34));
        float maxY = Math.max(min, pointerView.getHeight() - dp(43));
        pointerX = Math.max(min, Math.min(pointerX, maxX));
        pointerY = Math.max(min, Math.min(pointerY, maxY));
    }

    private void movePointer(float dx, float dy) {
        if (pointerView == null || pointerView.getVisibility() != View.VISIBLE) return;
        if (!pointerInitialized) {
            pointerX = pointerView.getWidth() * 0.48f;
            pointerY = pointerView.getHeight() * 0.42f;
            pointerInitialized = true;
        }
        pointerX += dx * dp(28);
        pointerY += dy * dp(28);
        clampPointer();
        pointerView.invalidate();
        sendPointerHover();
    }

    private void sendPointerHover() {
        WebView web = activeWeb();
        if (web == null || pointerView == null || fullscreen) return;
        long now = SystemClock.uptimeMillis();
        MotionEvent hover = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE,
                pointerX, pointerY, 0);
        hover.setSource(InputDevice.SOURCE_MOUSE);
        web.dispatchGenericMotionEvent(hover);
        hover.recycle();
    }

    private void clickAtPointer() {
        WebView web = activeWeb();
        if (web == null || pointerView == null || fullscreen) return;
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime,
                MotionEvent.ACTION_DOWN, pointerX, pointerY, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(down);
        down.recycle();

        long upTime = SystemClock.uptimeMillis();
        MotionEvent up = MotionEvent.obtain(downTime, upTime,
                MotionEvent.ACTION_UP, pointerX, pointerY, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(up);
        up.recycle();
        sendPointerHover();
    }

    private boolean handlePointerRemoteKey(KeyEvent event) {
        if (!"BROWSER".equals(mode) || fullscreen || address.hasFocus() || !pointerEnabled) return false;
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_DPAD_UP ||
                key == KeyEvent.KEYCODE_DPAD_DOWN ||
                key == KeyEvent.KEYCODE_DPAD_LEFT ||
                key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                float dx = 0, dy = 0;
                if (key == KeyEvent.KEYCODE_DPAD_UP) dy = -1;
                else if (key == KeyEvent.KEYCODE_DPAD_DOWN) dy = 1;
                else if (key == KeyEvent.KEYCODE_DPAD_LEFT) dx = -1;
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) dx = 1;
                movePointer(dx, dy);
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER ||
                key == KeyEvent.KEYCODE_ENTER ||
                key == KeyEvent.KEYCODE_NUMPAD_ENTER ||
                key == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) {
                clickAtPointer();
            }
            return true;
        }
        return false;
    }

    private void showHome() {
        mode = "HOME";
        content.removeAllViews();
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.setPadding(dp(36), dp(25), dp(36), dp(22));
        scroll.addView(page);

        TextView eyebrow = label("YOUR TV. YOUR WEB.", 11, Color.rgb(164, 151, 255), true);
        eyebrow.setLetterSpacing(0.15f);
        page.addView(eyebrow);

        TextView title = label("Browse beyond the ordinary.", 29, Color.WHITE, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-2, -2);
        tp.topMargin = dp(7);
        page.addView(title, tp);

        TextView sub = label("Search the web, watch videos and open your favourite sites.", 14,
                Color.rgb(165, 175, 194), false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-2, -2);
        sp.topMargin = dp(7);
        page.addView(sub, sp);

        TextView hint = label("Use the address bar above, or choose a shortcut.", 13,
                Color.rgb(154, 166, 188), false);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-2, -2);
        hp.topMargin = dp(20);
        page.addView(hint, hp);

        LinearLayout tiles = new LinearLayout(this);
        tiles.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tileParams = new LinearLayout.LayoutParams(-1, dp(92));
        tileParams.topMargin = dp(15);
        page.addView(tiles, tileParams);
        addTile(tiles, "▶", "YouTube", "https://www.youtube.com/");
        addTile(tiles, "G", "Google", "https://www.google.com/");
        addTile(tiles, "N", "Netflix", "https://www.netflix.com/");
        addTile(tiles, "P", "Prime Video", "https://www.primevideo.com/");
        addTile(tiles, "W", "Wikipedia", "https://www.wikipedia.org/");

        LinearLayout actions = new LinearLayout(this);
        actions.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(-1, dp(50));
        ap.topMargin = dp(18);
        page.addView(actions, ap);
        addAction(actions, "☆ Bookmarks", this::showBookmarks);
        addAction(actions, "◷ History", this::showHistory);
        addAction(actions, "⋮ Menu", () -> showMenu(toolbar.getChildAt(toolbar.getChildCount() - 1)));

        TextView note = label("NOVA TV BROWSER  •  ANDROID WEBVIEW  •  3 TABS MAX", 10,
                Color.rgb(101, 115, 139), true);
        LinearLayout.LayoutParams np = new LinearLayout.LayoutParams(-2, -2);
        np.topMargin = dp(22);
        page.addView(note, np);

        content.addView(scroll, new FrameLayout.LayoutParams(-1, -1));
        if (!address.hasFocus()) address.setText("");
        address.setHint("Search Google or enter website");
        updateNavigationButtons();
    }

    private void addTile(LinearLayout row, String icon, String name, String url) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setFocusable(true);
        tile.setClickable(true);
        tile.setBackground(focusBackground(Color.rgb(19, 27, 43), Color.rgb(63, 56, 120)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(82), 1f);
        p.leftMargin = dp(5);
        p.rightMargin = dp(5);
        row.addView(tile, p);

        TextView symbol = label(icon, 23, Color.rgb(182, 172, 255), true);
        symbol.setGravity(Gravity.CENTER);
        tile.addView(symbol, new LinearLayout.LayoutParams(-1, dp(42)));
        TextView text = label(name, 11, Color.WHITE, true);
        text.setGravity(Gravity.CENTER);
        tile.addView(text);
        tile.setContentDescription("Open " + name);
        tile.setOnClickListener(v -> loadInActiveTab(url));
    }

    private void addAction(LinearLayout row, String title, Runnable action) {
        TextView b = button(title, title);
        b.setTextSize(11);
        b.setPadding(dp(10), 0, dp(10), 0);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(40));
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        row.addView(b, p);
        b.setOnClickListener(v -> action.run());
    }

    private void refreshTabs() {
        tabsRow.removeAllViews();
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            Tab tab = tabs.get(i);
            LinearLayout chip = new LinearLayout(this);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            chip.setPadding(dp(10), 0, dp(7), 0);
            chip.setFocusable(true);
            chip.setClickable(true);
            chip.setBackground(focusBackground(
                    i == activeIndex ? Color.rgb(34, 42, 67) : Color.rgb(17, 24, 38),
                    Color.rgb(76, 66, 153)));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(155), dp(31));
            cp.rightMargin = dp(6);
            tabsRow.addView(chip, cp);

            TextView name = label(shortTitle(tab.title), 11,
                    i == activeIndex ? Color.WHITE : Color.rgb(158, 170, 191), i == activeIndex);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            chip.addView(name, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView close = label("×", 17, Color.rgb(173, 183, 201), true);
            close.setGravity(Gravity.CENTER);
            chip.addView(close, new LinearLayout.LayoutParams(dp(22), dp(27)));
            close.setOnClickListener(v -> closeTab(index));
            chip.setOnClickListener(v -> switchTab(index));
            chip.setContentDescription("Tab " + (i + 1) + ": " + tab.title);
        }
    }

    private String shortTitle(String s) {
        if (s == null || s.trim().isEmpty()) return "New tab";
        return s.length() > 20 ? s.substring(0, 18) + "…" : s;
    }

    private void switchTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        activeIndex = index;
        refreshTabs();
        Tab tab = activeTab();
        address.setText(tab.url == null ? "" : tab.url);
        if (tab.web.getUrl() == null) showHome();
        else showWebContent();
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;
        Tab tab = tabs.remove(index);
        if (tab.web.getParent() != null) ((ViewGroup)tab.web.getParent()).removeView(tab.web);
        tab.web.stopLoading();
        tab.web.destroy();
        if (tabs.isEmpty()) {
            activeIndex = -1;
            createTab(false);
        } else {
            if (index < activeIndex) activeIndex--;
            else if (index == activeIndex) activeIndex = Math.max(0, activeIndex - 1);
            switchTab(activeIndex);
        }
        refreshTabs();
    }

    private void updateNavigationButtons() {
        WebView web = activeWeb();
        if (web == null) return;
        backButton.setAlpha(web.canGoBack() ? 1f : 0.42f);
        forwardButton.setAlpha(web.canGoForward() ? 1f : 0.42f);
    }

    private void goBack() {
        WebView web = activeWeb();
        if (web != null && web.canGoBack()) web.goBack();
        else showHome();
    }

    private void goForward() {
        WebView web = activeWeb();
        if (web != null && web.canGoForward()) web.goForward();
    }

    private void reload() {
        WebView web = activeWeb();
        if (web != null && web.getUrl() != null) web.reload();
        else showHome();
    }

    private void showMenu(View anchor) {
        if (anchor == null) anchor = toolbar.getChildAt(toolbar.getChildCount() - 1);
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Focus address bar");
        popup.getMenu().add("Search engine: " + searchEngine);
        popup.getMenu().add("Pointer: " + (pointerEnabled ? "ON" : "OFF"));
        popup.getMenu().add("New tab");
        popup.getMenu().add("Add bookmark");
        popup.getMenu().add("Bookmarks");
        popup.getMenu().add("History");
        popup.getMenu().add(desktopMode ? "Switch to TV mode" : "Switch to Desktop mode");
        popup.getMenu().add("Ad blocker: " + (adBlockEnabled ? "ON" : "OFF"));
        popup.getMenu().add("Allow ads on this site");
        popup.getMenu().add("Find in page");
        popup.getMenu().add("Share page");
        popup.getMenu().add("Copy page link");
        popup.getMenu().add("Zoom in");
        popup.getMenu().add("Zoom out");
        popup.getMenu().add("Reset zoom");
        popup.getMenu().add("Reload without cache");
        popup.getMenu().add("Clear browsing data");
        popup.setOnMenuItemClickListener(item -> {
            String value = item.getTitle().toString();
            if ("Focus address bar".equals(value)) focusAddress();
            else if (value.startsWith("Search engine:")) cycleSearchEngine();
            else if (value.startsWith("Pointer:")) togglePointer();
            else if ("New tab".equals(value)) createTab(true);
            else if ("Add bookmark".equals(value)) addBookmark();
            else if ("Bookmarks".equals(value)) showBookmarks();
            else if ("History".equals(value)) showHistory();
            else if (value.startsWith("Switch to ")) toggleDesktopMode();
            else if (value.startsWith("Ad blocker:")) toggleAdBlocker();
            else if ("Allow ads on this site".equals(value)) allowAdsForCurrentSite();
            else if ("Find in page".equals(value)) findInPage();
            else if ("Share page".equals(value)) shareCurrentPage();
            else if ("Copy page link".equals(value)) copyCurrentPage();
            else if ("Zoom in".equals(value)) changeZoom(10);
            else if ("Zoom out".equals(value)) changeZoom(-10);
            else if ("Reset zoom".equals(value)) resetZoom();
            else if ("Reload without cache".equals(value)) reloadWithoutCache();
            else if ("Clear browsing data".equals(value)) clearData();
            return true;
        });
        popup.show();
    }

    private void cycleSearchEngine() {
        if ("Google".equals(searchEngine)) searchEngine = "Bing";
        else if ("Bing".equals(searchEngine)) searchEngine = "DuckDuckGo";
        else searchEngine = "Google";
        prefs.edit().putString("search_engine", searchEngine).apply();
        toast("Search engine: " + searchEngine);
    }

    private void togglePointer() {
        pointerEnabled = !pointerEnabled;
        prefs.edit().putBoolean("pointer_enabled", pointerEnabled).apply();
        if (pointerView != null) pointerView.setVisibility(pointerEnabled ? View.VISIBLE : View.GONE);
        toast(pointerEnabled ? "Pointer ON: arrows move, OK clicks." : "Pointer OFF: use page focus.");
    }

    private void toggleAdBlocker() {
        adBlockEnabled = !adBlockEnabled;
        prefs.edit().putBoolean("ad_block_enabled", adBlockEnabled).apply();
        toast("Ad blocker " + (adBlockEnabled ? "enabled" : "disabled") + ". Reloading page.");
        WebView web = activeWeb();
        if (web != null && web.getUrl() != null) web.reload();
    }

    private void allowAdsForCurrentSite() {
        WebView web = activeWeb();
        if (web == null || web.getUrl() == null) {
            toast("Open a website first.");
            return;
        }
        String host = Uri.parse(web.getUrl()).getHost();
        if (host == null) return;
        host = host.toLowerCase(Locale.ROOT);
        if (host.startsWith("www.")) host = host.substring(4);
        Set<String> next = new HashSet<>(allowedAdHosts);
        if (next.contains(host)) {
            next.remove(host);
            toast("Ad blocking restored for this site.");
        } else {
            next.add(host);
            toast("Ads allowed for this site.");
        }
        allowedAdHosts = next;
        prefs.edit().putStringSet("ad_allowlist", new HashSet<>(next)).apply();
        web.reload();
    }

    private void findInPage() {
        WebView web = activeWeb();
        if (web == null) return;
        EditText query = new EditText(this);
        query.setSingleLine(true);
        query.setHint("Find text on this page");
        new AlertDialog.Builder(this)
                .setTitle("Find in page")
                .setView(query)
                .setPositiveButton("Find next", (dialog, which) -> {
                    String term = query.getText().toString().trim();
                    if (!term.isEmpty()) {
                        web.findAllAsync(term);
                        web.findNext(true);
                    }
                })
                .setNegativeButton("Close", null)
                .show();
    }

    private void shareCurrentPage() {
        WebView web = activeWeb();
        if (web == null || web.getUrl() == null) return;
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_TEXT, web.getUrl());
        try {
            startActivity(Intent.createChooser(share, "Share page"));
        } catch (Exception e) {
            toast("No sharing app is available.");
        }
    }

    private void copyCurrentPage() {
        WebView web = activeWeb();
        if (web == null || web.getUrl() == null) return;
        ClipboardManager clipboard = (ClipboardManager)getSystemService(CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText("NOVA page URL", web.getUrl()));
            toast("Page link copied.");
        }
    }

    private void changeZoom(int delta) {
        WebView web = activeWeb();
        if (web == null) return;
        int zoom = Math.max(70, Math.min(180, web.getSettings().getTextZoom() + delta));
        web.getSettings().setTextZoom(zoom);
        toast("Text zoom: " + zoom + "%");
    }

    private void resetZoom() {
        WebView web = activeWeb();
        if (web == null) return;
        web.getSettings().setTextZoom(desktopMode ? 100 : 110);
        web.setInitialScale(0);
        toast("Zoom reset.");
    }

    private void reloadWithoutCache() {
        WebView web = activeWeb();
        if (web != null) {
            web.clearCache(true);
            web.reload();
        }
    }

    private void toggleDesktopMode() {
        desktopMode = !desktopMode;
        prefs.edit().putBoolean("desktop_mode", desktopMode).apply();
        for (Tab tab : tabs) {
            applyUserAgent(tab.web.getSettings());
            if (tab.web.getUrl() != null) tab.web.reload();
        }
        toast("Desktop site mode " + (desktopMode ? "enabled" : "disabled"));
    }

    private void addBookmark() {
        WebView web = activeWeb();
        if (web == null || web.getUrl() == null) {
            toast("Open a website first.");
            return;
        }
        JSONArray all = readArray("bookmarks");
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item != null && web.getUrl().equals(item.optString("url"))) {
                toast("Already saved.");
                return;
            }
        }
        JSONObject item = new JSONObject();
        try {
            item.put("url", web.getUrl());
            item.put("title", web.getTitle() == null ? web.getUrl() : web.getTitle());
            all.put(item);
        } catch (Exception ignored) { }
        prefs.edit().putString("bookmarks", all.toString()).apply();
        toast("Bookmark saved.");
    }

    private void saveHistory(String url, String title) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) return;
        JSONArray old = readArray("history");
        JSONArray next = new JSONArray();
        JSONObject item = new JSONObject();
        try {
            item.put("url", url);
            item.put("title", title == null ? url : title);
            item.put("time", System.currentTimeMillis());
            next.put(item);
        } catch (Exception ignored) { }
        for (int i = 0; i < old.length() && i < 79; i++) {
            JSONObject previous = old.optJSONObject(i);
            if (previous != null && !url.equals(previous.optString("url"))) next.put(previous);
        }
        prefs.edit().putString("history", next.toString()).apply();
    }

    private JSONArray readArray(String key) {
        try { return new JSONArray(prefs.getString(key, "[]")); }
        catch (Exception e) { return new JSONArray(); }
    }

    private void showBookmarks() {
        showSavedPanel("BOOKMARKS", readArray("bookmarks"));
    }

    private void showHistory() {
        showSavedPanel("RECENT HISTORY", readArray("history"));
    }

    private void showSavedPanel(String heading, JSONArray values) {
        mode = "PANEL";
        panelReturnMode = activeWeb() != null && activeWeb().getUrl() != null ? "BROWSER" : "HOME";
        content.removeAllViews();
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(32), dp(23), dp(32), dp(20));

        TextView title = label(heading, 12, Color.rgb(164, 151, 255), true);
        panel.addView(title);
        TextView subtitle = label("Choose an item to open it", 22, Color.WHITE, true);
        LinearLayout.LayoutParams sub = new LinearLayout.LayoutParams(-1, -2);
        sub.topMargin = dp(7);
        panel.addView(subtitle, sub);

        ScrollView scroll = new ScrollView(this);
        LinearLayout.LayoutParams sc = new LinearLayout.LayoutParams(-1, 0, 1f);
        sc.topMargin = dp(13);
        panel.addView(scroll, sc);
        LinearLayout rows = new LinearLayout(this);
        rows.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(rows);

        if (values.length() == 0) {
            rows.addView(label("Nothing saved yet.", 15, Color.LTGRAY, false));
        }
        for (int i = 0; i < values.length(); i++) {
            JSONObject item = values.optJSONObject(i);
            if (item == null) continue;
            String url = item.optString("url");
            String titleText = item.optString("title", url);
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(13), dp(8), dp(13), dp(8));
            row.setFocusable(true);
            row.setBackground(focusBackground(Color.rgb(19, 27, 43), Color.rgb(63, 56, 120)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(52));
            rp.bottomMargin = dp(6);
            rows.addView(row, rp);
            TextView itemTitle = label(titleText, 13, Color.WHITE, true);
            itemTitle.setSingleLine(true);
            itemTitle.setEllipsize(android.text.TextUtils.TruncateAt.END);
            row.addView(itemTitle, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView open = label("OPEN  ↗", 10, Color.rgb(174, 164, 255), true);
            row.addView(open);
            row.setOnClickListener(v -> loadInActiveTab(url));
        }

        TextView back = button("← BACK TO BROWSER", "Back to browser");
        back.setTextSize(12);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(-2, dp(42));
        bp.topMargin = dp(12);
        panel.addView(back, bp);
        back.setOnClickListener(v -> returnFromPanel());
        content.addView(panel, new FrameLayout.LayoutParams(-1, -1));
        mode = "PANEL";
        back.requestFocus();
    }

    private void returnFromPanel() {
        if ("BROWSER".equals(panelReturnMode) && activeWeb() != null && activeWeb().getUrl() != null) showWebContent();
        else showHome();
    }

    private void clearData() {
        for (Tab tab : tabs) {
            tab.web.clearHistory();
            tab.web.clearCache(true);
        }
        CookieManager.getInstance().removeAllCookies(null);
        CookieManager.getInstance().flush();
        prefs.edit().remove("history").remove("bookmarks").apply();
        toast("Browsing data cleared.");
        showHome();
    }

    private void startDownload(String url, String userAgent, String disposition, String mimeType) {
        if (url == null || !(url.startsWith("http://") || url.startsWith("https://"))) {
            toast("Unsupported download link.");
            return;
        }
        if (Build.VERSION.SDK_INT <= 28 &&
                checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = url;
            pendingDownloadAgent = userAgent;
            pendingDownloadDisposition = disposition;
            pendingDownloadMime = mimeType;
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        queueDownload(url, userAgent, disposition, mimeType);
    }

    private void queueDownload(String url, String userAgent, String disposition, String mimeType) {
        String fileName = "download";
        if (disposition != null && disposition.contains("filename=")) {
            fileName = disposition.substring(disposition.indexOf("filename=") + 9).replace("\"", "").trim();
        } else {
            String path = Uri.parse(url).getLastPathSegment();
            if (path != null && !path.isEmpty()) fileName = path;
        }
        fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
        try {
            DownloadManager.Request request = new DownloadManager.Request(Uri.parse(url));
            request.setTitle(fileName);
            request.setDescription("Downloading with NOVA TV Browser");
            request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            request.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName);
            if (userAgent != null) request.addRequestHeader("User-Agent", userAgent);
            String cookie = CookieManager.getInstance().getCookie(url);
            if (cookie != null) request.addRequestHeader("Cookie", cookie);
            if (mimeType != null) request.setMimeType(mimeType);
            ((DownloadManager)getSystemService(DOWNLOAD_SERVICE)).enqueue(request);
            toast("Download started. Check Downloads.");
        } catch (Exception e) {
            toast("Download could not start.");
        }
    }

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback callback) {
        if (fullscreen) {
            callback.onCustomViewHidden();
            return;
        }
        fullscreen = true;
        customView = view;
        customViewCallback = callback;
        toolbar.setVisibility(View.GONE);
        content.removeAllViews();
        if (tabScroll.getParent() == root) root.removeView(tabScroll);
        content.addView(view, new FrameLayout.LayoutParams(-1, -1));
        enterImmersive();
    }

    private void exitFullscreen() {
        if (!fullscreen) return;
        fullscreen = false;
        if (customView != null && customView.getParent() != null) {
            ((ViewGroup)customView.getParent()).removeView(customView);
        }
        customView = null;
        toolbar.setVisibility(View.VISIBLE);
        // Rebuild tab strip after fullscreen video exits.
        if (tabScroll.getParent() == null) root.addView(tabScroll, 1, new LinearLayout.LayoutParams(-1, dp(42)));
        showWebContent();
        if (customViewCallback != null) {
            customViewCallback.onCustomViewHidden();
            customViewCallback = null;
        }
    }

    private void updateNavigationButtonsSafe() { updateNavigationButtons(); }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
    }

    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP &&
                (event.getKeyCode() == KeyEvent.KEYCODE_MENU ||
                 event.getKeyCode() == KeyEvent.KEYCODE_SETTINGS)) {
            showMenu(toolbar.getChildAt(toolbar.getChildCount() - 1));
            return true;
        }
        if (handlePointerRemoteKey(event)) return true;
        return super.dispatchKeyEvent(event);
    }

    @Override public void onBackPressed() {
        if (fullscreen) {
            exitFullscreen();
            return;
        }
        if ("PANEL".equals(mode)) {
            returnFromPanel();
            return;
        }
        WebView web = activeWeb();
        if (web != null && web.canGoBack() && "BROWSER".equals(mode)) {
            web.goBack();
            return;
        }
        if (!"HOME".equals(mode)) {
            showHome();
            return;
        }
        super.onBackPressed();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && pendingDownload != null) {
                queueDownload(pendingDownload, pendingDownloadAgent, pendingDownloadDisposition, pendingDownloadMime);
            } else {
                toast("Storage permission denied; download cancelled.");
            }
            pendingDownload = null;
            pendingDownloadAgent = pendingDownloadDisposition = pendingDownloadMime = null;
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            Uri[] result = null;
            if (resultCode == RESULT_OK && data != null && data.getData() != null) result = new Uri[]{data.getData()};
            fileCallback.onReceiveValue(result);
            fileCallback = null;
        }
    }

    @Override protected void onPause() {
        super.onPause();
        for (Tab tab : tabs) tab.web.onPause();
    }

    @Override protected void onResume() {
        super.onResume();
        enterImmersive();
        for (Tab tab : tabs) tab.web.onResume();
    }

    @Override protected void onDestroy() {
        for (Tab tab : tabs) {
            try {
                tab.web.stopLoading();
                tab.web.destroy();
            } catch (Exception ignored) { }
        }
        tabs.clear();
        super.onDestroy();
    }
}
