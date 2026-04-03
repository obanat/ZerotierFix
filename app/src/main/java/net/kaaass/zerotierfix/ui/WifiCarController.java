package net.kaaass.zerotierfix.ui;


import android.view.KeyEvent;
import net.kaaass.zerotierfix.util.*;



/* loaded from: classes.dex */
public class WifiCarController {

    private static final String TAG = "WifiCarController";
    private boolean moveTashRunning = true;
    private int moveFlag = 0;
    private int lastMoveFlag = 0;  // 记录上一次的方向，用于检测变化
    private boolean isMoving = false;  // 是否正在移动
    private volatile boolean returnedToCenter = false;  // 归位标志，防止回弹抖动

    int speed = 10;
    private UdpSocket udpSocket;
    private Thread movingTask = new Thread() { // from class: com.bigeye.WifiCarController.1
        @Override // java.lang.Thread, java.lang.Runnable
        public void run() {
            while (WifiCarController.this.moveTashRunning) {
                try {
                    synchronized (WifiCarController.this) {
                        // 归位后忽略残余的 moveFlag，直接发 STOP 一次并保持静止
                        if (WifiCarController.this.returnedToCenter) {
                            if (WifiCarController.this.lastMoveFlag != 1) {
                                sendControlCmd(Constants.CMD_MOVE_STOP);
                                WifiCarController.this.lastMoveFlag = 1;
                                WifiCarController.this.isMoving = false;
                            }
                            WifiCarController.this.moveFlag = 1;
                        } else if (WifiCarController.this.moveFlag > 0) {
                            String cmd = null;
                            switch (WifiCarController.this.moveFlag) {
                                case 1:
                                    cmd = Constants.CMD_MOVE_STOP;
                                    break;
                                case 2:
                                    cmd = Constants.CMD_MOVE_UP;
                                    break;
                                case 3:
                                    cmd = Constants.CMD_MOVE_DOWN;
                                    break;
                                case 4:
                                    cmd = Constants.CMD_MOVE_LEFT;
                                    break;
                                case 5:
                                    cmd = Constants.CMD_MOVE_RIGHT;
                                    break;
                                default:
                                    cmd = Constants.CMD_MOVE_STOP;
                                    break;
                            }
                            // 非停止状态下持续发送，停止状态只在状态变化时发送一次
                            if (WifiCarController.this.moveFlag != 1) {
                                sendControlCmd(cmd);
                                WifiCarController.this.isMoving = true;
                            } else if (WifiCarController.this.lastMoveFlag != 1) {
                                sendControlCmd(cmd);
                                WifiCarController.this.isMoving = false;
                            }
                            WifiCarController.this.lastMoveFlag = WifiCarController.this.moveFlag;
                        } else if (WifiCarController.this.lastMoveFlag != 1) {
                            // 摇杆回到中心，发送停止命令
                            sendControlCmd(Constants.CMD_MOVE_STOP);
                            WifiCarController.this.lastMoveFlag = 1;
                            WifiCarController.this.isMoving = false;
                        }
                    }
                    Thread.sleep(100L);  // 缩短间隔到100ms，更平滑的控制
                } catch (Exception iOException) {
                    return;
                }
            }
        }
    };



    public void backToInit() {
        try {
            synchronized (this) {
                this.moveFlag = 1;
                this.returnedToCenter = true;
            }
        } catch (Exception e) {
        }
    }

    public void onKeyUp(int keyCode, KeyEvent event) {
        int action = event.getAction();
        if (action == 1) {
            if (keyCode == 102) {
                this.speed++;
                if (this.speed > 10) {
                    this.speed = 10;
                }
            } else if (keyCode == 104) {
                this.speed--;
                if (this.speed < 2) {
                    this.speed = 2;
                }
            }
        }
    }

    public void moveToPoint(int x, int y) {
        try {
            int distance = (int) Math.round(calculateDistance(x, y));
            if (distance >= 2) {  // 死区阈值从1提高到2，过滤回弹抖动
                double theta = CalculateAngle(x, y);
                synchronized (this) {
                    this.returnedToCenter = false;  // 新的移动指令，清除归位标志
                    if (MOVEMENT_ANGLES.FOWARD.isInDirection(theta)) {
                        this.moveFlag = 2;  // 前进
                    } else if (MOVEMENT_ANGLES.RIGHT.isInDirection(theta)) {
                        this.moveFlag = 5;  // 右转
                    } else if (MOVEMENT_ANGLES.LEFT.isInDirection(theta)) {
                        this.moveFlag = 4;  // 左转
                    } else if (MOVEMENT_ANGLES.BACKWARD.isInDirection(theta)) {
                        this.moveFlag = 3;  // 后退
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    private double CalculateAngle(int x, int y) {
        if (x == 0 && y > 0) {
            return 90.0d;
        }
        if (x == 0 && y < 0) {
            return 270.0d;
        }
        double theta = ((Math.atan(y / x) * 360.0d) / 2.0d) / 3.141592653589793d;
        if (x >= 0 && y >= 0) {
            if (theta < 22.5d) {
                return theta + 360.0d;
            }
            return theta;
        } else if (x < 0 && y >= 0) {
            return theta + 180.0d;
        } else {
            if (x < 0 && y < 0) {
                return theta + 180.0d;
            }
            if (x > 0 && y < 0) {
                return theta + 360.0d;
            }
            return theta;
        }
    }

    private double calculateDistance(int x, int y) {
        return Math.sqrt(Math.pow(x, 2.0d) + Math.pow(y, 2.0d));
    }

    /* loaded from: classes.dex */
    public enum MOVEMENT_ANGLES {
        FOWARD(135.0d, 46.0d),
        LEFT(225.0d, 136.0d),
        BACKWARD(315.0d, 226.0d),
        RIGHT(405.0d, 316.0d);

        private final double leftAngle;
        private final double rightAngle;



        MOVEMENT_ANGLES(double leftAngle, double rightAngle) {
            this.rightAngle = rightAngle;
            this.leftAngle = leftAngle;
        }

        public boolean isInDirection(double angle) {
            return this.leftAngle >= angle && angle >= this.rightAngle;
        }
    }

    public void init() {
        try {
            udpSocket = new UdpSocket();
            udpSocket.init();

            AppLog.i("TAG", "udp initialized!");
            //sendAnimationMessage(MainActivity.MSG_PLAY_ANIMATION_1);
        } catch (Exception e) {
            AppLog.e("TAG", "Error:" + e.getMessage());
        }
        new Thread(this.movingTask).start();

    }
    private void sendControlCmd(String cmd) {
        AppLog.i(TAG, "---->sendControlCmd: " + cmd);
        boolean rest = false;
        try {
            if (udpSocket != null && udpSocket.isInitialized()) {
                // 添加回车换行符 \r\n
                String cmdWithNewLine = cmd + "\r\n";
                rest = udpSocket.send(cmdWithNewLine, Constants.CAR_CONTROL_IP, Constants.CAR_CONTROL_PORT);
                AppLog.i(TAG, "<----sendControlCmd: " + cmd);
            } else {
                AppLog.e("TAG", "UDP socket not initialized");
            }
        } catch (Exception e) {
            AppLog.e("TAG", "Error sending control command: " + e.getMessage());
        }

    }

    /**
     * 断开UDP连接
     */
    public void disconnect() {
        if (udpSocket != null) {
            udpSocket.close();
        }
    }

    /**
     * 检查连接状态
     * @return 是否已连接
     */
    public boolean isConnected() {
        return udpSocket != null && udpSocket.isInitialized();
    }


}