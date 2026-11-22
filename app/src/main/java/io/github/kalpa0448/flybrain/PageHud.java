package io.github.kalpa0448.flybrain;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 通用原生控件层：可折叠数据卡 + 等宽操作条（超出部分收进「更多」）。
 */
class PageHud {

    /** 一个「标签 + 数值」对 */
    static class Pair {
        final String label;
        final String domId;

        Pair(String label, String domId) {
            this.label = label;
            this.domId = domId;
        }
    }

    /** 一行：pairs 横向排列；sectionTitle 不为空时作为分组标题渲染 */
    static class Row {
        final String sectionTitle;
        final List<Pair> pairs = new ArrayList<>();

        Row(Pair... ps) {
            this.sectionTitle = null;
            java.util.Collections.addAll(pairs, ps);
        }

        Row(String sectionTitle, Pair... ps) {
            this.sectionTitle = sectionTitle;
            java.util.Collections.addAll(pairs, ps);
        }
    }

    static class Act {
        final String label;
        final String selector;
        final String probe;      // 判断该功能是否处于开启状态（可空）
        final String labelExpr;  // 动态文案表达式（可空）

        Act(String label, String selector) {
            this(label, selector, null, null);
        }

        Act(String label, String selector, String probe) {
            this(label, selector, probe, null);
        }

        Act(String label, String selector, String probe, String labelExpr) {
            this.label = label;
            this.selector = selector;
            this.probe = probe;
            this.labelExpr = labelExpr;
        }
    }

    private static final int ACCENT = 0xFF34D399;
    private static final int TEXT = 0xFFE8ECF4;
    private static final int DIM = 0xFF8A93A6;
    private static final int FAINT = 0xFFA8B0C0;

    private final Activity act;
    private final FrameLayout root;
    private final String title;
    private final List<Row> rows;
    private final String textBlockId;
    private final String statusSelector;
    private final List<Act> primary;
    private final List<Act> overflow;
    private final String overlayProbe;

    private final Handler ui = new Handler(Looper.getMainLooper());
    private WebView web;

    private LinearLayout header;
    private TextView statusChip;
    private LinearLayout card;
    private LinearLayout cardBody;
    private TextView chevron;
    private View actionBar;
    private HorizontalScrollView actionScroll;
    private TextView blockView;

    private final List<TextView> valueViews = new ArrayList<>();
    private final List<Act> chipActs = new ArrayList<>();
    private final List<TextView> chipViews = new ArrayList<>();
    private final List<Boolean> chipActive = new ArrayList<>();

    private FrameLayout moreBox;
    private View moreScrim;
    private LinearLayout morePanel;
    private LinearLayout moreList;
    private boolean collapsed = false;
    private boolean shown = false;
    private boolean overlayUp = false;
    private int insetTop, insetBottom;

    PageHud(Activity activity, FrameLayout root, String title,
            List<Row> rows, String textBlockId, String statusSelector,
            List<Act> primary, List<Act> overflow, String overlayProbe) {
        this.act = activity;
        this.root = root;
        this.title = title;
        this.rows = rows;
        this.textBlockId = textBlockId;
        this.statusSelector = statusSelector;
        this.primary = primary;
        this.overflow = overflow;
        this.overlayProbe = overlayProbe;
        build();
    }

    private int dp(float v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    // ---------------- 构建 ----------------

    private void build() {
        // 顶栏
        header = new LinearLayout(act);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        View dot = new View(act);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(ACCENT);
        dot.setBackground(dg);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(6));
        dlp.rightMargin = dp(8);
        header.addView(dot, dlp);

        TextView t = new TextView(act);
        t.setText(title);
        t.setTextColor(TEXT);
        t.setTextSize(15.5f);
        t.setLetterSpacing(0.10f);
        t.getPaint().setFakeBoldText(true);
        header.addView(t);

        statusChip = new TextView(act);
        statusChip.setTextColor(ACCENT);
        statusChip.setTextSize(11f);
        statusChip.setBackground(roundRect(0x2634D399, 0x3D34D399, 20));
        statusChip.setPadding(dp(8), dp(3), dp(8), dp(3));
        statusChip.setVisibility(statusSelector == null ? View.GONE : View.VISIBLE);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.leftMargin = dp(9);
        header.addView(statusChip, slp);

        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hlp.gravity = Gravity.TOP | Gravity.START;
        root.addView(header, hlp);

        // 数据卡（可折叠）
        card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(0xCC0F131A, 0x1FFFFFFF, 18));
        card.setElevation(dp(4));
        card.setPadding(dp(14), dp(11), dp(14), dp(12));

        LinearLayout cardHead = new LinearLayout(act);
        cardHead.setOrientation(LinearLayout.HORIZONTAL);
        cardHead.setGravity(Gravity.CENTER_VERTICAL);
        cardHead.setClickable(true);
        cardHead.setOnClickListener(v -> toggleCard());

        TextView cardTitle = new TextView(act);
        cardTitle.setText("数据");
        cardTitle.setTextColor(FAINT);
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
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(9);
        card.addView(cardBody, blp);

        if (rows != null) {
            for (int i = 0; i < rows.size(); i++) {
                cardBody.addView(buildRow(rows.get(i)), rowParams(i));
            }
        } else {
            blockView = new TextView(act);
            blockView.setTextColor(0xFFC7CEDB);
            blockView.setTextSize(10.5f);
            blockView.setLineSpacing(dp(3), 1f);
            blockView.setText("—");
            blockBody(blockView);
        }

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                dp(226), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.TOP | Gravity.START;
        root.addView(card, clp);

        // 操作条：横向滑动
        buildActions();

        FrameLayout.LayoutParams alp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.gravity = Gravity.BOTTOM;
        root.addView(actionBar, alp);

        applyMargins();
        hide();
    }

    /** 信息块用同一个容器，方便折叠 */
    private void blockBody(TextView v) {
        cardBody.addView(v);
    }

    private LinearLayout.LayoutParams rowParams(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (index > 0) lp.topMargin = dp(7);
        return lp;
    }

    private View buildRow(Row row) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);

        if (row.sectionTitle != null) {
            TextView st = new TextView(act);
            st.setText(row.sectionTitle);
            st.setTextColor(0xFF6E7686);
            st.setTextSize(9.5f);
            st.setLetterSpacing(0.14f);
            box.addView(st);
        }

        LinearLayout line = new LinearLayout(act);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        if (row.sectionTitle != null) {
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            llp.topMargin = dp(2);
            box.addView(line, llp);
        } else {
            box.addView(line);
        }

        for (int i = 0; i < row.pairs.size(); i++) {
            Pair p = row.pairs.get(i);
            LinearLayout cell = new LinearLayout(act);
            cell.setOrientation(LinearLayout.HORIZONTAL);
            cell.setGravity(Gravity.CENTER_VERTICAL);

            TextView l = new TextView(act);
            l.setText(p.label);
            l.setTextColor(FAINT);
            l.setTextSize(11f);
            cell.addView(l);

            TextView v = new TextView(act);
            v.setText("—");
            v.setTextColor(TEXT);
            v.setTextSize(11.5f);
            v.getPaint().setFakeBoldText(true);
            LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            vlp.leftMargin = dp(6);
            cell.addView(v, vlp);
            valueViews.add(v);

            LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            line.addView(cell, clp);
        }
        return box;
    }

    // ---------------- 操作条 ----------------

    private void buildActions() {
        List<Act> all = new ArrayList<>();
        if (primary != null) all.addAll(primary);
        if (overflow != null) all.addAll(overflow);

        actionScroll = new HorizontalScrollView(act);
        actionScroll.setHorizontalScrollBarEnabled(false);
        actionScroll.setClipToPadding(false);
        actionScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);

        LinearLayout line = new LinearLayout(act);
        line.setOrientation(LinearLayout.HORIZONTAL);
        actionScroll.addView(line, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        chipActs.clear(); chipViews.clear(); chipActive.clear();
        for (Act a : all) {
            TextView c = chip(a.label, a.selector);
            chipActs.add(a);
            chipViews.add(c);
            chipActive.add(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(3);
            lp.rightMargin = dp(3);
            line.addView(c, lp);
        }
        actionBar = actionScroll;

    }

    private TextView chip(String label, final String selector) {
        TextView tv = new TextView(act);
        tv.setText(label);
        tv.setTextColor(TEXT);
        tv.setTextSize(12f);
        tv.setSingleLine(true);
        tv.setGravity(Gravity.CENTER);
        tv.setBackground(ripple(0xE0141922, 0x26FFFFFF, 18));
        tv.setPadding(dp(14), dp(10), dp(14), dp(10));
        tv.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            flash(v);
            click(selector);
        });
        return tv;
    }

    private void showMore() {
        if (moreBox == null) buildMoreSheet();
        moreList.removeAllViews();
        for (Act a : overflow) {
            TextView row = new TextView(act);
            row.setText(a.label);
            row.setTextColor(TEXT);
            row.setTextSize(14.5f);
            row.setPadding(dp(20), dp(15), dp(20), dp(15));
            row.setClickable(true);
            row.setBackground(ripple(0x00141922, 0x00000000, 14));
            final String sel = a.selector;
            row.setOnClickListener(v -> {
                v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
                click(sel);
                hideMore();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.leftMargin = dp(6);
            lp.rightMargin = dp(6);
            moreList.addView(row, lp);
        }
        moreBox.setVisibility(View.VISIBLE);
        moreScrim.setAlpha(0f);
        moreScrim.animate().alpha(1f).setDuration(160).start();
        morePanel.setTranslationY(dp(320));
        morePanel.animate().translationY(0f).setDuration(240)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    private void buildMoreSheet() {
        moreBox = new FrameLayout(act);
        moreBox.setVisibility(View.GONE);
        moreBox.setElevation(dp(30));

        moreScrim = new View(act);
        moreScrim.setBackgroundColor(0xB8000000);
        moreScrim.setOnClickListener(v -> hideMore());
        moreBox.addView(moreScrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        morePanel = new LinearLayout(act);
        morePanel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadii(new float[]{
                dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
        bg.setColor(0xFF10141C);
        bg.setStroke(dp(1), 0x1FA0F0D0);
        morePanel.setBackground(bg);
        morePanel.setPadding(dp(6), dp(8), dp(6), dp(16));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.BOTTOM;
        moreBox.addView(morePanel, plp);

        View handle = new View(act);
        GradientDrawable hg = new GradientDrawable();
        hg.setShape(GradientDrawable.RECTANGLE);
        hg.setCornerRadius(dp(2));
        hg.setColor(0x33FFFFFF);
        handle.setBackground(hg);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(38), dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(4);
        hlp.bottomMargin = dp(10);
        morePanel.addView(handle, hlp);

        TextView t = new TextView(act);
        t.setText("更多");
        t.setTextColor(0xFF8A93A6);
        t.setTextSize(10.5f);
        t.setLetterSpacing(0.22f);
        t.setPadding(dp(20), 0, dp(20), dp(8));
        morePanel.addView(t);

        moreList = new LinearLayout(act);
        moreList.setOrientation(LinearLayout.VERTICAL);
        morePanel.addView(moreList);

        root.addView(moreBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private void hideMore() {
        if (moreBox == null || moreBox.getVisibility() != View.VISIBLE) return;
        moreScrim.animate().alpha(0f).setDuration(130).start();
        morePanel.animate().translationY(dp(320)).setDuration(180)
                .withEndAction(() -> moreBox.setVisibility(View.GONE)).start();
    }

    private void paintChip(TextView v, boolean on) {
        v.setTextColor(on ? ACCENT : TEXT);
        v.setBackground(ripple(on ? 0x3034D399 : 0xE0141922,
                on ? 0x8034D399 : 0x26FFFFFF, 18));
    }

    /** 供外部（如原生参数面板）点亮某个按钮 */
    void setChipActive(String selector, boolean on) {
        for (int k = 0; k < chipActs.size(); k++) {
            if (selector.equals(chipActs.get(k).selector)) {
                chipActive.set(k, on);
                paintChip(chipViews.get(k), on);
            }
        }
    }

    /** 点一下短暂点亮，给出明确反馈 */
    private void flash(final View v) {
        v.setBackground(ripple(0x3034D399, 0x8034D399, 18));
        v.animate().scaleX(0.96f).scaleY(0.96f).setDuration(90).withEndAction(() ->
                v.animate().scaleX(1f).scaleY(1f).setDuration(140).withEndAction(() -> {
                    int idx = chipViews.indexOf(v);
                    boolean on = idx >= 0 && idx < chipActive.size() && chipActive.get(idx);
                    paintChip((TextView) v, on);
                }).start()).start();
    }

    // ---------------- 折叠 ----------------

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

    void setInsets(int top, int bottom) {
        this.insetTop = top;
        this.insetBottom = bottom;
        applyMargins();
    }

    private void applyMargins() {
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) header.getLayoutParams();
        lp.setMargins(dp(16), insetTop + dp(10), dp(72), 0);
        header.setLayoutParams(lp);

        FrameLayout.LayoutParams clp = (FrameLayout.LayoutParams) card.getLayoutParams();
        clp.setMargins(dp(16), insetTop + dp(44), 0, 0);
        card.setLayoutParams(clp);

        FrameLayout.LayoutParams alp = (FrameLayout.LayoutParams) actionBar.getLayoutParams();
        alp.gravity = Gravity.BOTTOM;
        alp.setMargins(dp(11), 0, dp(11), insetBottom + dp(104));
        actionBar.setLayoutParams(alp);
    }

    // ---------------- 显示 ----------------

    void attach(WebView wv) {
        this.web = wv;
        poll();
    }

    void detach() {
        shown = false;
        this.web = null;
        ui.removeCallbacks(tick);
    }

    void show() {
        shown = true;
        header.setVisibility(View.VISIBLE);
        card.setVisibility(View.VISIBLE);
        actionBar.setVisibility(View.VISIBLE);
        poll();

    }

    void hide() {
        shown = false;
        header.setVisibility(View.GONE);
        card.setVisibility(View.GONE);
        actionBar.setVisibility(View.GONE);
        ui.removeCallbacks(tick);
    }

    // ---------------- 轮询 ----------------

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!shown || web == null) return;
            web.evaluateJavascript(buildPollJs(), value -> {
                apply(value);
                pollLabels();
                ui.postDelayed(tick, 700);
            });
        }
    };

    private void poll() {
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    /** 动态文案单独请求，失败不影响数据 */
    private void pollLabels() {
        boolean any = false;
        StringBuilder js = new StringBuilder("(function(){var a=[];");
        for (int k = 0; k < chipActs.size(); k++) {
            String le = chipActs.get(k).labelExpr;
            if (le != null) any = true;
            js.append("try{a.push(String(").append(le == null ? "''" : le).append("));}")
                    .append("catch(e){a.push('');}");
        }
        js.append("return JSON.stringify(a);})()");
        if (!any) return;
        web.evaluateJavascript(js.toString(), value -> {
            if (value == null) return;
            String s2 = value;
            if (s2.startsWith("\"") && s2.endsWith("\"") && s2.length() > 1) {
                s2 = s2.substring(1, s2.length() - 1).replace("\\\"", "\"");
            }
            try {
                org.json.JSONArray arr = new org.json.JSONArray(s2);
                for (int k = 0; k < chipViews.size() && k < arr.length(); k++) {
                    String t = arr.optString(k, "");
                    if (!t.isEmpty() && !t.equals(chipViews.get(k).getText().toString())) {
                        chipViews.get(k).setText(t);
                    }
                }
            } catch (Exception ignored) {
            }
        });
    }

    private String buildPollJs() {
        StringBuilder sb = new StringBuilder();
        sb.append("(function(){var g=function(id){var e=document.getElementById(id);")
                .append("return e?e.textContent.trim():''};var o={};");
        if (rows != null) {
            for (Row r : rows) {
                for (Pair p : r.pairs) {
                    sb.append("o['").append(p.domId).append("']=g('").append(p.domId).append("');");
                }
            }
        }
        if (textBlockId != null) {
            sb.append("var tb=document.querySelector('").append(textBlockId).append("');")
                    .append("o.__text=tb?Array.prototype.map.call(tb.children,")
                    .append("function(c){return c.textContent.trim();}).filter(Boolean).join('\\n'):'';");
        }
        if (statusSelector != null) {
            sb.append("var sc=document.querySelector('").append(statusSelector).append("');")
                    .append("o.__status=sc?sc.textContent.trim():'';");
        }
        sb.append("o.__act=[");
        for (int k = 0; k < chipActs.size(); k++) {
            String pr = chipActs.get(k).probe;
            sb.append(k > 0 ? "," : "").append(pr == null ? "0"
                    : "((function(){try{return (" + pr + ")?1:0;}catch(e){return 0;}})())");
        }
        sb.append("];");

        if (overlayProbe != null) {
            sb.append("o.__ov=(").append(overlayProbe).append(")?1:0;");
        }
        sb.append("return JSON.stringify(o);})()");
        return sb.toString();
    }

    private void apply(String json) {
        if (json == null) return;
        String s = json;
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        try {
            JSONObject o = new JSONObject(s);
            if (rows != null) {
                int k = 0;
                for (Row r : rows) {
                    for (Pair p : r.pairs) {
                        if (k >= valueViews.size()) break;
                        String v = o.optString(p.domId, "—");
                        valueViews.get(k++).setText(v.isEmpty() ? "—" : v);
                    }
                }
            }
            if (blockView != null) {
                String t = o.optString("__text", "—");
                blockView.setText(t.isEmpty() ? "—" : t);
            }
            if (statusSelector != null) {
                String st = o.optString("__status", "");
                statusChip.setText(st.isEmpty() ? "运行中" : st);
            }
            org.json.JSONArray arr = o.optJSONArray("__act");
            if (arr != null) {
                for (int k = 0; k < chipViews.size() && k < arr.length(); k++) {
                    boolean on = arr.optInt(k, 0) == 1;
                    if (chipActive.get(k) != on) {
                        chipActive.set(k, on);
                        paintChip(chipViews.get(k), on);
                    }
                }
            }

            // 数据卡始终显示，二级面板自己限高避让
        } catch (Exception ignored) {
        }
    }

    private final java.util.Map<String, Runnable> hooks = new java.util.HashMap<>();

    void setAction(String selector, Runnable r) {
        hooks.put(selector, r);
    }

    void setParamAction(Runnable r) {
        setAction("#__param", r);
    }

    private void click(String selector) {
        Runnable hook = hooks.get(selector);
        if (hook != null) {
            hook.run();
            return;
        }
        if (web == null) return;
        web.evaluateJavascript("var e=document.querySelector('" + selector + "');if(e)e.click();", null);
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

    private Drawable ripple(int fill, int strokeColor, float radiusDp) {
        GradientDrawable base = new GradientDrawable();
        base.setShape(GradientDrawable.RECTANGLE);
        base.setCornerRadius(dp(radiusDp));
        base.setColor(fill);
        base.setStroke(dp(1), strokeColor);
        return new RippleDrawable(ColorStateList.valueOf(0x1AFFFFFF), base, null);
    }
}
