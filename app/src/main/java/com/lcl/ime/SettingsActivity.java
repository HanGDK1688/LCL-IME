package com.lcl.ime;

import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.provider.Settings;
import android.view.inputmethod.InputMethodManager;
import android.content.Context;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

public class SettingsActivity extends Activity {
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(36, 48, 36, 36);
        TextView title = new TextView(this);
        title.setText("LCL 离线输入法\n\n纯本地运行，不申请联网权限。先启用输入法，再选择使用。");
        title.setTextSize(20);
        box.addView(title);
        Button enable = new Button(this);
        enable.setText("1. 打开系统输入法设置");
        enable.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        box.addView(enable);
        Button choose = new Button(this);
        choose.setText("2. 选择 LCL 离线输入法");
        choose.setOnClickListener(v -> {
            InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) imm.showInputMethodPicker();
        });
        box.addView(choose);
        setContentView(box);
    }
}
