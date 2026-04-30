package net.kaaass.zerotierfix.ui;

import android.view.KeyEvent;
import net.kaaass.zerotierfix.util.*;

/**
 * WiFi 小车控制器
 * 
 * 方向模型（8方向 + 停止）：
 *   0 = 停止
 *   1 = 前进         MF
 *   2 = 前进右转     FR  （差速转弯，不顿挫）
 *   3 = 右转         MR  （原地转弯）
 *   4 = 后退右转     BR
 *   5 = 后退         MB
 *   6 = 后退左转     BL
 *   7 = 左转         ML
 *   8 = 前进左转     FL
 */
public class WifiCarController {

    private static final String TAG = "WifiCarController";
    private final android.content.Context context;

    // 8方向角度分区（每区45°），从正上方顺时针
    // 方向 0° = 前，90° = 右，180° = 后，270° = 左
    private static final double[] DIR_ANGLES = {
        90.0,   // 1: 前进
        45.0,   // 2: 前进右
         0.0,   // 3: 右转
       315.0,   // 4: 后退右
       270.0,   // 5: 后退
       225.0,   // 6: 后退左
       180.0,   // 7: 左转
       135.0    // 8: 前进左
    };

    // 方向 → 命令映射
    private static final String[] DIR_CMDS = {
        Constants.CMD_STOP,       // 0: 停止
        Constants.CMD_FWD,        // 1: 前进
        Constants.CMD_FWD_RIGHT,  // 2: 前进右转（差速）
        Constants.CMD_RIGHT,      // 3: 原地右转
        Constants.CMD_BWD_RIGHT,  // 4: 后退右转
        Constants.CMD_BWD,        // 5: 后退
        Constants.CMD_BWD_LEFT,   // 6: 后退左转
        Constants.CMD_LEFT,       // 7: 原地左转
        Constants.CMD_FWD_LEFT    // 8: 前进左转（差速）
    };

    private boolean moveTaskRunning = true;
    private volatile int moveFlag = 0;
    private volatile int lastSentFlag = 0;
    private volatile boolean returnedToCenter = true;

    // 速度档位
    public enum SpeedGear {
        LOW(Constants.CMD_SPEED_LOW),
        HIGH(Constants.CMD_SPEED_HIGH);

        public final String cmd;
        SpeedGear(String cmd) { this.cmd = cmd; }
    }
    private volatile SpeedGear currentGear = SpeedGear.LOW;
    private volatile boolean speedChanged = false;

    public WifiCarController(android.content.Context context) {
        this.context = context.getApplicationContext();
    }

    private static final long KEEPALIVE_INTERVAL = 150L;
    private long lastSendTime = 0;

    private UdpSocket udpSocket;

    private Thread movingTask = new Thread() {
        @Override
        public void run() {
            lastSendTime = System.currentTimeMillis();
            while (moveTaskRunning) {
                try {
                    long now = System.currentTimeMillis();

                    // 速度切换优先发送
                    if (speedChanged) {
                        sendControlCmd(currentGear.cmd);
                        speedChanged = false;
                    }

                    int currentFlag = moveFlag;

                    // 归位后只发一次 STOP
                    if (returnedToCenter) {
                        if (lastSentFlag != 0) {
                            sendControlCmd(Constants.CMD_STOP);
                            lastSentFlag = 0;
                        }
                    } else if (currentFlag > 0) {
                        long elapsed = now - lastSendTime;
                        boolean shouldSend = (currentFlag != lastSentFlag) || (elapsed >= KEEPALIVE_INTERVAL);

                        if (shouldSend) {
                            String cmd = DIR_CMDS[currentFlag];
                            if (cmd != null) {
                                sendControlCmd(cmd);
                            }
                            lastSentFlag = currentFlag;
                            lastSendTime = now;
                        }
                    }

                    Thread.sleep(20L);
                } catch (InterruptedException e) {
                    break;
                } catch (Exception e) {
                    AppLog.e(TAG, "movingTask error: " + e.getMessage());
                }
            }
        }
    };

    /**
     * 设置速度档位
     */
    public void setSpeedGear(SpeedGear gear) {
        if (this.currentGear != gear) {
            this.currentGear = gear;
            this.speedChanged = true;
        }
    }

    public SpeedGear getSpeedGear() {
        return currentGear;
    }

    /**
     * 切换速度档位（高↔低）
     */
    public void toggleSpeed() {
        setSpeedGear(currentGear == SpeedGear.LOW ? SpeedGear.HIGH : SpeedGear.LOW);
    }

    public void backToInit() {
        synchronized (this) {
            this.moveFlag = 0;
            this.returnedToCenter = true;
        }
    }

    /**
     * 处理游戏手柄按键（用于调速）
     */
    public void onKeyUp(int keyCode, KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            // 按键 R2(104) 切换高速，L2(102) 切换低速
            if (keyCode == 104) {
                setSpeedGear(SpeedGear.HIGH);
            } else if (keyCode == 102) {
                setSpeedGear(SpeedGear.LOW);
            }
        }
    }

    /**
     * 将摇杆坐标转换为8方向命令
     */
    public void moveToPoint(int x, int y) {
        try {
            int distance = (int) Math.round(Math.sqrt(x * x + y * y));
            if (distance < 2) return;  // 死区

            double angle = Math.toDegrees(Math.atan2(-y, x));  // 注意Y轴反转
            if (angle < 0) angle += 360.0;

            int direction = findClosestDirection(angle);
            synchronized (this) {
                this.returnedToCenter = false;
                this.moveFlag = direction;
            }
        } catch (Exception e) {
            AppLog.e(TAG, "moveToPoint error: " + e.getMessage());
        }
    }

    /**
     * 找到最接近的方向（8方向）
     */
    private int findClosestDirection(double angle) {
        int best = 1;
        double bestDiff = 999;
        for (int i = 1; i <= 8; i++) {
            double diff = angleDiff(angle, DIR_ANGLES[i - 1]);
            if (diff < bestDiff) {
                bestDiff = diff;
                best = i;
            }
        }
        return best;
    }

    private double angleDiff(double a, double b) {
        double d = Math.abs(a - b);
        if (d > 180) d = 360 - d;
        return d;
    }

    public void init() {
        try {
            udpSocket = new UdpSocket();
            udpSocket.init();
            AppLog.i(TAG, "UDP initialized");
        } catch (Exception e) {
            AppLog.e(TAG, "Init error: " + e.getMessage());
        }
        new Thread(movingTask).start();
    }

    private void sendControlCmd(String cmd) {
        AppLog.i(TAG, "sendCmd: " + cmd);
        try {
            if (udpSocket != null && udpSocket.isInitialized
                    ()) {
                udpSocket.send(cmd + "\r\n", Constants.getDeviceIp(context), Constants.CAR_CONTROL_PORT);
            }
        } catch (Exception e) {
            AppLog.e(TAG, "Send error: " + e.getMessage());
        }
    }

    public void disconnect() {
        moveTaskRunning = false;
        if (udpSocket != null) {
            udpSocket.close();
        }
    }

    public boolean isConnected() {
        return udpSocket != null && udpSocket.isInitialized();
    }
}
