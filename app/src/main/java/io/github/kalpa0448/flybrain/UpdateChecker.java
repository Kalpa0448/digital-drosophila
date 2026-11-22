package io.github.kalpa0448.flybrain;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

/** GitHub Release 检查与 APK 下载。无第三方依赖，全部走 HttpURLConnection。 */
class UpdateChecker {

    private static final String API =
            "https://api.github.com/repos/Kalpa0448/digital-drosophila/releases/latest";
    private static final String UA = "DigitalDrosophila-App";

    interface CheckCb {
        void onResult(Result r);
    }

    interface ProgressCb {
        void onProgress(int percent, long done, long total);
    }

    interface DownloadCb {
        void onDone(File apk);

        void onError(String msg);

        void onProgress(int percent);
    }

    static class Result {
        boolean ok;          // 请求本身是否成功
        boolean hasUpdate;   // 是否有更新
        String latestTag;    // v1.45.0
        String latestName;
        String notes;
        String apkUrl;
        long apkSize;
        String error;
        boolean checked = true;
    }

    private final Handler ui = new Handler(Looper.getMainLooper());

    // ---------------- 检查 ----------------

    void check(final CheckCb cb) {
        new Thread(() -> {
            Result r = new Result();
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(API).openConnection();
                c.setConnectTimeout(8000);
                c.setReadTimeout(12000);
                c.setRequestProperty("User-Agent", UA);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                int code = c.getResponseCode();
                if (code != 200) {
                    r.error = "GitHub 返回 " + code;
                    ui.post(() -> cb.onResult(r));
                    return;
                }
                StringBuilder sb = new StringBuilder();
                try (BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"))) {
                    String line;
                    while ((line = br.readLine()) != null) sb.append(line);
                }
                JSONObject o = new JSONObject(sb.toString());
                r.latestTag = o.optString("tag_name", "");
                r.latestName = o.optString("name", r.latestTag);
                r.notes = o.optString("body", "");
                JSONArray assets = o.optJSONArray("assets");
                if (assets != null) {
                    for (int i = 0; i < assets.length(); i++) {
                        JSONObject a = assets.getJSONObject(i);
                        String n = a.optString("name", "");
                        if (n.endsWith(".apk")) {
                            r.apkUrl = a.optString("browser_download_url", "");
                            r.apkSize = a.optLong("size", 0);
                            break;
                        }
                    }
                }
                r.ok = true;
                r.hasUpdate = isNewer(r.latestTag, BuildConfig.VERSION_NAME);
                if (!r.hasUpdate) r.notes = "";   // 已是最新就不展示旧说明
                ui.post(() -> cb.onResult(r));
            } catch (Exception e) {
                r.error = e.getClass().getSimpleName() + ": " + e.getMessage();
                ui.post(() -> cb.onResult(r));
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }

    /** v1.45.0 与 1.44.0 比较，只看数字段 */
    static boolean isNewer(String tag, String current) {
        int[] a = nums(tag);
        int[] b = nums(current);
        for (int i = 0; i < Math.max(a.length, b.length); i++) {
            int x = i < a.length ? a[i] : 0;
            int y = i < b.length ? b[i] : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int[] nums(String s) {
        if (s == null) return new int[0];
        String[] parts = s.replaceAll("[^0-9.]", "").split("\\.");
        int n = 0;
        for (String p : parts) if (!p.isEmpty()) n++;
        int[] out = new int[n];
        int k = 0;
        for (String p : parts) {
            if (p.isEmpty()) continue;
            try {
                out[k++] = Integer.parseInt(p);
            } catch (Exception e) {
                out[k++] = 0;
            }
        }
        return out;
    }

    // ---------------- 下载 ----------------

    File apkFile(android.content.Context ctx, String tag) {
        File dir = new File(ctx.getCacheDir(), "update");
        if (!dir.exists()) dir.mkdirs();
        String safe = tag == null ? "latest" : tag.replaceAll("[^0-9A-Za-z._-]", "");
        return new File(dir, "drosophila-" + safe + ".apk");
    }

    void download(final android.content.Context ctx, final Result r, final DownloadCb cb) {
        new Thread(() -> {
            HttpURLConnection c = null;
            File out = apkFile(ctx, r.latestTag);
            try {
                if (out.exists() && r.apkSize > 0 && out.length() == r.apkSize) {
                    ui.post(() -> cb.onDone(out));
                    return;
                }
                String url = r.apkUrl;
                c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(true);
                c.setConnectTimeout(10000);
                c.setReadTimeout(30000);
                c.setRequestProperty("User-Agent", UA);
                int code = c.getResponseCode();
                if (code != 200) {
                    ui.post(() -> cb.onError("下载失败 HTTP " + code));
                    return;
                }
                long total = c.getContentLengthLong();
                if (total <= 0) total = r.apkSize;
                InputStream in = c.getInputStream();
                File tmp = new File(out.getPath() + ".part");
                FileOutputStream fos = new FileOutputStream(tmp);
                byte[] buf = new byte[64 * 1024];
                long done = 0;
                int lastPct = -1;
                int n;
                while ((n = in.read(buf)) > 0) {
                    fos.write(buf, 0, n);
                    done += n;
                    if (total > 0) {
                        int pct = (int) (done * 100 / total);
                        if (pct != lastPct) {
                            lastPct = pct;
                            final int p = pct;
                            ui.post(() -> cb.onProgress(p));
                        }
                    }
                }
                fos.getFD().sync();
                fos.close();
                in.close();
                if (out.exists()) out.delete();
                if (!tmp.renameTo(out)) {
                    ui.post(() -> cb.onError("写入文件失败"));
                    return;
                }
                ui.post(() -> cb.onDone(out));
            } catch (Exception e) {
                ui.post(() -> cb.onError(e.getClass().getSimpleName() + ": " + e.getMessage()));
            } finally {
                if (c != null) c.disconnect();
            }
        }).start();
    }
}
