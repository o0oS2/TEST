package vn.automes.oqc;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.charset.Charset;
import java.util.Properties;
import java.util.UUID;

import jcifs.CIFSContext;
import jcifs.config.PropertyConfiguration;
import jcifs.context.BaseContext;
import jcifs.smb.NtlmPasswordAuthenticator;
import jcifs.smb.SmbFile;
import jcifs.smb.SmbFileInputStream;
import jcifs.smb.SmbFileOutputStream;

/** Đọc/ghi thư mục chia sẻ Windows qua SMB. Cùng 5 thao tác với FolderIo trong AutoMesOQC.exe. */
final class SmbIo {
    private static final Charset UTF8 = Charset.forName("UTF-8");

    static final class Entry { String name; long modified; long size; }

    private final String root;
    private final CIFSContext ctx;

    SmbIo(Config c) throws Exception {
        root = Config.normalize(c.folder);
        if (root.isEmpty()) throw new IOException("chưa nhập thư mục lưu");
        Properties p = new Properties();
        p.setProperty("jcifs.smb.client.minVersion", "SMB202");
        p.setProperty("jcifs.smb.client.maxVersion", "SMB311");
        p.setProperty("jcifs.resolveOrder", "DNS,BCAST");
        p.setProperty("jcifs.smb.client.connTimeout", "10000");
        p.setProperty("jcifs.smb.client.responseTimeout", "20000");
        p.setProperty("jcifs.smb.client.soTimeout", "30000");
        CIFSContext base = new BaseContext(new PropertyConfiguration(p));
        if (c.user == null || c.user.trim().isEmpty()) {
            ctx = base.withGuestCredentials();
        } else {
            ctx = base.withCredentials(new NtlmPasswordAuthenticator(
                    c.domain == null ? "" : c.domain.trim(), c.user.trim(), c.pass == null ? "" : c.pass));
        }
    }

    void close() { try { ctx.close(); } catch (Exception ignored) { } }

    // ------------------------------------------------------------ đường dẫn
    private String url(String rel, boolean dir) throws IOException {
        String r = rel == null ? "" : rel.replace('\\', '/');
        for (String seg : r.split("/")) if (seg.equals("..")) throw new IOException("đường dẫn ngoài thư mục lưu");
        while (r.startsWith("/")) r = r.substring(1);
        String u = root + r;
        if (dir && !u.endsWith("/")) u += "/";
        return u;
    }

    private static String parentOf(String fileUrl) {
        int i = fileUrl.lastIndexOf('/');
        return fileUrl.substring(0, i + 1);
    }

    private void ensureDir(String dirUrl) throws IOException {
        SmbFile d = new SmbFile(dirUrl, ctx);
        if (!d.exists()) d.mkdirs();
    }

    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException ignored) { } }

    // ------------------------------------------------------------ thao tác
    java.util.List<Entry> list(String dir) throws IOException {
        java.util.List<Entry> res = new java.util.ArrayList<>();
        SmbFile d = new SmbFile(url(dir, true), ctx);
        if (!d.exists()) return res;
        SmbFile[] fs = d.listFiles();
        if (fs == null) return res;
        for (SmbFile f : fs) {
            String n = f.getName();
            if (n == null || n.endsWith("/")) continue;                 // bỏ thư mục con
            if (n.toLowerCase().endsWith(".tmp")) continue;             // bỏ file tạm đang ghi dở
            Entry e = new Entry();
            e.name = n; e.modified = f.getLastModified(); e.size = f.length();
            res.add(e);
        }
        return res;
    }

    String read(String rel) throws IOException {
        String u = url(rel, false);
        IOException last = null;
        for (int i = 0; i < 4; i++) {
            try {
                SmbFile f = new SmbFile(u, ctx);
                if (!f.exists()) return null;
                try (InputStream in = new SmbFileInputStream(f)) {
                    ByteArrayOutputStream bo = new ByteArrayOutputStream();
                    byte[] buf = new byte[32768];
                    int n;
                    while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
                    String s = new String(bo.toByteArray(), UTF8);
                    return s.startsWith("\uFEFF") ? s.substring(1) : s;
                }
            } catch (IOException ex) { last = ex; sleep(25L * (i + 1)); }
        }
        if (last != null && !String.valueOf(last.getMessage()).toLowerCase().contains("not found")) throw last;
        return null;
    }

    // ghi qua file tạm rồi đổi tên: máy khác không bao giờ đọc phải file ghi dở
    boolean write(String rel, String text) throws IOException {
        String full = url(rel, false);
        ensureDir(parentOf(full));
        String tmpUrl = full + "." + UUID.randomUUID().toString().replace("-", "").substring(0, 8) + ".tmp";
        IOException last = null;
        for (int i = 0; i < 6; i++) {
            try {
                SmbFile tmp = new SmbFile(tmpUrl, ctx);
                try (OutputStream out = new SmbFileOutputStream(tmp)) {
                    out.write(text == null ? new byte[0] : text.getBytes(UTF8));
                }
                renameReplace(tmp, new SmbFile(full, ctx));
                return true;
            } catch (IOException ex) { last = ex; sleep(30L * (i + 1)); }
        }
        try { SmbFile t = new SmbFile(tmpUrl, ctx); if (t.exists()) t.delete(); } catch (Exception ignored) { }
        throw new IOException(last != null ? last.getMessage() : "không ghi được file");
    }

    /** Đổi tên đè lên file cũ. Ưu tiên rename(dest, replace=true) của jcifs-ng; không có thì xóa đích rồi đổi tên. */
    private static void renameReplace(SmbFile tmp, SmbFile dest) throws IOException {
        try {
            Method m = SmbFile.class.getMethod("rename", Class.forName("jcifs.SmbResource"), boolean.class);
            m.invoke(tmp, dest, Boolean.TRUE);
            return;
        } catch (InvocationTargetException ex) {
            Throwable c = ex.getCause();
            if (c instanceof IOException) throw (IOException) c;
            throw new IOException(String.valueOf(c));
        } catch (ReflectiveOperationException ignored) { /* bản thư viện không có -> dùng cách dưới */ }
        if (dest.exists()) dest.delete();
        tmp.renameTo(dest);
    }

    // tạo file mới (dùng làm khóa): true = tạo được, false = đã có
    boolean createNew(String rel, String text) throws IOException {
        String full = url(rel, false);
        ensureDir(parentOf(full));
        SmbFile f = new SmbFile(full, ctx);
        if (f.exists()) return false;
        try (OutputStream out = new SmbFileOutputStream(f)) {
            out.write(text == null ? new byte[0] : text.getBytes(UTF8));
        }
        return true;
    }

    boolean del(String rel) {
        try {
            SmbFile f = new SmbFile(url(rel, false), ctx);
            if (f.exists()) f.delete();
            return true;
        } catch (IOException e) { return false; }
    }

    /** Thử đọc thư mục + ghi/xóa 1 file nhỏ. Trả null nếu dùng được, ngược lại là thông báo lỗi. */
    String check() {
        try {
            ensureDir(root);
            SmbFile root0 = new SmbFile(root, ctx);
            root0.listFiles();
            String name = "_check_" + UUID.randomUUID().toString().replace("-", "").substring(0, 6) + ".tmp";
            SmbFile t = new SmbFile(root + name, ctx);
            try (OutputStream out = new SmbFileOutputStream(t)) { out.write("ok".getBytes(UTF8)); }
            t.delete();
            return null;
        } catch (Exception e) {
            String m = e.getMessage();
            return (m == null || m.isEmpty()) ? e.getClass().getSimpleName() : m;
        }
    }
}
