package com.melvin.phonekeyboard;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@SuppressLint({"MissingPermission", "SetTextI18n"})
public class MainActivity extends Activity implements HidManager.Listener {

    private static final String PERM_CONNECT = "android.permission.BLUETOOTH_CONNECT";
    private static final String PERM_ADVERTISE = "android.permission.BLUETOOTH_ADVERTISE";
    private static final int REQ_PERMS = 1;

    private static final int BG = Color.rgb(20, 22, 27);
    private static final int KEY = Color.rgb(52, 56, 66);
    private static final int KEY_ON = Color.rgb(46, 125, 230);
    private static final int GOLD = Color.rgb(232, 178, 60);

    private HidManager hid;
    private TextView status;
    private EditText input;
    private String lastText = "";
    private boolean ignoreChange;

    private int modifiers = 0;
    private final List<Button> modButtons = new ArrayList<>();

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hid = new HidManager(this, this);
        setContentView(buildUi());
        if (needsPermissions()) {
            requestPermissions(new String[]{PERM_CONNECT, PERM_ADVERTISE}, REQ_PERMS);
        } else {
            hid.start();
        }
    }

    @Override
    protected void onDestroy() {
        hid.stop();
        super.onDestroy();
    }

    private boolean needsPermissions() {
        return Build.VERSION.SDK_INT >= 31
                && (checkSelfPermission(PERM_CONNECT) != PackageManager.PERMISSION_GRANTED
                || checkSelfPermission(PERM_ADVERTISE) != PackageManager.PERMISSION_GRANTED);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        if (code != REQ_PERMS) return;
        if (needsPermissions()) {
            onStatus("Bluetooth permission is needed. Allow \"Nearby devices\" in app settings.", false);
        } else {
            hid.start();
        }
    }

    @Override
    public void onStatus(String s, boolean connected) {
        status.setText(s);
        status.setTextColor(connected ? Color.rgb(90, 210, 120) : Color.rgb(200, 205, 215));
    }

    // ---------------- UI ----------------

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        int pad = dp(12);
        root.setPadding(pad, pad, pad, pad);

        TextView title = new TextView(this);
        title.setText("Phone Keyboard");
        title.setTextColor(GOLD);
        title.setTextSize(20);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        root.addView(title);

        status = new TextView(this);
        status.setTextSize(14);
        status.setPadding(0, dp(4), 0, dp(8));
        root.addView(status);

        LinearLayout top = row();
        top.addView(key("Make visible", v -> makeDiscoverable()), weight(1));
        top.addView(key("Connect", v -> pickDevice()), weight(1));
        top.addView(key("Disconnect", v -> hid.disconnect()), weight(1));
        root.addView(top);

        input = new EditText(this);
        input.setHint("Tap here and type — it goes straight to your PC");
        input.setHintTextColor(Color.rgb(120, 125, 135));
        input.setTextColor(Color.WHITE);
        input.setTextSize(16);
        input.setMinLines(2);
        input.setMaxLines(3);
        input.setGravity(Gravity.TOP);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        input.setBackground(rounded(Color.rgb(32, 35, 42)));
        input.setPadding(dp(12), dp(10), dp(12), dp(10));
        input.addTextChangedListener(watcher);
        // Backspace when the box is already empty
        input.setOnKeyListener((v, keyCode, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DEL
                    && input.getText().length() == 0) {
                sendKey(KeyMap.BACKSPACE);
                return true;
            }
            return false;
        });
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        ip.setMargins(0, dp(8), 0, dp(6));
        root.addView(input, ip);

        LinearLayout mods = row();
        mods.addView(mod("Ctrl", KeyMap.CTRL), weight(1));
        mods.addView(mod("Alt", KeyMap.ALT), weight(1));
        mods.addView(mod("Shift", KeyMap.SHIFT), weight(1));
        mods.addView(mod("Win", KeyMap.WIN), weight(1));
        mods.addView(key("⌫", v -> sendKey(KeyMap.BACKSPACE)), weight(1));
        mods.addView(key("Enter", v -> sendKey(KeyMap.ENTER)), weight(1.3f));
        root.addView(mods);

        LinearLayout keys = row();
        keys.addView(key("Esc", v -> sendKey(KeyMap.ESC)), weight(1));
        keys.addView(key("Tab", v -> sendKey(KeyMap.TAB)), weight(1));
        keys.addView(key("←", v -> sendKey(KeyMap.LEFT)), weight(1));
        keys.addView(key("↑", v -> sendKey(KeyMap.UP)), weight(1));
        keys.addView(key("↓", v -> sendKey(KeyMap.DOWN)), weight(1));
        keys.addView(key("→", v -> sendKey(KeyMap.RIGHT)), weight(1));
        keys.addView(key("Del", v -> sendKey(KeyMap.DELETE)), weight(1));
        root.addView(keys);

        LinearLayout keys2 = row();
        keys2.addView(key("Home", v -> sendKey(KeyMap.HOME)), weight(1));
        keys2.addView(key("End", v -> sendKey(KeyMap.END)), weight(1));
        keys2.addView(key("PgUp", v -> sendKey(KeyMap.PAGE_UP)), weight(1));
        keys2.addView(key("PgDn", v -> sendKey(KeyMap.PAGE_DOWN)), weight(1));
        keys2.addView(key("F-keys", v -> pickFunctionKey()), weight(1.2f));
        keys2.addView(key("Clear", v -> clearBox()), weight(1));
        root.addView(keys2);

        TouchpadView pad2 = new TouchpadView(this, hid);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        tp.setMargins(0, dp(8), 0, dp(6));
        root.addView(pad2, tp);

        LinearLayout mouse = row();
        mouse.addView(key("Left click", v -> hid.click(1)), weight(1));
        mouse.addView(key("Right click", v -> hid.click(2)), weight(1));
        root.addView(mouse);

        return root;
    }

    // Sends whatever changed in the text box: new characters, or backspaces for deletions.
    // Comparing old vs new text also handles autocorrect replacing a word.
    private final TextWatcher watcher = new TextWatcher() {
        @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
        @Override public void onTextChanged(CharSequence s, int a, int b, int c) { }

        @Override
        public void afterTextChanged(Editable e) {
            if (ignoreChange) return;
            String now = e.toString();
            int p = 0;
            int max = Math.min(now.length(), lastText.length());
            while (p < max && now.charAt(p) == lastText.charAt(p)) p++;
            int deletes = lastText.length() - p;
            String added = now.substring(p);
            lastText = now;

            for (int i = 0; i < deletes; i++) hid.tapKey(0, KeyMap.BACKSPACE);
            if (!added.isEmpty()) {
                hid.typeText(added, modifiers);
                clearModifiers();
            }
            if (now.length() > 400) clearBox();
        }
    };

    private void clearBox() {
        ignoreChange = true;
        input.setText("");
        lastText = "";
        ignoreChange = false;
    }

    private void sendKey(int keyCode) {
        hid.tapKey(modifiers, keyCode);
        clearModifiers();
    }

    private void pickFunctionKey() {
        String[] names = new String[12];
        for (int i = 0; i < 12; i++) names[i] = "F" + (i + 1);
        new AlertDialog.Builder(this)
                .setTitle("Function key")
                .setItems(names, (d, which) -> sendKey(KeyMap.F1 + which))
                .show();
    }

    private void makeDiscoverable() {
        Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
        i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 120);
        try {
            startActivity(i);
            onStatus("Phone is visible for 2 minutes. On your PC: Settings → Bluetooth → Add device → pick this phone.", false);
        } catch (Exception ex) {
            onStatus("Couldn't make the phone visible: " + ex.getMessage(), false);
        }
    }

    private void pickDevice() {
        BluetoothAdapter a = BluetoothAdapter.getDefaultAdapter();
        if (a == null) return;
        Set<BluetoothDevice> bonded;
        try { bonded = a.getBondedDevices(); } catch (SecurityException ex) { bonded = null; }
        if (bonded == null || bonded.isEmpty()) {
            onStatus("No paired devices yet. Tap \"Make visible\" and pair from your PC first.", false);
            return;
        }
        final List<BluetoothDevice> list = new ArrayList<>(bonded);
        String[] names = new String[list.size()];
        for (int i = 0; i < list.size(); i++) names[i] = HidManager.name(list.get(i));
        new AlertDialog.Builder(this)
                .setTitle("Connect to which computer?")
                .setItems(names, (d, which) -> hid.connect(list.get(which)))
                .show();
    }

    private Button mod(String label, int bit) {
        Button btn = key(label, null);
        btn.setOnClickListener(v -> {
            modifiers ^= bit;
            refreshMods();
        });
        btn.setTag(bit);
        modButtons.add(btn);
        return btn;
    }

    private void clearModifiers() {
        if (modifiers == 0) return;
        modifiers = 0;
        refreshMods();
    }

    private void refreshMods() {
        for (Button b : modButtons) {
            int bit = (Integer) b.getTag();
            b.setBackground(rounded((modifiers & bit) != 0 ? KEY_ON : KEY));
        }
    }

    private Button key(String label, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(label);
        b.setAllCaps(false);
        b.setTextColor(Color.WHITE);
        b.setTextSize(14);
        b.setMinHeight(0);
        b.setMinimumHeight(0);
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setPadding(dp(2), dp(10), dp(2), dp(10));
        b.setBackground(rounded(KEY));
        if (l != null) b.setOnClickListener(l);
        return b;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setPadding(0, dp(3), 0, dp(3));
        return r;
    }

    private LinearLayout.LayoutParams weight(float w) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, w);
        p.setMargins(dp(3), 0, dp(3), 0);
        return p;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(10));
        return g;
    }

    private int dp(float v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
