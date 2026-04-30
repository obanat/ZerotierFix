package net.kaaass.zerotierfix.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.PixelFormat;
import android.os.Build;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import net.kaaass.zerotierfix.util.Constants;

/**
 * 悬浮校准圆点管理器（三圆点：左摇杆/右摇杆/触发键）
 * 左圆点限制在左半屏，右圆点限制在右半屏，触发圆点可全屏拖拽
 * 支持横竖屏切换，使用相对比例存储位置，切换时自动重新布局
 * 焦点模式下显示边框并且不可拖拽，非焦点模式下隐藏边框并可拖拽
 */
public class CalibrationFloatManager {

    private static CalibrationFloatManager instance;

    private WindowManager windowManager;
    private Context appContext;
    private FrameLayout leftDotView;
    private FrameLayout rightDotView;
    private FrameLayout triggerDotView;
    private View leftBorder;
    private View rightBorder;
    private View triggerBorder;
    private WindowManager.LayoutParams leftParams;
    private WindowManager.LayoutParams rightParams;
    private WindowManager.LayoutParams triggerParams;
    private boolean leftAdded = false;
    private boolean rightAdded = false;
    private boolean triggerAdded = false;

    // 边框是否可见（等同于焦点模式）
    private boolean borderVisible = false;

    // 上次记录的屏幕尺寸，用于检测横竖屏切换
    private int lastScreenWidth = 0;
    private int lastScreenHeight = 0;

    private CalibrationFloatManager(Context context) {
        appContext = context.getApplicationContext();
        windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        initDotViews(appContext);
    }

    public static synchronized CalibrationFloatManager getInstance(Context context) {
        if (instance == null) {
            instance = new CalibrationFloatManager(context.getApplicationContext());
        }
        return instance;
    }

    /**
     * 获取已有实例，不自动创建（供 TouchInjectService 调用）
     */
    public static CalibrationFloatManager getInstanceIfAvailable() {
        return instance;
    }

    /**
     * 动态获取当前屏幕尺寸
     */
    private int[] getScreenSize() {
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    /**
     * 从 SharedPreferences 读取相对比例，换算为当前屏幕的像素坐标
     * 如果没有保存过比例，返回默认像素位置
     */
    private int[] loadPixelFromRatio(SharedPreferences sp, String keyRx, String keyRy,
                                      int defaultX, int defaultY) {
        float rx = sp.getFloat(keyRx, -1f);
        float ry = sp.getFloat(keyRy, -1f);
        if (rx < 0 || ry < 0) {
            return new int[]{defaultX, defaultY};
        }
        int[] screen = getScreenSize();
        return new int[]{Math.round(rx * screen[0]), Math.round(ry * screen[1])};
    }

    private void initDotViews(Context context) {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(context);
        int[] screen = getScreenSize();
        lastScreenWidth = screen[0];
        lastScreenHeight = screen[1];

        // 默认像素位置
        int defaultLeftX = screen[0] / 4 - dpToPx(context, 10);
        int defaultRightX = screen[0] * 3 / 4 - dpToPx(context, 10);
        int defaultY = screen[1] / 2 - dpToPx(context, 10);
        int triggerDotSize = dpToPx(context, 36);
        int defaultTriggerX = screen[0] * 7 / 8 - triggerDotSize / 2;
        int defaultTriggerY = screen[1] * 7 / 8 - triggerDotSize / 2;

        // === 左圆点（蓝色，10px 半径）===
        leftDotView = createDotView(context, 0x3044AAFF, 0xFF44AAFF, 10 * 2);
        leftBorder = leftDotView.findViewWithTag("border");
        leftParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        leftParams.gravity = Gravity.TOP | Gravity.START;
        int[] leftPos = loadPixelFromRatio(sp,
                Constants.PREF_CALIBRATION_LEFT_RX, Constants.PREF_CALIBRATION_LEFT_RY,
                defaultLeftX, defaultY);
        leftParams.x = leftPos[0];
        leftParams.y = leftPos[1];
        setupDrag(leftDotView, leftParams, 0); // 限制左半屏

        // === 右圆点（红色，10px 半径）===
        rightDotView = createDotView(context, 0x30FF4444, 0xFFFF4444, 10 * 2);
        rightBorder = rightDotView.findViewWithTag("border");
        rightParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        rightParams.gravity = Gravity.TOP | Gravity.START;
        int[] rightPos = loadPixelFromRatio(sp,
                Constants.PREF_CALIBRATION_RIGHT_RX, Constants.PREF_CALIBRATION_RIGHT_RY,
                defaultRightX, defaultY);
        rightParams.x = rightPos[0];
        rightParams.y = rightPos[1];
        setupDrag(rightDotView, rightParams, 1); // 限制右半屏

        // === 触发圆点（黄色，原始大小 36dp）===
        triggerDotView = createDotView(context, 0x30FFDD00, 0xFFFFFF00, triggerDotSize);
        triggerBorder = triggerDotView.findViewWithTag("border");
        // 触发圆点默认无边框
        if (triggerBorder != null) triggerBorder.setVisibility(View.GONE);
        triggerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
        );
        triggerParams.gravity = Gravity.TOP | Gravity.START;
        int[] triggerPos = loadPixelFromRatio(sp,
                Constants.PREF_CALIBRATION_TRIGGER_RX, Constants.PREF_CALIBRATION_TRIGGER_RY,
                defaultTriggerX, defaultTriggerY);
        triggerParams.x = triggerPos[0];
        triggerParams.y = triggerPos[1];
        setupDrag(triggerDotView, triggerParams, 2); // 全屏
    }

    /**
     * 创建圆点视图
     * @param fillColor    内圆填充色
     * @param borderColor  边框色
     * @param dotSize      内圆直径（px）
     */
    private FrameLayout createDotView(Context context, int fillColor, int borderColor, int dotSize) {
        int touchArea = dpToPx(context, 96);
        int borderWidth = dpToPx(context, 2);

        // 内圆
        View innerDot = new View(context);
        android.graphics.drawable.GradientDrawable dotDrawable = new android.graphics.drawable.GradientDrawable();
        dotDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        dotDrawable.setColor(fillColor);
        innerDot.setBackground(dotDrawable);

        FrameLayout.LayoutParams dotParams = new FrameLayout.LayoutParams(dotSize, dotSize);
        dotParams.gravity = Gravity.CENTER;

        // 边框圆（略大于内圆）
        int borderSize = dotSize + borderWidth * 2;
        View border = new View(context);
        android.graphics.drawable.GradientDrawable borderDrawable = new android.graphics.drawable.GradientDrawable();
        borderDrawable.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        borderDrawable.setColor(0x00000000); // 透明
        borderDrawable.setStroke(borderWidth, borderColor);
        border.setBackground(borderDrawable);
        border.setTag("border");
        border.setVisibility(View.GONE); // 默认隐藏

        FrameLayout.LayoutParams borderParams = new FrameLayout.LayoutParams(borderSize, borderSize);
        borderParams.gravity = Gravity.CENTER;

        // 容器
        FrameLayout container = new FrameLayout(context);
        container.addView(border, borderParams);
        container.addView(innerDot, dotParams);

        // 根布局
        FrameLayout root = new FrameLayout(context);
        root.addView(container, new FrameLayout.LayoutParams(touchArea, touchArea));

        return root;
    }

    /**
     * 设置边框是否可见（焦点模式指示器）
     * 焦点模式下：圆点加 FLAG_NOT_TOUCHABLE，不响应触摸，显示边框
     * 非焦点模式下：圆点可触摸拖拽，隐藏边框，用于校准位置
     */
    public void setBorderVisible(boolean visible) {
        this.borderVisible = visible;
        if (leftBorder != null) {
            leftBorder.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (rightBorder != null) {
            rightBorder.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        if (triggerBorder != null) {
            triggerBorder.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        updateTouchableFlags(visible);
    }

    /**
     * 根据焦点模式更新圆点的 FLAG_NOT_TOUCHABLE 标志
     */
    private void updateTouchableFlags(boolean focusMode) {
        try {
            if (leftAdded && leftDotView != null && leftParams != null) {
                if (focusMode) {
                    leftParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                } else {
                    leftParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                }
                windowManager.updateViewLayout(leftDotView, leftParams);
            }
            if (rightAdded && rightDotView != null && rightParams != null) {
                if (focusMode) {
                    rightParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                } else {
                    rightParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                }
                windowManager.updateViewLayout(rightDotView, rightParams);
            }
            if (triggerAdded && triggerDotView != null && triggerParams != null) {
                if (focusMode) {
                    triggerParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                } else {
                    triggerParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                }
                windowManager.updateViewLayout(triggerDotView, triggerParams);
            }
        } catch (Exception e) {
            android.util.Log.e("CalibrationFloat", "updateTouchableFlags error: " + e.getMessage());
        }
    }

    public boolean isBorderVisible() {
        return borderVisible;
    }

    /**
     * 设置拖拽监听
     * @param restrictMode 0=限制左半屏, 1=限制右半屏, 2=全屏可拖拽
     */
    private void setupDrag(View dotView, WindowManager.LayoutParams params, int restrictMode) {
        float[] initial = new float[4];

        dotView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initial[0] = params.x;
                    initial[1] = params.y;
                    initial[2] = event.getRawX();
                    initial[3] = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    int newX = (int) (initial[0] + (event.getRawX() - initial[2]));
                    int newY = (int) (initial[1] + (event.getRawY() - initial[3]));

                    int[] screen = getScreenSize();
                    int halfWidth = screen[0] / 2;
                    int viewW = v.getWidth();
                    if (restrictMode == 0) {
                        // 限制左半屏
                        newX = Math.max(0, Math.min(newX, halfWidth - viewW));
                    } else if (restrictMode == 1) {
                        // 限制右半屏
                        newX = Math.max(halfWidth, Math.min(newX, screen[0] - viewW));
                    } else {
                        // 全屏
                        newX = Math.max(0, Math.min(newX, screen[0] - viewW));
                    }
                    newY = Math.max(0, Math.min(newY, screen[1] - v.getHeight()));

                    params.x = newX;
                    params.y = newY;
                    windowManager.updateViewLayout(dotView, params);
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    savePositions();
                    return true;
            }
            return false;
        });
    }

    /**
     * 保存位置：同时保存绝对像素坐标（兼容）和相对比例（用于横竖屏切换恢复）
     */
    private void savePositions() {
        Context ctx = leftDotView != null ? leftDotView.getContext() : null;
        if (ctx == null) return;

        int[] screen = getScreenSize();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(ctx);
        SharedPreferences.Editor editor = sp.edit();

        int screenW = Math.max(screen[0], 1);
        int screenH = Math.max(screen[1], 1);

        // 左圆点
        editor.putInt(Constants.PREF_CALIBRATION_LEFT_X, leftParams.x);
        editor.putInt(Constants.PREF_CALIBRATION_LEFT_Y, leftParams.y);
        editor.putInt(Constants.PREF_CALIBRATION_LEFT_CX, leftParams.x + leftDotView.getWidth() / 2);
        editor.putInt(Constants.PREF_CALIBRATION_LEFT_CY, leftParams.y + leftDotView.getHeight() / 2);
        editor.putFloat(Constants.PREF_CALIBRATION_LEFT_RX, (float) leftParams.x / screenW);
        editor.putFloat(Constants.PREF_CALIBRATION_LEFT_RY, (float) leftParams.y / screenH);

        // 右圆点
        editor.putInt(Constants.PREF_CALIBRATION_RIGHT_X, rightParams.x);
        editor.putInt(Constants.PREF_CALIBRATION_RIGHT_Y, rightParams.y);
        editor.putInt(Constants.PREF_CALIBRATION_RIGHT_CX, rightParams.x + rightDotView.getWidth() / 2);
        editor.putInt(Constants.PREF_CALIBRATION_RIGHT_CY, rightParams.y + rightDotView.getHeight() / 2);
        editor.putFloat(Constants.PREF_CALIBRATION_RIGHT_RX, (float) rightParams.x / screenW);
        editor.putFloat(Constants.PREF_CALIBRATION_RIGHT_RY, (float) rightParams.y / screenH);

        // 触发圆点
        editor.putInt(Constants.PREF_CALIBRATION_TRIGGER_X, triggerParams.x);
        editor.putInt(Constants.PREF_CALIBRATION_TRIGGER_Y, triggerParams.y);
        editor.putInt(Constants.PREF_CALIBRATION_TRIGGER_CX, triggerParams.x + triggerDotView.getWidth() / 2);
        editor.putInt(Constants.PREF_CALIBRATION_TRIGGER_CY, triggerParams.y + triggerDotView.getHeight() / 2);
        editor.putFloat(Constants.PREF_CALIBRATION_TRIGGER_RX, (float) triggerParams.x / screenW);
        editor.putFloat(Constants.PREF_CALIBRATION_TRIGGER_RY, (float) triggerParams.y / screenH);

        // 记录当前屏幕尺寸
        lastScreenWidth = screen[0];
        lastScreenHeight = screen[1];

        editor.apply();
    }

    /**
     * 检测横竖屏切换，用相对比例重新布局圆点位置
     */
    private void repositionForCurrentScreen() {
        int[] screen = getScreenSize();

        // 屏幕尺寸未变化，无需重新定位
        if (lastScreenWidth == screen[0] && lastScreenHeight == screen[1]) return;

        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(appContext);

        // 从相对比例恢复位置
        int[] leftPos = loadPixelFromRatio(sp,
                Constants.PREF_CALIBRATION_LEFT_RX, Constants.PREF_CALIBRATION_LEFT_RY,
                screen[0] / 4 - dpToPx(appContext, 10), screen[1] / 2 - dpToPx(appContext, 10));
        int[] rightPos = loadPixelFromRatio(sp,
                Constants.PREF_CALIBRATION_RIGHT_RX, Constants.PREF_CALIBRATION_RIGHT_RY,
                screen[0] * 3 / 4 - dpToPx(appContext, 10), screen[1] / 2 - dpToPx(appContext, 10));
        int triggerDotSize = dpToPx(appContext, 36);
        int[] triggerPos = loadPixelFromRatio(sp,
                Constants.PREF_CALIBRATION_TRIGGER_RX, Constants.PREF_CALIBRATION_TRIGGER_RY,
                screen[0] * 7 / 8 - triggerDotSize / 2, screen[1] * 7 / 8 - triggerDotSize / 2);

        leftParams.x = leftPos[0];
        leftParams.y = leftPos[1];
        rightParams.x = rightPos[0];
        rightParams.y = rightPos[1];
        triggerParams.x = triggerPos[0];
        triggerParams.y = triggerPos[1];

        lastScreenWidth = screen[0];
        lastScreenHeight = screen[1];

        // 如果圆点已显示，更新布局
        try {
            if (leftAdded) windowManager.updateViewLayout(leftDotView, leftParams);
            if (rightAdded) windowManager.updateViewLayout(rightDotView, rightParams);
            if (triggerAdded) windowManager.updateViewLayout(triggerDotView, triggerParams);
        } catch (Exception e) {
            android.util.Log.e("CalibrationFloat", "reposition error: " + e.getMessage());
        }

        // 同步保存当前屏幕的绝对坐标
        savePositions();

        android.util.Log.i("CalibrationFloat", "Repositioned for screen " + screen[0] + "x" + screen[1]
                + " using ratio");
    }

    public void show() {
        repositionForCurrentScreen();
        try {
            if (leftDotView != null && !leftAdded) {
                windowManager.addView(leftDotView, leftParams);
                leftAdded = true;
            }
            if (rightDotView != null && !rightAdded) {
                windowManager.addView(rightDotView, rightParams);
                rightAdded = true;
            }
            if (triggerDotView != null && !triggerAdded) {
                windowManager.addView(triggerDotView, triggerParams);
                triggerAdded = true;
            }
        } catch (Exception e) {
            android.util.Log.e("CalibrationFloat", "Failed to show: " + e.getMessage());
        }

        // 注册屏幕配置变化监听（横竖屏切换）
        registerOrientationListener();
    }

    public void hide() {
        unregisterOrientationListener();
        try {
            if (leftDotView != null && leftAdded) {
                windowManager.removeView(leftDotView);
                leftAdded = false;
            }
            if (rightDotView != null && rightAdded) {
                windowManager.removeView(rightDotView);
                rightAdded = false;
            }
            if (triggerDotView != null && triggerAdded) {
                windowManager.removeView(triggerDotView);
                triggerAdded = false;
            }
        } catch (Exception e) {
            android.util.Log.e("CalibrationFloat", "Failed to hide: " + e.getMessage());
        }
    }

    /**
     * 校准圆点是否可见
     */
    public boolean isVisible() {
        return leftAdded || rightAdded || triggerAdded;
    }

    /**
     * 获取左摇杆校准中心坐标（从相对比例换算）
     */
    public int[] getLeftCenter() {
        int[] screen = getScreenSize();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(appContext);
        float rx = sp.getFloat(Constants.PREF_CALIBRATION_LEFT_RX, -1f);
        float ry = sp.getFloat(Constants.PREF_CALIBRATION_LEFT_RY, -1f);
        if (rx >= 0 && ry >= 0) {
            // 比例存在，根据比例计算圆心（加上视图宽高的一半）
            int cx = Math.round(rx * screen[0]) + (leftDotView != null ? leftDotView.getWidth() / 2 : 0);
            int cy = Math.round(ry * screen[1]) + (leftDotView != null ? leftDotView.getHeight() / 2 : 0);
            return new int[]{cx, cy};
        }
        // 无比例数据，用旧的绝对坐标兜底
        return new int[]{
                sp.getInt(Constants.PREF_CALIBRATION_LEFT_CX, screen[0] / 4),
                sp.getInt(Constants.PREF_CALIBRATION_LEFT_CY, screen[1] / 2)
        };
    }

    /**
     * 获取右摇杆校准中心坐标（从相对比例换算）
     */
    public int[] getRightCenter() {
        int[] screen = getScreenSize();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(appContext);
        float rx = sp.getFloat(Constants.PREF_CALIBRATION_RIGHT_RX, -1f);
        float ry = sp.getFloat(Constants.PREF_CALIBRATION_RIGHT_RY, -1f);
        if (rx >= 0 && ry >= 0) {
            int cx = Math.round(rx * screen[0]) + (rightDotView != null ? rightDotView.getWidth() / 2 : 0);
            int cy = Math.round(ry * screen[1]) + (rightDotView != null ? rightDotView.getHeight() / 2 : 0);
            return new int[]{cx, cy};
        }
        return new int[]{
                sp.getInt(Constants.PREF_CALIBRATION_RIGHT_CX, screen[0] * 3 / 4),
                sp.getInt(Constants.PREF_CALIBRATION_RIGHT_CY, screen[1] / 2)
        };
    }

    /**
     * 获取触发键校准中心坐标（从相对比例换算）
     */
    public int[] getTriggerCenter() {
        int[] screen = getScreenSize();
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(appContext);
        float rx = sp.getFloat(Constants.PREF_CALIBRATION_TRIGGER_RX, -1f);
        float ry = sp.getFloat(Constants.PREF_CALIBRATION_TRIGGER_RY, -1f);
        if (rx >= 0 && ry >= 0) {
            int cx = Math.round(rx * screen[0]) + (triggerDotView != null ? triggerDotView.getWidth() / 2 : 0);
            int cy = Math.round(ry * screen[1]) + (triggerDotView != null ? triggerDotView.getHeight() / 2 : 0);
            return new int[]{cx, cy};
        }
        return new int[]{
                sp.getInt(Constants.PREF_CALIBRATION_TRIGGER_CX, screen[0] * 7 / 8),
                sp.getInt(Constants.PREF_CALIBRATION_TRIGGER_CY, screen[1] * 7 / 8)
        };
    }

    // ========== 横竖屏切换监听 ==========

    private View.OnLayoutChangeListener orientationLayoutListener;

    /**
     * 注册横竖屏切换监听：通过根视图的布局变化检测屏幕尺寸变化
     */
    private void registerOrientationListener() {
        if (orientationLayoutListener != null) return;

        // 使用一个不可见的根视图监听全局布局变化
        orientationLayoutListener = (view, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom) -> {
            int newW = right - left;
            int newH = bottom - top;
            if (newW > 0 && newH > 0 && (newW != lastScreenWidth || newH != lastScreenHeight)) {
                android.util.Log.i("CalibrationFloat", "Layout change detected: " + newW + "x" + newH
                        + " (was " + lastScreenWidth + "x" + lastScreenHeight + ")");
                repositionForCurrentScreen();
            }
        };

        // 给一个已添加的圆点注册布局变化监听（通过它间接感知屏幕变化）
        if (leftDotView != null) {
            leftDotView.addOnLayoutChangeListener(orientationLayoutListener);
        }
    }

    /**
     * 注销横竖屏切换监听
     */
    private void unregisterOrientationListener() {
        if (orientationLayoutListener != null && leftDotView != null) {
            leftDotView.removeOnLayoutChangeListener(orientationLayoutListener);
        }
        orientationLayoutListener = null;
    }

    public void release() {
        unregisterOrientationListener();
        try {
            if (windowManager != null) {
                if (leftDotView != null && leftAdded) windowManager.removeView(leftDotView);
                if (rightDotView != null && rightAdded) windowManager.removeView(rightDotView);
                if (triggerDotView != null && triggerAdded) windowManager.removeView(triggerDotView);
            }
        } catch (Exception e) {
            android.util.Log.e("CalibrationFloat", "Error in release: " + e.getMessage());
        } finally {
            leftAdded = false;
            rightAdded = false;
            triggerAdded = false;
            instance = null;
            leftDotView = null;
            rightDotView = null;
            triggerDotView = null;
            orientationLayoutListener = null;
        }
    }

    private int dpToPx(Context context, int dp) {
        float density = context.getResources().getDisplayMetrics().density;
        return (int) (dp * density + 0.5f);
    }
}
