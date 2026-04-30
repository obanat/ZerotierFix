package net.kaaass.zerotierfix.util;

/**
 * 维护程序中公共的常量
 *
 * @author kaaass
 */
public class Constants {

    public static final String PREF_NETWORK_USE_CELLULAR_DATA = "network_use_cellular_data";

    public static final String PREF_PLANET_USE_CUSTOM = "planet_use_custom";

    public static final String PREF_SET_PLANET_FILE = "set_planet_file";

    public static final String PREF_NETWORK_DISABLE_IPV6 = "network_disable_ipv6";

    public static final String PREF_GENERAL_START_ZEROTIER_ON_BOOT = "general_start_zerotier_on_boot";

    public static final String PREF_DISABLE_NO_NOTIFICATION_ALERT = "disable_no_notification_alert";

    public static final String FILE_CUSTOM_PLANET = "planet.custom";

    public static final String FILE_TEMP = "temp";

    public static final String FILE_PLANET = "planet";

    public static final String CHANNEL_ID = "PrivNetwork";

    public static final String VPN_SESSION_NAME = "PrivNetwork";

    // 基本运动命令（兼容旧协议）
    public static final String CMD_MOVE_STOP = "MO00";
    public static final String CMD_MOVE_UP = "MO11";
    public static final String CMD_MOVE_DOWN = "MO22";
    public static final String CMD_MOVE_LEFT = "MO10";
    public static final String CMD_MOVE_RIGHT = "MO01";

    // 新版运动命令（支持差速转弯）
    public static final String CMD_FWD = "MF";
    public static final String CMD_BWD = "MB";
    public static final String CMD_LEFT = "ML";
    public static final String CMD_RIGHT = "MR";
    public static final String CMD_STOP = "MS";
    public static final String CMD_FWD_LEFT = "FL";     // 差速前进左转
    public static final String CMD_FWD_RIGHT = "FR";    // 差速前进右转
    public static final String CMD_BWD_LEFT = "BL";     // 差速后退左转
    public static final String CMD_BWD_RIGHT = "BR";    // 差速后退右转

    // 速度档位
    public static final String CMD_SPEED_LOW = "SP0";
    public static final String CMD_SPEED_HIGH = "SP1";

    public static final String REDIS_HOST = "118.25.94.111";
    public static final int REDIS_PORT = 38086;
    public static final int IPV6_CHECK_PORT = 80;
    public static final int IPV6_CHECK_TIMEOUT = 3000;

    public static final boolean SHOW_JOYSTICKVIEW_DEF_VALUE = false;
    public static final boolean SHOW_CALIBRATION_DOT_DEF_VALUE = false;
    public static final boolean SHOW_LOCATION_MAP_DEF_VALUE = false;

    // 校准圆点坐标 SharedPreferences keys（左摇杆）
    public static final String PREF_CALIBRATION_LEFT_X = "calibration_left_x";
    public static final String PREF_CALIBRATION_LEFT_Y = "calibration_left_y";
    public static final String PREF_CALIBRATION_LEFT_CX = "calibration_left_cx";
    public static final String PREF_CALIBRATION_LEFT_CY = "calibration_left_cy";
    // 校准圆点坐标 SharedPreferences keys（右摇杆）
    public static final String PREF_CALIBRATION_RIGHT_X = "calibration_right_x";
    public static final String PREF_CALIBRATION_RIGHT_Y = "calibration_right_y";
    public static final String PREF_CALIBRATION_RIGHT_CX = "calibration_right_cx";
    public static final String PREF_CALIBRATION_RIGHT_CY = "calibration_right_cy";
    // 校准圆点坐标 SharedPreferences keys（触发键/L1）
    public static final String PREF_CALIBRATION_TRIGGER_X = "calibration_trigger_x";
    public static final String PREF_CALIBRATION_TRIGGER_Y = "calibration_trigger_y";
    public static final String PREF_CALIBRATION_TRIGGER_CX = "calibration_trigger_cx";
    public static final String PREF_CALIBRATION_TRIGGER_CY = "calibration_trigger_cy";

    // 校准圆点相对屏幕比例 SharedPreferences keys（横竖屏切换时用比例换算）
    public static final String PREF_CALIBRATION_LEFT_RX = "calibration_left_rx";
    public static final String PREF_CALIBRATION_LEFT_RY = "calibration_left_ry";
    public static final String PREF_CALIBRATION_RIGHT_RX = "calibration_right_rx";
    public static final String PREF_CALIBRATION_RIGHT_RY = "calibration_right_ry";
    public static final String PREF_CALIBRATION_TRIGGER_RX = "calibration_trigger_rx";
    public static final String PREF_CALIBRATION_TRIGGER_RY = "calibration_trigger_ry";

    public static final String DEFAULT_DEVICE_IP = "10.144.0.1";
    public static final String PREF_DEVICE_IP = "device_ip";
    public static final int CAR_CONTROL_PORT = 34002;//udp port for control message 4g dongle

    // 高德地图 API Key（需替换为实际 key）
    public static final String AMAP_API_KEY = "600e50998c6c2ce2b9cade69f541f0b9";

    // 位置查询接口
    public static final String LOCATION_API_PATH = "/cgi-bin/gpoint-location";
    public static final int LOCATION_POLL_INTERVAL_MS = 5000; // 轮询间隔 5 秒

    /**
     * 获取用户配置的设备 IP 地址
     */
    public static String getDeviceIp(android.content.Context context) {
        if (context == null) return DEFAULT_DEVICE_IP;
        android.content.SharedPreferences sp =
                android.preference.PreferenceManager.getDefaultSharedPreferences(context);
        return sp.getString(PREF_DEVICE_IP, DEFAULT_DEVICE_IP);
    }

    /**
     * 获取位置查询接口完整 URL
     */
    public static String getLocationApiUrl(android.content.Context context) {
        return "http://" + getDeviceIp(context) + LOCATION_API_PATH;
    }
}
