package com.melvin.phonekeyboard;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothHidDevice;
import android.bluetooth.BluetoothHidDeviceAppSdpSettings;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Makes the phone act as a Bluetooth keyboard + mouse (HID combo device).
 * Requires Android 9 (API 28) or newer.
 */
@SuppressLint("MissingPermission")
public class HidManager {

    public interface Listener {
        void onStatus(String status, boolean connected);
    }

    private static final int ID_KEYBOARD = 1;
    private static final int ID_MOUSE = 2;

    // HID report descriptor: keyboard (report 1) + 3-button mouse with wheel (report 2)
    private static final byte[] DESCRIPTOR = bytes(
            // Keyboard
            0x05, 0x01, 0x09, 0x06, 0xA1, 0x01, 0x85, ID_KEYBOARD,
            0x05, 0x07, 0x19, 0xE0, 0x29, 0xE7, 0x15, 0x00, 0x25, 0x01,
            0x75, 0x01, 0x95, 0x08, 0x81, 0x02,             // modifiers
            0x95, 0x01, 0x75, 0x08, 0x81, 0x01,             // reserved
            0x95, 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
            0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81, 0x00, // 6 keys
            0xC0,
            // Mouse
            0x05, 0x01, 0x09, 0x02, 0xA1, 0x01, 0x85, ID_MOUSE,
            0x09, 0x01, 0xA1, 0x00,
            0x05, 0x09, 0x19, 0x01, 0x29, 0x03, 0x15, 0x00, 0x25, 0x01,
            0x95, 0x03, 0x75, 0x01, 0x81, 0x02,             // buttons
            0x95, 0x01, 0x75, 0x05, 0x81, 0x03,             // padding
            0x05, 0x01, 0x09, 0x30, 0x09, 0x31, 0x09, 0x38,
            0x15, 0x81, 0x25, 0x7F, 0x75, 0x08, 0x95, 0x03,
            0x81, 0x06,                                     // x, y, wheel (relative)
            0xC0, 0xC0
    );

    private final Context context;
    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    // One thread sends every report in order, with a short gap so the PC never misses a key.
    private final ExecutorService sender = Executors.newSingleThreadExecutor();

    private BluetoothAdapter adapter;
    private BluetoothHidDevice hid;
    private BluetoothDevice host;
    private boolean registered;
    private int mouseButtons = 0;

    public HidManager(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    public void start() {
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) { status("This phone has no Bluetooth", false); return; }
        if (!adapter.isEnabled()) { status("Turn on Bluetooth, then reopen the app", false); return; }
        boolean ok = adapter.getProfileProxy(context, profileListener, BluetoothProfile.HID_DEVICE);
        if (!ok) status("This phone doesn't support keyboard mode", false);
        else status("Starting…", false);
    }

    public void stop() {
        if (hid != null) {
            try {
                if (host != null) hid.disconnect(host);
                hid.unregisterApp();
            } catch (Exception ignored) { }
            if (adapter != null) adapter.closeProfileProxy(BluetoothProfile.HID_DEVICE, hid);
        }
        hid = null;
        host = null;
        registered = false;
        sender.shutdownNow();
    }

    public boolean isConnected() { return host != null; }

    public boolean isReady() { return hid != null && registered; }

    public void connect(BluetoothDevice device) {
        if (!isReady()) { status("Not ready yet — wait a second and try again", false); return; }
        status("Connecting to " + name(device) + "…", false);
        hid.connect(device);
    }

    public void disconnect() {
        if (hid != null && host != null) hid.disconnect(host);
    }

    // ---------------- Keyboard ----------------

    /** Press and release one key with the given modifier bits. */
    public void tapKey(int modifiers, int keyCode) {
        queue(ID_KEYBOARD, new byte[]{(byte) modifiers, 0, (byte) keyCode, 0, 0, 0, 0, 0});
        queue(ID_KEYBOARD, new byte[8]);
    }

    /** Types a string (US keyboard layout). Extra modifiers (Ctrl/Alt/Win) apply to the first character only. */
    public void typeText(CharSequence text, int extraModifiers) {
        for (int i = 0; i < text.length(); i++) {
            int[] k = KeyMap.forChar(text.charAt(i));
            if (k == null) continue;
            tapKey(k[0] | (i == 0 ? extraModifiers : 0), k[1]);
        }
    }

    // ---------------- Mouse ----------------

    public void moveMouse(int dx, int dy) {
        while (dx != 0 || dy != 0) {
            int sx = clamp(dx), sy = clamp(dy);
            sendMouse(sx, sy, 0);
            dx -= sx;
            dy -= sy;
        }
    }

    public void scroll(int amount) { sendMouse(0, 0, clamp(amount)); }

    /** button: 1 = left, 2 = right, 4 = middle */
    public void click(int button) {
        setButton(button, true);
        setButton(button, false);
    }

    public void setButton(int button, boolean down) {
        mouseButtons = down ? (mouseButtons | button) : (mouseButtons & ~button);
        sendMouse(0, 0, 0);
    }

    private void sendMouse(int dx, int dy, int wheel) {
        queue(ID_MOUSE, new byte[]{(byte) mouseButtons, (byte) dx, (byte) dy, (byte) wheel});
    }

    // ---------------- Internals ----------------

    private void queue(int id, byte[] report) {
        if (sender.isShutdown()) return;
        sender.execute(() -> {
            BluetoothHidDevice h = hid;
            BluetoothDevice d = host;
            if (h == null || d == null) return;
            h.sendReport(d, id, report);
            try { Thread.sleep(id == ID_KEYBOARD ? 6 : 2); } catch (InterruptedException ignored) { }
        });
    }

    private final BluetoothProfile.ServiceListener profileListener = new BluetoothProfile.ServiceListener() {
        @Override
        public void onServiceConnected(int profile, BluetoothProfile proxy) {
            if (profile != BluetoothProfile.HID_DEVICE) return;
            hid = (BluetoothHidDevice) proxy;
            BluetoothHidDeviceAppSdpSettings sdp = new BluetoothHidDeviceAppSdpSettings(
                    "Phone Keyboard", "Phone keyboard and touchpad", "Phone Keyboard",
                    BluetoothHidDevice.SUBCLASS1_COMBO, DESCRIPTOR);
            boolean ok = hid.registerApp(sdp, null, null, Runnable::run, hidCallback);
            if (!ok) status("Couldn't start keyboard mode. Close other keyboard apps and retry.", false);
        }

        @Override
        public void onServiceDisconnected(int profile) {
            hid = null;
            host = null;
            registered = false;
            status("Bluetooth keyboard service stopped", false);
        }
    };

    private final BluetoothHidDevice.Callback hidCallback = new BluetoothHidDevice.Callback() {
        @Override
        public void onAppStatusChanged(BluetoothDevice pluggedDevice, boolean reg) {
            registered = reg;
            if (!reg) { status("Keyboard mode off", false); return; }
            status("Ready. Pair from your PC, or tap Connect.", false);
            if (pluggedDevice != null && hid != null) hid.connect(pluggedDevice);
        }

        @Override
        public void onConnectionStateChanged(BluetoothDevice device, int state) {
            if (state == BluetoothProfile.STATE_CONNECTED) {
                host = device;
                status("Connected to " + name(device), true);
            } else if (state == BluetoothProfile.STATE_CONNECTING) {
                status("Connecting to " + name(device) + "…", false);
            } else if (state == BluetoothProfile.STATE_DISCONNECTED) {
                if (device.equals(host)) host = null;
                status("Disconnected. Tap Connect to reconnect.", false);
            }
        }

        @Override
        public void onGetReport(BluetoothDevice device, byte type, byte id, int bufferSize) {
            if (hid == null) return;
            byte[] empty = id == ID_MOUSE ? new byte[4] : new byte[8];
            hid.replyReport(device, type, id, empty);
        }
    };

    private void status(String s, boolean connected) {
        main.post(() -> listener.onStatus(s, connected));
    }

    static String name(BluetoothDevice d) {
        String n = null;
        try { n = d.getName(); } catch (Exception ignored) { }
        return n != null ? n : d.getAddress();
    }

    private static int clamp(int v) { return Math.max(-127, Math.min(127, v)); }

    private static byte[] bytes(int... v) {
        byte[] b = new byte[v.length];
        for (int i = 0; i < v.length; i++) b[i] = (byte) v[i];
        return b;
    }
}
