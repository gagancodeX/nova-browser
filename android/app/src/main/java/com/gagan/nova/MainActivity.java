package com.gagan.nova;

import android.Manifest;
import android.app.Activity;
import android.app.DownloadManager;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.view.Gravity;
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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Locale;

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
    private EditText address;
    private TextView backButton, forwardButton, reloadButton;
    private ValueCallback<Uri[]> fileCallback;
    private View customView;
    private WebChromeClient.CustomViewCallback customViewCallback;
    private boolean fullscreen = false;
    private boolean desktopMode = false;
    private String panelReturnMode = "HOME";
    private String pendingDownload;
    private SharedPreferences prefs;

    private static class Tab {
        final WebView web;
        String title = "New tab";
        String url = "";
        Tab(WebView web) { this.web = web; }
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setStatusBarColor(Color.rgb(9, 13, 22));
        getWindow().setNavigationBarColor(Color.rgb(9, 13, 22));
        enterImmersive();
        prefs = getSharedPreferences(PREFS, MODE_PRIVATE);
        desktopMode = prefs.getBoolean("desktop_mode", false);
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

    private GradientDrawable focusBackground(int normal, int focused) {
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
        address.setBackground(rounded(Color.rgb(27, 36, 54), 13));
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

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
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
        s.setJavaScriptCanOpenWindowsAutomatically(true);
        s.setSupportMultipleWindows(true);
        s.setMediaPlaybackRequiresUserGesture(false);
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
                return handleExternalScheme(url);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return handleExternalScheme(request.getUrl().toString());
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
                if (view == activeWeb()) {
                    if (!address.hasFocus()) address.setText(url);
                    updateNavigationButtons();
                    refreshTabs();
                }
            }
            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (Build.VERSION.SDK_INT >= 23 && request.isForMainFrame() && view == activeWeb()) {
                    toast("Page failed to load. Check Wi-Fi and press Reload.");
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
                if (!userGesture || tabs.size() >= MAX_TABS) return false;
                createTab(false);
                showWebContent();
                WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
                transport.setWebView(activeWeb());
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
        if (desktopMode) {
            settings.setUserAgentString("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36");
        } else {
            String ua = settings.getUserAgentString();
            if (ua != null) {
                ua = ua.replace("; wv)", ")").replace("Version/4.0 ", "");
                settings.setUserAgentString(ua);
            }
        }
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
            return "https://www.google.com/search?q=" + URLEncoder.encode(q, "UTF-8");
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
        content.removeAllViews();
        WebView web = activeWeb();
        if (web != null) {
            if (web.getParent() != null) ((ViewGroup)web.getParent()).removeView(web);
            content.addView(web, new FrameLayout.LayoutParams(-1, -1));
            web.requestFocus();
        }
        updateNavigationButtons();
    }

    private void showHome() {
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
        PopupMenu popup = new PopupMenu(this, anchor);
        popup.getMenu().add("Add bookmark");
        popup.getMenu().add("Bookmarks");
        popup.getMenu().add("History");
        popup.getMenu().add(desktopMode ? "✓ Desktop site mode" : "Desktop site mode");
        popup.getMenu().add("Clear browsing data");
        popup.setOnMenuItemClickListener(item -> {
            String value = item.getTitle().toString();
            if ("Add bookmark".equals(value)) addBookmark();
            else if ("Bookmarks".equals(value)) showBookmarks();
            else if ("History".equals(value)) showHistory();
            else if (value.contains("Desktop site mode")) toggleDesktopMode();
            else if ("Clear browsing data".equals(value)) clearData();
            return true;
        });
        popup.show();
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
        root.removeViewAt(1);
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
        if (tabsRow.getParent() == null) {
            HorizontalScrollView strip = new HorizontalScrollView(this);
            strip.setHorizontalScrollBarEnabled(false);
            strip.setBackgroundColor(Color.rgb(10, 15, 25));
            strip.addView(tabsRow);
            root.addView(strip, 1, new LinearLayout.LayoutParams(-1, dp(42)));
        }
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
        if (event.getAction() == KeyEvent.ACTION_UP && event.getKeyCode() == KeyEvent.KEYCODE_MENU) {
            showMenu(toolbar.getChildAt(toolbar.getChildCount() - 1));
            return true;
        }
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
