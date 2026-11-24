package vn.automes.oqc;

import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/** Nhập thư mục chia sẻ (SMB), tài khoản, tên thiết bị; có nút kiểm tra kết nối. */
public class SettingsActivity extends AppCompatActivity {
    private EditText fFolder, fDomain, fUser, fPass, fMachine;
    private TextView status;
    private Button btnCheck;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Config c = Config.load(this);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = (int) (16 * getResources().getDisplayMetrics().density);
        root.setPadding(pad, pad, pad, pad);

        root.addView(label("Thư mục lưu dữ liệu (cùng thư mục với bản exe)"));
        fFolder = edit("\\\\MAY-KHO\\OQC  hoặc  smb://192.168.1.10/OQC", c.folder.isEmpty() ? "" : c.folder);
        fFolder.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        root.addView(fFolder);

        root.addView(label("Tên đăng nhập Windows (để trống = khách)"));
        fUser = edit("vd: oqc", c.user);
        root.addView(fUser);

        root.addView(label("Mật khẩu"));
        fPass = edit("", c.pass);
        fPass.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        root.addView(fPass);

        root.addView(label("Domain / Workgroup (thường để trống)"));
        fDomain = edit("", c.domain);
        root.addView(fDomain);

        root.addView(label("Tên thiết bị"));
        fMachine = edit("", c.machine);
        root.addView(fMachine);

        btnCheck = new Button(this);
        btnCheck.setText("Kiểm tra kết nối");
        btnCheck.setOnClickListener(v -> check());
        root.addView(btnCheck);

        Button save = new Button(this);
        save.setText("Lưu");
        save.setOnClickListener(v -> save());
        root.addView(save);

        status = new TextView(this);
        status.setPadding(0, pad / 2, 0, 0);
        root.addView(status);

        ScrollView sv = new ScrollView(this);
        sv.addView(root);
        setContentView(sv);
    }

    private TextView label(String s) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setPadding(0, (int) (12 * getResources().getDisplayMetrics().density), 0, 0);
        return t;
    }

    private EditText edit(String hint, String value) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setText(value);
        e.setSingleLine(true);
        return e;
    }

    private Config read() {
        Config c = new Config();
        c.folder = Config.normalize(fFolder.getText().toString());
        c.user = fUser.getText().toString().trim();
        c.pass = fPass.getText().toString();
        c.domain = fDomain.getText().toString().trim();
        c.machine = fMachine.getText().toString().trim();
        if (c.machine.isEmpty()) c.machine = android.os.Build.MODEL == null ? "Android" : android.os.Build.MODEL;
        return c;
    }

    private void check() {
        final Config c = read();
        if (!c.ready()) { status.setText("Chưa nhập thư mục lưu."); return; }
        status.setText("Đang kiểm tra...");
        btnCheck.setEnabled(false);
        new Thread(() -> {
            String err;
            SmbIo io = null;
            try { io = new SmbIo(c); err = io.check(); }
            catch (Exception e) { err = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage(); }
            finally { if (io != null) io.close(); }
            final String res = err;
            runOnUiThread(() -> {
                btnCheck.setEnabled(true);
                status.setText(res == null ? "✔ Kết nối tốt: đọc và ghi được thư mục." : "✘ Lỗi: " + res);
            });
        }).start();
    }

    private void save() {
        Config c = read();
        if (!c.ready()) { Toast.makeText(this, "Hãy nhập thư mục lưu", Toast.LENGTH_SHORT).show(); return; }
        c.save(this);
        Toast.makeText(this, "Đã lưu", Toast.LENGTH_SHORT).show();
        finish();
    }
}
