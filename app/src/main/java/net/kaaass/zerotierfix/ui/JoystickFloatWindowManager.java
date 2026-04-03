package net.kaaass.zerotierfix.ui;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;

import net.kaaass.zerotierfix.R;

/**
 * Joystick悬浮窗管理器
 * 负责将JoystickView以悬浮窗形式显示
 */
public class JoystickFloatWindowManager {
    private static JoystickFloatWindowManager instance;

    private WindowManager windowManager;
    private View floatView;
    private JoystickView joystickView;
    private WindowManager.LayoutParams layoutParams;

    // 拖动相关
    private float initialX;
    private float initialY;
    private float initialTouchX;
    private float initialTouchY;
    private boolean isDragging = false;

    private JoystickView.JoystickMovedListener moveListener;

    private JoystickFloatWindowManager(Context context) {
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        initFloatView(context);
    }

    /**
     * 获取单例实例
     */
    public static JoystickFloatWindowManager getInstance(Context context) {
        if (instance == null) {
            synchronized (JoystickFloatWindowManager.class) {
                if (instance == null) {
                    instance = new JoystickFloatWindowManager(context.getApplicationContext());
                }
            }
        }
        return instance;
    }

    private void initFloatView(Context context) {
        // 创建悬浮窗容器
        floatView = LayoutInflater.from(context).inflate(R.layout.float_joystick_layout, null);

        // 初始化布局参数
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        layoutParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );

        layoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
        layoutParams.x = 60;  // 初始X位置（与controller.xml中的marginLeft对应）
        layoutParams.y = 80;  // 初始Y位置（与controller.xml中的marginBottom对应）

        // 获取摇杆视图
        joystickView = floatView.findViewById(R.id.joystickView);

        // 设置触摸监听，支持拖动悬浮窗和焦点切换
        floatView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = layoutParams.x;
                        initialY = layoutParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;

                        // 点击悬浮窗时，临时获取焦点以接收键盘/手柄事件
                        requestFocus();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        float deltaX = event.getRawX() - initialTouchX;
                        float deltaY = event.getRawY() - initialTouchY;

                        // 如果移动距离超过阈值，认为是拖动
                        if (Math.abs(deltaX) > 10 || Math.abs(deltaY) > 10) {
                            isDragging = true;
                        }

                        if (isDragging) {
                            layoutParams.x = (int) (initialX + deltaX);
                            layoutParams.y = (int) (initialY - deltaY); // Y轴反向
                            windowManager.updateViewLayout(floatView, layoutParams);
                        }
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (!isDragging) {
                            // 不是拖动，可能是点击操作
                            return false;
                        }
                        isDragging = false;
                        return true;
                }
                return false;
            }
        });

        // 注意：不在这里添加视图，而是在show()方法中添加
    }

    /**
     * 设置移动监听器
     */
    public void setOnJoystickMovedListener(JoystickView.JoystickMovedListener listener) {
        this.moveListener = listener;
        if (joystickView != null) {
            joystickView.setOnJostickMovedListener(new JoystickView.JoystickMovedListener() {
                @Override
                public void OnMoved(int x, int y) {
                    if (moveListener != null) {
                        moveListener.OnMoved(x, y);
                    }
                }

                @Override
                public void OnReleased() {
                    if (moveListener != null) {
                        moveListener.OnReleased();
                    }
                }

                @Override
                public void OnReturnedToCenter() {
                    if (moveListener != null) {
                        moveListener.OnReturnedToCenter();
                    }
                }
            });
        }
    }

    /**
     * 显示悬浮窗
     */
    public void show() {
        try {
            if (floatView == null) {
                android.util.Log.e("JoystickFloatWindow", "floatView is null, cannot show");
                return;
            }

            if (isViewAdded()) {
                android.util.Log.d("JoystickFloatWindow", "Float window already added, trying to bring to front");
                try {
                    // 如果已添加，尝试更新布局以显示
                    windowManager.updateViewLayout(floatView, layoutParams);
                } catch (Exception e) {
                    android.util.Log.e("JoystickFloatWindow", "Failed to update view layout: " + e.getMessage());
                    // 如果更新失败，可能视图已失效，尝试移除后重新添加
                    try {
                        windowManager.removeView(floatView);
                    } catch (Exception ex) {
                        android.util.Log.d("JoystickFloatWindow", "View already removed or never added");
                    }
                    windowManager.addView(floatView, layoutParams);
                    android.util.Log.d("JoystickFloatWindow", "Float window re-added successfully");
                }
            } else {
                windowManager.addView(floatView, layoutParams);
                android.util.Log.d("JoystickFloatWindow", "Float window added successfully");
            }
        } catch (Exception e) {
            android.util.Log.e("JoystickFloatWindow", "Failed to show float window: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * 检查视图是否已添加到WindowManager
     */
    private boolean isViewAdded() {
        return floatView != null && floatView.getWindowToken() != null
                && floatView.isAttachedToWindow();
    }

    /**
     * 隐藏悬浮窗
     */
    public void hide() {
        try {
            if (floatView != null && isViewAdded()) {
                windowManager.removeView(floatView);
            }
        } catch (Exception e) {
            android.util.Log.e("JoystickFloatWindow", "Failed to remove float window: " + e.getMessage());
        }
    }

    /**
     * 更新悬浮窗位置
     */
    public void updatePosition(int x, int y) {
        if (layoutParams != null) {
            layoutParams.x = x;
            layoutParams.y = y;
            windowManager.updateViewLayout(floatView, layoutParams);
        }
    }

    /**
     * 请求焦点以接收键盘/手柄事件
     */
    private void requestFocus() {
        try {
            // 临时移除 FLAG_NOT_FOCUSABLE 标志以获取焦点
            int oldFlags = layoutParams.flags;
            layoutParams.flags = layoutParams.flags & ~WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
            windowManager.updateViewLayout(floatView, layoutParams);
            android.util.Log.d("JoystickFloatWindow", "Requested focus");
        } catch (Exception e) {
            android.util.Log.e("JoystickFloatWindow", "Failed to request focus: " + e.getMessage());
        }
    }

    /**
     * 释放焦点，让其他界面可以获取焦点
     */
    public void releaseFocus() {
        try {
            // 重新添加 FLAG_NOT_FOCUSABLE 标志以释放焦点
            if ((layoutParams.flags & WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE) == 0) {
                layoutParams.flags = layoutParams.flags | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
                windowManager.updateViewLayout(floatView, layoutParams);
                android.util.Log.d("JoystickFloatWindow", "Released focus");
            }
        } catch (Exception e) {
            android.util.Log.e("JoystickFloatWindow", "Failed to release focus: " + e.getMessage());
        }
    }

    /**
     * 获取当前位置
     */
    public int[] getPosition() {
        return new int[]{layoutParams.x, layoutParams.y};
    }

    /**
     * 释放资源
     */
    public void release() {
        try {
            if (windowManager != null && floatView != null) {
                windowManager.removeView(floatView);
            }
        } catch (Exception e) {
            android.util.Log.e("JoystickFloatWindow", "Error in release: " + e.getMessage());
        } finally {
            // 确保重置所有状态，防止下次复用时出现问题
            instance = null;
            floatView = null;
            joystickView = null;
        }
    }
}
