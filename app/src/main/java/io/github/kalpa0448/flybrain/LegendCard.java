package io.github.kalpa0448.flybrain;

import android.app.Activity;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** 大世界图例：右上角按钮 + 底部抽屉弹出。样式全部从网页计算样式还原。 */
class LegendCard {

    private final Activity act;
    private final FrameLayout root;

    private LinearLayout btn;
    private FrameLayout box;
    private View scrim;
    private LinearLayout panel;
    private LinearLayout rows;
    private WebView web;
    private boolean open = false;

    LegendCard(Activity activity, FrameLayout root) {
        this.act = activity;
        this.root = root;
        build();
    }

    private int dp(float v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    private void build() {
        // ---------- 右上角按钮 ----------
        btn = new LinearLayout(act);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(round(0x14FFFFFF, 0x33A0F0D0, 16));
        btn.setOnClickListener(v -> toggle(web));

        TextView label = new TextView(act);
        label.setText("图例");
        label.setTextColor(0xFF9AA5B8);
        label.setTextSize(10.5f);
        label.setLetterSpacing(0.1f);
        btn.addView(label);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(50), dp(30));
        blp.gravity = Gravity.TOP | Gravity.END;
        blp.topMargin = dp(96);
        blp.rightMargin = dp(14);
        root.addView(btn, blp);

        // ---------- 抽屉 ----------
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
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadii(new float[]{dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
        bg.setColor(0xFF10141C);
        bg.setStroke(dp(1), 0x1FA0F0D0);
        panel.setBackground(bg);
        panel.setPadding(0, 0, 0, dp(10));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp.gravity = Gravity.BOTTOM;
        box.addView(panel, plp);

        View handle = new View(act);
        handle.setBackground(round(0x33FFFFFF, 0x33FFFFFF, 2));
        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(dp(38), dp(4));
        hlp.gravity = Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(8);
        hlp.bottomMargin = dp(10);
        panel.addView(handle, hlp);

        TextView title = new TextView(act);
        title.setText("图例");
        title.setTextColor(0xFFE8ECF4);
        title.setTextSize(15f);
        title.setLetterSpacing(0.1f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        title.setPadding(dp(20), 0, dp(20), 0);
        panel.addView(title);

        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        rows = new LinearLayout(act);
        rows.setOrientation(LinearLayout.VERTICAL);
        rows.setPadding(dp(20), dp(12), dp(20), dp(8));
        sv.addView(rows, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void setWeb(WebView w) {
        this.web = w;
    }

    void toggle(WebView w) {
        if (open) hide();
        else show(w);
    }

    void show(WebView w) {
        if (w == null) return;
        web = w;
        open = true;
        box.setVisibility(View.VISIBLE);
        box.bringToFront();
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();
        panel.setTranslationY(dp(560));
        panel.animate().translationY(0f).setDuration(250)
                .setInterpolator(new DecelerateInterpolator()).start();
        highlight(true);
        load();
    }

    void hide() {
        if (!open) return;
        open = false;
        scrim.animate().alpha(0f).setDuration(130).start();
        panel.animate().translationY(dp(560)).setDuration(200)
                .withEndAction(() -> box.setVisibility(View.GONE)).start();
        highlight(false);
    }

    boolean isOpen() {
        return open;
    }

    void setVisible(boolean v) {
        btn.setVisibility(v ? View.VISIBLE : View.GONE);
        if (!v) hide();
    }

    private void highlight(boolean on) {
        btn.setBackground(on ? round(0x2234D399, 0x8C34D399, 16)
                : round(0x14FFFFFF, 0x33A0F0D0, 16));
        ((TextView) btn.getChildAt(0)).setTextColor(on ? 0xFF34D399 : 0xFF9AA5B8);
    }

    // ---------------- 数据 ----------------

    private void load() {
        if (web == null) return;
        web.evaluateJavascript(
                "(function(){var out=[];"
                        + "document.querySelectorAll('#legend > div').forEach(function(d){"
                        + "  var cs=getComputedStyle(d);"
                        + "  if(cs.display==='none'||cs.visibility==='hidden') return;"
                        + "  var i=d.querySelector('i');"
                        + "  var lbl='';for(var n=0;n<d.childNodes.length;n++){var ch=d.childNodes[n];if(ch.nodeType===3)lbl+=ch.textContent;}"
                        + "  var o={t:lbl.trim()||d.textContent.trim()};"
                        + "  if(i){var s=getComputedStyle(i);"
                        + "    o.bg=s.backgroundColor;o.bc=s.borderTopColor;"
                        + "    o.bw=parseFloat(s.borderTopWidth)||0;"
                        + "    o.rad=s.borderTopLeftRadius;o.fg=s.color;"
                        + "    o.ic=(i.textContent||'').trim();}"
                        + "  var cv=d.querySelector('canvas');var png='';"
                        + "  if(cv){try{png=cv.toDataURL('image/png');}catch(e){}}"
                        + "  o.png=png;out.push(o);"
                        + "});return JSON.stringify(out);})()",
                this::render);
    }

    private void render(String json) {
        rows.removeAllViews();
        try {
            JSONArray arr = new JSONArray(unquote(json));
            for (int i = 0; i < arr.length(); i++) {
                rows.addView(item(arr.getJSONObject(i)), rowParams(i));
            }
        } catch (Exception ignored) {
        }
    }

    private LinearLayout.LayoutParams rowParams(int i) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (i > 0) lp.topMargin = dp(9);
        return lp;
    }

    private View item(JSONObject o) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        String png = o.optString("png", "");
        if (png.startsWith("data:image")) {
            try {
                byte[] bytes = android.util.Base64.decode(
                        png.substring(png.indexOf(',') + 1), android.util.Base64.DEFAULT);
                android.graphics.Bitmap bmp =
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                ImageView iv = new ImageView(act);
                iv.setImageBitmap(bmp);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(15), dp(15));
                lp.rightMargin = dp(9);
                row.addView(iv, lp);
                row.addView(label(o.optString("t")));
                return row;
            } catch (Exception ignored) {
            }
        }

        String icon = o.optString("ic", "");
        String bg = o.optString("bg", "");
        String bc = o.optString("bc", "");
        String rad = o.optString("rad", "50%");
        float bw = (float) o.optDouble("bw", 0);
        boolean transparentBg = bg.startsWith("rgba(0, 0, 0, 0") || bg.equals("transparent");

        if (!icon.isEmpty()) {
            TextView g = new TextView(act);
            g.setText(icon);
            g.setTextColor(parseColor(o.optString("fg"), 0xFFC0504D));
            g.setTextSize(11f);
            g.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            g.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(14), dp(14));
            lp.rightMargin = dp(9);
            row.addView(g, lp);
        } else {
            View dot = new View(act);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.RECTANGLE);
            float r;
            if (rad.contains("%")) {
                r = dp(7);
            } else {
                try {
                    r = dp(Float.parseFloat(rad.replace("px", "").trim()));
                } catch (Exception e) {
                    r = dp(2);
                }
            }
            dg.setCornerRadius(r);
            dg.setColor(transparentBg ? 0x00000000 : parseColor(bg, 0xFF888888));
            if (bw > 0) dg.setStroke(Math.max(1, Math.round(dp(bw))),
                    parseColor(bc, 0xFFFFFFFF));
            dot.setBackground(dg);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(14), dp(14));
            lp.rightMargin = dp(9);
            row.addView(dot, lp);
        }

        row.addView(label(o.optString("t")));
        return row;
    }

    private TextView label(String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(0xFFC7CEDB);
        t.setTextSize(12f);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(rDp));
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        return g;
    }

    private static String unquote(String v) {
        if (v == null) return "[]";
        String s = v;
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1)
                    .replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return s;
    }

    private static int parseColor(String css, int fallback) {
        if (css == null || css.isEmpty()) return fallback;
        String s = css.trim();
        try {
            if (s.startsWith("#")) {
                if (s.length() == 7) return Color.parseColor(s);
                if (s.length() == 4) {
                    return Color.parseColor("#" + s.charAt(1) + s.charAt(1)
                            + s.charAt(2) + s.charAt(2) + s.charAt(3) + s.charAt(3));
                }
            }
            if (s.startsWith("rgb")) {
                String body = s.substring(s.indexOf('(') + 1, s.indexOf(')'));
                String[] p = body.split(",");
                int r = (int) Float.parseFloat(p[0].trim());
                int g = (int) Float.parseFloat(p[1].trim());
                int b = (int) Float.parseFloat(p[2].trim());
                if (p.length > 3) {
                    float a = Float.parseFloat(p[3].trim());
                    return Color.argb((int) (a * 255), r, g, b);
                }
                return Color.rgb(r, g, b);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}
