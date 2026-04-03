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

    public static final String CMD_MOVE_STOP = "MO00";
    public static final String CMD_MOVE_UP = "MO11";
    public static final String CMD_MOVE_DOWN = "MO22";
    public static final String CMD_MOVE_LEFT = "MO10";
    public static final String CMD_MOVE_RIGHT = "MO01";

    public static final String REDIS_HOST = "i4free.x3322.net";
    public static final int REDIS_PORT = 38086;
    public static final int IPV6_CHECK_PORT = 80;
    public static final int IPV6_CHECK_TIMEOUT = 3000;

    public static final String CAR_CONTROL_IP = "10.144.0.1";//zerotier private ip of 4g dongle
    public static final int CAR_CONTROL_PORT = 34002;//udp port for control message 4g dongle
}
