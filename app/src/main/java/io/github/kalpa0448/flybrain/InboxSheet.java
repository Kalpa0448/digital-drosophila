package io.github.kalpa0448.flybrain;

import android.app.Activity;
import android.content.res.ColorStateList;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * 「观察与审核」的原生面板：读取网页 #msgs 里的记录，用原生列表渲染。
 */
class InboxSheet {

    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private String lastSig = "";
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (box.getVisibility() == View.VISIBLE) load();
            ui.postDelayed(this, 1200);
        }
    };

    private final Activity act;
    private final FrameLayout root;
    private WebView web;

    private FrameLayout box;
    private View scrim;
    private LinearLayout panel;
    private LinearLayout list;
    private TextView empty;

    private Runnable onClose;

    InboxSheet(Activity activity, FrameLayout root) {
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
        box.setElevation(dp(64));

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

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(440));
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
        hlp.topMargin = dp(8);
        hlp.bottomMargin = dp(10);
        panel.addView(handle, hlp);

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), 0, dp(12), dp(10));

        TextView title = new TextView(act);
        title.setText("观察与审核");
        title.setTextColor(0xFFE8ECF4);
        title.setTextSize(16f);
        title.getPaint().setFakeBoldText(true);
        head.addView(title, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(act);
        close.setText("✕");
        close.setTextColor(0xFF8A93A6);
        close.setTextSize(16f);
        close.setGravity(Gravity.CENTER);
        close.setBackground(roundRect(0x1AFFFFFF, 18));
        close.setOnClickListener(v -> hide());
        head.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));
        panel.addView(head);

        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(14), 0, dp(14), dp(14));
        sv.addView(list, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        empty = new TextView(act);
        empty.setText("还没有观察记录");
        empty.setTextColor(0xFF5C6478);
        empty.setTextSize(12f);
        empty.setGravity(Gravity.CENTER);
        empty.setPadding(0, dp(40), 0, 0);
        panel.addView(empty);

        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------------- 显示 ----------------

    void show() {
        if (web == null) return;
        box.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();
        panel.setTranslationY(dp(460));
        panel.animate().translationY(0f).setDuration(250)
                .setInterpolator(new DecelerateInterpolator()).start();
        lastSig = "";
        load();
        ui.removeCallbacks(tick);
        ui.postDelayed(tick, 1200);
    }

    void hide() {
        if (box.getVisibility() != View.VISIBLE) return;
        ui.removeCallbacks(tick);
        scrim.animate().alpha(0f).setDuration(130).start();
        panel.animate().translationY(dp(460)).setDuration(200)
                .withEndAction(() -> {
                    box.setVisibility(View.GONE);
                    if (onClose != null) onClose.run();
                }).start();
    }

    boolean isOpen() {
        return box.getVisibility() == View.VISIBLE;
    }

    private void load() {
        web.evaluateJavascript(
                "(function(){var out=[];var ms=document.querySelectorAll('#msgs .msg');"
                        + "for(var i=0;i<ms.length&&i<20;i++){var m=ms[i];"
                        + "var w=m.querySelector('.who');"
                        + "var t=m.children[1];"
                        + "var r=m.querySelector('.rul');"
                        + "out.push({w:w?w.textContent.trim():'',"
                        + "t:t?t.textContent.trim():'',"
                        + "r:r?r.textContent.trim():''});}"
                        + "return JSON.stringify(out);})()",
                value -> render(value));
    }

    private void render(String json) {
        String sig = json == null ? "" : json;
        if (sig.equals(lastSig)) return;
        lastSig = sig;
        list.removeAllViews();
        String s = json;
        if (s != null && s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        int count = 0;
        try {
            JSONArray arr = new JSONArray(s == null ? "[]" : s);
            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.addView(buildItem(o.optString("w"), o.optString("t"), o.optString("r")),
                        itemParams(i));
                count++;
            }
        } catch (Exception ignored) {
        }
        empty.setVisibility(count == 0 ? View.VISIBLE : View.GONE);
    }

    private LinearLayout.LayoutParams itemParams(int index) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (index > 0) lp.topMargin = dp(9);
        return lp;
    }

    private View buildItem(String who, String text, String rule) {
        LinearLayout card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(roundRect(0x14FFFFFF, 14));
        card.setPadding(dp(14), dp(12), dp(14), dp(12));

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView w = new TextView(act);
        w.setText(who);
        w.setTextColor(0xFF34D399);
        w.setTextSize(10.5f);
        w.setLetterSpacing(0.08f);
        top.addView(w, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (rule != null && !rule.isEmpty()) {
            TextView r = new TextView(act);
            boolean ok = rule.contains("批准");
            r.setText(ok ? "已批准" : "已驳回");
            r.setTextColor(ok ? 0xFF34D399 : 0xFFF87171);
            r.setTextSize(10f);
            r.setBackground(roundRect(ok ? 0x2634D399 : 0x26F87171, 10));
            r.setPadding(dp(8), dp(2), dp(8), dp(2));
            top.addView(r);
        }
        card.addView(top);

        TextView body = new TextView(act);
        body.setText(text);
        body.setTextColor(0xFFC7CEDB);
        body.setTextSize(12.5f);
        body.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.topMargin = dp(6);
        card.addView(body, blp);

        return card;
    }

    private GradientDrawable roundRect(int fill, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(radiusDp));
        g.setColor(fill);
        return g;
    }
}
