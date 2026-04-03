

// TODO: clear up
public class NetworkListFragment extends Fragment {

    private JoystickFloatWindowManager joystickFloatWindow;
    private WifiCarController wifiCarController;
    private ActivityResultLauncher<Intent> overlayPermissionLauncher;
    private SharedPreferences.OnSharedPreferenceChangeListener prefListener;
   

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        // 初始化 VPN 授权结果回调
       
        // 初始化悬浮窗权限回调
        overlayPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(), (activityResult) -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(context)) {
                showJoystickFloatWindow();
            }
        });
    }

    

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log.d(TAG, "NetworkListFragment.onCreate");
        super.onCreate(savedInstanceState);
        // 初始化 WifiCarController
        initJoystickFloatWindow();
    }

    @Override
    public void onResume() {
        super.onResume();

        // 显示摇杆悬浮窗
        requestJoystickFloatWindow();


    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        doUnbindService();
        // 反注册偏好监听
        if (prefListener != null) {
            SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
            sp.unregisterOnSharedPreferenceChangeListener(prefListener);
        }
        // 释放摇杆悬浮窗资源
        if (joystickFloatWindow != null) {
            joystickFloatWindow.release();
            joystickFloatWindow = null;
        }
        if (wifiCarController != null) {
            wifiCarController.disconnect();
        }
    }

    /**
     * 请求悬浮窗权限，权限获取后显示摇杆悬浮窗
     */
    private void requestJoystickFloatWindow() {
        if (!getBoolSharedPreference("showJoystick", true)) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(requireContext())) {
            // 无悬浮窗权限，请求权限
            var intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + requireContext().getPackageName()));
            overlayPermissionLauncher.launch(intent);
        } else {
            showJoystickFloatWindow();
        }
    }

    /**
     * 显示摇杆悬浮窗并绑定控制器
     */
    private void showJoystickFloatWindow() {
        try {
            joystickFloatWindow.show();
            Log.d(TAG, "Joystick float window shown");
        } catch (Exception e) {
            Log.e(TAG, "Failed to show joystick float window: " + e.getMessage());
        }
    }

    private void initJoystickFloatWindow() {
        if (joystickFloatWindow == null) {
            joystickFloatWindow = JoystickFloatWindowManager.getInstance(requireContext());

            wifiCarController = new WifiCarController();
            wifiCarController.init();
            // 绑定摇杆事件到 WifiCarController
            joystickFloatWindow.setOnJoystickMovedListener(new JoystickView.JoystickMovedListener() {
                @Override
                public void OnMoved(int x, int y) {
                    if (wifiCarController != null) {
                        wifiCarController.moveToPoint(x, y);
                    }
                }

                @Override
                public void OnReleased() {
                }

                @Override
                public void OnReturnedToCenter() {
                    if (wifiCarController != null) {
                        wifiCarController.backToInit();
                    }
                }
            });

        }

        // 注册偏好变化监听，实时响应开关切换
        SharedPreferences sp = PreferenceManager.getDefaultSharedPreferences(getContext());
        prefListener = (sharedPreferences, key) -> {
            if ("showJoystick".equals(key)) {
                boolean show = sharedPreferences.getBoolean(key, true);
                if (show) {
                    requestJoystickFloatWindow();
                } else if (joystickFloatWindow != null) {
                    joystickFloatWindow.hide();
                }
            }
        };
        sp.registerOnSharedPreferenceChangeListener(prefListener);
    }

    /**
     * 从 SharedPreferences 获取值
     */
    private boolean getBoolSharedPreference(String key, boolean defaultValue) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sharedPreferences.getBoolean(key, defaultValue);
    }

    private String getSharedPreference(String key, String defaultValue) {
        SharedPreferences sharedPreferences = PreferenceManager.getDefaultSharedPreferences(getContext());
        return sharedPreferences.getString(key, defaultValue);
    }
    



    /**
     * 后台获取 IPv6 地址，获取成功后检测 TCP 连接
     */
    private void fetchIpv6Address() {
        if (ipv6AddressView == null) return;
        ipv6AddressView.setText(getString(R.string.ipv6_not_available));
        ipv6StatusView.setText(getString(R.string.ipv6_disconnected));
        ipv6StatusView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
        new Thread(() -> {
            try {
                String ipv6 = getIpv6HostName();
                if (ipv6 == null || ipv6.isEmpty()) {
                    Log.w(TAG, "IPv6 address is empty");
                    return;
                }
                ipv6Handler.post(() -> ipv6AddressView.setText("IPv6: " + ipv6));
                checkIpv6TcpConnection(ipv6);
            } catch (Exception e) {
                Log.e(TAG, "Failed to fetch IPv6: " + e.getMessage());
            }
        }).start();
    }
    private String getIpv6HostName() {
        String url;
        String clientId = getSharedPreference("dev_mac", "dji0001");

        url = String.format("http://%s:%d/wificar/getClientIp?mac=%s",
                Constants.REDIS_HOST, Constants.REDIS_PORT, clientId);

        AppLog.i("MainActivity", "wificar server url:" + url);

        String ipaddr = getURLContent(url);

        AppLog.i("MainActivity", "ip v6 addr:" + ipaddr);
        return ipaddr;
    }

        private String getURLContent(String url) {
        StringBuffer sb = new StringBuffer();
        try {
            URL updateURL = new URL(url);
            URLConnection conn = updateURL.openConnection();
            BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF8"));
            String s;
            while ((s = rd.readLine()) != null) {
                sb.append(s);
            }
            rd.close();
        } catch (Exception e) {
            AppLog.e("MainActivity", "Error getting URL content: " + e.getMessage());
        }
        return sb.toString();
    }
    /**
     * TCP 连接检测 IPv6 地址的连通性
     */
    private void checkIpv6TcpConnection(String ipv6) {
        ipv6Handler.post(() -> {
            ipv6StatusView.setText(getString(R.string.ipv6_connecting));
            ipv6StatusView.setTextColor(getResources().getColor(android.R.color.holo_orange_light));
        });
        new Thread(() -> {
            boolean connected = false;
            Socket socket = null;
            try {
                socket = new Socket();
                socket.connect(new InetSocketAddress(ipv6, Constants.IPV6_CHECK_PORT), Constants.IPV6_CHECK_TIMEOUT);
                connected = true;
            } catch (Exception e) {
                Log.d(TAG, "IPv6 TCP check failed: " + e.getMessage());
            } finally {
                try { if (socket != null) socket.close(); } catch (Exception ignored) {}
            }
            boolean result = connected;
            ipv6Handler.post(() -> {
                if (result) {
                    ipv6StatusView.setText(getString(R.string.ipv6_connected));
                    ipv6StatusView.setTextColor(getResources().getColor(android.R.color.holo_green_light));
                } else {
                    ipv6StatusView.setText(getString(R.string.ipv6_disconnected));
                    ipv6StatusView.setTextColor(getResources().getColor(android.R.color.holo_red_light));
                }
            });
        }).start();
    }
}
