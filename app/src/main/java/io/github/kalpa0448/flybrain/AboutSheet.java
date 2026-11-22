package io.github.kalpa0448.flybrain;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** 「关于」面板：头部渐变 + 数据统计 + 分组卡片。 */
class AboutSheet {

    private static final int ACCENT = 0xFF34D399;
    private static final int TEXT = 0xFFE8ECF4;
    private static final int DIM = 0xFF8A93A6;
    private static final int FAINT = 0xFF5C6478;

    private final Activity act;
    private final FrameLayout root;

    interface Listener {
        void onCheckUpdate();

        void onAutoUpdateChanged(boolean on);
    }

    private Listener listener;
    private TextView updateValue;
    private android.widget.Switch autoSwitch;

    private FrameLayout box;
    private View scrim;
    private LinearLayout panel;

    AboutSheet(Activity activity, FrameLayout root) {
        this.act = activity;
        this.root = root;
        build();
    }

    private int dp(float v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    private void build() {
        box = new FrameLayout(act);
        box.setVisibility(View.GONE);
        box.setElevation(dp(70));

        scrim = new View(act);
        scrim.setBackgroundColor(0xB8000000);
        scrim.setOnClickListener(v -> hide());
        box.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0xFF16211F, 0xFF10141C});
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadii(new float[]{dp(28), dp(28), dp(28), dp(28), 0, 0, 0, 0});
        bg.setStroke(dp(1), 0x26A0F0D0);
        panel.setBackground(bg);

        int h = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.82f);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h);
        plp.gravity = Gravity.BOTTOM;
        box.addView(panel, plp);

        // 拖动条 + 关闭
        FrameLayout topBar = new FrameLayout(act);
        View handle = new View(act);
        GradientDrawable hg = new GradientDrawable();
        hg.setShape(GradientDrawable.RECTANGLE);
        hg.setCornerRadius(dp(2));
        hg.setColor(0x33FFFFFF);
        handle.setBackground(hg);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(dp(38), dp(4));
        hlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(9);
        topBar.addView(handle, hlp);

        TextView close = new TextView(act);
        close.setText("✕");
        close.setTextColor(0xFFB9C2D0);
        close.setTextSize(14f);
        close.setGravity(Gravity.CENTER);
        close.setBackground(ripple(0x14FFFFFF, 0x14FFFFFF, 18));
        close.setOnClickListener(v -> hide());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(34), dp(34));
        clp.gravity = Gravity.TOP | Gravity.END;
        clp.topMargin = dp(14);
        clp.rightMargin = dp(16);
        topBar.addView(close, clp);

        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(60));
        panel.addView(topBar, tlp);

        // 头部
        LinearLayout hero = new LinearLayout(act);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setGravity(Gravity.CENTER_HORIZONTAL);
        hero.setPadding(0, 0, 0, dp(6));

        ImageView logo = new ImageView(act);
        logo.setImageResource(R.drawable.ic_splash_logo);
        LinearLayout.LayoutParams lgp = new LinearLayout.LayoutParams(dp(62), dp(62));
        hero.addView(logo, lgp);

        TextView name = new TextView(act);
        name.setText("数字果蝇");
        name.setTextColor(TEXT);
        name.setTextSize(23f);
        name.setLetterSpacing(0.14f);
        name.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nlp.topMargin = dp(10);
        hero.addView(name, nlp);

        TextView sub = new TextView(act);
        sub.setText("DROSOPHILA  ·  FLYWIRE CONNECTOME");
        sub.setTextColor(ACCENT);
        sub.setTextSize(8.5f);
        sub.setLetterSpacing(0.28f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = dp(7);
        hero.addView(sub, slp);

        TextView ver = new TextView(act);
        ver.setText("v" + BuildConfig.VERSION_NAME);
        ver.setTextColor(0xFF9FE8C8);
        ver.setTextSize(10.5f);
        ver.setBackground(round(0x1F34D399, 20));
        ver.setPadding(dp(10), dp(3), dp(10), dp(3));
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        vlp.topMargin = dp(11);
        hero.addView(ver, vlp);

        panel.addView(hero, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // 统计
        LinearLayout stats = new LinearLayout(act);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        stats.setPadding(dp(16), dp(18), dp(16), dp(6));
        stats.addView(statCard("138,639", "神经元"));
        stats.addView(statCard("15.09M", "连接边"));
        stats.addView(statCard("3", "实验场景"));
        panel.addView(stats);

        // 正文
        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        LinearLayout body = new LinearLayout(act);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(16), dp(10), dp(16), dp(18));

        body.addView(settingsCard());

        body.addView(card("这是什么", new String[]{
                "把果蝇全脑模拟实验做成能装进口袋的原生应用。",
                "3D 活体围栏、2D 生态大世界、3D 养果蝇三个场景全部内置于 APK，断网也能运行。",
        }, null));

        body.addView(card("数据来源", null, new String[][]{
                {"连接组", "FlyWire FAFB v783 · Nature 634:124–138, 2024"},
                {"全脑模型", "Shiu et al., Nature 2024 · LIF"},
                {"降维脑", "FlyEM 雄性 CNS v1.0 · 165,122 神经元"},
                {"降维比", "25 功能群 → 48 神经元 / 145 边（实测 82%）"},
        }));

        body.addView(card("开源引用与致谢", new String[]{
                "养果蝇场景的神经群体模型、行为读出与参数体系参考自 fly-sim。",
        }, new String[][]{
                {"fly-sim", "果蝇 3D 模拟器 · MIT · github.com/sjy20100819mc-cmyk/fly-sim"},
                {"three.js", "r147 · MIT"},
                {"连接组", "FlyEM 雄性 CNS v1.0 · Janelia / Cambridge / Google"},
                {"方法判据", "flybench · Shiu et al. 2024"},
        }));

        body.addView(card("技术栈", new String[]{
                "原生外壳 Android View（Java），零第三方依赖",
                "渲染 three.js + Canvas 2D，资源内置，自定义 https 拦截",
                "本地运算 138,639 神经元，1 ms 步长",
        }, null));

        body.addView(card("作者", new String[]{
                "范特西 · io.github.kalpa0448.flybrain",
                "仅供学习与研究，数据版权归原作者所有",
        }, null));

        sv.addView(body, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------------- 组件 ----------------

    private View statCard(String value, String label) {
        LinearLayout v = new LinearLayout(act);
        v.setOrientation(LinearLayout.VERTICAL);
        v.setGravity(Gravity.CENTER_HORIZONTAL);
        v.setBackground(round(0x0FFFFFFF, 16));
        v.setPadding(dp(6), dp(12), dp(6), dp(11));

        TextView num = new TextView(act);
        num.setText(value);
        num.setTextColor(TEXT);
        num.setTextSize(15f);
        num.setLetterSpacing(0.02f);
        num.setGravity(Gravity.CENTER);
        num.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        v.addView(num, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView l = new TextView(act);
        l.setText(label);
        l.setTextColor(FAINT);
        l.setTextSize(9.5f);
        l.setLetterSpacing(0.1f);
        l.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(3);
        v.addView(l, lp);

        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        p.leftMargin = dp(4);
        p.rightMargin = dp(4);
        v.setLayoutParams(p);
        return v;
    }

    void setListener(Listener l) {
        this.listener = l;
    }

    /** 供外部更新右侧状态文字（如「发现新版本」） */
    void setUpdateStatus(String text, boolean highlight) {
        if (updateValue == null) return;
        updateValue.setText(text);
        updateValue.setTextColor(highlight ? 0xFF34D399 : 0xFF5C6478);
    }

    void setAutoChecked(boolean on) {
        if (autoSwitch != null) autoSwitch.setChecked(on);
    }

    private View settingsCard() {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(18));
        bg.setColor(0x0DFFFFFF);
        c.setBackground(bg);
        c.setPadding(dp(14), dp(4), dp(14), dp(4));
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(10);
        c.setLayoutParams(clp);

        // 检查更新
        LinearLayout r1 = new LinearLayout(act);
        r1.setOrientation(LinearLayout.HORIZONTAL);
        r1.setGravity(Gravity.CENTER_VERTICAL);
        r1.setPadding(0, dp(12), 0, dp(12));
        r1.setClickable(true);
        r1.setOnClickListener(v -> {
            if (listener != null) listener.onCheckUpdate();
        });

        TextView t1 = new TextView(act);
        t1.setText("检查更新");
        t1.setTextColor(0xFFD7DEEA);
        t1.setTextSize(12.5f);
        r1.addView(t1, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        updateValue = new TextView(act);
        updateValue.setText("v" + BuildConfig.VERSION_NAME);
        updateValue.setTextColor(0xFF5C6478);
        updateValue.setTextSize(11f);
        r1.addView(updateValue);
        c.addView(r1);

        View sep = new View(act);
        sep.setBackgroundColor(0x14FFFFFF);
        c.addView(sep, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));

        // 自动检查更新
        LinearLayout r2 = new LinearLayout(act);
        r2.setOrientation(LinearLayout.HORIZONTAL);
        r2.setGravity(Gravity.CENTER_VERTICAL);
        r2.setPadding(0, dp(10), 0, dp(10));

        TextView t2 = new TextView(act);
        t2.setText("自动检查更新");
        t2.setTextColor(0xFFD7DEEA);
        t2.setTextSize(12.5f);
        r2.addView(t2, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        autoSwitch = new android.widget.Switch(act);
        autoSwitch.setShowText(false);
        autoSwitch.setThumbTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        autoSwitch.setTrackTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
        autoSwitch.setOnCheckedChangeListener((b, checked) -> {
            autoSwitch.setTrackTintList(android.content.res.ColorStateList.valueOf(
                    checked ? 0x8834D399 : 0x33FFFFFF));
            if (listener != null) listener.onAutoUpdateChanged(checked);
        });
        r2.addView(autoSwitch);
        c.addView(r2);

        return c;
    }

    private View card(String title, String[] lines, String[][] pairs) {
        LinearLayout c = new LinearLayout(act);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(0x0DFFFFFF, 18));
        c.setPadding(dp(14), dp(13), dp(14), dp(14));

        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.bottomMargin = dp(10);
        c.setLayoutParams(clp);

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(act);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(ACCENT);
        dot.setBackground(dg);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(5), dp(5));
        dlp.rightMargin = dp(7);
        head.addView(dot, dlp);

        TextView t = new TextView(act);
        t.setText(title);
        t.setTextColor(0xFFD7DEEA);
        t.setTextSize(12.5f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        head.addView(t);
        c.addView(head);

        if (lines != null) {
            for (String line : lines) {
                TextView b = new TextView(act);
                b.setText(line);
                b.setTextColor(0xFFAFB8C7);
                b.setTextSize(12f);
                b.setLineSpacing(dp(4), 1f);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(7);
                c.addView(b, lp);
            }
        }

        if (pairs != null) {
            for (String[] p : pairs) {
                LinearLayout row = new LinearLayout(act);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.TOP);

                TextView k = new TextView(act);
                k.setText(p[0]);
                k.setTextColor(FAINT);
                k.setTextSize(11f);
                k.setSingleLine(true);
                LinearLayout.LayoutParams klp = new LinearLayout.LayoutParams(
                        dp(64), ViewGroup.LayoutParams.WRAP_CONTENT);
                row.addView(k, klp);

                TextView v = new TextView(act);
                v.setText(p[1]);
                v.setTextColor(0xFFAFB8C7);
                v.setTextSize(11.5f);
                v.setLineSpacing(dp(3), 1f);
                row.addView(v, new LinearLayout.LayoutParams(0,
                        ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

                LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rlp.topMargin = dp(7);
                c.addView(row, rlp);
            }
        }
        return c;
    }

    // ---------------- 小工具 ----------------

    private GradientDrawable round(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(radiusDp));
        g.setColor(fill);
        return g;
    }

    private RippleDrawable ripple(int fill, int stroke, float radiusDp) {
        GradientDrawable base = new GradientDrawable();
        base.setShape(GradientDrawable.RECTANGLE);
        base.setCornerRadius(dp(radiusDp));
        base.setColor(fill);
        base.setStroke(dp(1), stroke);
        return new RippleDrawable(ColorStateList.valueOf(0x1AFFFFFF), base, null);
    }

    // ---------------- 显示 ----------------

    void show() {
        box.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();
        panel.setTranslationY(dp(700));
        panel.animate().translationY(0f).setDuration(280)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    void hide() {
        if (box.getVisibility() != View.VISIBLE) return;
        scrim.animate().alpha(0f).setDuration(130).start();
        panel.animate().translationY(dp(700)).setDuration(220)
                .withEndAction(() -> box.setVisibility(View.GONE)).start();
    }

    boolean isOpen() {
        return box.getVisibility() == View.VISIBLE;
    }
}
