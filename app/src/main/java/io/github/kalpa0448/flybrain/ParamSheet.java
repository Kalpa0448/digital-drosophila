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
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * 参数面板的原生实现：从网页读取滑块定义，用原生 SeekBar 重建。
 */
class ParamSheet {

    private final Activity act;
    private final FrameLayout root;
    private WebView web;

    private FrameLayout box;
    private View scrim;
    private LinearLayout panel;
    private LinearLayout listBox;
    private TextView titleView;
    private Runnable onClose;

    private final String rowSelector;
    private final String sheetTitle;

    private final String groupSelector;

    ParamSheet(Activity activity, FrameLayout root, String title,
               String rowSelector, String groupSelector) {
        this.act = activity;
        this.root = root;
        this.sheetTitle = title;
        this.rowSelector = rowSelector;
        this.groupSelector = groupSelector;
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
        panel.setBackground(roundRect(0xFF10141C, 0x1FA0F0D0, 26, true));

        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(520));
        plp.gravity = Gravity.BOTTOM;
        box.addView(panel, plp);

        // 标题行
        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(dp(20), dp(16), dp(12), dp(10));

        titleView = new TextView(act);
        titleView.setText(sheetTitle);
        titleView.setTextColor(0xFFE8ECF4);
        titleView.setTextSize(16f);
        titleView.getPaint().setFakeBoldText(true);
        head.addView(titleView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(act);
        close.setText("✕");
        close.setTextColor(0xFF8A93A6);
        close.setTextSize(16f);
        close.setGravity(Gravity.CENTER);
        close.setBackground(ripple(0x1AFFFFFF, 20));
        close.setOnClickListener(v -> hide());
        head.addView(close, new LinearLayout.LayoutParams(dp(34), dp(34)));

        panel.addView(head);

        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        listBox = new LinearLayout(act);
        listBox.setOrientation(LinearLayout.VERTICAL);
        listBox.setPadding(dp(12), 0, dp(12), dp(8));
        sv.addView(listBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // 底部按钮
        LinearLayout btns = new LinearLayout(act);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setPadding(dp(14), dp(10), dp(14), dp(16));

        TextView reset = new TextView(act);
        reset.setText("恢复默认");
        reset.setTextColor(0xFFC7CEDB);
        reset.setTextSize(13.5f);
        reset.setGravity(Gravity.CENTER);
        reset.setBackground(ripple(0x14FFFFFF, 16));
        reset.setPadding(0, dp(12), 0, dp(12));
        reset.setOnClickListener(v -> click("paramReset"));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        rlp.rightMargin = dp(8);
        btns.addView(reset, rlp);

        TextView save = new TextView(act);
        save.setText("保存");
        save.setTextColor(0xFF06281E);
        save.setTextSize(13.5f);
        save.setGravity(Gravity.CENTER);
        save.setBackground(ripple(0xFF34D399, 16));
        save.setPadding(0, dp(12), 0, dp(12));
        save.setOnClickListener(v -> {
            click("paramSave");
            hide();
        });
        btns.addView(save, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        panel.addView(btns);

        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------------- 显示 ----------------

    void show() {
        if (web == null) return;
        box.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(180).start();
        panel.setTranslationY(dp(560));
        panel.animate().translationY(0f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
        loadDefs();
    }

    void hide() {
        if (box.getVisibility() != View.VISIBLE) return;
        scrim.animate().alpha(0f).setDuration(140).start();
        panel.animate().translationY(dp(560)).setDuration(200)
                .withEndAction(() -> {
                    box.setVisibility(View.GONE);
                    if (onClose != null) onClose.run();
                }).start();
    }

    boolean isOpen() {
        return box.getVisibility() == View.VISIBLE;
    }

    private void loadDefs() {
        String sel = rowSelector + (groupSelector == null ? "" : "," + groupSelector);
        web.evaluateJavascript(
                "(function(){var out=[];var ri=0;"
                        + "document.querySelectorAll('" + sel + "').forEach(function(el){"
                        + "  if(el.matches('" + (groupSelector == null ? "__none__" : groupSelector) + "')){"
                        + "    out.push({t:'g',label:el.textContent.trim()});return;}"
                        + "  var it=el.querySelector('input'); if(!it) return;"
                        + "  var lb=el.querySelector('label')||el.querySelector('span');"
                        + "  var txt=lb?(lb.childNodes[0]?lb.childNodes[0].textContent:lb.textContent):'';"
                        + "  var isChk=it.type==='checkbox';"
                        + "  out.push({t:'r',id:it.id||('@'+ri),label:txt.trim(),"
                        + "    type:isChk?'check':'range',"
                        + "    min:isChk?0:parseFloat(it.min),max:isChk?1:parseFloat(it.max),"
                        + "    step:isChk?1:(parseFloat(it.step)||1),"
                        + "    val:isChk?(it.checked?1:0):parseFloat(it.value)});"
                        + "  ri++;"
                        + "});"
                        + "return JSON.stringify(out);})()",
                value -> {
                    buildRows(decode(value));
                    panel.setTranslationY(dp(560));
                    panel.animate().translationY(0f).setDuration(260)
                            .setInterpolator(new DecelerateInterpolator()).start();
                });
    }

    private String decode(String json) {
        if (json == null) return "[]";
        String s = json;
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .replace("\\n", "");
        }
        return s;
    }

    private void buildRows(String json) {
        listBox.removeAllViews();
        List<JSONObject> defs = new ArrayList<>();
        try {
            JSONArray arr = new JSONArray(json);
            for (int i = 0; i < arr.length(); i++) defs.add(arr.getJSONObject(i));
        } catch (Exception e) {
            TextView t = new TextView(act);
            t.setText("读取参数失败");
            t.setTextColor(0xFF8A93A6);
            t.setPadding(dp(14), dp(20), dp(14), dp(20));
            listBox.addView(t);
            return;
        }
        for (JSONObject o : defs) {
            if ("g".equals(o.optString("t"))) {
                listBox.addView(buildGroup(o.optString("label")));
            } else {
                listBox.addView(buildRow(o));
            }
        }
    }

    private View buildGroup(String label) {
        LinearLayout box = new LinearLayout(act);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(16), 0, dp(4));

        LinearLayout line = new LinearLayout(act);
        line.setOrientation(LinearLayout.HORIZONTAL);
        line.setGravity(Gravity.CENTER_VERTICAL);
        line.setPadding(dp(14), 0, dp(14), 0);

        View dot = new View(act);
        GradientDrawable dg = new GradientDrawable();
        dg.setShape(GradientDrawable.OVAL);
        dg.setColor(0xFF34D399);
        dot.setBackground(dg);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(5), dp(5));
        dlp.rightMargin = dp(7);
        line.addView(dot, dlp);

        TextView t = new TextView(act);
        t.setText(label);
        t.setTextColor(0xFFD7DEEA);
        t.setTextSize(13f);
        t.setLetterSpacing(0.05f);
        t.getPaint().setFakeBoldText(true);
        line.addView(t);
        box.addView(line);

        View divider = new View(act);
        GradientDrawable lg = new GradientDrawable();
        lg.setColor(0x1AFFFFFF);
        divider.setBackground(lg);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
        vlp.topMargin = dp(8);
        vlp.leftMargin = dp(14);
        vlp.rightMargin = dp(14);
        box.addView(divider, vlp);
        return box;
    }

    private View buildCheckRow(String id, String label, boolean on) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(12), dp(14), dp(12), dp(14));

        TextView l = new TextView(act);
        l.setText(label);
        l.setTextColor(0xFFC7CEDB);
        l.setTextSize(12.5f);
        row.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        android.widget.CheckBox cb = new android.widget.CheckBox(act);
        cb.setChecked(on);
        cb.setButtonTintList(ColorStateList.valueOf(0xFF34D399));
        cb.setOnCheckedChangeListener((b, checked) -> {
            if (web != null) {
                web.evaluateJavascript("(function(){var e=document.getElementById('" + id + "');"
                        + "if(!e)return;e.checked=" + (checked ? "true" : "false") + ";"
                        + "e.dispatchEvent(new Event('change',{bubbles:true}));})()", null);
            }
        });
        row.addView(cb);
        return row;
    }

    private View buildRow(final JSONObject o) {
        String id = o.optString("id");
        String label = o.optString("label");
        double min = o.optDouble("min", 0);
        double max = o.optDouble("max", 100);
        double step = o.optDouble("step", 1);
        double val = o.optDouble("val", min);
        if (step <= 0) step = 1;

        if ("check".equals(o.optString("type"))) {
            return buildCheckRow(id, label, val > 0);
        }

        int steps = (int) Math.round((max - min) / step);
        if (steps < 1) steps = 1;

        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(6), dp(9), dp(6), dp(9));

        LinearLayout top = new LinearLayout(act);
        top.setOrientation(LinearLayout.HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);

        TextView l = new TextView(act);
        l.setText(label);
        l.setTextColor(0xFFC7CEDB);
        l.setTextSize(12.5f);
        top.addView(l, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        final TextView v = new TextView(act);
        v.setTextSize(12f);
        v.setTextColor(0xFF34D399);
        v.setGravity(Gravity.END);
        top.addView(v);
        row.addView(top);

        final int decimals = decimalsOf(step);
        final double fMin = min, fStep = step;
        final TextView valueView = v;
        valueView.setText(fmt(val, decimals));

        SeekBar sb = new SeekBar(act);
        sb.setMax(steps);
        sb.setProgress((int) Math.round((val - min) / step));
        sb.setProgressTintList(ColorStateList.valueOf(0xFF34D399));
        sb.setProgressBackgroundTintList(ColorStateList.valueOf(0x33FFFFFF));

        // 自绘圆点滑块：直径 14dp，左右留出半径，端点不会被裁一半
        GradientDrawable thumb = new GradientDrawable();
        thumb.setShape(GradientDrawable.OVAL);
        thumb.setColor(0xFF34D399);
        thumb.setSize(dp(14), dp(14));
        sb.setThumb(thumb);
        sb.setThumbOffset(0);
        sb.setPadding(dp(8), dp(6), dp(8), dp(4));

        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                double nv = fMin + progress * fStep;
                valueView.setText(fmt(nv, decimals));
                if (fromUser) setValue(id, nv);
            }

            @Override
            public void onStartTrackingTouch(SeekBar bar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar bar) {
            }
        });

        row.addView(sb, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return row;
    }

    private void setValue(String id, double v) {
        if (web == null) return;
        if (id.startsWith("@")) {
            int idx = Integer.parseInt(id.substring(1));
            String js = "(function(){var r=document.querySelectorAll('" + rowSelector + "')[" + idx + "];"
                    + "if(!r)return;var e=r.querySelector('input');e.value=" + v + ";"
                    + "e.dispatchEvent(new Event('input',{bubbles:true}));})()";
            web.evaluateJavascript(js, null);
            return;
        }
        String js = "(function(){var e=document.getElementById('" + id + "');if(!e)return;"
                + "e.value=" + v + ";"
                + "e.dispatchEvent(new Event('input',{bubbles:true}));"
                + "e.dispatchEvent(new Event('change',{bubbles:true}));})()";
        web.evaluateJavascript(js, null);
    }

    private void click(String domId) {
        if (web == null) return;
        web.evaluateJavascript("var e=document.getElementById('" + domId + "');if(e)e.click();", null);
    }

    private static int decimalsOf(double step) {
        String s = String.valueOf(step);
        int i = s.indexOf('.');
        return i < 0 ? 0 : Math.min(4, s.length() - i - 1);
    }

    private static String fmt(double v, int decimals) {
        return String.format("%." + decimals + "f", v);
    }

    // ---------------- 小工具 ----------------

    private GradientDrawable roundRect(int fill, int stroke, float radiusDp, boolean topOnly) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        if (topOnly) {
            g.setCornerRadii(new float[]{
                    dp(radiusDp), dp(radiusDp), dp(radiusDp), dp(radiusDp), 0, 0, 0, 0});
        } else {
            g.setCornerRadius(dp(radiusDp));
        }
        g.setColor(fill);
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }

    private GradientDrawable ripple(int fill, float radiusDp) {
        GradientDrawable base = new GradientDrawable();
        base.setShape(GradientDrawable.RECTANGLE);
        base.setCornerRadius(dp(radiusDp));
        base.setColor(fill);
        return base;
    }
}
