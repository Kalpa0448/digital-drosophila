package io.github.kalpa0448.flybrain;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * 围栏页的原生 HUD —— 顶栏、驱动力面板、工具条、缩放按钮。
 * 数据通过轮询网页里的 DOM 文本获取，操作通过触发网页按钮的 click 完成。
 */
class Hud {

    private static final int ACCENT = 0xFF34D399;
    private static final int TEXT = 0xFFE8ECF4;
    private static final int DIM = 0xFF8A93A6;

    private final Activity act;
    private final FrameLayout root;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private WebView web;
    private TextView stateText;
    private TextView hungerText;
    private ProgressBar pbHunger, pbFear, pbFatigue, pbCuriosity;
    private TextView recText;

    private LinearLayout header, card, cardBody, tools, zoom;
    private TextView chevron;
    private boolean collapsed = false;
    private ChoiceSheet modeSheet;
    private int modeIndex = 0;
    private Runnable onInbox;
    private Runnable onObs;
    private int insetTop, insetBottom;

    Hud(Activity activity, FrameLayout root) {
        this.act = activity;
        this.root = root;
        build();
    }

    void showModeSheet() {
        if (modeSheet == null) return;
        java.util.List<ChoiceSheet.Option> opts = new java.util.ArrayList<>();
        opts.add(new ChoiceSheet.Option("真实模拟回放", "预跑轨迹回放，流畅省电", () -> {
            if (modeIndex != 0) click("modebtn");
        }));
        opts.add(new ChoiceSheet.Option("本地运算", "138,639 神经元实时放电", () -> {
            if (modeIndex != 1) click("modebtn");
        }));
        modeSheet.show("运行模式", opts, modeIndex);
    }

    boolean isModeSheetOpen() {
        return modeSheet != null && modeSheet.isOpen();
    }

    void hideModeSheet() {
        if (modeSheet != null) modeSheet.hide();
    }

    void setOnInboxClick(Runnable r) {
        this.onInbox = r;
    }

    void attach(WebView wv) {
        this.web = wv;
        poll();
    }

    void detach() {
        this.web = null;
        ui.removeCallbacks(tick);
    }

    void setInsets(int top, int bottom) {
        this.insetTop = top;
        this.insetBottom = bottom;
        applyMargins();
    }

    private int dp(float v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    // ---------------- 构建 ----------------

    private void build() {
        // 顶栏：名字 · 状态 · 饥饿
        header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        // 小圆点 + 标题
        View dot = new View(act);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(ACCENT);
        dot.setBackground(dg);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dlp.rightMargin = dp(8);
        header.addView(dot, dlp);

        TextView title = new TextView(act);
        title.setText("数字果蝇");
        title.setTextColor(TEXT);
        title.setTextSize(15.5f);
        title.setLetterSpacing(0.10f);
        title.getPaint().setFakeBoldText(true);
        header.addView(title);

        stateText = new TextView(act);
        stateText.setText("初始化…");
        stateText.setTextColor(ACCENT);
        stateText.setTextSize(11.5f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(9);
        stateText.setBackground(chip(0x1F34D399, 0));
        stateText.setPadding(dp(9), dp(4), dp(9), dp(4));
        header.addView(stateText, slp);

        hungerText = new TextView(act);
        hungerText.setText("—");
        hungerText.setTextColor(DIM);
        hungerText.setTextSize(11f);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.leftMargin = dp(9);
        // 状态胶囊已包含饥饿信息，这里不再单独显示

        FrameLayout.LayoutParams hdrLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hdrLp.gravity = Gravity.TOP | Gravity.START;
        root.addView(header, hdrLp);

        // 驱动力卡片
        card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(0xCC0F131A, 0x1FFFFFFF, 18));
        card.setPadding(dp(14), dp(11), dp(14), dp(12));
        card.setElevation(dp(4));

        LinearLayout cardHead = new LinearLayout(act);
        cardHead.setOrientation(LinearLayout.HORIZONTAL);
        cardHead.setGravity(Gravity.CENTER_VERTICAL);
        cardHead.setClickable(true);
        cardHead.setOnClickListener(v -> toggleCard());

        TextView cardTitle = new TextView(act);
        cardTitle.setText("状态");
        cardTitle.setTextColor(0xFFA8B0C0);
        cardTitle.setTextSize(10.5f);
        cardTitle.setLetterSpacing(0.22f);
        cardHead.addView(cardTitle, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        chevron = new TextView(act);
        chevron.setText("▾");
        chevron.setTextColor(DIM);
        chevron.setTextSize(12f);
        chevron.setGravity(Gravity.CENTER);
        cardHead.addView(chevron, new LinearLayout.LayoutParams(dp(22), dp(18)));
        card.addView(cardHead);

        cardBody = new LinearLayout(act);
        cardBody.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams bodyLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        bodyLp.topMargin = dp(9);
        card.addView(cardBody, bodyLp);

        pbHunger = addDrive(cardBody, "饥饿", 0xFFF5A524);
        pbFear = addDrive(cardBody, "恐惧", 0xFFF87171);
        pbFatigue = addDrive(cardBody, "疲劳", 0xFF94A3B8);
        pbCuriosity = addDrive(cardBody, "好奇", ACCENT);

        FrameLayout.LayoutParams cardLp = new FrameLayout.LayoutParams(
                dp(196), ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.gravity = Gravity.TOP | Gravity.START;
        root.addView(card, cardLp);

        // 观察记录
        recText = new TextView(act);
        recText.setText("📥 观察记录 · 0");
        recText.setTextColor(0xFFC7CEDB);
        recText.setTextSize(11.5f);
        recText.setBackground(chip(0xCC0F131A, 0x1FFFFFFF));
        recText.setPadding(dp(12), dp(7), dp(12), dp(7));
        recText.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            if (onInbox != null) onInbox.run(); else click("inboxBtn");
        });
        FrameLayout.LayoutParams recLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        recLp.gravity = Gravity.TOP | Gravity.START;
        root.addView(recText, recLp);

        // 工具条：一行等宽
        tools = new LinearLayout(act);
        tools.setOrientation(LinearLayout.HORIZONTAL);

        addTool("🍎", "投喂", "feedBtn");
        addTool("🧽", "清食物", "foodClear");
        addTool("🟨", "粘板", "stickyBtn");
        addTool("🧹", "清粘板", "stickyClear");
        addTool("🧠", "观察", "__obs");

        FrameLayout.LayoutParams toolsLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        toolsLp.gravity = Gravity.BOTTOM;
        root.addView(tools, toolsLp);

        // 缩放
        zoom = new LinearLayout(act);
        zoom.setOrientation(LinearLayout.VERTICAL);
        addZoom("＋");
        addZoom("－");
        addZoom("⟳");

        FrameLayout.LayoutParams zoomLp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        zoomLp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
        root.addView(zoom, zoomLp);

        modeSheet = new ChoiceSheet(act, root, "运行模式");

        applyMargins();
        hide();
    }

    private ProgressBar addDrive(LinearLayout parent, String label, int color) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.VERTICAL);

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);

        TextView l = new TextView(act);
        l.setText(label);
        l.setTextColor(0xFFA8B0C0);
        l.setTextSize(10.5f);
        top.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView val = new TextView(act);
        val.setText("0%");
        val.setTextColor(0xFFE8ECF4);
        val.setTextSize(10.5f);
        val.setGravity(Gravity.END);
        top.addView(val);
        row.addView(top);

        ProgressBar pb = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        pb.setMax(100);
        pb.setProgressTintList(ColorStateList.valueOf(color));
        pb.setProgressBackgroundTintList(ColorStateList.valueOf(0x22FFFFFF));
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4));
        plp.topMargin = dp(5);
        row.addView(pb, plp);

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (parent.getChildCount() > 0) rlp.topMargin = dp(10);
        parent.addView(row, rlp);

        // 把数值 TextView 挂到 progressBar 的 tag 上方便更新
        pb.setTag(val);
        return pb;
    }

    private void addTool(String emoji, String label, final String jsId) {
        LinearLayout col = new LinearLayout(act);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackground(ripple(0xE0141922, 0x26FFFFFF, 18));
        col.setPadding(dp(4), dp(8), dp(4), dp(7));
        col.setClickable(true);

        TextView e = new TextView(act);
        e.setText(emoji);
        e.setTextSize(15f);
        e.setGravity(Gravity.CENTER);
        col.addView(e);

        TextView t = new TextView(act);
        t.setText(label);
        t.setTextColor(0xFFC7CEDB);
        t.setTextSize(9.5f);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tlp.topMargin = dp(2);
        col.addView(t, tlp);

        final String id = jsId;
        col.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            click(id);
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = dp(3);
        lp.rightMargin = dp(3);
        tools.addView(col, lp);
    }

    private void addZoom(String glyph) {
        TextView tv = new TextView(act);
        tv.setText(glyph);
        tv.setTextColor(TEXT);
        tv.setTextSize(17f);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(ripple(0xCC0F131A, 0x1FFFFFFF, 22));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(42), dp(42));
        lp.bottomMargin = dp(9);
        final String id = "＋".equals(glyph) ? "zin" : ("－".equals(glyph) ? "zout" : "zrst");
        tv.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            click(id);
        });
        zoom.addView(tv, lp);
    }

    private void toggleCard() {
        collapsed = !collapsed;
        chevron.animate().rotation(collapsed ? -90f : 0f).setDuration(160).start();
        if (collapsed) {
            cardBody.animate().alpha(0f).setDuration(120).withEndAction(() -> {
                cardBody.setVisibility(View.GONE);
            }).start();
        } else {
            cardBody.setVisibility(View.VISIBLE);
            cardBody.setAlpha(0f);
            cardBody.animate().alpha(1f).setDuration(150).start();
        }
    }

    // ---------------- 位置 ----------------

    private void applyMargins() {
        if (header.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) header.getLayoutParams();
            lp.setMargins(dp(16), insetTop + dp(10), dp(72), 0);
            header.setLayoutParams(lp);
        }
        if (card.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) card.getLayoutParams();
            lp.setMargins(dp(16), insetTop + dp(46), 0, 0);
            card.setLayoutParams(lp);
        }
        if (recText.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) recText.getLayoutParams();
            lp.setMargins(dp(16), insetTop + dp(248), 0, 0);
            recText.setLayoutParams(lp);
        }
        if (tools.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) tools.getLayoutParams();
            lp.setMargins(dp(11), 0, dp(11), insetBottom + dp(196));
            tools.setLayoutParams(lp);
        }
        if (zoom.getLayoutParams() instanceof FrameLayout.LayoutParams) {
            FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) zoom.getLayoutParams();
            lp.setMargins(dp(14), 0, 0, dp(40));
            lp.gravity = Gravity.START | Gravity.CENTER_VERTICAL;
            zoom.setLayoutParams(lp);
        }
    }

    // ---------------- 显示/隐藏 ----------------

    void show() {
        header.setVisibility(View.VISIBLE);
        card.setVisibility(View.VISIBLE);
        recText.setVisibility(View.VISIBLE);
        tools.setVisibility(View.VISIBLE);
        zoom.setVisibility(View.VISIBLE);
        poll();
    }

    void hide() {
        header.setVisibility(View.GONE);
        card.setVisibility(View.GONE);
        recText.setVisibility(View.GONE);
        tools.setVisibility(View.GONE);
        zoom.setVisibility(View.GONE);
        ui.removeCallbacks(tick);
    }

    // ---------------- 轮询 ----------------

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (web == null) return;
            web.evaluateJavascript(
                    "(function(){var g=function(id){var e=document.getElementById(id);"
                            + "return e?e.textContent.trim():''};"
                            + "var r=document.getElementById('inboxBtn');"
                            + "return JSON.stringify({st:g('state'),hn:g('v-hunger'),"
                            + "fr:g('v-fear'),ft:g('v-fatigue'),cu:g('v-curiosity'),"
                            + "rec:r?r.textContent.trim():'',"
                            + "stp:((document.getElementById('strip')||{getBoundingClientRect:function(){return {top:0}}}).getBoundingClientRect().top),"
                            + "ih:window.innerHeight});})()",
                    value -> {
                        apply(value);
                        ui.postDelayed(tick, 650);
                    });
        }
    };

    private void poll() {
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    private void apply(String json) {
        if (json == null) return;
        String s = json;
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        try {
            JSONObject o = new JSONObject(s);
            stateText.setText(nonEmpty(o.optString("st"), "观察中"));
            setBar(pbHunger, o.optString("hn"));
            setBar(pbFear, o.optString("fr"));
            setBar(pbFatigue, o.optString("ft"));
            setBar(pbCuriosity, o.optString("cu"));
            hungerText.setText("饥饿 " + nonEmpty(o.optString("hn"), "—"));
            String mb = o.optString("mb", "");
            modeIndex = (mb.contains("本地运算")) ? 0 : 1;   // 按钮文案是"切到X"，当前就是另一种
            String rec = o.optString("rec");
            if (rec != null && !rec.isEmpty()) recText.setText(rec);

        } catch (Exception ignored) {
        }
    }

    private void setBar(ProgressBar pb, String text) {
        int v = parsePct(text);
        pb.setProgress(v);
        if (pb.getTag() instanceof TextView) ((TextView) pb.getTag()).setText(v + "%");
    }

    private static int parsePct(String t) {
        if (t == null) return 0;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c >= '0' && c <= '9') sb.append(c);
            else if (sb.length() > 0) break;
        }
        if (sb.length() == 0) return 0;
        try {
            return Math.max(0, Math.min(100, Integer.parseInt(sb.toString())));
        } catch (Exception e) {
            return 0;
        }
    }

    private static String nonEmpty(String s, String fallback) {
        return (s == null || s.trim().isEmpty() || "null".equals(s)) ? fallback : s;
    }

    void setOnObsClick(Runnable r) {
        this.onObs = r;
    }

    private void click(String domId) {
        if ("__obs".equals(domId)) {
            if (onObs != null) onObs.run();
            return;
        }
        if (web == null) return;
        web.evaluateJavascript("var e=document.getElementById('" + domId + "');if(e)e.click();", null);
    }

    // ---------------- 小工具 ----------------

    private Drawable roundRect(int fill, int stroke, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(radiusDp));
        g.setColor(fill);
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private Drawable chip(int fill, int stroke) {
        return roundRect(fill, stroke, 20);
    }

    private Drawable ripple(int fill, int strokeColor, float radiusDp) {
        GradientDrawable base = new GradientDrawable();
        base.setShape(GradientDrawable.RECTANGLE);
        base.setCornerRadius(dp(radiusDp));
        base.setColor(fill);
        base.setStroke(dp(1), strokeColor);
        return new RippleDrawable(ColorStateList.valueOf(0x1AFFFFFF), base, null);
    }
}
