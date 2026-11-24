package vn.automes.oqc;

import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Base64;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.ConsoleMessage;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Cửa sổ "Xem dữ liệu": nạp assets/index.html, đọc/ghi thư mục chia sẻ qua SMB bằng cầu nối AndroidBridge. */
public class MainActivity extends AppCompatActivity {
    private static final String HOST = "appassets.androidplatform.net";
    private static final String START_URL = "https://" + HOST + "/index.html";
    private static final int REQ_SAVE = 4021;
    private static final Charset UTF8 = Charset.forName("UTF-8");

    private WebView web;
    private Config cfg;
    private volatile SmbIo io;
    private String cfgSig = null;
    private boolean askedSettings = false;
    private byte[] pendingSave;
    private final ExecutorService pool = Executors.newFixedThreadPool(4);

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        setTitle("Auto Mes OQC - Xem dữ liệu");
        web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setSupportZoom(false);

        web.addJavascriptInterface(new Bridge(), "AndroidBridge");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (u != null && HOST.equals(u.getHost())) {
                    String p = u.getPath();
                    if (p == null || p.equals("/") || p.equals("/index.html")) return indexResponse();
                    return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", null,
                            new ByteArrayInputStream(new byte[0]));
                }
                return null;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                return u == null || !HOST.equals(u.getHost()); // không cho trang chuyển sang web ngoài
            }
        });
        web.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onJsAlert(WebView v, String url, String msg, final JsResult res) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setCancelable(false)
                        .setPositiveButton("OK", (d, w) -> res.confirm()).show();
                return true;
            }

            @Override
            public boolean onJsConfirm(WebView v, String url, String msg, final JsResult res) {
                new AlertDialog.Builder(MainActivity.this).setMessage(msg).setCancelable(false)
                        .setPositiveButton("OK", (d, w) -> res.confirm())
                        .setNegativeButton("Hủy", (d, w) -> res.cancel()).show();
                return true;
            }

            @Override
            public boolean onConsoleMessage(ConsoleMessage m) { return true; }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        cfg = Config.load(this);
        String sig = cfg.folder + "|" + cfg.user + "|" + cfg.pass + "|" + cfg.domain + "|" + cfg.machine;
        if (sig.equals(cfgSig)) return; // không đổi gì -> giữ nguyên trang đang xem
        cfgSig = sig;

        SmbIo old = io;
        io = null;
        if (old != null) old.close();

        if (!cfg.ready()) {
            web.loadData("<html><body style='font-family:sans-serif;padding:24px'><h3>Chưa có thư mục lưu</h3>"
                    + "<p>Bấm menu ⋮ → <b>Cài đặt</b> để nhập thư mục chia sẻ (ví dụ \\\\MAY-KHO\\OQC).</p></body></html>",
                    "text/html; charset=utf-8", "utf-8");
            if (!askedSettings) { askedSettings = true; startActivity(new Intent(this, SettingsActivity.class)); }
            return;
        }
        try {
            io = new SmbIo(cfg);
        } catch (Exception e) {
            Toast.makeText(this, "Lỗi cài đặt: " + e.getMessage(), Toast.LENGTH_LONG).show();
            return;
        }
        web.loadUrl(START_URL);
    }

    @Override
    protected void onDestroy() {
        pool.shutdownNow();
        SmbIo cur = io;
        if (cur != null) cur.close();
        if (web != null) { web.removeJavascriptInterface("AndroidBridge"); web.destroy(); }
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack(); else super.onBackPressed();
    }

    // ------------------------------------------------------------ menu
    @Override
    public boolean onCreateOptionsMenu(Menu m) {
        m.add(0, 1, 0, "Tải lại");
        m.add(0, 2, 1, "Cài đặt");
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem it) {
        if (it.getItemId() == 1) { if (io != null) web.loadUrl(START_URL); return true; }
        if (it.getItemId() == 2) { startActivity(new Intent(this, SettingsActivity.class)); return true; }
        return super.onOptionsItemSelected(it);
    }

    // ------------------------------------------------------------ nạp index.html + chèn shim
    private WebResourceResponse indexResponse() {
        try {
            String html = readAsset("index.html");
            String shim = readAsset("am-shim.js")
                    .replace("__FOLDER__", JSONObject.quote(cfg == null ? "" : cfg.folder))
                    .replace("__MACHINE__", JSONObject.quote(cfg == null ? "" : cfg.machine));
            String tag = "<script>" + shim + "</script>";
            int i = html.indexOf("<head>");
            html = i >= 0 ? html.substring(0, i + 6) + tag + html.substring(i + 6) : tag + html;
            WebResourceResponse r = new WebResourceResponse("text/html", "UTF-8", new ByteArrayInputStream(html.getBytes(UTF8)));
            return r;
        } catch (Exception e) {
            return new WebResourceResponse("text/plain", "utf-8", 500, "Error", null,
                    new ByteArrayInputStream(String.valueOf(e.getMessage()).getBytes(UTF8)));
        }
    }

    private String readAsset(String name) throws Exception {
        try (InputStream in = getAssets().open(name)) {
            java.io.ByteArrayOutputStream bo = new java.io.ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            return new String(bo.toByteArray(), UTF8);
        }
    }

    // ------------------------------------------------------------ lưu file tải từ trang (CSV / Excel)
    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_SAVE) return;
        byte[] bytes = pendingSave;
        pendingSave = null;
        if (res != RESULT_OK || data == null || data.getData() == null || bytes == null) return;
        try (OutputStream out = getContentResolver().openOutputStream(data.getData())) {
            out.write(bytes);
            Toast.makeText(this, "Đã lưu file", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            Toast.makeText(this, "Không lưu được: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    // ------------------------------------------------------------ cầu nối JS <-> Java
    private final class Bridge {
        @JavascriptInterface
        public void post(final String raw) {
            pool.execute(() -> handle(raw));
        }

        @JavascriptInterface
        public void saveFile(final String name, final String mime, final String b64) {
            runOnUiThread(() -> {
                try {
                    pendingSave = Base64.decode(b64, Base64.DEFAULT);
                    Intent i = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    i.addCategory(Intent.CATEGORY_OPENABLE);
                    i.setType(mime == null || mime.isEmpty() ? "application/octet-stream" : mime);
                    i.putExtra(Intent.EXTRA_TITLE, name == null || name.isEmpty() ? "file" : name);
                    startActivityForResult(i, REQ_SAVE);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, "Không lưu được: " + e.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    private void handle(String raw) {
        JSONObject r = new JSONObject();
        Object id = null;
        try {
            JSONObject m = new JSONObject(raw);
            id = m.get("id");
            r.put("id", id);
            String op = m.optString("op", "");
            JSONObject a = m.optJSONObject("args");
            if (a == null) a = new JSONObject();
            SmbIo cur = io;
            if (cur == null) throw new java.io.IOException("chưa nhập thư mục lưu");
            Object res;
            switch (op) {
                case "list": {
                    JSONArray arr = new JSONArray();
                    List<SmbIo.Entry> es = cur.list(a.optString("dir", ""));
                    for (SmbIo.Entry e : es) {
                        JSONObject o = new JSONObject();
                        o.put("n", e.name); o.put("m", e.modified); o.put("s", e.size);
                        arr.put(o);
                    }
                    res = arr;
                    break;
                }
                case "read": res = cur.read(a.optString("path", "")); break;
                case "write": res = cur.write(a.optString("path", ""), a.optString("text", "")); break;
                case "createNew": res = cur.createNew(a.optString("path", ""), a.optString("text", "")); break;
                case "del": res = cur.del(a.optString("path", "")); break;
                default: throw new java.io.IOException("lệnh không hỗ trợ");
            }
            r.put("ok", true);
            r.put("result", res == null ? JSONObject.NULL : res);
        } catch (Exception e) {
            try {
                if (id != null) r.put("id", id);
                r.put("ok", false);
                String msg = e.getMessage();
                r.put("error", msg == null || msg.isEmpty() ? e.getClass().getSimpleName() : msg);
            } catch (Exception ignored) { }
        }
        if (!r.has("id")) return;
        final String js = "window.__amRecv(" + JSONObject.quote(r.toString()) + ")";
        runOnUiThread(() -> { if (web != null) web.evaluateJavascript(js, null); });
    }
}
