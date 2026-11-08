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
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.CompoundButton;
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
    private int pointerSpeedDp = 18;
    private int pointerSizeDp = 36;
    private int pointerColor = Color.WHITE;
    private String pointerShape = "Arrow";
    private boolean pointerOutlineEnabled = true;
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

    // Visible TV pointer drawn above WebView content. Style is controlled in Cursor settings.
    private final class PointerView extends View {
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint outline = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint accent = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path arrow = new Path();
        private boolean clickPulse = false;

        PointerView(Context context) {
            super(context);
            setFocusable(false);
            setClickable(false);
            setWillNotDraw(false);
            fill.setStyle(Paint.Style.FILL);
            outline.setStyle(Paint.Style.STROKE);
            outline.setStrokeJoin(Paint.Join.ROUND);
            accent.setStyle(Paint.Style.STROKE);
            accent.setStrokeWidth(dp(2));
            accent.setColor(Color.rgb(100, 221, 232));
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }

        void showClickPulse() {
            clickPulse = true;
            invalidate();
            postDelayed(() -> {
                clickPulse = false;
                invalidate();
            }, 180);
        }

        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (getVisibility() != View.VISIBLE) return;

            float scale = Math.max(0.65f, Math.min(1.75f, pointerSizeDp / 36f));
            canvas.save();
            canvas.translate(pointerX, pointerY);
            canvas.scale(scale, scale);

            fill.setColor(pointerColor);
            outline.setColor(Color.rgb(10, 14, 24));
            outline.setStrokeWidth(2.2f / scale);
            fill.setShadowLayer(4, 0, 2, 0x99000000);

            if ("Crosshair".equals(pointerShape)) {
                canvas.drawCircle(0, 0, 10, outline);
                canvas.drawLine(-17, 0, -5, 0, outline);
                canvas.drawLine(5, 0, 17, 0, outline);
                canvas.drawLine(0, -17, 0, -5, outline);
                canvas.drawLine(0, 5, 0, 17, outline);
                canvas.drawCircle(0, 0, 3, fill);
            } else if ("Ring".equals(pointerShape)) {
                fill.setStyle(Paint.Style.STROKE);
                fill.setStrokeWidth(3.5f / scale);
                canvas.drawCircle(0, 0, 12, fill);
                fill.setStyle(Paint.Style.FILL);
                canvas.drawCircle(0, 0, 2.5f, fill);
            } else {
                arrow.reset();
                arrow.moveTo(0, 0);
                arrow.lineTo(5, 29);
                arrow.lineTo(12, 22);
                arrow.lineTo(20, 39);
                arrow.lineTo(26, 36);
                arrow.lineTo(18, 19);
                arrow.lineTo(31, 18);
                arrow.close();
                canvas.drawPath(arrow, fill);
                if (pointerOutlineEnabled) canvas.drawPath(arrow, outline);
            }

            fill.clearShadowLayer();
            if (clickPulse) {
                accent.setAlpha(235);
                canvas.drawCircle(0, 0, 22, accent);
            }
            canvas.restore();
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
        pointerSpeedDp = prefs.getInt("pointer_speed_dp", 18);
        pointerSizeDp = prefs.getInt("pointer_size_dp", 36);
        pointerColor = prefs.getInt("pointer_color", Color.WHITE);
        pointerShape = prefs.getString("pointer_shape", "Arrow");
        pointerOutlineEnabled = prefs.getBoolean("pointer_outline", true);
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

    private String chromeVersionFrom(String userAgent) {
        if (userAgent == null) return "unknown";
        Matcher matcher = Pattern.compile("Chrome/([0-9.]+)").matcher(userAgent);
        return matcher.find() ? matcher.group(1) : "unknown";
    }

    private void applyUserAgent(WebSettings settings) {
        String actual = settings.getUserAgentString();
        if (actual == null || actual.trim().isEmpty()) return;

        // Keep the real Android release and the actual installed Chromium/WebView version.
        // Never claim Android 9 or a newer Chrome build on an Android 6 device.
        String cleanActual = actual.replace("; wv)", ")").replace("Version/4.0 ", "");
        if (desktopMode) {
            String chrome = chromeVersionFrom(actual);
            if (!"unknown".equals(chrome)) {
                settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/" + chrome + " Safari/537.36");
            } else {
                settings.setUserAgentString(cleanActual);
            }
            settings.setLoadWithOverviewMode(false);
            settings.setTextZoom(100);
        } else {
            settings.setUserAgentString(cleanActual);
            settings.setLoadWithOverviewMode(true);
            settings.setTextZoom(110);
        }
        settings.setUseWideViewPort(true);
    }

    private String webViewProviderInfo() {
        String[] candidates = {"com.google.android.webview", "com.android.webview", "com.android.chrome"};
        for (String packageName : candidates) {
            try {
                android.content.pm.PackageInfo info = getPackageManager().getPackageInfo(packageName, 0);
                String version = info.versionName == null ? "version unknown" : info.versionName;
                return packageName + " (" + version + ")";
            } catch (PackageManager.NameNotFoundException ignored) { }
        }
        return "System WebView provider package not identified";
    }

    private void showEngineInfo() {
        WebView web = activeWeb();
        String ua = web == null ? "" : web.getSettings().getUserAgentString();
        String chrome = chromeVersionFrom(ua);
        String androidVersion = Build.VERSION.RELEASE == null ? "unknown" : Build.VERSION.RELEASE;
        String abis = Build.SUPPORTED_ABIS == null ? "unknown" : android.text.TextUtils.join(", ", Build.SUPPORTED_ABIS);
        String message = "Android version: " + androidVersion +
                " (API " + Build.VERSION.SDK_INT + ")\n" +
                "WebView package found: " + webViewProviderInfo() + "\n" +
                "Chromium in user-agent: " + chrome + "\n" +
                "CPU ABIs: " + abis + "\n\n" +
                "No Play Store? You can transfer a compatible WebView APK by USB, but Android must accept its package signature and firmware provider configuration. Do not uninstall the built-in WebView.\n\n" +
                "Android 6's final Google WebView branch is 105.0.5195.136. It may still be too old for some current websites.";
        new AlertDialog.Builder(this)
                .setTitle("NOVA browser engine")
                .setMessage(message)
                .setPositiveButton("Open Android 6 WebView APK page", (dialog, which) -> {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://www.apkmirror.com/apk/google-inc/android-system-webview/android-system-webview-105-0-5195-136-release/"));
                        startActivity(intent);
                    } catch (Exception e) {
                        toast("Open the APKMirror page on a computer, then copy the compatible APK to USB.");
                    }
                })
                .setNegativeButton("Close", null)
                .show();
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
        float min = dp(4);
        float maxX = Math.max(min, pointerView.getWidth() - dp(pointerSizeDp + 4));
        float maxY = Math.max(min, pointerView.getHeight() - dp(pointerSizeDp + 5));
        pointerX = Math.max(min, Math.min(pointerX, maxX));
        pointerY = Math.max(min, Math.min(pointerY, maxY));
    }

    private void movePointer(float dx, float dy, int repeatCount) {
        if (pointerView == null || pointerView.getVisibility() != View.VISIBLE) return;
        if (!pointerInitialized) {
            pointerX = pointerView.getWidth() * 0.48f;
            pointerY = pointerView.getHeight() * 0.42f;
            pointerInitialized = true;
        }
        float step = pointerSpeedDp;
        if (repeatCount >= 5) step *= 1.5f;
        if (repeatCount >= 10) step *= 1.7f;
        pointerX += dx * dp(step);
        pointerY += dy * dp(step);
        clampPointer();
        pointerView.invalidate();
        sendPointerHover();
    }

    private void resetPointerPosition() {
        if (pointerView == null) return;
        pointerX = pointerView.getWidth() * 0.48f;
        pointerY = pointerView.getHeight() * 0.42f;
        pointerInitialized = true;
        clampPointer();
        pointerView.invalidate();
        sendPointerHover();
        toast("Pointer returned to centre.");
    }

    private void sendPointerHover() {
        WebView web = activeWeb();
        if (web == null || pointerView == null || fullscreen) return;
        long now = SystemClock.uptimeMillis();
        MotionEvent hover = MotionEvent.obtain(now, now, MotionEvent.ACTION_HOVER_MOVE, pointerX, pointerY, 0);
        hover.setSource(InputDevice.SOURCE_MOUSE);
        web.dispatchGenericMotionEvent(hover);
        hover.recycle();
    }

    private void clickAtPointer() {
        WebView web = activeWeb();
        if (web == null || pointerView == null || fullscreen) return;
        pointerView.showClickPulse();
        long downTime = SystemClock.uptimeMillis();
        MotionEvent down = MotionEvent.obtain(downTime, downTime, MotionEvent.ACTION_DOWN, pointerX, pointerY, 0);
        down.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(down);
        down.recycle();

        long upTime = SystemClock.uptimeMillis();
        MotionEvent up = MotionEvent.obtain(downTime, upTime, MotionEvent.ACTION_UP, pointerX, pointerY, 0);
        up.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        web.dispatchTouchEvent(up);
        up.recycle();
        sendPointerHover();
    }

    private boolean handlePointerRemoteKey(KeyEvent event) {
        if (!"BROWSER".equals(mode) || fullscreen || address.hasFocus() || !pointerEnabled) return false;
        int key = event.getKeyCode();
        if (key == KeyEvent.KEYCODE_DPAD_UP || key == KeyEvent.KEYCODE_DPAD_DOWN ||
                key == KeyEvent.KEYCODE_DPAD_LEFT || key == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (event.getAction() == KeyEvent.ACTION_DOWN) {
                float dx = 0, dy = 0;
                if (key == KeyEvent.KEYCODE_DPAD_UP) dy = -1;
                else if (key == KeyEvent.KEYCODE_DPAD_DOWN) dy = 1;
                else if (key == KeyEvent.KEYCODE_DPAD_LEFT) dx = -1;
                else if (key == KeyEvent.KEYCODE_DPAD_RIGHT) dx = 1;
                movePointer(dx, dy, event.getRepeatCount());
            }
            return true;
        }
        if (key == KeyEvent.KEYCODE_DPAD_CENTER || key == KeyEvent.KEYCODE_ENTER ||
                key == KeyEvent.KEYCODE_NUMPAD_ENTER || key == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.getAction() == KeyEvent.ACTION_UP && event.getRepeatCount() == 0) clickAtPointer();
            return true;
        }
        return false;
    }

    private void showHome() {
        mode = "HOME";
        content.removeAllViews();

        LinearLayout shell = new LinearLayout(this);
        shell.setOrientation(LinearLayout.HORIZONTAL);
        shell.setBackgroundColor(Color.rgb(8, 12, 22));
        content.addView(shell, new FrameLayout.LayoutParams(-1, -1));

        // Compact navigation rail, sized for TV remotes.
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.TOP | Gravity.CENTER_HORIZONTAL);
        rail.setPadding(dp(8), dp(16), dp(8), dp(12));
        rail.setBackgroundColor(Color.rgb(11, 17, 30));
        shell.addView(rail, new LinearLayout.LayoutParams(dp(68), -1));

        TextView brand = label("N", 23, Color.rgb(10, 17, 28), true);
        brand.setGravity(Gravity.CENTER);
        brand.setBackground(rounded(Color.rgb(127, 207, 242), 15));
        rail.addView(brand, new LinearLayout.LayoutParams(dp(42), dp(42)));
        TextView brandCaption = label("NOVA", 9, Color.rgb(178, 190, 213), true);
        brandCaption.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams bc = new LinearLayout.LayoutParams(-1, dp(24));
        bc.bottomMargin = dp(14);
        rail.addView(brandCaption, bc);

        addRailButton(rail, "⌂", "Home", this::showHome, true);
        addRailButton(rail, "☆", "Bookmarks", this::showBookmarks, false);
        addRailButton(rail, "◷", "History", this::showHistory, false);
        addRailButton(rail, "⇩", "Downloads", this::openDownloads, false);
        addRailButton(rail, "＋", "New tab", () -> createTab(true), false);
        View railSpacer = new View(this);
        rail.addView(railSpacer, new LinearLayout.LayoutParams(1, 0, 1f));
        addRailButton(rail, "⚙", "Browser tools", () -> showMenu(toolbar.getChildAt(toolbar.getChildCount() - 1)), false);

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setClipToPadding(false);
        shell.addView(scroll, new LinearLayout.LayoutParams(0, -1, 1f));

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setPadding(dp(25), dp(20), dp(25), dp(26));
        scroll.addView(page);

        // Header / status row.
        LinearLayout head = new LinearLayout(this);
        head.setGravity(Gravity.CENTER_VERTICAL);
        page.addView(head, new LinearLayout.LayoutParams(-1, dp(32)));
        TextView heading = label("YOUR WEB, REIMAGINED", 10, Color.rgb(151, 164, 190), true);
        heading.setLetterSpacing(0.16f);
        head.addView(heading, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView modePill = label(desktopMode ? "▣ DESKTOP MODE" : "◈ TV MODE", 9, Color.rgb(181, 173, 255), true);
        modePill.setGravity(Gravity.CENTER);
        modePill.setPadding(dp(12), dp(7), dp(12), dp(7));
        modePill.setBackground(rounded(Color.rgb(30, 31, 59), 20));
        head.addView(modePill);

        TextView title = label("The internet looks better here.", 28, Color.WHITE, true);
        title.setLetterSpacing(-0.025f);
        LinearLayout.LayoutParams titleP = new LinearLayout.LayoutParams(-1, -2);
        titleP.topMargin = dp(7);
        page.addView(title, titleP);

        TextView subtitle = label("Search, stream and explore from your sofa.", 13,
                Color.rgb(150, 163, 188), false);
        LinearLayout.LayoutParams subP = new LinearLayout.LayoutParams(-1, -2);
        subP.topMargin = dp(4);
        page.addView(subtitle, subP);

        // Search bar. It submits through the same navigation path as the browser address bar.
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setGravity(Gravity.CENTER_VERTICAL);
        searchBar.setPadding(dp(14), 0, dp(7), 0);
        searchBar.setBackground(focusBackground(Color.rgb(25, 39, 54), Color.rgb(50, 75, 108)));
        LinearLayout.LayoutParams searchP = new LinearLayout.LayoutParams(-1, dp(54));
        searchP.topMargin = dp(17);
        page.addView(searchBar, searchP);

        TextView searchIcon = label("⌕", 27, Color.rgb(140, 222, 228), true);
        searchIcon.setGravity(Gravity.CENTER);
        searchBar.addView(searchIcon, new LinearLayout.LayoutParams(dp(34), -1));
        EditText homeSearch = new EditText(this);
        homeSearch.setSingleLine(true);
        homeSearch.setTextSize(15);
        homeSearch.setTextColor(Color.WHITE);
        homeSearch.setHintTextColor(Color.rgb(158, 176, 197));
        homeSearch.setHint("Search anything or enter a website");
        homeSearch.setSelectAllOnFocus(true);
        homeSearch.setImeOptions(EditorInfo.IME_ACTION_GO);
        homeSearch.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        homeSearch.setBackgroundColor(Color.TRANSPARENT);
        homeSearch.setPadding(dp(5), 0, dp(5), 0);
        searchBar.addView(homeSearch, new LinearLayout.LayoutParams(0, -1, 1f));

        TextView engine = label(searchEngine.toUpperCase(Locale.ROOT), 9, Color.rgb(182, 190, 213), true);
        engine.setGravity(Gravity.CENTER);
        engine.setPadding(dp(10), 0, dp(10), 0);
        engine.setFocusable(true);
        engine.setBackground(focusBackground(Color.rgb(35, 47, 64), Color.rgb(70, 68, 126)));
        searchBar.addView(engine, new LinearLayout.LayoutParams(-2, dp(36)));
        engine.setOnClickListener(v -> {
            cycleSearchEngine();
            engine.setText(searchEngine.toUpperCase(Locale.ROOT));
        });

        TextView go = label("GO  ›", 12, Color.rgb(12, 18, 29), true);
        go.setGravity(Gravity.CENTER);
        go.setPadding(dp(16), 0, dp(16), 0);
        go.setBackground(rounded(Color.rgb(126, 206, 235), 11));
        LinearLayout.LayoutParams goP = new LinearLayout.LayoutParams(-2, dp(38));
        goP.leftMargin = dp(8);
        searchBar.addView(go, goP);
        Runnable submitSearch = () -> {
            String query = homeSearch.getText().toString().trim();
            if (query.isEmpty()) return;
            InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
            if (imm != null) imm.hideSoftInputFromWindow(homeSearch.getWindowToken(), 0);
            loadInActiveTab(query);
        };
        go.setFocusable(true);
        go.setOnClickListener(v -> submitSearch.run());
        homeSearch.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER && event.getAction() == KeyEvent.ACTION_UP;
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE || enter) {
                submitSearch.run();
                return true;
            }
            return false;
        });

        // Search engine and browser feature switches.
        LinearLayout chips = new LinearLayout(this);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams chipP = new LinearLayout.LayoutParams(-1, dp(34));
        chipP.topMargin = dp(8);
        page.addView(chips, chipP);
        addHomeChip(chips, "Search: " + searchEngine, () -> {
            cycleSearchEngine();
            showHome();
        });
        addHomeChip(chips, "Ad blocker " + (adBlockEnabled ? "ON" : "OFF"), () -> {
            toggleAdBlocker();
            showHome();
        });
        addHomeChip(chips, "Pointer " + (pointerEnabled ? "ON" : "OFF"), () -> {
            togglePointer();
            showHome();
        });
        addHomeChip(chips, "Browser tools", () -> showMenu(toolbar.getChildAt(toolbar.getChildCount() - 1)));

        addHomeSectionTitle(page, "QUICK LAUNCH", "Your favourite places, one click away");
        HorizontalScrollView quickScroll = new HorizontalScrollView(this);
        quickScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout quickRow = new LinearLayout(this);
        quickRow.setGravity(Gravity.CENTER_VERTICAL);
        quickScroll.addView(quickRow);
        LinearLayout.LayoutParams quickP = new LinearLayout.LayoutParams(-1, dp(112));
        quickP.topMargin = dp(10);
        page.addView(quickScroll, quickP);
        addHomeSiteCard(quickRow, "▶", "YouTube", "Videos & creators", "https://www.youtube.com/", 0xFFFF414B);
        addHomeSiteCard(quickRow, "G", "Google", "Search the web", "https://www.google.com/", 0xFF7CAEFF);
        addHomeSiteCard(quickRow, "S", "Shorts", "Quick videos", "https://www.youtube.com/shorts/", 0xFFFF6B74);
        addHomeSiteCard(quickRow, "N", "Netflix", "Movies & series", "https://www.netflix.com/", 0xFFE84B5B);
        addHomeSiteCard(quickRow, "P", "Prime Video", "Films & originals", "https://www.primevideo.com/", 0xFF55BDEB);
        addHomeSiteCard(quickRow, "J", "JioHotstar", "Sports & shows", "https://www.jiohotstar.com/", 0xFF9A83FF);
        addHomeSiteCard(quickRow, "S", "Sony LIV", "Live & on demand", "https://www.sonyliv.com/", 0xFFB28BFF);
        addHomeSiteCard(quickRow, "W", "Wikipedia", "Explore knowledge", "https://www.wikipedia.org/", 0xFFB9C9E8);

        // Open tabs as resumable cards.
        LinearLayout tabsHeader = addHomeSectionTitle(page, "PICK UP WHERE YOU LEFT OFF", "Open tabs");
        TextView closeAll = label("CLOSE ALL", 9, Color.rgb(164, 172, 192), true);
        closeAll.setGravity(Gravity.CENTER);
        closeAll.setPadding(dp(10), dp(6), dp(10), dp(6));
        closeAll.setBackground(rounded(Color.rgb(26, 33, 47), 9));
        closeAll.setFocusable(true);
        tabsHeader.addView(closeAll);
        closeAll.setOnClickListener(v -> confirmCloseAllTabs());
        HorizontalScrollView tabCardsScroll = new HorizontalScrollView(this);
        tabCardsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabCards = new LinearLayout(this);
        tabCardsScroll.addView(tabCards);
        LinearLayout.LayoutParams tabCardsP = new LinearLayout.LayoutParams(-1, dp(82));
        tabCardsP.topMargin = dp(9);
        page.addView(tabCardsScroll, tabCardsP);
        for (int i = 0; i < tabs.size(); i++) {
            final int index = i;
            Tab tab = tabs.get(i);
            String tabTitle = tab.title == null || tab.title.trim().isEmpty() ? "New tab" : tab.title;
            String tabHost = tab.url == null || tab.url.isEmpty() ? "Start a new journey" : hostName(tab.url);
            addHomeTabCard(tabCards, tabTitle, tabHost, i == activeIndex, () -> switchTab(index));
        }
        addHomeTabCard(tabCards, "＋ New tab", "Open another website", false, () -> createTab(true));

        addHomeSectionTitle(page, "YOUR LIBRARY", "Everything you saved");
        LinearLayout library = new LinearLayout(this);
        library.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams libP = new LinearLayout.LayoutParams(-1, dp(76));
        libP.topMargin = dp(9);
        page.addView(library, libP);
        addHomeLibraryCard(library, "☆", "Bookmarks", "Saved websites", this::showBookmarks);
        addHomeLibraryCard(library, "◷", "History", "Recently visited", this::showHistory);
        addHomeLibraryCard(library, "⇩", "Downloads", "Downloaded files", this::openDownloads);
        addHomeLibraryCard(library, "⚙", "Settings", "Browser preferences", () -> showMenu(toolbar.getChildAt(toolbar.getChildCount() - 1)));

        addHomeSectionTitle(page, "RECENTLY VISITED", "Jump back into a page");
        addRecentHistory(page);

        TextView footer = label("NOVA  •  BUILT FOR THE BIG SCREEN  •  " + (desktopMode ? "DESKTOP MODE" : "TV MODE"),
                9, Color.rgb(99, 116, 143), true);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footP = new LinearLayout.LayoutParams(-1, dp(30));
        footP.topMargin = dp(15);
        page.addView(footer, footP);

        content.addView(shell, new FrameLayout.LayoutParams(-1, -1));
        if (!address.hasFocus()) address.setText("");
        address.setHint("Search " + searchEngine + " or enter website");
        updateNavigationButtons();
    }

    private void addRailButton(LinearLayout rail, String icon, String description, Runnable action, boolean selected) {
        TextView button = label(icon, 21, selected ? Color.rgb(13, 21, 32) : Color.rgb(160, 176, 201), true);
        button.setGravity(Gravity.CENTER);
        button.setFocusable(true);
        button.setContentDescription(description);
        button.setBackground(focusBackground(selected ? Color.rgb(112, 192, 222) : Color.TRANSPARENT, Color.rgb(64, 76, 126)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(44), dp(43));
        p.bottomMargin = dp(7);
        rail.addView(button, p);
        button.setOnClickListener(v -> action.run());
    }

    private void addHomeChip(LinearLayout row, String text, Runnable action) {
        TextView chip = label(text, 9, Color.rgb(180, 192, 213), true);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(10), 0, dp(10), 0);
        chip.setFocusable(true);
        chip.setBackground(focusBackground(Color.rgb(22, 30, 45), Color.rgb(63, 61, 115)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-2, dp(28));
        p.rightMargin = dp(7);
        row.addView(chip, p);
        chip.setOnClickListener(v -> action.run());
    }

    private LinearLayout addHomeSectionTitle(LinearLayout parent, String title, String subtitle) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.BOTTOM | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(38));
        p.topMargin = dp(18);
        parent.addView(row, p);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView head = label(title, 10, Color.rgb(154, 169, 193), true);
        head.setLetterSpacing(0.12f);
        words.addView(head);
        TextView sub = label(subtitle, 9, Color.rgb(104, 122, 150), false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(3);
        words.addView(sub, sp);
        return row;
    }

    private void addHomeSiteCard(LinearLayout row, String mark, String name, String caption, String url, int accent) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(12), dp(8), dp(12), dp(8));
        card.setFocusable(true);
        card.setClickable(true);
        card.setBackground(focusBackground(Color.rgb(20, 29, 45), Color.rgb(54, 63, 106)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(126), dp(100));
        p.rightMargin = dp(9);
        row.addView(card, p);
        TextView icon = label(mark, 22, Color.WHITE, true);
        icon.setGravity(Gravity.CENTER);
        icon.setBackground(rounded(accent, 12));
        card.addView(icon, new LinearLayout.LayoutParams(dp(38), dp(38)));
        TextView title = label(name, 11, Color.WHITE, true);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(-1, -2);
        tp.topMargin = dp(7);
        card.addView(title, tp);
        TextView info = label(caption, 8, Color.rgb(137, 154, 180), false);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(-1, -2);
        ip.topMargin = dp(2);
        card.addView(info, ip);
        card.setContentDescription("Open " + name);
        card.setOnClickListener(v -> loadInActiveTab(url));
    }

    private void addHomeTabCard(LinearLayout row, String titleText, String detail, boolean active, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(13), dp(8), dp(13), dp(8));
        card.setFocusable(true);
        card.setBackground(focusBackground(active ? Color.rgb(33, 48, 68) : Color.rgb(19, 27, 41), Color.rgb(58, 65, 117)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(190), dp(70));
        p.rightMargin = dp(9);
        row.addView(card, p);
        TextView title = label((active ? "●  " : "") + shortTitle(titleText), 11, Color.WHITE, true);
        title.setSingleLine(true);
        title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        card.addView(title);
        TextView host = label(detail, 9, Color.rgb(132, 151, 179), false);
        host.setSingleLine(true);
        host.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(6);
        card.addView(host, hp);
        card.setOnClickListener(v -> action.run());
    }

    private void addHomeLibraryCard(LinearLayout row, String iconText, String titleText, String subtitleText, Runnable action) {
        LinearLayout card = new LinearLayout(this);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(9), dp(8), dp(8), dp(8));
        card.setFocusable(true);
        card.setBackground(focusBackground(Color.rgb(19, 27, 42), Color.rgb(58, 64, 115)));
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(67), 1f);
        p.rightMargin = dp(7);
        row.addView(card, p);
        TextView icon = label(iconText, 20, Color.rgb(156, 205, 227), true);
        icon.setGravity(Gravity.CENTER);
        card.addView(icon, new LinearLayout.LayoutParams(dp(29), dp(36)));
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wp = new LinearLayout.LayoutParams(0, -2, 1f);
        wp.leftMargin = dp(7);
        card.addView(words, wp);
        words.addView(label(titleText, 10, Color.WHITE, true));
        TextView sub = label(subtitleText, 8, Color.rgb(126, 145, 171), false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(3);
        words.addView(sub, sp);
        card.setOnClickListener(v -> action.run());
    }

    private void addRecentHistory(LinearLayout parent) {
        JSONArray recent = readArray("history");
        if (recent.length() == 0) {
            TextView empty = label("Your recently visited websites will appear here.", 10,
                    Color.rgb(114, 132, 158), false);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(38));
            p.topMargin = dp(8);
            parent.addView(empty, p);
            return;
        }
        int count = Math.min(4, recent.length());
        for (int i = 0; i < count; i++) {
            JSONObject item = recent.optJSONObject(i);
            if (item == null) continue;
            String url = item.optString("url");
            String title = item.optString("title", hostName(url));
            TextView entry = label("↗   " + shortTitle(title) + "     ·     " + hostName(url), 10,
                    Color.rgb(181, 195, 216), true);
            entry.setSingleLine(true);
            entry.setEllipsize(android.text.TextUtils.TruncateAt.END);
            entry.setGravity(Gravity.CENTER_VERTICAL);
            entry.setPadding(dp(11), 0, dp(11), 0);
            entry.setFocusable(true);
            entry.setBackground(focusBackground(Color.rgb(16, 23, 36), Color.rgb(54, 61, 107)));
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(35));
            p.topMargin = dp(4);
            parent.addView(entry, p);
            entry.setOnClickListener(v -> loadInActiveTab(url));
        }
    }

    private void openDownloads() {
        try {
            startActivity(new Intent(DownloadManager.ACTION_VIEW_DOWNLOADS));
        } catch (Exception e) {
            toast("Downloads screen is not available on this TV.");
        }
    }

    private void confirmCloseAllTabs() {
        new AlertDialog.Builder(this)
                .setTitle("Close all tabs?")
                .setMessage("This will close all open websites and start a fresh tab.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Close all", (dialog, which) -> {
                    for (Tab tab : new ArrayList<>(tabs)) {
                        try {
                            if (tab.web.getParent() != null) ((ViewGroup)tab.web.getParent()).removeView(tab.web);
                            tab.web.stopLoading();
                            tab.web.destroy();
                        } catch (Exception ignored) { }
                    }
                    tabs.clear();
                    activeIndex = -1;
                    createTab(false);
                })
                .show();
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
        popup.getMenu().add("Browser engine info");
        popup.getMenu().add("Search engine: " + searchEngine);
        popup.getMenu().add("Pointer: " + (pointerEnabled ? "ON" : "OFF"));
        popup.getMenu().add("Cursor settings");
        popup.getMenu().add("Browser settings");
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
            else if ("Browser engine info".equals(value)) showEngineInfo();
            else if (value.startsWith("Search engine:")) cycleSearchEngine();
            else if (value.startsWith("Pointer:")) togglePointer();
            else if ("Cursor settings".equals(value)) showCursorSettings();
            else if ("Browser settings".equals(value)) showBrowserSettings();
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


    private interface IntSettingListener {
        void onChanged(int value);
    }

    private TextView settingText(String value, int size, int color, boolean bold) {
        TextView t = label(value, size, color, bold);
        t.setGravity(Gravity.CENTER_VERTICAL);
        return t;
    }

    private void settingHeading(LinearLayout parent, String title, String subtitle) {
        TextView h = settingText(title, 13, Color.WHITE, true);
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(-1, -2);
        hp.topMargin = dp(13);
        parent.addView(h, hp);
        TextView sub = settingText(subtitle, 9, Color.rgb(147, 164, 190), false);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(-1, -2);
        sp.topMargin = dp(3);
        parent.addView(sub, sp);
    }

    private void settingSwitch(LinearLayout parent, String title, String subtitle,
                               boolean checked, CompoundButton.OnCheckedChangeListener listener) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(-1, dp(54));
        rp.topMargin = dp(5);
        parent.addView(row, rp);
        LinearLayout words = new LinearLayout(this);
        words.setOrientation(LinearLayout.VERTICAL);
        row.addView(words, new LinearLayout.LayoutParams(0, -2, 1f));
        words.addView(settingText(title, 12, Color.WHITE, true));
        TextView sub = settingText(subtitle, 9, Color.rgb(147, 164, 190), false);
        words.addView(sub);
        Switch toggle = new Switch(this);
        toggle.setChecked(checked);
        toggle.setFocusable(true);
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        toggle.setOnCheckedChangeListener(listener);
    }

    private TextView settingButton(String text, Runnable action) {
        TextView b = label(text, 11, Color.WHITE, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(10), dp(6), dp(10), dp(6));
        b.setFocusable(true);
        b.setBackground(focusBackground(Color.rgb(31, 43, 63), Color.rgb(68, 68, 132)));
        b.setOnClickListener(v -> action.run());
        return b;
    }

    private void settingSeekBar(LinearLayout parent, String title, String detail,
                                int min, int max, int current, IntSettingListener listener) {
        settingHeading(parent, title, detail);
        TextView value = settingText(current + " dp", 10, Color.rgb(123, 218, 234), true);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(-1, -2);
        vp.topMargin = dp(4);
        parent.addView(value, vp);
        SeekBar seek = new SeekBar(this);
        seek.setMax(max - min);
        seek.setProgress(Math.max(0, Math.min(max - min, current - min)));
        seek.setFocusable(true);
        parent.addView(seek, new LinearLayout.LayoutParams(-1, dp(40)));
        seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                int actual = min + progress;
                value.setText(actual + (title.toLowerCase(Locale.ROOT).contains("speed") ? " dp / press" : " dp"));
                if (fromUser) listener.onChanged(actual);
            }
            @Override public void onStartTrackingTouch(SeekBar bar) { }
            @Override public void onStopTrackingTouch(SeekBar bar) { }
        });
    }

    private String pointerColorName(int color) {
        if (color == Color.rgb(74, 225, 239)) return "Cyan";
        if (color == Color.rgb(255, 224, 72)) return "Yellow";
        if (color == Color.rgb(126, 245, 135)) return "Lime";
        if (color == Color.rgb(255, 105, 210)) return "Magenta";
        if (color == Color.rgb(255, 164, 71)) return "Orange";
        return "White";
    }

    private void showCursorSettings() {
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(14));
        panel.setBackgroundColor(Color.rgb(14, 21, 35));
        scroll.addView(panel);

        TextView intro = settingText("Set pointer size, speed and appearance for your viewing distance.", 10, Color.rgb(154, 171, 196), false);
        panel.addView(intro);

        settingSwitch(panel, "Show pointer", "Enable the visible remote-controlled cursor.", pointerEnabled,
                (button, checked) -> {
                    pointerEnabled = checked;
                    prefs.edit().putBoolean("pointer_enabled", checked).apply();
                    if (pointerView != null) pointerView.setVisibility(checked ? View.VISIBLE : View.GONE);
                });

        settingSeekBar(panel, "Movement speed", "Distance moved for each arrow-key press.", 5, 40, pointerSpeedDp, value -> {
            pointerSpeedDp = value;
            prefs.edit().putInt("pointer_speed_dp", value).apply();
        });

        settingSeekBar(panel, "Cursor size", "Increase the pointer to make it easier to see.", 24, 60, pointerSizeDp, value -> {
            pointerSizeDp = value;
            prefs.edit().putInt("pointer_size_dp", value).apply();
            clampPointer();
            if (pointerView != null) pointerView.invalidate();
        });

        settingSwitch(panel, "Dark outline", "Improves visibility over bright websites.", pointerOutlineEnabled,
                (button, checked) -> {
                    pointerOutlineEnabled = checked;
                    prefs.edit().putBoolean("pointer_outline", checked).apply();
                    if (pointerView != null) pointerView.invalidate();
                });

        settingHeading(panel, "Cursor shape", "Arrow, crosshair or ring.");
        TextView shape = settingButton("Selected: " + pointerShape + "   ▾", () -> {
            String[] options = {"Arrow", "Crosshair", "Ring"};
            int index = Math.max(0, java.util.Arrays.asList(options).indexOf(pointerShape));
            new AlertDialog.Builder(this).setTitle("Cursor shape")
                    .setSingleChoiceItems(options, index, (dialog, which) -> {
                        pointerShape = options[which];
                        prefs.edit().putString("pointer_shape", pointerShape).apply();
                        dialog.dismiss();
                        shape.setText("Selected: " + pointerShape + "   ▾");
                        if (pointerView != null) pointerView.invalidate();
                    }).setNegativeButton("Cancel", null).show();
        });
        panel.addView(shape, new LinearLayout.LayoutParams(-1, dp(42)));

        settingHeading(panel, "Cursor colour", "Choose a high-contrast colour.");
        TextView colour = settingButton("Selected: " + pointerColorName(pointerColor) + "   ▾", () -> {
            String[] names = {"White", "Cyan", "Yellow", "Lime", "Magenta", "Orange"};
            int[] colors = {Color.WHITE, Color.rgb(74, 225, 239), Color.rgb(255, 224, 72),
                    Color.rgb(126, 245, 135), Color.rgb(255, 105, 210), Color.rgb(255, 164, 71)};
            int index = 0;
            for (int i = 0; i < colors.length; i++) if (colors[i] == pointerColor) index = i;
            new AlertDialog.Builder(this).setTitle("Cursor colour")
                    .setSingleChoiceItems(names, index, (dialog, which) -> {
                        pointerColor = colors[which];
                        prefs.edit().putInt("pointer_color", pointerColor).apply();
                        dialog.dismiss();
                        colour.setText("Selected: " + names[which] + "   ▾");
                        if (pointerView != null) pointerView.invalidate();
                    }).setNegativeButton("Cancel", null).show();
        });
        panel.addView(colour, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView centre = settingButton("Reset pointer to centre", this::resetPointerPosition);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(-1, dp(42));
        cp.topMargin = dp(13);
        panel.addView(centre, cp);

        TextView defaults = settingButton("Restore cursor defaults", () -> {
            pointerEnabled = true;
            pointerSpeedDp = 18;
            pointerSizeDp = 36;
            pointerColor = Color.WHITE;
            pointerShape = "Arrow";
            pointerOutlineEnabled = true;
            prefs.edit().putBoolean("pointer_enabled", true)
                    .putInt("pointer_speed_dp", 18)
                    .putInt("pointer_size_dp", 36)
                    .putInt("pointer_color", Color.WHITE)
                    .putString("pointer_shape", "Arrow")
                    .putBoolean("pointer_outline", true).apply();
            if (pointerView != null) {
                pointerView.setVisibility(View.VISIBLE);
                pointerView.invalidate();
            }
            toast("Cursor defaults restored. Reopen settings to refresh the controls.");
        });
        LinearLayout.LayoutParams dpDefault = new LinearLayout.LayoutParams(-1, dp(42));
        dpDefault.topMargin = dp(7);
        panel.addView(defaults, dpDefault);

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("NOVA · Cursor settings")
                .setView(scroll).setPositiveButton("Done", null).show();
        dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(60), dp(620)),
                Math.min(getResources().getDisplayMetrics().heightPixels - dp(60), dp(650)));
    }

    private void showBrowserSettings() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(20), dp(8), dp(20), dp(14));
        panel.setBackgroundColor(Color.rgb(14, 21, 35));
        scroll.addView(panel);

        settingSwitch(panel, "Ad blocker", "Block requests to selected common ad networks.", adBlockEnabled,
                (button, checked) -> {
                    adBlockEnabled = checked;
                    prefs.edit().putBoolean("ad_block_enabled", checked).apply();
                    WebView web = activeWeb();
                    if (web != null && web.getUrl() != null) web.reload();
                });

        settingSwitch(panel, "Desktop site mode", "Request desktop layouts from websites.", desktopMode,
                (button, checked) -> {
                    desktopMode = checked;
                    prefs.edit().putBoolean("desktop_mode", checked).apply();
                    for (Tab tab : tabs) {
                        applyUserAgent(tab.web.getSettings());
                        if (tab.web.getUrl() != null) tab.web.reload();
                    }
                });

        settingSwitch(panel, "Remote pointer", "Arrow keys move the cursor; OK clicks.", pointerEnabled,
                (button, checked) -> {
                    pointerEnabled = checked;
                    prefs.edit().putBoolean("pointer_enabled", checked).apply();
                    if (pointerView != null) pointerView.setVisibility(checked ? View.VISIBLE : View.GONE);
                });

        settingHeading(panel, "Search provider", "Choose the provider used for searches.");
        TextView search = settingButton("Selected: " + searchEngine + "   ▾", () -> {
            String[] options = {"Google", "Bing", "DuckDuckGo"};
            int index = Math.max(0, java.util.Arrays.asList(options).indexOf(searchEngine));
            new AlertDialog.Builder(this).setTitle("Search provider")
                    .setSingleChoiceItems(options, index, (dialog, which) -> {
                        searchEngine = options[which];
                        prefs.edit().putString("search_engine", searchEngine).apply();
                        dialog.dismiss();
                        search.setText("Selected: " + searchEngine + "   ▾");
                    }).setNegativeButton("Cancel", null).show();
        });
        panel.addView(search, new LinearLayout.LayoutParams(-1, dp(42)));

        settingHeading(panel, "Pointer appearance", "Customise cursor speed, size, shape and colour.");
        TextView cursor = settingButton("Open cursor settings", this::showCursorSettings);
        panel.addView(cursor, new LinearLayout.LayoutParams(-1, dp(42)));

        settingHeading(panel, "Diagnostics", "Check the Android and Chromium/WebView versions.");
        TextView engine = settingButton("Browser engine information", this::showEngineInfo);
        panel.addView(engine, new LinearLayout.LayoutParams(-1, dp(42)));

        TextView clear = settingButton("Clear browsing data", () -> new AlertDialog.Builder(this)
                .setTitle("Clear browsing data?")
                .setMessage("This removes cookies, cache, history and bookmarks.")
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Clear", (dialog, which) -> clearData()).show());
        LinearLayout.LayoutParams clearP = new LinearLayout.LayoutParams(-1, dp(42));
        clearP.topMargin = dp(14);
        panel.addView(clear, clearP);

        AlertDialog dialog = new AlertDialog.Builder(this).setTitle("NOVA · Browser settings")
                .setView(scroll).setPositiveButton("Done", null).show();
        dialog.getWindow().setLayout(Math.min(getResources().getDisplayMetrics().widthPixels - dp(60), dp(620)),
                Math.min(getResources().getDisplayMetrics().heightPixels - dp(60), dp(650)));
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
