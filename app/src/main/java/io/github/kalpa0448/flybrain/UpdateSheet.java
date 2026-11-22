package io.github.kalpa0448.flybrain;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** 检查更新面板：版本比对、极简 Markdown 说明、下载与安装。 */
class UpdateSheet {

    private static final int ACCENT = 0xFF34D399;
    private static final int TEXT = 0xFFE8ECF4;
    private static final int DIM = 0xFF8A93A6;
    private static final int FAINT = 0xFF5C6478;

    private final Activity act;
    private final FrameLayout root;
    private final UpdateChecker checker = new UpdateChecker();

    private FrameLayout box;
    private View scrim;
    private LinearLayout panel;

    private TextView status;
    private LinearLayout notesBox;
    private ProgressBar bar;
    private TextView action;
    private TextView percent;

    private UpdateChecker.Result result;
    private File downloaded;
    private boolean open = false;

    UpdateSheet(Activity activity, FrameLayout root) {
        this.act = activity;
        this.root = root;
        build();
    }

    private int dp(float v) {
        return Math.round(v * act.getResources().getDisplayMetrics().density);
    }

    // ---------------- UI ----------------

    private void build() {
        box = new FrameLayout(act);
        box.setVisibility(View.GONE);
        box.setElevation(dp(72));

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
        bg.setStroke(dp(1), 0x26A0F0D0);
        panel.setBackground(bg);

        int h = (int) (act.getResources().getDisplayMetrics().heightPixels * 0.72f);
        FrameLayout.LayoutParams plp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, h);
        plp.gravity = Gravity.BOTTOM;
        box.addView(panel, plp);

        FrameLayout head = new FrameLayout(act);
        View handle = new View(act);
        GradientDrawable hg = new GradientDrawable();
        hg.setShape(GradientDrawable.RECTANGLE);
        hg.setCornerRadius(dp(2));
        hg.setColor(0x33FFFFFF);
        handle.setBackground(hg);
        FrameLayout.LayoutParams hlp = new FrameLayout.LayoutParams(dp(38), dp(4));
        hlp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        hlp.topMargin = dp(9);
        head.addView(handle, hlp);

        TextView close = new TextView(act);
        close.setText("✕");
        close.setTextColor(0xFFB9C2D0);
        close.setTextSize(14f);
        close.setGravity(Gravity.CENTER);
        GradientDrawable cbg = new GradientDrawable();
        cbg.setShape(GradientDrawable.OVAL);
        cbg.setColor(0x14FFFFFF);
        cbg.setStroke(dp(1), 0x14FFFFFF);
        close.setBackground(cbg);
        close.setOnClickListener(v -> hide());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(dp(34), dp(34));
        clp.gravity = Gravity.TOP | Gravity.END;
        clp.topMargin = dp(14);
        clp.rightMargin = dp(16);
        head.addView(close, clp);

        panel.addView(head, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(58)));

        LinearLayout titleBox = new LinearLayout(act);
        titleBox.setOrientation(LinearLayout.HORIZONTAL);
        titleBox.setGravity(Gravity.CENTER_VERTICAL);
        titleBox.setPadding(dp(20), 0, dp(20), 0);

        TextView title = new TextView(act);
        title.setText("检查更新");
        title.setTextColor(TEXT);
        title.setTextSize(16f);
        title.setLetterSpacing(0.06f);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titleBox.addView(title);

        status = new TextView(act);
        status.setText("");
        status.setTextColor(FAINT);
        status.setTextSize(11f);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        slp.leftMargin = dp(10);
        status.setGravity(Gravity.END);
        titleBox.addView(status, slp);
        panel.addView(titleBox);

        // 下载进度
        LinearLayout progBox = new LinearLayout(act);
        progBox.setOrientation(LinearLayout.VERTICAL);
        progBox.setPadding(dp(20), dp(12), dp(20), 0);
        progBox.setVisibility(View.GONE);

        bar = new ProgressBar(act, null, android.R.attr.progressBarStyleHorizontal);
        bar.setMax(100);
        bar.setProgressTintList(android.content.res.ColorStateList.valueOf(ACCENT));
        bar.setProgressBackgroundTintList(android.content.res.ColorStateList.valueOf(0x33FFFFFF));
        bar.setMinimumHeight(dp(4));
        progBox.addView(bar, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(4)));

        percent = new TextView(act);
        percent.setTextColor(ACCENT);
        percent.setTextSize(10.5f);
        percent.setGravity(Gravity.END);
        LinearLayout.LayoutParams plp2 = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        plp2.topMargin = dp(6);
        progBox.addView(percent, plp2);
        panel.addView(progBox);
        this.progBox = progBox;

        ScrollView sv = new ScrollView(act);
        sv.setVerticalScrollBarEnabled(false);
        notesBox = new LinearLayout(act);
        notesBox.setOrientation(LinearLayout.VERTICAL);
        notesBox.setPadding(dp(20), dp(14), dp(20), dp(10));
        sv.addView(notesBox, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(sv, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        action = new TextView(act);
        action.setText("检查更新");
        action.setTextColor(0xFF06281E);
        action.setTextSize(13.5f);
        action.setGravity(Gravity.CENTER);
        action.setBackground(round(ACCENT, 16));
        action.setOnClickListener(v -> onAction());
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        alp.setMargins(dp(20), dp(8), dp(20), dp(18));
        panel.addView(action, alp);

        root.addView(box, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
    }

    private LinearLayout progBox;

    private GradientDrawable round(int color, float rDp) {
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.RECTANGLE);
        g.setCornerRadius(dp(rDp));
        g.setColor(color);
        return g;
    }

    // ---------------- 流程 ----------------

    void show() {
        open = true;
        box.setVisibility(View.VISIBLE);
        box.bringToFront();
        scrim.setAlpha(0f);
        scrim.animate().alpha(1f).setDuration(160).start();
        panel.setTranslationY(dp(600));
        panel.animate().translationY(0f).setDuration(260)
                .setInterpolator(new DecelerateInterpolator()).start();
        startCheck(false);
    }

    void hide() {
        if (!open) return;
        open = false;
        scrim.animate().alpha(0f).setDuration(130).start();
        panel.animate().translationY(dp(600)).setDuration(200)
                .withEndAction(() -> box.setVisibility(View.GONE)).start();
    }

    boolean isOpen() {
        return open;
    }

    /** silent = 启动时的后台检查：只在发现新版时提示，不打断 */
    void startCheck(boolean silent) {
        this.silent = silent;
        this.downloaded = null;
        notesBox.removeAllViews();
        progBox.setVisibility(View.GONE);
        status.setText("检查中…");
        action.setText("检查更新");
        action.setBackground(round(0x1F34D399, 16));
        action.setTextColor(ACCENT);
        addLine("正在连接 GitHub…", DIM, 12f, false);
        checker.check(r -> {
            result = r;
            if (!r.ok) {
                status.setText("检查失败");
                notesBox.removeAllViews();
                addLine("无法获取更新信息", DIM, 12.5f, false);
                addLine(r.error == null ? "网络不可用" : r.error, FAINT, 11f, false);
                action.setText("重试");
                action.setBackground(round(ACCENT, 16));
                action.setTextColor(0xFF06281E);
                return;
            }
            if (!r.hasUpdate) {
                status.setText("已是最新");
                notesBox.removeAllViews();
                addLine("当前版本 v" + BuildConfig.VERSION_NAME, TEXT, 13f, true);
                addLine("GitHub 最新版本 " + r.latestTag, DIM, 11.5f, false);
                addLine("已在最新版本，无需更新。", FAINT, 11.5f, false);
                action.setText("关闭");
                action.setBackground(round(0x14FFFFFF, 16));
                action.setTextColor(0xFFB9C2D0);
                return;
            }
            status.setText("发现新版本");
            status.setTextColor(ACCENT);
            notesBox.removeAllViews();
            addLine("v" + BuildConfig.VERSION_NAME + "  →  " + r.latestTag, TEXT, 13f, true);
            if (r.apkSize > 0) {
                addLine(String.format("安装包 %.1f MB", r.apkSize / 1048576f), FAINT, 11f, false);
            }
            View sep = new View(act);
            sep.setBackgroundColor(0x14FFFFFF);
            LinearLayout.LayoutParams sepLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(1));
            sepLp.topMargin = dp(10);
            sepLp.bottomMargin = dp(4);
            notesBox.addView(sep, sepLp);
            renderNotes(r.notes);
            action.setText(r.apkUrl == null || r.apkUrl.isEmpty() ? "前往下载页" : "下载并安装");
            action.setBackground(round(ACCENT, 16));
            action.setTextColor(0xFF06281E);
        });
    }

    private boolean silent = false;

    private void onAction() {
        if (result == null || !result.ok) {
            startCheck(false);
            return;
        }
        if (!result.hasUpdate) {
            hide();
            return;
        }
        if (downloaded != null) {
            install(downloaded);
            return;
        }
        if (result.apkUrl == null || result.apkUrl.isEmpty()) {
            try {
                act.startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://github.com/Kalpa0448/digital-drosophila/releases")));
            } catch (Exception ignored) {
            }
            return;
        }
        startDownload();
    }

    private void startDownload() {
        progBox.setVisibility(View.VISIBLE);
        bar.setProgress(0);
        percent.setText("0%");
        action.setEnabled(false);
        action.setText("下载中…");
        checker.download(act, result, new UpdateChecker.DownloadCb() {
            @Override
            public void onProgress(int pct) {
                bar.setProgress(pct);
                percent.setText(pct + "%");
            }

            @Override
            public void onDone(File apk) {
                downloaded = apk;
                progBox.setVisibility(View.GONE);
                action.setEnabled(true);
                action.setText("安装");
                status.setText("下载完成");
                status.setTextColor(ACCENT);
            }

            @Override
            public void onError(String msg) {
                progBox.setVisibility(View.GONE);
                action.setEnabled(true);
                action.setText("重试下载");
                status.setText("下载失败");
                status.setTextColor(0xFFFF6B6B);
                addLine(msg, 0xFFFF8A8A, 11f, false);
            }
        });
    }

    private void install(File apk) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && !act.getPackageManager().canRequestPackageInstalls()) {
            // 首次需要用户允许"安装未知应用"
            addLine("请允许本应用安装未知应用后返回重试", 0xFFFFC857, 11.5f, false);
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                        Uri.parse("package:" + act.getPackageName()));
                act.startActivity(i);
            } catch (Exception ignored) {
            }
            return;
        }
        try {
            Uri uri = ApkProvider.uriFor(apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_ACTIVITY_NEW_TASK);
            act.startActivity(i);
        } catch (Exception e) {
            addLine("无法调起安装器：" + e.getMessage(), 0xFFFF8A8A, 11f, false);
        }
    }

    // ---------------- 极简 Markdown ----------------

    private void addLine(String text, int color, float sizeSp, boolean bold) {
        TextView t = new TextView(act);
        t.setText(inline(text));
        t.setTextColor(color);
        t.setTextSize(sizeSp);
        t.setLineSpacing(dp(3), 1f);
        if (bold) t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(notesBox.getChildCount() == 0 ? 0 : 7);
        notesBox.addView(t, lp);
    }

    private void renderNotes(String md) {
        if (md == null || md.trim().isEmpty()) {
            addLine("（该版本未提供更新说明）", FAINT, 11.5f, false);
            return;
        }
        boolean inCode = false;
        for (String raw : md.replace("\r", "").split("\n")) {
            String line = raw.trim();
            if (line.startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (inCode) {
                if (!line.isEmpty()) addCode(line);
                continue;
            }
            if (line.isEmpty()) {
                View gap = new View(act);
                LinearLayout.LayoutParams glp = new LinearLayout.LayoutParams(1, dp(4));
                notesBox.addView(gap, glp);
                continue;
            }
            if (line.startsWith("### ") || line.startsWith("## ") || line.startsWith("# ")) {
                addHeading(line.replaceFirst("^#+\\s*", ""));
            } else if (line.startsWith("- ") || line.startsWith("* ")) {
                addBullet(line.substring(2));
            } else if (line.matches("^\\d+[.)]\\s+.*")) {
                addLine(line, 0xFFAFB8C7, 11.5f, false);
            } else if (line.startsWith("> ")) {
                addLine(line.substring(2), DIM, 11.5f, false);
            } else {
                addLine(line, 0xFFAFB8C7, 11.5f, false);
            }
        }
    }

    private void addHeading(String text) {
        TextView t = new TextView(act);
        t.setText(inline(text));
        t.setTextColor(TEXT);
        t.setTextSize(12.5f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(notesBox.getChildCount() == 0 ? 0 : 12);
        notesBox.addView(t, lp);
    }

    private void addBullet(String text) {
        LinearLayout row = new LinearLayout(act);
        row.setOrientation(LinearLayout.HORIZONTAL);

        TextView dot = new TextView(act);
        dot.setText("·");
        dot.setTextColor(ACCENT);
        dot.setTextSize(12f);
        row.addView(dot, new LinearLayout.LayoutParams(dp(10),
                ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView t = new TextView(act);
        t.setText(inline(text));
        t.setTextColor(0xFFAFB8C7);
        t.setTextSize(11.5f);
        t.setLineSpacing(dp(3), 1f);
        row.addView(t, new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(5);
        notesBox.addView(row, lp);
    }

    private void addCode(String text) {
        TextView t = new TextView(act);
        t.setText(text);
        t.setTextColor(0xFF9FE8C8);
        t.setTextSize(10.5f);
        t.setTypeface(Typeface.MONOSPACE);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(6));
        bg.setColor(0x14FFFFFF);
        t.setBackground(bg);
        t.setPadding(dp(8), dp(5), dp(8), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(4);
        notesBox.addView(t, lp);
    }

    /** 处理 **粗体**、`代码`、[文字](链接) */
    private CharSequence inline(String s) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        int i = 0;
        while (i < s.length()) {
            if (s.startsWith("**", i)) {
                int end = s.indexOf("**", i + 2);
                if (end > 0) {
                    int st = sb.length();
                    sb.append(s, i + 2, end);
                    sb.setSpan(new StyleSpan(Typeface.BOLD), st, sb.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 2;
                    continue;
                }
            }
            if (s.charAt(i) == '`') {
                int end = s.indexOf('`', i + 1);
                if (end > 0) {
                    int st = sb.length();
                    sb.append(s, i + 1, end);
                    sb.setSpan(new TypefaceSpan("monospace"), st, sb.length(),
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    i = end + 1;
                    continue;
                }
            }
            if (s.charAt(i) == '[') {
                int mid = s.indexOf("](", i);
                int end = mid > 0 ? s.indexOf(')', mid) : -1;
                if (mid > 0 && end > 0) {
                    sb.append(s, i + 1, mid);
                    i = end + 1;
                    continue;
                }
            }
            sb.append(s.charAt(i));
            i++;
        }
        return sb;
    }
}
