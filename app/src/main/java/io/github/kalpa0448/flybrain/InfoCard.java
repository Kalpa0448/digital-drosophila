package io.github.kalpa0448.flybrain;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** 养果蝇页右上角的「i」按钮 + 紧贴其下方的开源引用卡片。 */
class InfoCard {

    private final Activity act;
    private final FrameLayout root;

    private LinearLayout btn;
    private LinearLayout card;
    private boolean open = false;

    InfoCard(Activity activity, FrameLayout root) {
        this.act = activity;
        this.root = root;
        build();
    }

    private int dp(float v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    private void build() {
        // ---- 圆形 i 按钮 ----
        btn = new LinearLayout(act);
        btn.setGravity(Gravity.CENTER);
        btn.setBackground(circle(0x14FFFFFF, 0x33A0F0D0));
        btn.setOnClickListener(v -> toggle());

        TextView sym = new TextView(act);
        sym.setText("i");
        sym.setTextColor(0xFF9AA5B8);
        sym.setTextSize(15f);
        sym.setTypeface(Typeface.create("serif", Typeface.BOLD_ITALIC));
        btn.addView(sym);

        FrameLayout.LayoutParams blp = new FrameLayout.LayoutParams(dp(34), dp(34));
        blp.gravity = Gravity.TOP | Gravity.END;
        blp.topMargin = dp(72);
        blp.rightMargin = dp(16);
        root.addView(btn, blp);

        // ---- 引用卡片 ----
        card = new LinearLayout(act);
        card.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(14));
        bg.setColor(0xFF0E121A);
        bg.setStroke(dp(1), 0x33A0F0D0);
        card.setBackground(bg);
        card.setPadding(dp(14), dp(13), dp(14), dp(14));
        card.setElevation(dp(12));
        card.setVisibility(View.GONE);

        TextView title = new TextView(act);
        title.setText("开源引用与致谢");
        title.setTextColor(0xFFE8ECF4);
        title.setTextSize(12.5f);
        title.setLetterSpacing(0.04f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        card.addView(title);

        TextView lead = new TextView(act);
        lead.setText("养果蝇场景的神经群体模型、行为读出与参数体系参考自 fly-sim。");
        lead.setTextColor(0xFF96A1B2);
        lead.setTextSize(11.5f);
        lead.setLineSpacing(dp(3), 1f);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        llp.topMargin = dp(8);
        card.addView(lead, llp);

        row("项目", "果蝇 3D 模拟器（MIT）");
        row("仓库", "github.com/sjy20100819mc-cmyk/fly-sim");
        row("数据", "FlyEM 雄性 CNS v1.0 · Janelia / Cambridge / Google");
        row("渲染", "three.js r147（MIT）");
        row("判据", "flybench · Shiu et al. 2024");

        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(
                dp(282), ViewGroup.LayoutParams.WRAP_CONTENT);
        clp.gravity = Gravity.TOP | Gravity.END;
        clp.topMargin = dp(114);
        clp.rightMargin = dp(14);
        root.addView(card, clp);
    }

    private void row(String key, String value) {
        LinearLayout r = new LinearLayout(act);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.TOP);

        TextView k = new TextView(act);
        k.setText(key);
        k.setTextColor(0xFF5C6478);
        k.setTextSize(10.5f);
        r.addView(k, new LinearLayout.LayoutParams(dp(46),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView v = new TextView(act);
        v.setText(value);
        v.setTextColor(0xFFA9B3C4);
        v.setTextSize(11f);
        v.setLineSpacing(dp(3), 1f);
        r.addView(v, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        rlp.topMargin = dp(9);
        card.addView(r, rlp);
    }

    private GradientDrawable circle(int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(fill);
        g.setStroke(dp(1), stroke);
        return g;
    }

    void setVisible(boolean v) {
        btn.setVisibility(v ? View.VISIBLE : View.GONE);
        if (!v) hide();
    }

    private void toggle() {
        if (open) hide();
        else show();
    }

    private void show() {
        open = true;
        card.setVisibility(View.VISIBLE);
        card.setAlpha(0f);
        card.setTranslationY(-dp(8));
        card.animate().alpha(1f).translationY(0f).setDuration(150).start();
        btn.setBackground(circle(0x2234D399, 0x8C34D399));
        ((TextView) btn.getChildAt(0)).setTextColor(0xFF34D399);
        card.bringToFront();
        btn.bringToFront();
    }

    private void hide() {
        if (!open) return;
        open = false;
        card.animate().alpha(0f).translationY(-dp(8)).setDuration(120)
                .withEndAction(() -> card.setVisibility(View.GONE)).start();
        btn.setBackground(circle(0x14FFFFFF, 0x33A0F0D0));
        ((TextView) btn.getChildAt(0)).setTextColor(0xFF9AA5B8);
    }

    boolean isOpen() {
        return open;
    }

    /** 点在卡片与按钮之外时收起 */
    boolean handleOutsideTouch() {
        if (open) {
            hide();
            return true;
        }
        return false;
    }
}
