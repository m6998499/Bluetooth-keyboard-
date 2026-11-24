package com.melvin.phonekeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * One finger: move the pointer. Tap: left click. Two-finger tap: right click.
 * Two-finger drag: scroll.
 */
public class TouchpadView extends View {

    private final HidManager hid;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float lastX, lastY, startX, startY;
    private long downTime;
    private int maxPointers;
    private boolean moved;
    private float remX, remY, scrollRem;

    private static final float SPEED = 1.6f;

    public TouchpadView(Context c, HidManager hid) {
        super(c);
        this.hid = hid;
        fill.setColor(Color.rgb(38, 41, 48));
        text.setColor(Color.rgb(130, 136, 150));
        text.setTextAlign(Paint.Align.CENTER);
        text.setTextSize(15 * getResources().getDisplayMetrics().scaledDensity);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float r = 18 * getResources().getDisplayMetrics().density;
        rect.set(0, 0, getWidth(), getHeight());
        canvas.drawRoundRect(rect, r, r, fill);
        float cy = getHeight() / 2f;
        canvas.drawText("Touchpad", getWidth() / 2f, cy - text.getTextSize(), text);
        canvas.drawText("tap = click · 2-finger tap = right click", getWidth() / 2f, cy + 4, text);
        canvas.drawText("2-finger drag = scroll", getWidth() / 2f, cy + text.getTextSize() * 1.5f, text);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float slop = 10 * getResources().getDisplayMetrics().density;
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = startX = e.getX();
                lastY = startY = e.getY();
                downTime = e.getEventTime();
                maxPointers = 1;
                moved = false;
                remX = remY = scrollRem = 0;
                return true;

            case MotionEvent.ACTION_POINTER_DOWN:
                maxPointers = Math.max(maxPointers, e.getPointerCount());
                lastX = avgX(e);
                lastY = avgY(e);
                return true;

            case MotionEvent.ACTION_MOVE: {
                float x = avgX(e), y = avgY(e);
                float dx = x - lastX, dy = y - lastY;
                lastX = x;
                lastY = y;
                if (Math.abs(e.getX() - startX) > slop || Math.abs(e.getY() - startY) > slop) moved = true;
                if (e.getPointerCount() >= 2) {
                    scrollRem += -dy / (14 * getResources().getDisplayMetrics().density);
                    int s = (int) scrollRem;
                    if (s != 0) { hid.scroll(s); scrollRem -= s; }
                } else {
                    remX += dx * SPEED;
                    remY += dy * SPEED;
                    int mx = (int) remX, my = (int) remY;
                    if (mx != 0 || my != 0) {
                        hid.moveMouse(mx, my);
                        remX -= mx;
                        remY -= my;
                    }
                }
                return true;
            }

            case MotionEvent.ACTION_POINTER_UP:
                // Re-anchor on the remaining finger(s) so the pointer doesn't jump
                lastX = avgXExcluding(e, e.getActionIndex());
                lastY = avgYExcluding(e, e.getActionIndex());
                return true;

            case MotionEvent.ACTION_UP:
                if (!moved && e.getEventTime() - downTime < 250) {
                    hid.click(maxPointers >= 2 ? 2 : 1);
                    performClick();
                }
                return true;
        }
        return super.onTouchEvent(e);
    }

    @Override
    public boolean performClick() { return super.performClick(); }

    private static float avgX(MotionEvent e) {
        float s = 0;
        for (int i = 0; i < e.getPointerCount(); i++) s += e.getX(i);
        return s / e.getPointerCount();
    }

    private static float avgY(MotionEvent e) {
        float s = 0;
        for (int i = 0; i < e.getPointerCount(); i++) s += e.getY(i);
        return s / e.getPointerCount();
    }

    private static float avgXExcluding(MotionEvent e, int skip) {
        float s = 0; int n = 0;
        for (int i = 0; i < e.getPointerCount(); i++) if (i != skip) { s += e.getX(i); n++; }
        return n == 0 ? e.getX() : s / n;
    }

    private static float avgYExcluding(MotionEvent e, int skip) {
        float s = 0; int n = 0;
        for (int i = 0; i < e.getPointerCount(); i++) if (i != skip) { s += e.getY(i); n++; }
        return n == 0 ? e.getY() : s / n;
    }
}
