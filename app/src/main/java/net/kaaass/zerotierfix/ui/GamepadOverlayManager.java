package net.kaaass.zerotierfix.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import net.kaaass.zerotierfix.service.TouchInjectService;

/**
 * 1x1 像素透明窗口，用于在应用退到后台后接收蓝牙手柄的摇杆事件
 * <p>
 * 双模式设计：
 * - 默认模式：FLAG_NOT_FOCUSABLE，不抢焦点，游戏正常显示
 * - 焦点模式：移除 FLAG_NOT_FOCUSABLE，可获焦点接收摇杆事件
 * <p>
 * 通过手柄 A 键（AccessibilityService.onKeyEvent 全局监听）切换两种模式
 */
public class GamepadOverlayManager {

    private static final String TAG = "GamepadOverlay";
    private static GamepadOverlayManager instance;

    private WindowManager windowManager;
    private View overlayView;
    private WindowManager.LayoutParams params;
    private boolean isAdded = false;
    private boolean focusMode = false; // 默认不抢焦点

    // 死区阈值（仅过滤硬件噪声，实际激活/释放由 TouchInjectService 滞后阈值控制）
    private static final float DEAD_ZONE = 0.05f;

    private GamepadOverlayManager(Context context) {
        windowManager = (WindowManager) context.getApplicationContext()
                .getSystemService(Context.WINDOW_SERVICE);
        initOverlay(context);
    }

    public static synchronized GamepadOverlayManager getInstance(Context context) {
        if (instance == null) {
            instance = new GamepadOverlayManager(context.getApplicationContext());
        }
        return instance;
    }

    public static GamepadOverlayManager getInstanceIfAvailable() {
        return instance;
    }

    private void initOverlay(Context context) {
        overlayView = new View(context) {
            @Override
            public boolean onGenericMotionEvent(MotionEvent event) {
                return handleGenericMotion(event);
            }

            @Override
            protected void onFocusChanged(boolean hasFocus, int direction,
                                          Rect previouslyFocusedRect) {
                super.onFocusChanged(hasFocus, direction, previouslyFocusedRect);
                Log.d(TAG, "onFocusChanged: hasFocus=" + hasFocus + " focusMode=" + focusMode);
                // 焦点模式下丢失焦点时自动重新请求
                if (!hasFocus && isAdded && focusMode) {
                    postDelayed(() -> {
                        if (isAdded && focusMode && overlayView != null) {
                            boolean requested = overlayView.requestFocus();
                            Log.d(TAG, "Re-requested focus: " + requested);
                        }
                    }, 200);
                }
            }
        };

        overlayView.setFocusable(true);
        overlayView.setFocusableInTouchMode(true);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // 默认 FLAG_NOT_FOCUSABLE + FLAG_NOT_TOUCHABLE，不抢焦点不拦截触摸
        params = new WindowManager.LayoutParams(
                1,
                1,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 0;
        params.y = 0;
    }

    /**
     * 切换焦点模式
     * @param focused true=获焦点接收摇杆事件, false=不抢焦点游戏正常
     */
    public void setFocusMode(boolean focused) {
        this.focusMode = focused;
        if (!isAdded || overlayView == null) return;

        try {
            if (focused) {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                windowManager.updateViewLayout(overlayView, params);
                overlayView.requestFocus();
                Log.d(TAG, "Focus mode ON: overlay can receive joystick events");
            } else {
                params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN;
                windowManager.updateViewLayout(overlayView, params);
                Log.d(TAG, "Focus mode OFF: game has focus");
            }
        } catch (Exception e) {
            Log.e(TAG, "setFocusMode error: " + e.getMessage());
        }
    }

    public boolean isFocusMode() {
        return focusMode;
    }

    private boolean handleGenericMotion(MotionEvent event) {
        int source = event.getSource();
        boolean isJoystick = (source & InputDevice.SOURCE_JOYSTICK) != 0
                || (source & InputDevice.SOURCE_GAMEPAD) != 0;

        Log.d(TAG, "handleGenericMotion: source=0x" + Integer.toHexString(source)
                + " action=" + event.getAction()
                + " isJoystick=" + isJoystick);

        if (!isJoystick) return false;

        TouchInjectService service = TouchInjectService.getInstance();
        Log.d(TAG, "TouchInjectService instance: " + (service != null ? "available" : "NULL"));

        if (service == null) return false;

        // 左摇杆：AXIS_X (左右), AXIS_Y (上下)
        float lx = event.getAxisValue(MotionEvent.AXIS_X);
        float ly = event.getAxisValue(MotionEvent.AXIS_Y);

        // 右摇杆：AXIS_RX (左右), AXIS_RY (上下)
        float rx = event.getAxisValue(MotionEvent.AXIS_RX);
        float ry = event.getAxisValue(MotionEvent.AXIS_RY);

        // 备用轴映射（部分手柄使用 AXIS_Z/AXIS_RZ）
        float rz = event.getAxisValue(MotionEvent.AXIS_RZ);
        float z = event.getAxisValue(MotionEvent.AXIS_Z);

        Log.d(TAG, "Axes: LX=" + String.format("%.2f", lx)
                + " LY=" + String.format("%.2f", ly)
                + " RX=" + String.format("%.2f", rx)
                + " RY=" + String.format("%.2f", ry)
                + " | Z=" + String.format("%.2f", z)
                + " RZ=" + String.format("%.2f", rz));

        // 如果 RX/RY 为 0，尝试 Z/RZ
        if (Math.abs(rx) < DEAD_ZONE && Math.abs(ry) < DEAD_ZONE) {
            if (Math.abs(z) >= DEAD_ZONE) rx = z;
            if (Math.abs(rz) >= DEAD_ZONE) ry = rz;
        }

        // 应用死区
        if (Math.abs(lx) < DEAD_ZONE) lx = 0f;
        if (Math.abs(ly) < DEAD_ZONE) ly = 0f;
        if (Math.abs(rx) < DEAD_ZONE) rx = 0f;
        if (Math.abs(ry) < DEAD_ZONE) ry = 0f;

        // 更新左摇杆
        if (lx != 0f || ly != 0f) {
            Log.d(TAG, "Left stick active: " + String.format("%.2f", lx)
                    + ", " + String.format("%.2f", ly));
            service.updateLeftStick(lx, ly);
        } else {
            service.releaseLeftStick();
        }

        // 更新右摇杆
        if (rx != 0f || ry != 0f) {
            Log.d(TAG, "Right stick active: " + String.format("%.2f", rx)
                    + ", " + String.format("%.2f", ry));
            service.updateRightStick(rx, ry);
        } else {
            service.releaseRightStick();
        }

        return true;
    }

    public void show() {
        try {
            if (overlayView == null || isAdded) return;
            windowManager.addView(overlayView, params);
            isAdded = true;
            Log.d(TAG, "Gamepad overlay shown (1x1 pixel), focusMode=" + focusMode);
        } catch (Exception e) {
            Log.e(TAG, "Failed to show overlay: " + e.getMessage());
        }
    }

    public void hide() {
        try {
            if (overlayView != null && isAdded) {
                windowManager.removeView(overlayView);
                isAdded = false;
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to hide overlay: " + e.getMessage());
        }
    }

    public void release() {
        hide();
        instance = null;
        overlayView = null;
    }
}
