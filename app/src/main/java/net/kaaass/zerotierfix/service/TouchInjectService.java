package net.kaaass.zerotierfix.service;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;

import net.kaaass.zerotierfix.ui.CalibrationFloatManager;
import net.kaaass.zerotierfix.ui.GamepadOverlayManager;
import net.kaaass.zerotierfix.util.Constants;

/**
 * 线性链 continueStroke 方案
 *
 * 关键设计：
 * - DOWN: StrokeDescription(center→target, willContinue=true) → 创建活跃指针
 * - MOVE: 从 lLast 续接，每次成功后更新 lLast，虚拟时间逐步递增
 * - UP:   从 lLast 续接 (willContinue=false)
 *
 * 每次 continueStroke 都从上一次成功的 stroke 续接，形成线性链，
 * 避免从同一个 base 分支导致系统 cancelled。
 */
public class TouchInjectService extends AccessibilityService {

    private static final String TAG = "TouchInjectService";
    private static TouchInjectService instance;
    private Handler handler = new Handler(Looper.getMainLooper());

    private static final float ACTIVE = 0.15f;
    private static final float RELEASE = 0.10f;
    private static final int DUR = 40;       // DOWN stroke 持续时间
    private static final int MOVE_DUR = 1;   // MOVE/UP stroke 持续时间

    /** 摇杆偏移半径 = 横屏宽度（较长边）的一半 */
    private int radius() {
        int[] s = screen();
        return Math.max(s[0], s[1]) / 2;
    }

    /** 将坐标钳制到屏幕范围 [0, max] */
    private int clamp(int v, int max) {
        return Math.max(0, Math.min(v, max));
    }

    // ---- 左摇杆 ----
    private boolean leftOn = false;
    private float lAx, lAy;
    private GestureDescription.StrokeDescription lLast; // 链中最后一个 stroke
    private int lPx, lPy; // 当前指针位置

    // ---- 右摇杆 ----
    private boolean rightOn = false;
    private float rAx, rAy;
    private GestureDescription.StrokeDescription rLast;
    private int rPx, rPy;

    // ---- 上次已调度的轴值（防止同一坐标重复触发 MOVE）----
    private float lastLax, lastLay;
    private float lastRax, lastRay;

    private boolean busy = false;
    private int seq = 0;

    // =================== Lifecycle ===================

    @Override public void onCreate() { super.onCreate(); instance = this; Log.i(TAG, "=== CREATED ==="); }
    @Override public void onServiceConnected() { super.onServiceConnected(); instance = this; Log.i(TAG, "=== CONNECTED ==="); }
    @Override public void onAccessibilityEvent(AccessibilityEvent e) {}
    @Override public void onInterrupt() {}
    @Override public void onDestroy() { super.onDestroy(); instance = null; }

    public static TouchInjectService getInstance() { return instance; }

    // =================== API ===================

    public void updateLeftStick(float x, float y) {
        boolean changed = axisChanged(x, y, lastLax, lastLay);
        lAx = x; lAy = y;
        Log.d(TAG, String.format("[UPD-L] (%.3f,%.3f) on=%b busy=%b changed=%b", x, y, leftOn, busy, changed));
        if (changed) schedule();
    }

    public void updateRightStick(float x, float y) {
        boolean changed = axisChanged(x, y, lastRax, lastRay);
        rAx = x; rAy = y;
        Log.d(TAG, String.format("[UPD-R] (%.3f,%.3f) on=%b busy=%b changed=%b", x, y, rightOn, busy, changed));
        if (changed) schedule();
    }

    public void releaseLeftStick()  { updateLeftStick(0, 0); }
    public void releaseRightStick() { updateRightStick(0, 0); }

    /** 判断轴值是否发生有意义的变化（阈值 0.01） */
    private boolean axisChanged(float x, float y, float ox, float oy) {
        return Math.abs(x - ox) > 0.01f || Math.abs(y - oy) > 0.01f;
    }

    // =================== 调度 ===================

    private void schedule() {
        if (busy) return;
        doProcess();
    }

    /** 回调完成后检查是否需要继续调度（轴值变化时才触发） */
    private void afterCallback() {
        busy = false;
        boolean lChanged = axisChanged(lAx, lAy, lastLax, lastLay);
        boolean rChanged = axisChanged(rAx, rAy, lastRax, lastRay);
        if (lChanged || rChanged) {
            doProcess();
        }
    }

    private void doProcess() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        GestureDescription.Builder b = new GestureDescription.Builder();
        boolean any = false;
        String act = "";

        float lm = Math.max(Math.abs(lAx), Math.abs(lAy));
        if (!leftOn && lm > ACTIVE)          { act += "L-DOWN ";  leftOn = true;  any |= doDownL(b); }
        else if (leftOn && lm >= RELEASE)    { act += "L-MOVE ";  any |= doMoveL(b); }
        else if (leftOn)                     { act += "L-UP ";    leftOn = false; any |= doUpL(b); }

        float rm = Math.max(Math.abs(rAx), Math.abs(rAy));
        if (!rightOn && rm > ACTIVE)         { act += "R-DOWN ";  rightOn = true;  any |= doDownR(b); }
        else if (rightOn && rm >= RELEASE)   { act += "R-MOVE ";  any |= doMoveR(b); }
        else if (rightOn)                    { act += "R-UP ";    rightOn = false; any |= doUpR(b); }

        if (!any) return;

        // 记录已调度的轴值（用于回调后判断是否需要再次调度）
        lastLax = lAx; lastLay = lAy;
        lastRax = rAx; lastRay = rAy;

        seq++;
        int n = seq;
        Log.d(TAG, String.format("[DISP #%d] %s", n, act));

        boolean ok = dispatchGesture(b.build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                Log.d(TAG, String.format("[DONE  #%d] ok", n));
                afterCallback();
            }
            @Override public void onCancelled(GestureDescription g) {
                Log.w(TAG, String.format("[DONE  #%d] cancelled", n));
                afterCallback();
            }
        }, handler);

        if (ok) busy = true;
        else Log.e(TAG, String.format("[DISP #%d] FAILED", n));
    }

    // =================== 左摇杆 ===================

    private boolean doDownL(GestureDescription.Builder b) {
        int[] s = screen();
        int cx = lcX(), cy = lcY();
        int tx = clamp(cx + (int)(lAx * radius()), s[0] - 1);
        int ty = clamp(cy + (int)(lAy * radius()), s[1] - 1);
        lPx = tx; lPy = ty;
        Path p = new Path(); p.moveTo(cx, cy); p.lineTo(tx, ty);
        lLast = new GestureDescription.StrokeDescription(p, 0, DUR, true);
        b.addStroke(lLast);
        Log.d(TAG, String.format("[L-DOWN] (%d,%d)->(%d,%d)", cx, cy, tx, ty));
        return true;
    }

    /** MOVE: 从 lLast 续接，形成线性链 */
    private boolean doMoveL(GestureDescription.Builder b) {
        if (lLast == null) return false;
        int[] s = screen();
        int cx = lcX(), cy = lcY();
        int tx = clamp(cx + (int)(lAx * radius()), s[0] - 1);
        int ty = clamp(cy + (int)(lAy * radius()), s[1] - 1);
        int sx = clamp(lPx, s[0] - 1), sy = clamp(lPy, s[1] - 1);
        Path p = new Path(); p.moveTo(sx, sy); p.lineTo(tx, ty);
        try {
            long st = lLast.getStartTime() + lLast.getDuration();
            GestureDescription.StrokeDescription next = lLast.continueStroke(p, st, MOVE_DUR, true);
            b.addStroke(next);
            lLast = next; // 更新链尾
            lPx = tx; lPy = ty;
            Log.v(TAG, String.format("[L-MOVE] (%d,%d)->(%d,%d) st=%d", sx, sy, tx, ty, st));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "[L-MOVE] err: " + e.getMessage());
            return false;
        }
    }

    /** UP: 从 lLast 续接释放 */
    private boolean doUpL(GestureDescription.Builder b) {
        if (lLast == null) return false;
        int[] s = screen();
        int cx = lcX(), cy = lcY();
        int sx = clamp(lPx, s[0] - 1), sy = clamp(lPy, s[1] - 1);
        Path p = new Path(); p.moveTo(sx, sy); p.lineTo(cx, cy);
        try {
            long st = lLast.getStartTime() + lLast.getDuration();
            GestureDescription.StrokeDescription next = lLast.continueStroke(p, st, MOVE_DUR, false);
            b.addStroke(next);
            Log.d(TAG, String.format("[L-UP] (%d,%d)->(%d,%d) st=%d dur=%d",
                    sx, sy, cx, cy, st, MOVE_DUR));
        } catch (Exception e) {
            Log.e(TAG, "[L-UP] err: " + e.getMessage());
        }
        lLast = null;
        return true;
    }

    // =================== 右摇杆 ===================

    private boolean doDownR(GestureDescription.Builder b) {
        int[] s = screen();
        int cx = rcX(), cy = rcY();
        int tx = clamp(cx + (int)(rAx * radius()), s[0] - 1);
        int ty = clamp(cy + (int)(rAy * radius()), s[1] - 1);
        rPx = tx; rPy = ty;
        Path p = new Path(); p.moveTo(cx, cy); p.lineTo(tx, ty);
        rLast = new GestureDescription.StrokeDescription(p, 0, DUR, true);
        b.addStroke(rLast);
        Log.d(TAG, String.format("[R-DOWN] (%d,%d)->(%d,%d)", cx, cy, tx, ty));
        return true;
    }

    private boolean doMoveR(GestureDescription.Builder b) {
        if (rLast == null) return false;
        int[] s = screen();
        int cx = rcX(), cy = rcY();
        int tx = clamp(cx + (int)(rAx * radius()), s[0] - 1);
        int ty = clamp(cy + (int)(rAy * radius()), s[1] - 1);
        int sx = clamp(rPx, s[0] - 1), sy = clamp(rPy, s[1] - 1);
        Path p = new Path(); p.moveTo(sx, sy); p.lineTo(tx, ty);
        try {
            long st = rLast.getStartTime() + rLast.getDuration();
            GestureDescription.StrokeDescription next = rLast.continueStroke(p, st, MOVE_DUR, true);
            b.addStroke(next);
            rLast = next;
            rPx = tx; rPy = ty;
            Log.v(TAG, String.format("[R-MOVE] (%d,%d)->(%d,%d) st=%d", sx, sy, tx, ty, st));
            return true;
        } catch (Exception e) {
            Log.e(TAG, "[R-MOVE] err: " + e.getMessage());
            return false;
        }
    }

    private boolean doUpR(GestureDescription.Builder b) {
        if (rLast == null) return false;
        int[] s = screen();
        int cx = rcX(), cy = rcY();
        int sx = clamp(rPx, s[0] - 1), sy = clamp(rPy, s[1] - 1);
        Path p = new Path(); p.moveTo(sx, sy); p.lineTo(cx, cy);
        try {
            long st = rLast.getStartTime() + rLast.getDuration();
            GestureDescription.StrokeDescription next = rLast.continueStroke(p, st, MOVE_DUR, false);
            b.addStroke(next);
            Log.d(TAG, String.format("[R-UP] (%d,%d)->(%d,%d) st=%d dur=%d",
                    sx, sy, cx, cy, st, MOVE_DUR));
        } catch (Exception e) {
            Log.e(TAG, "[R-UP] err: " + e.getMessage());
        }
        rLast = null;
        return true;
    }

    // =================== 按键 ===================

    @Override
    public boolean onKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
        boolean pad = (event.getSource() & InputDevice.SOURCE_GAMEPAD) != 0
                || (event.getSource() & InputDevice.SOURCE_JOYSTICK) != 0;
        if (!pad) return super.onKeyEvent(event);

        // A / L1 / R1 → 切换焦点模式
        if (keyCode == KeyEvent.KEYCODE_BUTTON_A
                || keyCode == KeyEvent.KEYCODE_BUTTON_L1
                || keyCode == KeyEvent.KEYCODE_BUTTON_R1) {
            if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                GamepadOverlayManager ov = GamepadOverlayManager.getInstanceIfAvailable();
                if (ov != null) {
                    boolean f = !ov.isFocusMode();
                    ov.setFocusMode(f);
                    CalibrationFloatManager fm = CalibrationFloatManager.getInstanceIfAvailable();
                    if (fm != null) fm.setBorderVisible(f);
                    Log.i(TAG, "[FOCUS] " + f);
                }
            }
            return true;
        }

        // L2 / R2 → 触发圆点3的触摸点击（焦点模式下生效）
        if (keyCode == KeyEvent.KEYCODE_BUTTON_L2
                || keyCode == KeyEvent.KEYCODE_BUTTON_R2) {
            GamepadOverlayManager ov = GamepadOverlayManager.getInstanceIfAvailable();
            if (ov != null && ov.isFocusMode()) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && event.getRepeatCount() == 0) {
                    injectTriggerClick();
                }
            }
            return true;
        }

        return super.onKeyEvent(event);
    }

    /**
     * 在触发圆点位置注入一个触摸点击（DOWN + UP）
     */
    private void injectTriggerClick() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;

        CalibrationFloatManager fm = CalibrationFloatManager.getInstanceIfAvailable();
        if (fm == null) return;

        int[] center = fm.getTriggerCenter();
        int cx = center[0], cy = center[1];

        Path p = new Path();
        p.moveTo(cx, cy);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(p, 0, 10, false);
        GestureDescription.Builder b = new GestureDescription.Builder();
        b.addStroke(stroke);

        seq++;
        int n = seq;
        Log.d(TAG, String.format("[TRIGGER-CLICK] (%d,%d) #%d", cx, cy, n));

        dispatchGesture(b.build(), new GestureResultCallback() {
            @Override public void onCompleted(GestureDescription g) {
                Log.d(TAG, String.format("[TRIGGER  #%d] ok", n));
            }
            @Override public void onCancelled(GestureDescription g) {
                Log.w(TAG, String.format("[TRIGGER  #%d] cancelled", n));
            }
        }, handler);
    }

    // =================== 坐标 ===================

    private int[] screen() {
        WindowManager wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        if (wm != null) {
            DisplayMetrics dm = new DisplayMetrics();
            wm.getDefaultDisplay().getRealMetrics(dm);
            return new int[]{dm.widthPixels, dm.heightPixels};
        }
        return new int[]{1080, 1920};
    }

    private int lcX() { int[] s = screen(); return PreferenceManager.getDefaultSharedPreferences(this).getInt(Constants.PREF_CALIBRATION_LEFT_CX, s[0]/4); }
    private int lcY() { int[] s = screen(); return PreferenceManager.getDefaultSharedPreferences(this).getInt(Constants.PREF_CALIBRATION_LEFT_CY, s[1]/2); }
    private int rcX() { int[] s = screen(); return PreferenceManager.getDefaultSharedPreferences(this).getInt(Constants.PREF_CALIBRATION_RIGHT_CX, s[0]*3/4); }
    private int rcY() { int[] s = screen(); return PreferenceManager.getDefaultSharedPreferences(this).getInt(Constants.PREF_CALIBRATION_RIGHT_CY, s[1]/2); }
}
