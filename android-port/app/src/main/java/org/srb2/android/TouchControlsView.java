package org.srb2.android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import org.libsdl.app.SDLActivity;

/**
 * Touch controls overlay for SRB2 on Android.
 * Renders a virtual D-pad (left side) and action buttons (right side)
 * and sends corresponding key events to the native game via SDL.
 *
 * Layout (landscape):
 *   Left side:  Virtual analog stick / D-Pad (movement)
 *   Right side: A (Jump), B (Spin), C (Custom1), D (Custom2)
 *   Top-right:  PAUSE, MENU buttons
 *   Center:     Camera drag zone
 */
public class TouchControlsView extends View {

    // Android AKEYCODE constants (SDLActivity.onNativeKeyDown expects these)
    private static final int AKEYCODE_DPAD_UP = 19;
    private static final int AKEYCODE_DPAD_DOWN = 20;
    private static final int AKEYCODE_DPAD_LEFT = 21;
    private static final int AKEYCODE_DPAD_RIGHT = 22;
    private static final int AKEYCODE_SPACE = 62;       // Jump
    private static final int AKEYCODE_SHIFT_LEFT = 59;  // Spin
    private static final int AKEYCODE_CTRL_LEFT = 113;  // Fire/Custom1
    private static final int AKEYCODE_W = 51;           // Toss flag
    private static final int AKEYCODE_ESCAPE = 111;     // Menu/Pause
    private static final int AKEYCODE_ENTER = 66;       // Enter/Start
    private static final int AKEYCODE_T = 48;           // Chat
    private static final int AKEYCODE_GRAVE = 68;       // Console (~)

    // Paint objects
    private final Paint dpadPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dpadActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint buttonActivePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint smallButtonPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    // D-Pad state
    private float dpadCenterX, dpadCenterY, dpadRadius;
    private float dpadTouchX, dpadTouchY;
    private boolean dpadActive = false;
    private int dpadPointerId = -1;
    private boolean dpadUp, dpadDown, dpadLeft, dpadRight;

    // Action buttons
    private final RectF jumpButton = new RectF();
    private final RectF spinButton = new RectF();
    private final RectF fireButton = new RectF();
    private final RectF tossButton = new RectF();
    private final RectF pauseButton = new RectF();
    private final RectF startButton = new RectF();
    private final RectF chatButton = new RectF();
    private final RectF consoleButton = new RectF();
    private final RectF kbButton = new RectF();

    private boolean jumpPressed, spinPressed, firePressed, tossPressed;
    private boolean pausePressed, startPressed;
    private boolean chatPressed, consolePressed, kbPressed;

    // Button pointer tracking
    private final int[] buttonPointers = new int[9]; // one per button

    // Camera drag tracking
    private int cameraPointerId = -1;
    private float cameraLastX, cameraLastY;

    // Sizing
    private float buttonRadius;
    private float opacity = 0.35f;

    public TouchControlsView(Context context) {
        super(context);
        init();
    }

    public TouchControlsView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        // D-Pad appearance
        dpadPaint.setColor(Color.WHITE);
        dpadPaint.setAlpha((int) (opacity * 255));
        dpadPaint.setStyle(Paint.Style.STROKE);
        dpadPaint.setStrokeWidth(4f);

        dpadActivePaint.setColor(Color.WHITE);
        dpadActivePaint.setAlpha((int) (opacity * 1.5f * 255));
        dpadActivePaint.setStyle(Paint.Style.FILL);

        // Buttons
        buttonPaint.setColor(Color.WHITE);
        buttonPaint.setAlpha((int) (opacity * 255));
        buttonPaint.setStyle(Paint.Style.STROKE);
        buttonPaint.setStrokeWidth(3f);

        buttonActivePaint.setColor(Color.WHITE);
        buttonActivePaint.setAlpha((int) (opacity * 1.5f * 255));
        buttonActivePaint.setStyle(Paint.Style.FILL);

        // Text labels
        textPaint.setColor(Color.WHITE);
        textPaint.setAlpha((int) (opacity * 2.0f * 255));
        textPaint.setTextAlign(Paint.Align.CENTER);

        smallButtonPaint.setColor(Color.WHITE);
        smallButtonPaint.setAlpha((int) (opacity * 0.8f * 255));
        smallButtonPaint.setStyle(Paint.Style.STROKE);
        smallButtonPaint.setStrokeWidth(2f);

        for (int i = 0; i < buttonPointers.length; i++) {
            buttonPointers[i] = -1;
        }

        setClickable(true);
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        layoutControls(w, h);
    }

    private void layoutControls(int w, int h) {
        float scale = Math.min(w, h) / 480f; // scale factor based on screen size

        // D-Pad (left side)
        dpadRadius = 75 * scale;
        dpadCenterX = dpadRadius + 40 * scale;
        dpadCenterY = h - dpadRadius - 40 * scale;

        // Action buttons (right side) - diamond layout
        buttonRadius = 35 * scale;
        float btnCenterX = w - buttonRadius * 3 - 40 * scale;
        float btnCenterY = h - buttonRadius * 3 - 30 * scale;
        float btnSpacing = buttonRadius * 2.3f;

        // Jump (A) - bottom
        jumpButton.set(
            btnCenterX - buttonRadius,
            btnCenterY + btnSpacing - buttonRadius,
            btnCenterX + buttonRadius,
            btnCenterY + btnSpacing + buttonRadius
        );

        // Spin (B) - right
        spinButton.set(
            btnCenterX + btnSpacing - buttonRadius,
            btnCenterY - buttonRadius,
            btnCenterX + btnSpacing + buttonRadius,
            btnCenterY + buttonRadius
        );

        // Fire (C) - left  
        fireButton.set(
            btnCenterX - btnSpacing - buttonRadius,
            btnCenterY - buttonRadius,
            btnCenterX - btnSpacing + buttonRadius,
            btnCenterY + buttonRadius
        );

        // Toss Flag (D) - top
        tossButton.set(
            btnCenterX - buttonRadius,
            btnCenterY - btnSpacing - buttonRadius,
            btnCenterX + buttonRadius,
            btnCenterY - btnSpacing + buttonRadius
        );

        // Pause / Menu buttons (top area)
        float smallBtnSize = 25 * scale;
        pauseButton.set(
            w - smallBtnSize * 2 - 20 * scale, 15 * scale,
            w - 20 * scale, 15 * scale + smallBtnSize * 1.5f
        );
        startButton.set(
            w - smallBtnSize * 4.5f - 20 * scale, 15 * scale,
            w - smallBtnSize * 2.5f - 20 * scale, 15 * scale + smallBtnSize * 1.5f
        );

        // Chat / Console / Keyboard buttons (top-left area)
        chatButton.set(
            20 * scale, 15 * scale,
            20 * scale + smallBtnSize * 2, 15 * scale + smallBtnSize * 1.5f
        );
        consoleButton.set(
            20 * scale + smallBtnSize * 2.5f, 15 * scale,
            20 * scale + smallBtnSize * 4.5f, 15 * scale + smallBtnSize * 1.5f
        );
        kbButton.set(
            20 * scale + smallBtnSize * 5.0f, 15 * scale,
            20 * scale + smallBtnSize * 7.0f, 15 * scale + smallBtnSize * 1.5f
        );

        textPaint.setTextSize(16 * scale);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        // --- D-Pad ---
        // Outer circle
        canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius, dpadPaint);
        // Inner dead zone circle
        canvas.drawCircle(dpadCenterX, dpadCenterY, dpadRadius * 0.3f, dpadPaint);

        // Direction indicators
        if (dpadUp) drawDpadDirection(canvas, 0, -1);
        if (dpadDown) drawDpadDirection(canvas, 0, 1);
        if (dpadLeft) drawDpadDirection(canvas, -1, 0);
        if (dpadRight) drawDpadDirection(canvas, 1, 0);

        // Thumb position indicator
        if (dpadActive) {
            canvas.drawCircle(dpadTouchX, dpadTouchY, dpadRadius * 0.25f, dpadActivePaint);
        }

        // --- Action Buttons ---
        drawButton(canvas, jumpButton, "JUMP", jumpPressed);
        drawButton(canvas, spinButton, "SPIN", spinPressed);
        drawButton(canvas, fireButton, "FIRE", firePressed);
        drawButton(canvas, tossButton, "TOSS", tossPressed);

        // --- Small buttons ---
        drawSmallButton(canvas, pauseButton, "ESC", pausePressed);
        drawSmallButton(canvas, startButton, "START", startPressed);
        drawSmallButton(canvas, chatButton, "CHAT", chatPressed);
        drawSmallButton(canvas, consoleButton, "CON", consolePressed);
        drawSmallButton(canvas, kbButton, "KB", kbPressed);
    }

    private void drawDpadDirection(Canvas canvas, int dx, int dy) {
        float indicatorX = dpadCenterX + dx * dpadRadius * 0.65f;
        float indicatorY = dpadCenterY + dy * dpadRadius * 0.65f;
        canvas.drawCircle(indicatorX, indicatorY, dpadRadius * 0.2f, dpadActivePaint);
    }

    private void drawButton(Canvas canvas, RectF rect, String label, boolean pressed) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float r = rect.width() / 2;
        canvas.drawCircle(cx, cy, r, pressed ? buttonActivePaint : buttonPaint);
        canvas.drawText(label, cx, cy + textPaint.getTextSize() * 0.35f, textPaint);
    }

    private void drawSmallButton(Canvas canvas, RectF rect, String label, boolean pressed) {
        canvas.drawRoundRect(rect, 8, 8, pressed ? buttonActivePaint : smallButtonPaint);
        canvas.drawText(label, rect.centerX(), rect.centerY() + textPaint.getTextSize() * 0.35f, textPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int pointerIndex = event.getActionIndex();
        int pointerId = event.getPointerId(pointerIndex);

        switch (action) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                handleTouchDown(event, pointerIndex, pointerId);
                break;

            case MotionEvent.ACTION_MOVE:
                handleTouchMove(event);
                break;

            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_CANCEL:
                handleTouchUp(pointerId);
                break;
        }

        invalidate();
        return true;
    }

    private void handleTouchDown(MotionEvent event, int pointerIndex, int pointerId) {
        float x = event.getX(pointerIndex);
        float y = event.getY(pointerIndex);

        // Check D-Pad area (left half of screen, lower area)
        float dpadDist = distance(x, y, dpadCenterX, dpadCenterY);
        if (dpadDist < dpadRadius * 1.5f && x < getWidth() / 2f) {
            dpadActive = true;
            dpadPointerId = pointerId;
            dpadTouchX = x;
            dpadTouchY = y;
            updateDpadState();
            return;
        }

        // Check action buttons
        if (isInCircle(x, y, jumpButton)) {
            setJump(true);
            buttonPointers[0] = pointerId;
        } else if (isInCircle(x, y, spinButton)) {
            setSpin(true);
            buttonPointers[1] = pointerId;
        } else if (isInCircle(x, y, fireButton)) {
            setFire(true);
            buttonPointers[2] = pointerId;
        } else if (isInCircle(x, y, tossButton)) {
            setToss(true);
            buttonPointers[3] = pointerId;
        } else if (pauseButton.contains(x, y)) {
            setPause(true);
            buttonPointers[4] = pointerId;
        } else if (startButton.contains(x, y)) {
            setStart(true);
            buttonPointers[5] = pointerId;
        } else if (chatButton.contains(x, y)) {
            setChat(true);
            buttonPointers[6] = pointerId;
        } else if (consoleButton.contains(x, y)) {
            setConsole(true);
            buttonPointers[7] = pointerId;
        } else if (kbButton.contains(x, y)) {
            toggleKeyboard();
            buttonPointers[8] = pointerId;
        } else {
            // No button matched — forward to SDL as camera touch
            cameraPointerId = pointerId;
            cameraLastX = x;
            cameraLastY = y;
            float nx = x / getWidth();
            float ny = y / getHeight();
            SDLActivity.onNativeTouch(0, pointerId, MotionEvent.ACTION_DOWN, nx, ny, 1.0f);
        }
    }

    private void handleTouchMove(MotionEvent event) {
        for (int i = 0; i < event.getPointerCount(); i++) {
            int pid = event.getPointerId(i);
            float x = event.getX(i);
            float y = event.getY(i);

            if (pid == dpadPointerId && dpadActive) {
                dpadTouchX = x;
                dpadTouchY = y;
                updateDpadState();
            } else if (pid == cameraPointerId) {
                // Forward camera drag motion to SDL for native camera handling
                float nx = x / getWidth();
                float ny = y / getHeight();
                SDLActivity.onNativeTouch(0, pid, MotionEvent.ACTION_MOVE, nx, ny, 1.0f);
                cameraLastX = x;
                cameraLastY = y;
            }
        }
    }

    private void handleTouchUp(int pointerId) {
        if (pointerId == dpadPointerId) {
            releaseDpad();
        }
        if (pointerId == cameraPointerId) {
            float nx = cameraLastX / Math.max(1, getWidth());
            float ny = cameraLastY / Math.max(1, getHeight());
            SDLActivity.onNativeTouch(0, pointerId, MotionEvent.ACTION_UP, nx, ny, 0f);
            cameraPointerId = -1;
        }
        if (pointerId == buttonPointers[0]) { setJump(false); buttonPointers[0] = -1; }
        if (pointerId == buttonPointers[1]) { setSpin(false); buttonPointers[1] = -1; }
        if (pointerId == buttonPointers[2]) { setFire(false); buttonPointers[2] = -1; }
        if (pointerId == buttonPointers[3]) { setToss(false); buttonPointers[3] = -1; }
        if (pointerId == buttonPointers[4]) { setPause(false); buttonPointers[4] = -1; }
        if (pointerId == buttonPointers[5]) { setStart(false); buttonPointers[5] = -1; }
        if (pointerId == buttonPointers[6]) { setChat(false); buttonPointers[6] = -1; }
        if (pointerId == buttonPointers[7]) { setConsole(false); buttonPointers[7] = -1; }
        if (pointerId == buttonPointers[8]) { kbPressed = false; buttonPointers[8] = -1; }
    }

    private void updateDpadState() {
        float dx = dpadTouchX - dpadCenterX;
        float dy = dpadTouchY - dpadCenterY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float deadZone = dpadRadius * 0.25f;

        boolean newUp = false, newDown = false, newLeft = false, newRight = false;

        if (dist > deadZone) {
            float angle = (float) Math.atan2(dy, dx);
            // 8-directional with tolerance
            float threshold = 0.38f; // ~22 degrees tolerance for diagonals

            if (dx / dist > threshold) newRight = true;
            if (dx / dist < -threshold) newLeft = true;
            if (dy / dist > threshold) newDown = true;
            if (dy / dist < -threshold) newUp = true;
        }

        if (newUp != dpadUp) { sendKey(AKEYCODE_DPAD_UP, newUp); dpadUp = newUp; }
        if (newDown != dpadDown) { sendKey(AKEYCODE_DPAD_DOWN, newDown); dpadDown = newDown; }
        if (newLeft != dpadLeft) { sendKey(AKEYCODE_DPAD_LEFT, newLeft); dpadLeft = newLeft; }
        if (newRight != dpadRight) { sendKey(AKEYCODE_DPAD_RIGHT, newRight); dpadRight = newRight; }
    }

    private void releaseDpad() {
        dpadActive = false;
        dpadPointerId = -1;
        if (dpadUp) { sendKey(AKEYCODE_DPAD_UP, false); dpadUp = false; }
        if (dpadDown) { sendKey(AKEYCODE_DPAD_DOWN, false); dpadDown = false; }
        if (dpadLeft) { sendKey(AKEYCODE_DPAD_LEFT, false); dpadLeft = false; }
        if (dpadRight) { sendKey(AKEYCODE_DPAD_RIGHT, false); dpadRight = false; }
    }

    private void setJump(boolean pressed) {
        if (jumpPressed != pressed) { sendKey(AKEYCODE_SPACE, pressed); jumpPressed = pressed; }
    }
    private void setSpin(boolean pressed) {
        if (spinPressed != pressed) { sendKey(AKEYCODE_SHIFT_LEFT, pressed); spinPressed = pressed; }
    }
    private void setFire(boolean pressed) {
        if (firePressed != pressed) { sendKey(AKEYCODE_CTRL_LEFT, pressed); firePressed = pressed; }
    }
    private void setToss(boolean pressed) {
        if (tossPressed != pressed) { sendKey(AKEYCODE_W, pressed); tossPressed = pressed; }
    }
    private void setPause(boolean pressed) {
        if (pausePressed != pressed) { sendKey(AKEYCODE_ESCAPE, pressed); pausePressed = pressed; }
    }
    private void setStart(boolean pressed) {
        if (startPressed != pressed) { sendKey(AKEYCODE_ENTER, pressed); startPressed = pressed; }
    }
    private void setChat(boolean pressed) {
        if (chatPressed != pressed) { sendKey(AKEYCODE_T, pressed); chatPressed = pressed; }
    }
    private void setConsole(boolean pressed) {
        if (consolePressed != pressed) { sendKey(AKEYCODE_GRAVE, pressed); consolePressed = pressed; }
    }

    private boolean keyboardShown = false;
    private void toggleKeyboard() {
        kbPressed = true;
        keyboardShown = !keyboardShown;
        post(() -> {
            InputMethodManager imm = (InputMethodManager) getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (keyboardShown) {
                SDLActivity.showTextInput(0, 0, getWidth(), getHeight());
            } else {
                imm.hideSoftInputFromWindow(getWindowToken(), 0);
            }
        });
    }

    /**
     * Send a key event to SDL via its native interface.
     */
    private void sendKey(int scancode, boolean pressed) {
        if (pressed) {
            SDLActivity.onNativeKeyDown(scancode);
        } else {
            SDLActivity.onNativeKeyUp(scancode);
        }
    }

    private boolean isInCircle(float x, float y, RectF rect) {
        float cx = rect.centerX();
        float cy = rect.centerY();
        float r = rect.width() / 2f * 1.3f; // slightly enlarged touch target
        return distance(x, y, cx, cy) <= r;
    }

    private float distance(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        return (float) Math.sqrt(dx * dx + dy * dy);
    }
}
