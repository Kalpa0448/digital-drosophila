package io.github.kalpa0448.flybrain;

import android.app.Activity;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.webkit.WebView;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

/** 大世界的「种群曲线」「图例」原生弹窗。 */
class ChartSheet {

    private static final String[] SERIES_KEY = {"f", "p", "ef", "ep", "o"};
    private static final String[] SERIES_NAME = {"果蝇", "蜘蛛", "果蝇卵", "蜘蛛卵", "食物"};
    private static final int[] SERIES_COLOR = {
            0xFF7C8CFF, 0xFFC0504D, 0xFFE9E3D0, 0xFFB06A6A, 0xFFE8A33D};

    private final Activity act;
    private final FrameLayout root;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private WebView web;
    private FrameLayout box;
    private View scrim;
    private LinearLayout panel;
    private TextView titleView;
    private TextView hintView;
    private LineChart chart;
    private LinearLayout chartBox;
    private final java.util.List<android.widget.ImageView> chartImgs = new java.util.ArrayList<>();
    private LinearLayout legendRow;
    private boolean legendMode = false;

    ChartSheet(Activity activity, FrameLayout root) {
        this.act = activity;
        this.root = root;
        build();
    }

    void attach(WebView wv) {
        this.web = wv;
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
        panel.setPadding(dp(18), dp(8), dp(18), dp(20));

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
        hlp.bottomMargin = dp(12);
        panel.addView(handle, hlp);

        LinearLayout head = new LinearLayout(act);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        titleView = new TextView(act);
        titleView.setText("种群曲线");
        titleView.setTextColor(0xFFE8ECF4);
        titleView.setTextSize(16f);
        titleView.getPaint().setFakeBoldText(true);
        head.addView(titleView, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        hintView = new TextView(act);
        hintView.setTextColor(0xFF6B7385);
        hintView.setTextSize(10.5f);
        head.addView(hintView);
        panel.addView(head);

        chart = new LineChart(act);
        chart.setVisibility(View.GONE);
        chartBox = new LinearLayout(act);
        chartBox.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams clp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.topMargin = dp(12);
        panel.addView(chartBox, clp);

        legendRow = new LinearLayout(act);
        legendRow.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(12);
        panel.addView(legendRow, llp);

        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    // ---------------- 显示 ----------------

    void showChart() {
        legendMode = false;
        prepare("种群曲线", "最近 5 分钟");
        chart.setVisibility(View.GONE);
        chartBox.setVisibility(View.VISIBLE);
        legendRow.setVisibility(View.GONE);
        open();
        pollChart();
    }

    void showLegend() {
        legendMode = true;
        prepare("图例", "");
        chartBox.setVisibility(View.GONE);
        legendRow.setVisibility(View.VISIBLE);
        open();
        loadLegend();
    }

    private void prepare(String title, String hint) {
        titleView.setText(title);
        hintView.setText(hint);
        legendRow.removeAllViews();
    }

    private void open() {
        box.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();
        panel.setTranslationY(dp(400));
        panel.animate().translationY(0f).setDuration(250)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    void hide() {
        if (box.getVisibility() != View.VISIBLE) return;
        ui.removeCallbacks(tick);
        scrim.animate().alpha(0f).setDuration(130).start();
        panel.animate().translationY(dp(400)).setDuration(200)
                .withEndAction(() -> box.setVisibility(View.GONE)).start();
    }

    boolean isOpen() {
        return box.getVisibility() == View.VISIBLE;
    }

    // ---------------- 数据 ----------------

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (!legendMode && web != null && box.getVisibility() == View.VISIBLE) {
                loadChart();
                ui.postDelayed(tick, 1000);
            }
        }
    };

    private void pollChart() {
        ui.removeCallbacks(tick);
        ui.post(tick);
    }

    private void loadChart() {
        web.evaluateJavascript(
                "(function(){function grab(id){var c=document.getElementById(id);"
                        + "if(!c)return '';try{return c.toDataURL('image/png');}catch(e){return '';}}"
                        + "return JSON.stringify({a:grab('chartCv'),b:grab('chartCv2')});})()",
                value -> {
                    String raw = unquote(value);
                    try {
                        org.json.JSONObject o = new org.json.JSONObject(raw);
                        renderCharts(o.optString("a"), o.optString("b"));
                    } catch (Exception ignored) {
                    }
                });
    }

    private void renderCharts(String a, String b) {
        String[] list = {a, b};
        chartBox.removeAllViews();
        for (String url : list) {
            if (url == null || !url.startsWith("data:image")) continue;
            try {
                String b64 = url.substring(url.indexOf(',') + 1);
                byte[] bytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT);
                android.graphics.Bitmap bmp =
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                if (bmp == null) continue;
                android.widget.ImageView iv = new android.widget.ImageView(act);
                iv.setImageBitmap(bmp);
                iv.setAdjustViewBounds(true);
                iv.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                lp.topMargin = dp(8);
                chartBox.addView(iv, lp);
            } catch (Exception ignored) {
            }
        }
        if (chartBox.getChildCount() == 0) {
            TextView t = new TextView(act);
            t.setText("曲线还在采样中，稍等片刻");
            t.setTextColor(0xFF6B7385);
            t.setTextSize(11.5f);
            t.setPadding(0, dp(30), 0, dp(30));
            t.setGravity(Gravity.CENTER);
            chartBox.addView(t);
        }
    }

    private void loadChartOld() {
        web.evaluateJavascript(
                "(function(){if(typeof chHist==='undefined'||!chHist)return '[]';"
                        + "return JSON.stringify(chHist.slice(-90));})()",
                value -> {
                    String s = unquote(value);
                    try {
                        JSONArray arr = new JSONArray(s);
                        int n = arr.length();
                        float[][] data = new float[SERIES_KEY.length][n];
                        for (int i = 0; i < n; i++) {
                            JSONObject o = arr.getJSONObject(i);
                            for (int k = 0; k < SERIES_KEY.length; k++) {
                                data[k][i] = (float) o.optDouble(SERIES_KEY[k], 0);
                            }
                        }
                        chart.setData(data, SERIES_COLOR);
                        buildChartLegend(data);
                    } catch (Exception ignored) {
                    }
                });
    }

    private void buildChartLegend(float[][] data) {
        legendRow.removeAllViews();
        LinearLayout line = new LinearLayout(act);
        line.setOrientation(LinearLayout.HORIZONTAL);
        for (int k = 0; k < SERIES_KEY.length; k++) {
            float last = data[k].length > 0 ? data[k][data[k].length - 1] : 0;
            LinearLayout item = new LinearLayout(act);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);

            View dot = new View(act);
            GradientDrawable dg = new GradientDrawable();
            dg.setShape(GradientDrawable.OVAL);
            dg.setColor(SERIES_COLOR[k]);
            dot.setBackground(dg);
            LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(dp(6), dp(6));
            dlp.rightMargin = dp(5);
            item.addView(dot, dlp);

            TextView t = new TextView(act);
            t.setText(SERIES_NAME[k] + " " + Math.round(last));
            t.setTextColor(0xFFA8B0C0);
            t.setTextSize(10.5f);
            item.addView(t);

            LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            line.addView(item, ilp);
        }
        legendRow.addView(line);
    }

    private void loadLegend() {
        web.evaluateJavascript(
                "(function(){var out=[];"
                        + "document.querySelectorAll('#legend > div').forEach(function(d){"
                        + "  var cs=getComputedStyle(d);"
                        + "  if(cs.display==='none'||cs.visibility==='hidden') return;"
                        + "  var i=d.querySelector('i');"
                        + "  var bg='',rad='';"
                        + "  if(i){var ics=getComputedStyle(i);bg=ics.backgroundColor;rad=ics.borderRadius;}"
                        + "  var cv=d.querySelector('canvas');var ic='';"
                        + "  if(cv){try{ic=cv.toDataURL('image/png');}catch(e){}}"
                        + "  out.push({t:d.textContent.trim(),c:bg,r:rad,ic:ic});"
                        + "});return JSON.stringify(out);})()",
                value -> {
                    legendRow.removeAllViews();
                    String s = unquote(value);
                    try {
                        JSONArray arr = new JSONArray(s);
                        for (int i = 0; i < arr.length(); i++) {
                            JSONObject o = arr.getJSONObject(i);
                            legendRow.addView(legendRowItem(o.optString("t"),
                                            o.optString("c"), o.optString("r"), o.optString("ic")),
                                    rowParams(i));
                        }
                    } catch (Exception ignored) {
                    }
                });
    }

    private LinearLayout.LayoutParams rowParams(int i) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (i > 0) lp.topMargin = dp(10);
        return lp;
    }

    private View legendRowItem(String text, String cssColor, String radius, String iconUrl) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        int size = dp(13);
        if (iconUrl != null && iconUrl.startsWith("data:image")) {
            try {
                byte[] bytes = android.util.Base64.decode(
                        iconUrl.substring(iconUrl.indexOf(',') + 1),
                        android.util.Base64.DEFAULT);
                android.graphics.Bitmap bmp =
                        android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                ImageView iv = new ImageView(act);
                iv.setImageBitmap(bmp);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(dp(16), dp(16));
                ilp.rightMargin = dp(8);
                row.addView(iv, ilp);
            } catch (Exception ignored) {
            }
            row.addView(label(text));
            return row;
        }

        View dot = new View(act);
        GradientDrawable dg = new GradientDrawable();
        float r = 3f;
        if (radius != null) {
            if (radius.contains("%")) {
                r = 7f;
            } else {
                String num = radius.replace("px", "").trim().split(" ")[0];
                try {
                    r = Math.max(1f, Float.parseFloat(num));
                } catch (Exception ignored) {
                }
            }
        }
        dg.setCornerRadius(dp(r));
        dg.setColor(parseColor(cssColor));
        dot.setBackground(dg);
        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(size, size);
        dlp.rightMargin = dp(10);
        row.addView(dot, dlp);

        row.addView(label(text));
        return row;
    }

    private TextView label(String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(0xFFC7CEDB);
        t.setTextSize(12.5f);
        return t;
    }

    private static int parseColor(String css) {
        if (css == null || css.isEmpty()) return 0x88FFFFFF;
        String s = css.trim();
        try {
            if (s.startsWith("#")) {
                if (s.length() == 7) return Color.parseColor(s);
                if (s.length() == 4) {
                    String r = "" + s.charAt(1) + s.charAt(1);
                    String g = "" + s.charAt(2) + s.charAt(2);
                    String b = "" + s.charAt(3) + s.charAt(3);
                    return Color.parseColor("#" + r + g + b);
                }
            }
            if (s.startsWith("rgb")) {
                String body = s.substring(s.indexOf('(') + 1, s.indexOf(')'));
                String[] p = body.split(",");
                return Color.rgb(Integer.parseInt(p[0].trim()),
                        Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
            }
        } catch (Exception ignored) {
        }
        return 0x88FFFFFF;
    }

    private static String unquote(String json) {
        if (json == null) return "[]";
        String s = json;
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() > 1) {
            s = s.substring(1, s.length() - 1)
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\");
        }
        return s;
    }

    // ---------------- 折线图 ----------------

    static class LineChart extends View {
        private float[][] data;
        private int[] colors;
        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);

        LineChart(Context c) {
            super(c);
            line.setStyle(Paint.Style.STROKE);
            line.setStrokeWidth(2.2f);
            grid.setStyle(Paint.Style.STROKE);
            grid.setStrokeWidth(1f);
            grid.setColor(0x1FFFFFFF);
        }

        void setData(float[][] d, int[] c) {
            this.data = d;
            this.colors = c;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth(), h = getHeight();
            float pad = 8f;

            for (int k = 1; k <= 3; k++) {
                float y = pad + (h - pad * 2) * k / 4f;
                canvas.drawLine(0, y, w, y, grid);
            }
            if (data == null || data.length == 0 || data[0].length < 2) return;

            float max = 1f;
            for (float[] s : data) for (float v : s) if (v > max) max = v;
            max *= 1.15f;

            int n = data[0].length;
            float stepX = w / (float) (n - 1);

            for (int s = 0; s < data.length; s++) {
                line.setColor(colors[s % colors.length]);
                Path p = new Path();
                for (int i = 0; i < n; i++) {
                    float x = i * stepX;
                    float y = pad + (h - pad * 2) * (1 - data[s][i] / max);
                    if (i == 0) p.moveTo(x, y);
                    else p.lineTo(x, y);
                }
                canvas.drawPath(p, line);
            }
        }
    }
}
