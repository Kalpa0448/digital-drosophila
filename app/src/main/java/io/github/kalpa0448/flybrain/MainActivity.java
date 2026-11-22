package io.github.kalpa0448.flybrain;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.animation.DecelerateInterpolator;
import android.webkit.ConsoleMessage;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * 数字果蝇 —— 原生外壳 + 离线资源
 *
 * 三个页面（围栏 / 大世界 / 养果蝇）打包在 assets/www，
 * 通过 https://fly.local 虚拟域名由 shouldInterceptRequest 提供，
 * 这样 Worker、fetch、.gz 解压、localStorage 才能正常工作。
 */
public class MainActivity extends Activity {

    private static final String TAG = "FlyBrain";
    private static final String HOST = "fly.local";
    private static final String BASE = "https://" + HOST;

    private static final String[] TAB_PATH = {"/index.html", "/world/index.html", "/colony/index.html"};
    private static final int[] TAB_ICON = {
            R.drawable.ic_tab_brain, R.drawable.ic_tab_world, R.drawable.ic_tab_colony};

    private static final int ACCENT = 0xFF34D399;
    private static final int DIM = 0xFF6B7385;

    private FrameLayout root;
    private View barWrap;
    private ImageButton menuFab;
    private FrameLayout.LayoutParams fabParams;
    private FrameLayout sheetBox;
    private View sheetScrim;
    private LinearLayout sheetPanel;
    private TextView sheetModeValue;
    private Hud hud;
    private ParamSheet paramSheet;
    private InboxSheet inboxSheet;
    private ObsSheet obsSheet;
    private ParamSheet worldParamSheet;
    private ChartSheet chartSheet;
    private AboutSheet aboutSheet;
    private InfoCard infoCard;
    private UpdateSheet updateSheet;
    private android.content.SharedPreferences prefs;
    private LegendCard legendCard;
    private ParamSheet colonyParamSheet;
    private PageHud worldHud;
    private PageHud colonyHud;
    private FrameLayout webHost;
    private View tabIndicator;
    private boolean indicatorPlaced = false;
    private View stripHit;
    private final android.os.Handler uiH = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable guardStrip = new Runnable() {
        @Override
        public void run() {
            if (infoCard != null) infoCard.setVisible(current == 2);
            updateStripHit();
            uiH.postDelayed(this, 350);
        }
    };

    /** 有弹窗/抽屉打开时，放电条热区必须让路，否则会抢走弹窗底部按钮的点击 */
    private void updateStripHit() {
        if (stripHit == null) return;
        boolean overlay = (sheetPanel != null && sheetPanel.getVisibility() == View.VISIBLE)
                || (inboxSheet != null && inboxSheet.isOpen())
                || (obsSheet != null && obsSheet.isOpen())
                || (chartSheet != null && chartSheet.isOpen())
                || (aboutSheet != null && aboutSheet.isOpen())
                || (paramSheet != null && paramSheet.isOpen())
                || (worldParamSheet != null && worldParamSheet.isOpen())
                || (colonyParamSheet != null && colonyParamSheet.isOpen())
                || (exitSheet != null && exitSheet.isOpen());
        boolean on = current == 0 && !overlay;
        stripHit.setVisibility(on ? View.VISIBLE : View.GONE);
        if (on) stripHit.bringToFront();
    }
    private LinearLayout bottomBar;
    private FrameLayout.LayoutParams barParams;
    private View splash;

    private ObjectAnimator logoPulse;
    private ObjectAnimator lineSweep;

    private final WebView[] webs = new WebView[TAB_PATH.length];
    private final TextView[] tabs = new TextView[TAB_PATH.length];
    private int current = -1;
    private boolean splashDone = false;
    private ChoiceSheet exitSheet;
    private long lastBackAt = 0L;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().setBackgroundDrawable(new ColorDrawable(0xFF0B0D13));
        if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true);

        root = new FrameLayout(this);
        root.setBackgroundColor(0xFF0B0D13);

        stripHit = new View(this);
        stripHit.setBackgroundColor(0x00000000);
        stripHit.setOnClickListener(v -> {
            if (webs[0] == null) return;
            hud.hide();
            obsSheet.attach(webs[0]);
            obsSheet.show();
        });
        stripHit.setVisibility(View.GONE);
        uiH.postDelayed(guardStrip, 350);

        webHost = new FrameLayout(this);
        FrameLayout.LayoutParams shp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(96));
        shp.gravity = Gravity.BOTTOM;
        shp.bottomMargin = dp(74) + 6;
        root.addView(stripHit, shp);

        root.addView(webHost, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        hud = new Hud(this, root);
        paramSheet = new ParamSheet(this, root, "参数", "#paramPanel .pp-row", "#paramPanel .pp-group");
        worldParamSheet = new ParamSheet(this, root, "个性化设置", ".prow", ".pg");
        chartSheet = new ChartSheet(this, root);
        aboutSheet = new AboutSheet(this, root);
        updateSheet = new UpdateSheet(this, root);
        prefs = getSharedPreferences("flybrain", MODE_PRIVATE);
        aboutSheet.setListener(new AboutSheet.Listener() {
            @Override
            public void onCheckUpdate() {
                aboutSheet.hide();
                updateSheet.show();
            }

            @Override
            public void onAutoUpdateChanged(boolean on) {
                prefs.edit().putBoolean("auto_update", on).apply();
                if (on) {
                    updateSheet.startCheck(true);
                }
            }
        });
        aboutSheet.setAutoChecked(prefs.getBoolean("auto_update", true));
        infoCard = new InfoCard(this, root);
        legendCard = new LegendCard(this, root);
        colonyParamSheet = new ParamSheet(this, root, "参数", "#sliders .row", "#sliders h3");
        inboxSheet = new InboxSheet(this, root);
        obsSheet = new ObsSheet(this, root);
        exitSheet = new ChoiceSheet(this, root, "退出");
        worldHud = buildWorldHud();
        colonyHud = buildColonyHud();

        hud.setOnObsClick(() -> {
            obsSheet.attach(webs[0]);
            obsSheet.show();
        });
        hud.setOnInboxClick(() -> {
            hud.hide();
            inboxSheet.attach(webs[0]);
            inboxSheet.show();
        });
        inboxSheet.setOnClose(() -> {
            if (current == 0) hud.show();
        });
        paramSheet.setOnClose(() -> {
            if (current == 0) hud.show();
        });

        // 悬浮式导航条（启动页期间隐藏，随内容一起淡入）
        FrameLayout bar = new FrameLayout(this);
        bar.setBackgroundResource(R.drawable.bg_bar);
        bar.setElevation(dp(14));
        bar.setAlpha(0f);
        barWrap = bar;

        tabIndicator = new View(this);
        GradientDrawable ind = new GradientDrawable();
        ind.setShape(GradientDrawable.RECTANGLE);
        ind.setCornerRadius(dp(18));
        ind.setColor(0x2234D399);
        ind.setStroke(dp(1), 0x4D34D399);
        tabIndicator.setBackground(ind);
        bar.addView(tabIndicator, new FrameLayout.LayoutParams(0, 0));

        bottomBar = new LinearLayout(this);
        bottomBar.setOrientation(LinearLayout.HORIZONTAL);
        bottomBar.setPadding(dp(6), dp(6), dp(6), dp(6));
        bottomBar.setClipChildren(false);
        bottomBar.setClipToPadding(false);
        bar.addView(bottomBar, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        barParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        barParams.gravity = Gravity.BOTTOM;
        barParams.setMargins(dp(12), 0, dp(12), dp(12));
        root.addView(bar, barParams);

        buildTabs();
        applyInsets();

        buildFab();
        buildSheet();

        splash = buildSplash();
        splash.setElevation(dp(80));
        root.addView(splash, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        setContentView(root);
        enterImmersive();
        select(0);

        if (prefs.getBoolean("auto_update", true)) {
            uiH.postDelayed(() -> {
                if (prefs.getBoolean("auto_update", true)) autoCheckOnce();
            }, 2600);
        }
    }

    /** 启动时静默检查：有新版就更新「关于」行状态，不打断用户 */
    private void autoCheckOnce() {
        new UpdateChecker().check(r -> {
            if (r.ok && r.hasUpdate) {
                aboutSheet.setUpdateStatus("发现 " + r.latestTag, true);
                android.widget.Toast.makeText(this,
                        "发现新版本 " + r.latestTag + "，可在「关于」页更新",
                        android.widget.Toast.LENGTH_LONG).show();
            } else if (r.ok) {
                aboutSheet.setUpdateStatus("已是最新", false);
            }
        });
    }

    // ---------------- 底部导航 ----------------

    private void buildTabs() {
        String[] labels = {getString(R.string.tab_fence), getString(R.string.tab_world), getString(R.string.tab_colony)};
        for (int i = 0; i < labels.length; i++) {
            TextView tv = new TextView(this);
            tv.setText(labels[i]);
            tv.setTextSize(11f);
            tv.setLetterSpacing(0.04f);
            tv.setGravity(Gravity.CENTER);
            tv.setIncludeFontPadding(false);
            tv.setCompoundDrawablesWithIntrinsicBounds(0, TAB_ICON[i], 0, 0);
            tv.setCompoundDrawablePadding(dp(5));
            tv.setPadding(dp(4), dp(8), dp(4), dp(7));
            tv.setBackground(null);
            tv.setClickable(true);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(3), 0, dp(3), 0);
            tv.setLayoutParams(lp);

            final int idx = i;
            tv.setOnClickListener(v -> {
                if (idx == current) return;
                v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
                select(idx);
            });
            bottomBar.addView(tv);
            tabs[i] = tv;
        }
        refreshTabs(false);
    }

    private void refreshTabs(boolean animate) {
        for (int i = 0; i < tabs.length; i++) {
            boolean on = (i == current);
            tabs[i].setSelected(on);
            tabs[i].setTextColor(on ? ACCENT : DIM);
            Drawable[] ds = tabs[i].getCompoundDrawables();
            if (ds[1] != null) {
                ds[1].mutate().setColorFilter(new PorterDuffColorFilter(on ? ACCENT : DIM,
                        PorterDuff.Mode.SRC_IN));
            }
            if (animate) {
                float target = on ? 1.02f : 1f;
                tabs[i].animate().scaleX(target).scaleY(target).setDuration(170)
                        .setInterpolator(new DecelerateInterpolator()).start();
            }
        }
        slideIndicator(animate);
    }

    /** 高亮块在底栏上滑动到当前页 */
    private void slideIndicator(boolean animate) {
        if (tabIndicator == null || current < 0 || tabs[current] == null) return;
        View t = tabs[current];
        if (t.getWidth() == 0 || bottomBar.getWidth() == 0) {
            // 还没完成布局，等一帧再来
            tabIndicator.post(() -> slideIndicator(false));
            return;
        }
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tabIndicator.getLayoutParams();
        int targetX = bottomBar.getLeft() + t.getLeft();
        if (!indicatorPlaced || !animate) {
            lp.width = t.getWidth();
            lp.height = t.getHeight();
            lp.leftMargin = targetX;
            lp.topMargin = bottomBar.getTop() + t.getTop();
            tabIndicator.setLayoutParams(lp);
            tabIndicator.setTranslationX(0f);
            indicatorPlaced = true;
            return;
        }
        if (lp.width != t.getWidth() || lp.height != t.getHeight()) {
            lp.width = t.getWidth();
            lp.height = t.getHeight();
            lp.topMargin = bottomBar.getTop() + t.getTop();
            tabIndicator.setLayoutParams(lp);
        }
        tabIndicator.animate().translationX(targetX - lp.leftMargin)
                .setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void select(int i) {
        if (i < 0 || i >= TAB_PATH.length || i == current) return;
        WebView prev = (current >= 0) ? webs[current] : null;
        if (prev != null) {
            prev.onPause();          // 停掉后台页面的 JS，省电省内存
            prev.setVisibility(View.GONE);
        }
        current = i;
        // 立即跟随切页，不等 350ms 守护轮询
        if (infoCard != null) infoCard.setVisible(i == 2);
        if (legendCard != null) legendCard.setVisible(i == 1);
        if (legendCard != null && i != 1) legendCard.hide();
        if (webs[i] == null) webs[i] = createWeb(i);
        WebView wv = webs[i];
        if (legendCard != null) legendCard.setWeb(wv);
        wv.setVisibility(View.VISIBLE);
        wv.onResume();
        // 交叉淡入，避免硬切黑屏
        wv.setAlpha(0f);
        wv.animate().alpha(1f).setDuration(prev == null ? 0 : 190).start();
        // 页面被隐藏时 canvas 可能按 0 尺寸重排过，恢复时补一次 resize
        wv.postDelayed(() -> wv.evaluateJavascript(
                "window.dispatchEvent(new Event('resize'));", null), 260);
        refreshTabs(true);
        updateFab();
        if (i == 0 && webs[0] != null) {
            hud.attach(webs[0]);
            if (splashDone) hud.show();
        } else {
            hud.hide();
            hud.detach();
        }
        if (i == 1 && webs[1] != null) {
            worldHud.attach(webs[1]);
            if (splashDone) worldHud.show();
        } else {
            worldHud.hide();
            worldHud.detach();
        }
        if (i == 2 && webs[2] != null) {
            colonyHud.attach(webs[2]);
            if (splashDone) colonyHud.show();
        } else {
            colonyHud.hide();
            colonyHud.detach();
        }
    }



    private PageHud buildWorldHud() {
        java.util.List<PageHud.Row> rows = java.util.Arrays.asList(
                new PageHud.Row(new PageHud.Pair("果蝇", "n-fly"),
                        new PageHud.Pair("卵", "n-egg")),
                new PageHud.Row(new PageHud.Pair("蜘蛛", "n-pred2"),
                        new PageHud.Pair("卵", "n-eggp")),
                new PageHud.Row(new PageHud.Pair("食物", "n-food")),
                new PageHud.Row(new PageHud.Pair("青蛙", "n-frog"),
                        new PageHud.Pair("捕蝇草", "n-trap")),
                new PageHud.Row("平均能量",
                        new PageHud.Pair("果蝇", "avg-e"),
                        new PageHud.Pair("蜘蛛", "avg-p")),
                new PageHud.Row("最高代",
                        new PageHud.Pair("果蝇", "gen"),
                        new PageHud.Pair("蜘蛛", "gen-p")),
                new PageHud.Row("交配",
                        new PageHud.Pair("果蝇", "mate-f"),
                        new PageHud.Pair("蜘蛛", "mate-p")),
                new PageHud.Row("被捕食",
                        new PageHud.Pair("蜘蛛", "eat-p"),
                        new PageHud.Pair("青蛙", "eat-f"),
                        new PageHud.Pair("捕蝇草", "eat-t")));

        java.util.List<PageHud.Act> primary = java.util.Arrays.asList(
                new PageHud.Act("🍎 撒食物", "#tFood"),
                new PageHud.Act("🪰 放果蝇", "#tFly"),
                new PageHud.Act("🕷 放蜘蛛", "#tPred"),
                new PageHud.Act("🧽 清蜘蛛", "#tClear"));

        java.util.List<PageHud.Act> more = java.util.Arrays.asList(
                new PageHud.Act("📈 种群曲线", "#tChart",
                        "document.getElementById('chart').classList.contains('on')"),
                new PageHud.Act("⚙︎ 参数", "#__param"),
                new PageHud.Act("⏩ 加速", "#tSpeed",
                        "(function(){var t=document.getElementById('tSpeed').textContent;"
                                + "return t.indexOf('×')>=0&&t.indexOf('1×')<0;})()",
                        "(function(){var t=document.getElementById('tSpeed').textContent;"
                                + "return t.indexOf('1×')>=0?'⏩ 加速':t;})()"),

                new PageHud.Act("⏸ 暂停", "#__pause",
                        "document.getElementById('btnPause').textContent.indexOf('▶')>=0"),
                new PageHud.Act("↺ 重置", "#btnReset"));

        PageHud hudW = new PageHud(this, root, "大世界", rows, null, null, primary, more, null);
        hudW.setParamAction(() -> {
            if (webs[1] != null) {
                worldParamSheet.attach(webs[1]);
                worldParamSheet.show();
                hudW.setChipActive("#__param", true);
            }
        });
        worldParamSheet.setOnClose(() -> hudW.setChipActive("#__param", false));
        hudW.setAction("#__pause", () -> {
            if (webs[1] == null) return;
            webs[1].evaluateJavascript(
                    "(function(){try{paused=!paused;var b=document.getElementById('btnPause');"
                            + "if(b)b.textContent=paused?'▶':'⏸';}catch(e){"
                            + "var b=document.getElementById('btnPause');if(b)b.click();}})()", null);
        });
        return hudW;
    }

    private PageHud buildColonyHud() {
        java.util.List<PageHud.Act> primary = java.util.Arrays.asList(
                new PageHud.Act("🍎 食物", "[data-mode=\"food\"]"),
                new PageHud.Act("🟨 粘板", "[data-mode=\"trap\"]"),
                new PageHud.Act("🪰 投蝇", "[data-mode=\"fly\"]"),
                new PageHud.Act("🧽 清除", "[data-mode=\"erase\"]"));

        java.util.List<PageHud.Act> more = java.util.Arrays.asList(
                new PageHud.Act("🎥 切换视角", "#btnView"),
                new PageHud.Act("🧠 脑活动", "#btnBrain",
                        "document.getElementById('btnBrain').classList.contains('on')"),
                new PageHud.Act("👃 气味场", "#btnField",
                        "document.getElementById('btnField').classList.contains('on')"),
                new PageHud.Act("⏸ 暂停", "#btnPause",
                        "document.getElementById('btnPause').classList.contains('on')",
                        "(function(){var b=document.getElementById('btnPause');"
                                + "return b&&b.classList.contains('on')?'▶ 已暂停':'⏸ 暂停';})()"),
                new PageHud.Act("⚙︎ 参数", "#__param"),
                new PageHud.Act("↺ 重置", "#btnReset"));

        String probe = "(document.getElementById('panel').classList.contains('show')"
                + "||document.body.classList.contains('brainon'))";
        PageHud hudC = new PageHud(this, root, "养果蝇", null, "#stats", null, primary, more, probe);
        hudC.setParamAction(() -> {
            if (webs[2] != null) {
                colonyParamSheet.attach(webs[2]);
                colonyParamSheet.show();
                hudC.setChipActive("#__param", true);
            }
        });
        colonyParamSheet.setOnClose(() -> hudC.setChipActive("#__param", false));
        return hudC;
    }

    // ---------------- 原生二级菜单 ----------------

    private void buildFab() {
        menuFab = new ImageButton(this);
        menuFab.setImageResource(R.drawable.ic_tune);
        menuFab.setBackgroundResource(R.drawable.bg_fab);
        menuFab.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        menuFab.setPadding(dp(10), dp(10), dp(10), dp(10));
        menuFab.setAlpha(0f);
        menuFab.setVisibility(View.GONE);
        menuFab.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            openSheet();
        });
        fabParams = new FrameLayout.LayoutParams(dp(44), dp(44));
        fabParams.gravity = Gravity.TOP | Gravity.END;
        fabParams.setMargins(0, dp(14), dp(14), 0);
        root.addView(menuFab, fabParams);
    }

    /** 只有围栏页有运行模式/参数，其余页面用自己的控件 */
    private void updateFab() {
        if (menuFab == null) return;
        boolean show = (current == 0);
        menuFab.setVisibility(show ? View.VISIBLE : View.GONE);
        if (show) menuFab.setAlpha(1f);
    }

    private void buildSheet() {
        sheetBox = new FrameLayout(this);
        sheetBox.setVisibility(View.GONE);
        sheetBox.setElevation(dp(28));

        sheetScrim = new View(this);
        sheetScrim.setBackgroundColor(0xB3000000);
        sheetScrim.setOnClickListener(v -> closeSheet());
        sheetBox.addView(sheetScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        sheetPanel = new LinearLayout(this);
        sheetPanel.setOrientation(LinearLayout.VERTICAL);
        sheetPanel.setBackgroundResource(R.drawable.bg_sheet);
        sheetPanel.setPadding(dp(8), dp(8), dp(8), dp(14));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.BOTTOM;
        sheetBox.addView(sheetPanel, plp);

        // 顶部小横条
        View handle = new View(this);
        android.graphics.drawable.GradientDrawable hg = new android.graphics.drawable.GradientDrawable();
        hg.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        hg.setCornerRadius(dp(2));
        hg.setColor(0x33FFFFFF);
        handle.setBackground(hg);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(38), dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(4);
        hlp.bottomMargin = dp(12);
        sheetPanel.addView(handle, hlp);

        TextView title = new TextView(this);
        title.setText("运行控制");
        title.setTextColor(0xFF8A93A6);
        title.setTextSize(11f);
        title.setLetterSpacing(0.22f);
        title.setPadding(dp(18), 0, dp(18), dp(8));
        sheetPanel.addView(title);

        sheetPanel.addView(buildRow("参数设置", null, () -> {
            closeSheet();
            if (webs[0] != null) {
                hud.hide();
                paramSheet.attach(webs[0]);
                paramSheet.show();
            }
        }));

        sheetPanel.addView(buildRow("观察记录", null, () -> {
            closeSheet();
            if (webs[0] != null) {
                hud.hide();
                inboxSheet.attach(webs[0]);
                inboxSheet.show();
            }
        }));

        sheetPanel.addView(buildRow("关于数字果蝇", null, () -> {
            closeSheet();
            aboutSheet.show();
        }));

        TextView tips = new TextView(this);
        tips.setText("点地面 扰动气流 · 点果蝇 惊跳 · 双指缩放 · 拖动旋转");
        tips.setTextColor(0xFF5C6478);
        tips.setTextSize(11f);
        tips.setLineSpacing(dp(3), 1f);
        tips.setPadding(dp(18), dp(14), dp(18), dp(2));
        sheetPanel.addView(tips);

        root.addView(sheetBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    /** 一行菜单项：左标题 + 右值 + 点击 */
    private View buildRow(String label, TextView valueOut, final Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(18), dp(15), dp(18), dp(15));
        row.setClickable(true);
        row.setBackground(makeRipple());

        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(0xFFE8ECF4);
        t.setTextSize(15f);
        row.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (valueOut != null) {
            valueOut.setTextColor(ACCENT);
            valueOut.setTextSize(12.5f);
            valueOut.setMaxWidth(dp(190));
            valueOut.setGravity(Gravity.END);
            row.addView(valueOut);
        } else {
            TextView arrow = new TextView(this);
            arrow.setText("›");
            arrow.setTextColor(0xFF5C6478);
            arrow.setTextSize(18f);
            row.addView(arrow);
        }

        row.setOnClickListener(v -> {
            v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING);
            if (action != null) action.run();
        });
        return row;
    }

    private android.graphics.drawable.Drawable makeRipple() {
        android.graphics.drawable.GradientDrawable base = new android.graphics.drawable.GradientDrawable();
        base.setShape(android.graphics.drawable.GradientDrawable.RECTANGLE);
        base.setCornerRadius(dp(14));
        base.setColor(0x00000000);
        return new android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x1AFFFFFF), base, null);
    }

    private void openSheet() {
        if (sheetBox == null) return;
        sheetBox.setVisibility(View.VISIBLE);
        sheetScrim.setAlpha(0f);
        sheetScrim.animate().alpha(1f).setDuration(180).start();
        sheetPanel.setTranslationY(dp(320));
        sheetPanel.animate().translationY(0f).setDuration(240)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void closeSheet() {
        if (sheetBox == null || sheetBox.getVisibility() != View.VISIBLE) return;
        sheetScrim.animate().alpha(0f).setDuration(140).start();
        sheetPanel.animate().translationY(dp(320)).setDuration(180)
                .withEndAction(() -> sheetBox.setVisibility(View.GONE)).start();
    }

    private void js(String code) {
        if (current >= 0 && webs[current] != null) {
            webs[current].evaluateJavascript(code, null);
        }
    }

    // ---------------- WebView ----------------

    @SuppressLint("SetJavaScriptEnabled")
    private WebView createWeb(final int index) {
        WebView wv = new WebView(this);

        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setDatabaseEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setAllowFileAccessFromFileURLs(false);
        s.setAllowUniversalAccessFromFileURLs(false);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setDisplayZoomControls(false);
        s.setTextZoom(100);
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setCacheMode(WebSettings.LOAD_NO_CACHE);
        s.setUserAgentString(s.getUserAgentString() + " FlyBrain/1.0");

        wv.setBackgroundColor(0xFF0B0D13);
        wv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        wv.setLayerType(View.LAYER_TYPE_HARDWARE, null);

        wv.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u == null || !HOST.equals(u.getHost())) return null;
                return serveAsset(u.getPath());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                if (u == null || HOST.equals(u.getHost())) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, u));
                } catch (Exception ignored) {
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (index == 0) {
                    hideSplash();
                }
            }
        });

        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onConsoleMessage(ConsoleMessage m) {
                Log.d(TAG, "[" + index + "] " + m.message() + " @" + m.lineNumber());
                return true;
            }
        });

        webHost.addView(wv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        wv.loadUrl(BASE + TAB_PATH[index] + (index == 0 ? "?app=1&mode=local" : "?app=1"));
        Log.d(TAG, "load " + BASE + TAB_PATH[index]);
        return wv;
    }

    /** 把 assets/www 下的文件当作 https 资源返回 */
    private WebResourceResponse serveAsset(String path) {
        if (path == null || path.isEmpty()) path = "/";
        if (path.endsWith("/")) path = path + "index.html";
        String assetPath = "www" + path;
        try {
            InputStream in = getAssets().open(assetPath);
            String mime = mimeOf(path);
            String enc = isText(mime) ? "UTF-8" : null;
            WebResourceResponse r = new WebResourceResponse(mime, enc, in);
            Map<String, String> headers = new HashMap<>();
            headers.put("Cache-Control", "no-cache");
            headers.put("Access-Control-Allow-Origin", "*");
            r.setResponseHeaders(headers);
            r.setStatusCodeAndReasonPhrase(200, "OK");
            return r;
        } catch (IOException e) {
            if (path.endsWith("favicon.ico")) {
                return new WebResourceResponse("image/png", null,
                        new ByteArrayInputStream(new byte[0]));
            }
            Log.w(TAG, "404 " + assetPath);
            WebResourceResponse r = new WebResourceResponse("text/plain", "UTF-8",
                    new ByteArrayInputStream(("404 " + path).getBytes()));
            r.setStatusCodeAndReasonPhrase(404, "Not Found");
            return r;
        }
    }

    private static String mimeOf(String p) {
        String s = p.toLowerCase();
        if (s.endsWith(".html") || s.endsWith(".htm")) return "text/html";
        if (s.endsWith(".js") || s.endsWith(".mjs")) return "application/javascript";
        if (s.endsWith(".json")) return "application/json";
        if (s.endsWith(".css")) return "text/css";
        if (s.endsWith(".gz")) return "application/gzip";
        if (s.endsWith(".wasm")) return "application/wasm";
        if (s.endsWith(".png")) return "image/png";
        if (s.endsWith(".jpg") || s.endsWith(".jpeg")) return "image/jpeg";
        if (s.endsWith(".webp")) return "image/webp";
        if (s.endsWith(".svg")) return "image/svg+xml";
        if (s.endsWith(".ico")) return "image/x-icon";
        if (s.endsWith(".woff2")) return "font/woff2";
        if (s.endsWith(".mp3")) return "audio/mpeg";
        return "application/octet-stream";
    }

    private static boolean isText(String mime) {
        return mime.startsWith("text/") || mime.contains("javascript") || mime.contains("json");
    }

    // ---------------- 启动页 ----------------

    private View buildSplash() {
        FrameLayout sp = new FrameLayout(this);
        sp.setBackgroundResource(R.drawable.bg_splash);

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.CENTER;
        sp.addView(col, clp);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_splash_logo);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(dp(104), dp(104));
        col.addView(logo, llp);

        logoPulse = ObjectAnimator.ofFloat(logo, View.ALPHA, 0.55f, 1f);
        logoPulse.setDuration(2100);
        logoPulse.setRepeatMode(ValueAnimator.REVERSE);
        logoPulse.setRepeatCount(ValueAnimator.INFINITE);
        logoPulse.start();

        TextView title = new TextView(this);
        title.setText("数字果蝇");
        title.setTextColor(0xFFE8ECF4);
        title.setTextSize(27f);
        title.setLetterSpacing(0.16f);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(18);
        col.addView(title, tlp);

        TextView sub = new TextView(this);
        sub.setText("DROSOPHILA  ·  FLYWIRE CONNECTOME");
        sub.setTextColor(ACCENT);
        sub.setTextSize(9.5f);
        sub.setLetterSpacing(0.26f);
        sub.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(9);
        col.addView(sub, slp);

        View line = new View(this);
        line.setBackgroundResource(R.drawable.line_progress);
        LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(dp(108), dp(2));
        glp.topMargin = dp(30);
        col.addView(line, glp);
        line.setScaleX(0.05f);
        line.setAlpha(0.9f);

        lineSweep = ObjectAnimator.ofFloat(line, View.SCALE_X, 0.05f, 1f);
        lineSweep.setDuration(1850);
        lineSweep.setInterpolator(new DecelerateInterpolator());
        lineSweep.setRepeatMode(ValueAnimator.REVERSE);
        lineSweep.setRepeatCount(ValueAnimator.INFINITE);
        lineSweep.start();

        TextView hint = new TextView(this);
        hint.setText("载入 138,639 神经元");
        hint.setTextColor(0xFF6B7385);
        hint.setTextSize(10.5f);
        hint.setLetterSpacing(0.1f);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.topMargin = dp(16);
        col.addView(hint, hlp);

        return sp;
    }

    private void hideSplash() {
        if (splash == null || splash.getVisibility() != View.VISIBLE) return;
        if (logoPulse != null) logoPulse.cancel();
        if (lineSweep != null) lineSweep.cancel();
        if (barWrap != null) {
            barWrap.animate().alpha(1f).setDuration(420).setStartDelay(140).start();
        }
        if (menuFab != null) {
            menuFab.animate().alpha(1f).setDuration(420).setStartDelay(140).start();
        }
        splash.animate().alpha(0f).scaleX(1.05f).scaleY(1.05f).setDuration(560)
                .setInterpolator(new DecelerateInterpolator())
                .withEndAction(() -> {
                    if (splash != null) splash.setVisibility(View.GONE);
                    splashDone = true;
                    if (current == 0) hud.show();
                    else if (current == 1) worldHud.show();
                    else if (current == 2) colonyHud.show();
                }).start();
    }

    // ---------------- 系统 UI ----------------

    private void enterImmersive() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
    }

    private void applyInsets() {
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            int top = insets.getSystemWindowInsetTop();
            int bottom = insets.getSystemWindowInsetBottom();
            barParams.setMargins(dp(12), 0, dp(12), dp(12) + bottom);
            bottomBar.requestLayout();
            webHost.setPadding(0, top, 0, dp(74) + bottom);
            if (infoCard != null) infoCard.setVisible(current == 2);
            updateStripHit();
            if (hud != null) hud.setInsets(top, bottom);
            if (worldHud != null) worldHud.setInsets(top, bottom);
            if (colonyHud != null) colonyHud.setInsets(top, bottom);
            if (menuFab != null) {
                fabParams.setMargins(0, dp(14) + top, dp(14), 0);
                menuFab.requestLayout();
            }
            return insets;
        });
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }

    // ---------------- 返回键 ----------------

    @Override
    public void onBackPressed() {
        if (updateSheet != null && updateSheet.isOpen()) {
            updateSheet.hide();
            return;
        }
        if (aboutSheet != null && aboutSheet.isOpen()) {
            aboutSheet.hide();
            return;
        }
        if (chartSheet != null && chartSheet.isOpen()) {
            chartSheet.hide();
            worldHud.setChipActive("#__chart", false);
            return;
        }
        if (obsSheet != null && obsSheet.isOpen()) {
            obsSheet.hide();
            return;
        }
        if (hud != null && hud.isModeSheetOpen()) {
            hud.hideModeSheet();
            return;
        }
        if (exitSheet != null && exitSheet.isOpen()) {
            exitSheet.hide();
            return;
        }
        if (inboxSheet != null && inboxSheet.isOpen()) {
            inboxSheet.hide();
            return;
        }
        if (colonyParamSheet != null && colonyParamSheet.isOpen()) {
            colonyParamSheet.hide();
            return;
        }
        if (worldParamSheet != null && worldParamSheet.isOpen()) {
            worldParamSheet.hide();
            return;
        }
        if (paramSheet != null && paramSheet.isOpen()) {
            paramSheet.hide();
            return;
        }
        if (sheetBox != null && sheetBox.getVisibility() == View.VISIBLE) {
            closeSheet();
            return;
        }
        if (current != 0) {
            select(0);
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastBackAt < 2000) {
            super.onBackPressed();
            return;
        }
        lastBackAt = now;
        java.util.List<ChoiceSheet.Option> opts = new java.util.ArrayList<>();
        opts.add(new ChoiceSheet.Option("取消", "继续观察", null));
        opts.add(new ChoiceSheet.Option("退出应用", "下次打开会重新载入连接组", this::finish));
        exitSheet.show("要退出数字果蝇吗？", opts, -1);
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersive();
        if (current >= 0 && webs[current] != null) webs[current].onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        for (WebView w : webs) if (w != null) w.onPause();
    }

    @Override
    protected void onDestroy() {
        if (logoPulse != null) logoPulse.cancel();
        if (lineSweep != null) lineSweep.cancel();
        for (int i = 0; i < webs.length; i++) {
            if (webs[i] != null) {
                webHost.removeView(webs[i]);
                webs[i].destroy();
                webs[i] = null;
            }
        }
        super.onDestroy();
    }
}
