package io.github.kalpa0448.flybrain;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONObject;

/** 「单只观察」的原生面板：轮询网页里的 o-* 字段。 */
class ObsSheet {

    private static final String[] LABELS = {
            "状态", "能量 / 饥饿", "厌恶性权重 MBON_av", "粘板数 / 最近距离", "被粘次数 / 累计时长", "MN_walk / MN_feed"
    };
    private static final String[] IDS = {
            "o-state", "o-energy", "o-avers", "o-sticky", "o-stuck", "o-mn"
    };

    private final Activity act;
    private final FrameLayout root;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private WebView web;
    private FrameLayout box;
    private View scrim;
    private LinearLayout panel;
    private TextView subTitle;
    private final TextView[] values = new TextView[LABELS.length];

    private Runnable onClose;

    ObsSheet(Activity activity, FrameLayout root) {
        this.act = activity;
        this.root = root;
        build();
    }

    void attach(WebView wv) {
        this.web = wv;
    }

    void setOnClose(Runnable r) {
        this.onClose = r;
    }

    private int dp(float v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    private void build() {
        box = new FrameLayout(act);
        box.setVisibility(View.GONE);
        box.setElevation(dp(66));

        scrim = new View(act);
        scrim.setBackgroundColor(0xB8000000);
        scrim.setOnClickListener(v -> hide());
        box.addView(scrim, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadii(new float[]{dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
        bg.setColor(0xFF10141C);
        bg.setStroke(dp(1), 0x1FA0F0D0);
        panel.setBackground(bg);
        panel.setPadding(dp(18), dp(8), dp(18), dp(22));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.BOTTOM;
        box.addView(panel, plp);

        View handle = new View(act);
        GradientDrawable hg = new GradientDrawable();
        hg.setShape(GradientDrawable.RECTANGLE);
        hg.setCornerRadius(dp(2));
        hg.setColor(0x33FFFFFF);
        handle.setBackground(hg);
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(38), dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(4);
        hlp.bottomMargin = dp(14);
        panel.addView(handle, hlp);

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(act);
        title.setText("单只观察");
        title.setTextColor(0xFFE8ECF4);
        title.setTextSize(16f);
        title.getPaint().setFakeBoldText(true);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        subTitle = new TextView(act);
        subTitle.setTextColor(0xFF34D399);
        subTitle.setTextSize(11f);
        head.addView(subTitle);
        panel.addView(head);

        for (int i = 0; i < LABELS.length; i++) {
            LinearLayout row = new LinearLayout(act);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(11), 0, dp(11));

            TextView l = new TextView(act);
            l.setText(LABELS[i]);
            l.setTextColor(0xFF8A93A6);
            l.setTextSize(12f);
            row.addView(l, new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

            TextView v = new TextView(act);
            v.setText("—");
            v.setTextColor(0xFFE8ECF4);
            v.setTextSize(12.5f);
            v.setGravity(Gravity.END);
            row.addView(v);
            values[i] = v;

            LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) rlp.topMargin = dp(1);
            panel.addView(row, rlp);

            if (i < LABELS.length - 1) {
                View div = new View(act);
                GradientDrawable dg = new GradientDrawable();
                dg.setColor(0x12FFFFFF);
                div.setBackground(dg);
                panel.addView(div, new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, dp(1)));
            }
        }

        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------------- 显示 ----------------

    private boolean pageOn = false;

    void show() {
        if (web == null) return;
        // 让页面把 o-* 字段填上（面板本身已被 CSS 隐藏）
        if (!pageOn) {
            pageOn = true;
            web.evaluateJavascript("var b=document.getElementById('obsBtn');if(b)b.click();", null);
        }
        box.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();
        panel.setTranslationY(dp(380));
        panel.animate().translationY(0f).setDuration(250)
                .setInterpolator(new DecelerateInterpolator()).start();
        poll();
    }

    void hide() {
        if (box.getVisibility() != View.VISIBLE) return;
        if (web != null && pageOn) {
            pageOn = false;
            web.evaluateJavascript("var b=document.getElementById('obsBtn');if(b)b.click();", null);
        }
        ui.removeCallbacks(tick);
        scrim.animate().alpha(0f).setDuration(130).start();
        panel.animate().translationY(dp(380)).setDuration(200)
                .withEndAction(() -> {
                    box.setVisibility(View.GONE);
                    if (onClose != null) onClose.run();
                }).start();
    }

    boolean isOpen() {
        return box.getVisibility() == View.VISIBLE;
    }

    // ---------------- 轮询 ----------------

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (web == null || box.getVisibility() != View.VISIBLE) return;
            StringBuilder js = new StringBuilder(
                    "(function(){var g=function(id){var e=document.getElementById(id);"
                            + "return e?e.textContent.trim():''};var o={};");
            for (String id : IDS) {
                js.append("o['").append(id).append("']=g('").append(id).append("');");
            }
            js.append("o.__info=g('bInfo');return JSON.stringify(o);})()");
            web.evaluateJavascript(js.toString(), value -> {
                apply(value);
                ui.postDelayed(tick, 500);
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
            for (int i = 0; i < IDS.length; i++) {
                String v = o.optString(IDS[i], "—");
                values[i].setText(v.isEmpty() ? "—" : v);
            }
            String info = o.optString("__info", "");
            subTitle.setText(info.isEmpty() ? "" : info);
        } catch (Exception ignored) {
        }
    }
}
