package net.kaaass.zerotierfix.util;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class UdpSocket {
    private static final String TAG = "UdpSocket";
    private DatagramSocket socket;
    private ReceiveThread receiveThread;
    private UdpReceiveListener receiveListener;
    private volatile boolean isRunning = false;

    public interface UdpReceiveListener {
        void onDataReceived(byte[] data, int length, String fromAddress, int port);
    }

    public UdpSocket() {
    }

    /**
     * 初始化UDP Socket
     * @return 是否成功
     */
    public boolean init() {
        try {
            if (socket != null && !socket.isClosed()) {
                close();
            }
            socket = new DatagramSocket();
            isRunning = true;
            AppLog.i(TAG, "UDP socket initialized on random port");
            return true;
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to initialize UDP socket: " + e.getMessage());
            return false;
        }
    }

    /**
     * 发送UDP数据
     * @param data 数据
     * @param length 数据长度
     * @param targetAddress 目标地址
     * @param targetPort 目标端口
     * @return 是否成功
     */
    public boolean send(byte[] data, int length, String targetAddress, int targetPort) {
        if (socket == null || socket.isClosed()) {
            AppLog.e(TAG, "Socket not initialized");
            return false;
        }
        try {
            InetAddress address = InetAddress.getByName(targetAddress);
            DatagramPacket packet = new DatagramPacket(data, length, address, targetPort);
            socket.send(packet);
            AppLog.i(TAG, "Sent " + length + " bytes to " + targetAddress + ":" + targetPort);
            return true;
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to send UDP packet: " + e.getMessage());
            return false;
        }
    }

    /**
     * 发送UDP数据（字符串）
     * @param data 字符串数据
     * @param targetAddress 目标地址
     * @param targetPort 目标端口
     * @return 是否成功
     */
    public boolean send(String data, String targetAddress, int targetPort) {
        if (socket == null || socket.isClosed()) {
            AppLog.e(TAG, "Socket not initialized");
            return false;
        }
        try {
            byte[] bytes = data.getBytes();
            InetAddress address = InetAddress.getByName(targetAddress);
            DatagramPacket packet = new DatagramPacket(bytes, bytes.length, address, targetPort);
            socket.send(packet);
            AppLog.i(TAG, "Sent string '" + data + "' (" + bytes.length + " bytes) to " + targetAddress + ":" + targetPort);
            return true;
        } catch (Exception e) {
            AppLog.e(TAG, "Failed to send UDP string: " + e.getMessage());
            return false;
        }
    }

    /**
     * 发送UDP数据（完整数据）
     * @param data 数据
     * @param targetAddress 目标地址
     * @param targetPort 目标端口
     * @return 是否成功
     */
    public boolean send(byte[] data, String targetAddress, int targetPort) {
        return send(data, data.length, targetAddress, targetPort);
    }

    /**
     * 开始接收数据
     * @param listener 接收监听器
     */
    public void startReceive(UdpReceiveListener listener) {
        if (socket == null || socket.isClosed()) {
            AppLog.e(TAG, "Socket not initialized");
            return;
        }
        this.receiveListener = listener;
        if (receiveThread == null || !receiveThread.isAlive()) {
            receiveThread = new ReceiveThread();
            receiveThread.start();
            AppLog.i(TAG, "Start receiving UDP packets");
        }
    }

    /**
     * 停止接收数据
     */
    public void stopReceive() {
        isRunning = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        if (receiveThread != null) {
            receiveThread.interrupt();
        }
        AppLog.i(TAG, "Stop receiving UDP packets");
    }

    /**
     * 关闭Socket
     */
    public void close() {
        stopReceive();
        if (socket != null && !socket.isClosed()) {
            socket.close();
            socket = null;
        }
        AppLog.i(TAG, "UDP socket closed");
    }

    /**
     * 获取本地端口
     * @return 本地端口，如果未初始化返回-1
     */
    public int getLocalPort() {
        if (socket != null && !socket.isClosed()) {
            return socket.getLocalPort();
        }
        return -1;
    }

    /**
     * 检查是否已初始化
     * @return 是否已初始化
     */
    public boolean isInitialized() {
        return socket != null && !socket.isClosed();
    }

    /**
     * 接收线程
     */
    private class ReceiveThread extends Thread {
        @Override
        public void run() {
            byte[] buffer = new byte[32767];
            while (isRunning && socket != null && !socket.isClosed()) {
                try {
                    DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                    socket.receive(packet);
                    int length = packet.getLength();
                    String fromAddress = packet.getAddress().getHostAddress();
                    int port = packet.getPort();

                    AppLog.i(TAG, "Received " + length + " bytes from " + fromAddress + ":" + port);

                    if (receiveListener != null) {
                        byte[] data = new byte[length];
                        System.arraycopy(packet.getData(), packet.getOffset(), data, 0, length);
                        receiveListener.onDataReceived(data, length, fromAddress, port);
                    }
                } catch (IOException e) {
                    if (isRunning) {
                        AppLog.e(TAG, "Error receiving UDP packet: " + e.getMessage());
                    }
                }
            }
        }
    }
}
