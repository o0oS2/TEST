package vn.automes.oqc;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;

/** Cài đặt kết nối thư mục chia sẻ (lưu trong SharedPreferences riêng của app). */
final class Config {
    String folder = "";   // dạng smb://MAY-KHO/OQC/
    String user = "";
    String pass = "";
    String domain = "";
    String machine = "";

    static Config load(Context c) {
        SharedPreferences p = c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
        Config k = new Config();
        k.folder = p.getString("folder", "");
        k.user = p.getString("user", "");
        k.pass = p.getString("pass", "");
        k.domain = p.getString("domain", "");
        k.machine = p.getString("machine", "");
        if (k.machine.isEmpty()) k.machine = (Build.MODEL == null ? "Android" : Build.MODEL);
        return k;
    }

    void save(Context c) {
        c.getSharedPreferences("cfg", Context.MODE_PRIVATE).edit()
                .putString("folder", folder).putString("user", user).putString("pass", pass)
                .putString("domain", domain).putString("machine", machine).apply();
    }

    boolean ready() { return folder != null && !folder.trim().isEmpty(); }

    /** Nhận \\MAY-KHO\OQC, //MAY-KHO/OQC, smb://MAY-KHO/OQC ... -> smb://MAY-KHO/OQC/ */
    static String normalize(String s) {
        if (s == null) return "";
        s = s.trim().replace('\\', '/');
        if (s.isEmpty()) return "";
        if (s.regionMatches(true, 0, "smb://", 0, 6)) s = s.substring(6);
        while (s.startsWith("/")) s = s.substring(1);
        while (s.endsWith("/")) s = s.substring(0, s.length() - 1);
        if (s.isEmpty()) return "";
        return "smb://" + s + "/";
    }
}
