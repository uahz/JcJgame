package com.uahz.fengqi;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

/**
 * 风起旧厂街 · 高启强 —— 原生安卓外壳（v2.7）
 *
 * 相对 v2.6 的变化：
 *  1. 【状态栏真正可见】v2.6 只设了透明色，部分 ROM / 系统版本上系统栏图标仍会被
 *     隐藏或被垫上黑色底板（就是那条「大黑边」）。本版把三条路一起走死：
 *       · 21+  FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS + setStatusBarColor(TRANSPARENT)
 *       · 21-29 setSystemUiVisibility(LAYOUT_STABLE|LAYOUT_FULLSCREEN|LAYOUT_HIDE_NAVIGATION)
 *       · 30+  setDecorFitsSystemWindows(false) + 完全交给 WindowInsetsController 管图标
 *     30+ 不再使用已废弃的 setSystemUiVisibility（新版系统上会被忽略，图标明暗就切不动）。
 *  2. 【insets 一律吃掉】监听器里显式 consume，避免系统把状态栏高度当成 padding
 *     垫在 WebView 上——那会在内容顶部留一条谁也没画的黑色空带。
 *  3. 安全区仍是「物理像素 ÷ density」换算成 CSS px 后注入 --sat/--sab。
 *  4. 返回键三级处理保持：网页 window.__onBack() 说能关就关，说不能关才退出。
 *  5. 主题切换时状态栏/导航栏图标明暗跟随（日间深色图标、夜间浅色图标）。
 */
public class MainActivity extends Activity {

    private static final int BG = 0xFF07090B;
    private static final String START_URL = "file:///android_asset/index.html";

    private WebView web;
    /** 网页侧最近一次要求的图标明暗，供每次 insets 变化后重新套用 */
    private boolean darkIcons = false;

    /** 供网页侧调用：切换系统栏图标明暗（日间模式需要深色图标） */
    public class Host {
        @JavascriptInterface
        public void setBarsAppearance(final boolean dark) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    darkIcons = dark;
                    applyBarIconMode(dark);
                }
            });
        }
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setupEdgeToEdge();

        web = new WebView(this);
        web.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(BG);
        web.setFitsSystemWindows(false);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(true);
        s.setAllowContentAccess(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);
        s.setGeolocationEnabled(false);
        s.setSaveFormData(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        // 禁止系统强制暗色，保住写实背景照片的原调色
        try {
            if (Build.VERSION.SDK_INT >= 33) {
                s.setAlgorithmicDarkeningAllowed(false);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                s.setForceDark(WebSettings.FORCE_DARK_OFF);
            }
        } catch (Throwable ignore) {
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                s.setOffscreenPreRaster(true);
            } catch (Throwable ignore) {
            }
        }

        web.addJavascriptInterface(new Host(), "AndroidHost");

        // 安全区实时跟随（旋转、手势导航与三键切换、键盘弹出都会触发）
        web.setOnApplyWindowInsetsListener(new View.OnApplyWindowInsetsListener() {
            @Override
            public WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
                pushInsets(insets);
                // 显式消费：不让系统把状态栏 / 导航栏高度再垫一层 padding 到 WebView 上
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    return WindowInsets.CONSUMED;
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT_WATCH) {
                    @SuppressWarnings("deprecation")
                    WindowInsets consumed = insets.consumeSystemWindowInsets();
                    return consumed;
                }
                return insets;
            }
        });

        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                WindowInsets wi = v.getRootWindowInsets();
                if (wi != null) pushInsets(wi);
                applyBarIconMode(darkIcons);
            }
        });
        web.setWebChromeClient(new WebChromeClient());
        web.setVerticalScrollBarEnabled(false);
        web.setHorizontalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setLongClickable(false);
        web.setHapticFeedbackEnabled(true);
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;   // 长按留给网页里的拖拽选择，不弹系统文本菜单
            }
        });

        setContentView(web);
        web.loadUrl(START_URL);
    }

    /**
     * 真正的 edge-to-edge：状态栏 / 导航栏保留且内容铺到它们下面。
     * 关键是「透明 + 不垫对比度蒙版 + decor 不吃 insets」三件事同时成立。
     */
    @SuppressWarnings("deprecation")
    private void setupEdgeToEdge() {
        Window w = getWindow();

        // ① 明确允许内容画到系统栏之后，并清掉任何残留的全屏标志
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.clearFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN);
        w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            w.setStatusBarColor(Color.TRANSPARENT);
            w.setNavigationBarColor(Color.TRANSPARENT);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // ★ 关键：关掉系统给透明状态栏/导航栏垫的对比度蒙版（默认 true），否则就是一条黑边
            w.setStatusBarContrastEnforced(false);
            w.setNavigationBarContrastEnforced(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            w.setDecorFitsSystemWindows(false);   // API 30+ 官方 edge-to-edge
        } else {
            // API 21-29 走老的 systemUiVisibility 路线（30+ 上这个 API 已废弃且可能被忽略）
            w.getDecorView().setSystemUiVisibility(barFlags(false));
        }
        applyBarIconMode(false);
    }

    /** 夜景用浅色图标；日间模式用深色图标 */
    @SuppressWarnings("deprecation")
    private void applyBarIconMode(boolean dark) {
        Window w = getWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowInsetsController c = w.getInsetsController();
            if (c == null) return;
            int mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            c.setSystemBarsAppearance(dark ? mask : 0, mask);
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            w.getDecorView().setSystemUiVisibility(barFlags(dark));
        }
    }

    @SuppressWarnings("deprecation")
    private int barFlags(boolean darkIcons) {
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (darkIcons && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        if (darkIcons && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        return flags;
    }

    /**
     * 把状态栏 / 导航条高度注入网页 CSS 变量。
     * ★ getSystemWindowInsetTop() 返回**物理像素**，而 CSS 的 px 等于 dp，
     *   必须除以 density，否则在 dpr 2.6 的机器上顶栏会被撑高到近一倍。
     */
    private void pushInsets(WindowInsets wi) {
        if (web == null || wi == null) return;
        try {
            float density = getResources().getDisplayMetrics().density;
            if (density <= 0f) return;
            int topPx, bottomPx;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                android.graphics.Insets bars = wi.getInsets(WindowInsets.Type.systemBars());
                topPx = bars.top;
                bottomPx = bars.bottom;
                // 刘海屏：状态栏本身可能为 0，用 cutout 兜底，否则内容会顶到刘海下面
                if (topPx <= 0) {
                    android.graphics.Insets cut = wi.getInsets(WindowInsets.Type.displayCutout());
                    if (cut.top > 0) topPx = cut.top;
                }
            } else {
                topPx = wi.getSystemWindowInsetTop();
                bottomPx = wi.getSystemWindowInsetBottom();
                if (topPx <= 0) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && wi.getDisplayCutout() != null) {
                        topPx = wi.getDisplayCutout().getSafeInsetTop();
                    }
                    if (topPx <= 0) topPx = fallbackStatusBarPx();
                }
            }
            int topCss = Math.round(topPx / density);
            int bottomCss = Math.round(bottomPx / density);
            final String js = "document.documentElement.style.setProperty('--sat','" + topCss + "px');"
                    + "document.documentElement.style.setProperty('--sab','" + bottomCss + "px');"
                    + "document.body && document.body.classList.add('in-app');"
                    + "window.__navRelayout && window.__navRelayout();";
            web.evaluateJavascript(js, null);
        } catch (Throwable ignore) {
        }
    }

    /** 取不到 insets 时按状态栏资源高度兜底 */
    private int fallbackStatusBarPx() {
        int id = getResources().getIdentifier("status_bar_height", "dimen", "android");
        if (id > 0) return getResources().getDimensionPixelSize(id);
        return Math.round(24 * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) {
            web.onResume();
            applyBarIconMode(darkIcons);
            WindowInsets wi = web.getRootWindowInsets();
            if (wi != null) pushInsets(wi);
        }
    }

    @Override
    protected void onPause() {
        if (web != null) web.onPause();
        super.onPause();
    }

    /**
     * 返回键三级处理：先让网页决定。
     *   - 网页返回 true：已消费（关掉图鉴/面板/结局，或已提示「再按一次退出」）
     *   - 网页返回 false：确实无处可退，交给系统退出
     */
    @Override
    public void onBackPressed() {
        if (web == null) {
            super.onBackPressed();
            return;
        }
        web.evaluateJavascript(
                "(function(){try{return window.__onBack?window.__onBack():false}catch(e){return false}})()",
                new ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String v) {
                        boolean handled = v != null && v.contains("true");
                        if (!handled) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                finishAndRemoveTask();
                            } else {
                                finish();
                            }
                        }
                    }
                });
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.loadUrl("about:blank");
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
