package io.github.kalpa0448.flybrain;

import android.app.Activity;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/** 应用内的选项面板：底部弹出、圆角、带说明的列表。 */
class ChoiceSheet {

    static class Option {
        final String label;
        final String hint;
        final Runnable action;

        Option(String label, String hint, Runnable action) {
            this.label = label;
            this.hint = hint;
            this.action = action;
        }
    }

    private final Activity act;
    private final FrameLayout root;

    private FrameLayout box;
    private View scrim;
    private LinearLayout panel;
    private LinearLayout list;
    private TextView titleView;
    private String title;

    ChoiceSheet(Activity activity, FrameLayout root, String title) {
        this.act = activity;
        this.root = root;
        this.title = title;
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
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadii(new float[]{dp(26), dp(26), dp(26), dp(26), 0, 0, 0, 0});
        bg.setColor(0xFF10141C);
        bg.setStroke(dp(1), 0x1FA0F0D0);
        panel.setBackground(bg);
        panel.setPadding(dp(6), dp(8), dp(6), dp(18));

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
        hlp.bottomMargin = dp(10);
        panel.addView(handle, hlp);

        titleView = new TextView(act);
        titleView.setText(title);
        titleView.setTextColor(0xFF8A93A6);
        titleView.setTextSize(10.5f);
        titleView.setLetterSpacing(0.22f);
        titleView.setPadding(dp(20), 0, dp(20), dp(10));
        panel.addView(titleView);

        list = new LinearLayout(act);
        list.setOrientation(LinearLayout.VERTICAL);
        panel.addView(list);

        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    void show(String sheetTitle, List<Option> options, int checkedIndex) {
        titleView.setText(sheetTitle);
        list.removeAllViews();
        for (int i = 0; i < options.size(); i++) {
            list.addView(row(options.get(i), i == checkedIndex), params(i));
        }
        box.setVisibility(View.VISIBLE);
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();
        panel.setTranslationY(dp(320));
        panel.animate().translationY(0f).setDuration(240)
                .setInterpolator(new DecelerateInterpolator()).start();
    }

    void hide() {
        if (box.getVisibility() != View.VISIBLE) return;
        scrim.animate().alpha(0f).setDuration(130).start();
        panel.animate().translationY(dp(320)).setDuration(180)
                .withEndAction(() -> box.setVisibility(View.GONE)).start();
    }

    boolean isOpen() {
        return box.getVisibility() == View.VISIBLE;
    }

    private LinearLayout.LayoutParams params(int i) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (i > 0) lp.topMargin = dp(4);
        lp.leftMargin = dp(8);
        lp.rightMargin = dp(8);
        return lp;
    }

    private View row(final Option o, boolean checked) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(16), dp(15), dp(16), dp(15));
        row.setClickable(true);
        row.setBackground(checked ? solid(0x2634D399, 16) : solid(0x0FFFFFFF, 16));

        LinearLayout text = new LinearLayout(act);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(act);
        t.setText(o.label);
        t.setTextColor(checked ? 0xFF34D399 : 0xFFE8ECF4);
        t.setTextSize(14.5f);
        text.addView(t);
        if (o.hint != null) {
            TextView h = new TextView(act);
            h.setText(o.hint);
            h.setTextColor(0xFF6B7385);
            h.setTextSize(10.5f);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.topMargin = dp(3);
            text.addView(h, hp);
        }
        row.addView(text, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        if (checked) {
            TextView mark = new TextView(act);
            mark.setText("✓");
            mark.setTextColor(0xFF34D399);
            mark.setTextSize(15f);
            row.addView(mark);
        }

        row.setOnClickListener(v -> {
            v.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            hide();
            if (o.action != null) o.action.run();
        });
        return row;
    }

    private GradientDrawable solid(int color, float radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(radiusDp));
        g.setColor(color);
        return g;
    }
}
