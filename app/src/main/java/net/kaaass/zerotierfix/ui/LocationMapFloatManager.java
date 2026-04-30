package net.kaaass.zerotierfix.ui;

import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.amap.api.maps2d.AMap;
import com.amap.api.maps2d.AMapOptions;
import com.amap.api.maps2d.CameraUpdateFactory;
import com.amap.api.maps2d.MapView;
import com.amap.api.maps2d.model.BitmapDescriptor;
import com.amap.api.maps2d.model.BitmapDescriptorFactory;
import com.amap.api.maps2d.model.LatLng;
import com.amap.api.maps2d.model.Marker;
import com.amap.api.maps2d.model.MarkerOptions;
import com.amap.api.maps2d.model.Polyline;
import com.amap.api.maps2d.model.PolylineOptions;

import net.kaaass.zerotierfix.util.Constants;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * 位置地图悬浮窗管理器
 * 使用高德地图 SDK 显示 GPS 定位
 * 默认右上角，可拖拽，点击切换大小（正常 / 缩小 1/3）
 * 不可获得焦点
 */
public class LocationMapFloatManager {

    private static final String TAG = "LocationMapFloat";
    private static LocationMapFloatManager instance;

    private WindowManager windowManager;
    private Context appContext;
    private FrameLayout containerView;
    private MapView mapView;
    private AMap aMap;
    private Marker locationMarker;       // 设备位置（远程 GPS）
    private Marker myLocationMarker;     // 本机位置
    private WindowManager.LayoutParams layoutParams;
    private boolean isAdded = false;

    // 本机位置
    private LocationManager locationManager;
    private double myLat = 0;
    private double myLon = 0;

    // 大小状态
    private boolean isMinimized = false;
    private int normalWidth;
    private int normalHeight;
    private int miniWidth;
    private int miniHeight;

    // 位置轮询
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

    // 拖拽相关
    private float[] dragInitial = new float[4];
    private boolean isDragging = false;
    private static final int DRAG_THRESHOLD = 10;

    // WGS84 转 GCJ02 偏移量缓存
    private double lastLat = 0;
    private double lastLon = 0;

    // 设备运动轨迹
    private final List<LatLng> trackPoints = new ArrayList<>();
    private Polyline trackPolyline;

    private LocationMapFloatManager(Context context) {
        appContext = context.getApplicationContext();
        windowManager = (WindowManager) appContext.getSystemService(Context.WINDOW_SERVICE);
        locationManager = (LocationManager) appContext.getSystemService(Context.LOCATION_SERVICE);
        initSize();
        initFloatView();
    }

    public static synchronized LocationMapFloatManager getInstance(Context context) {
        if (instance == null) {
            instance = new LocationMapFloatManager(context.getApplicationContext());
        }
        return instance;
    }

    public static LocationMapFloatManager getInstanceIfAvailable() {
        return instance;
    }

    private void initSize() {
        // 固定像素大小，不随屏幕尺寸和方向变化
        normalWidth = 240;
        normalHeight = 160;
        miniWidth = normalWidth / 2;
        miniHeight = normalHeight / 2;
    }

    private int[] getScreenSize() {
        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getRealMetrics(dm);
        return new int[]{dm.widthPixels, dm.heightPixels};
    }

    private void initFloatView() {
        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        // 容器：使用自定义 FrameLayout 拦截所有触摸事件，防止 MapView 消费
        containerView = new FrameLayout(appContext) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                // 拦截所有触摸事件，不让子 View（MapView）消费
                return true;
            }
        };

        // 地图 View
        AMapOptions mapOptions = new AMapOptions();
        mapOptions.zoomControlsEnabled(false);
        mapOptions.scaleControlsEnabled(false);
        mapOptions.logoPosition(AMapOptions.LOGO_POSITION_BOTTOM_LEFT);
        mapView = new MapView(appContext, mapOptions);
        mapView.onCreate(null);
        mapView.setClickable(false);
        mapView.setFocusable(false);
        mapView.setFocusableInTouchMode(false);

        FrameLayout.LayoutParams mapParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
        );
        containerView.addView(mapView, mapParams);

        // 初始化地图
        aMap = mapView.getMap();
        aMap.getUiSettings().setAllGesturesEnabled(false);
        aMap.getUiSettings().setZoomControlsEnabled(false);
        aMap.setMapType(AMap.MAP_TYPE_NORMAL);

        // 从缓存恢复上次地图位置，提升体验
        LatLng defaultPos;
        float defaultZoom;
        try {
            android.content.SharedPreferences sp = appContext.getSharedPreferences("location_map_cache", Context.MODE_PRIVATE);
            float cachedLat = sp.getFloat("cache_lat", 0f);
            float cachedLon = sp.getFloat("cache_lon", 0f);
            float cachedZoom = sp.getFloat("cache_zoom", 0f);
            if (cachedLat != 0f || cachedLon != 0f) {
                defaultPos = new LatLng(cachedLat, cachedLon);
                defaultZoom = cachedZoom > 0 ? cachedZoom : 16f;
            } else {
                defaultPos = new LatLng(35.0, 105.0);
                defaultZoom = 5f;
            }
        } catch (Exception e) {
            defaultPos = new LatLng(35.0, 105.0);
            defaultZoom = 5f;
        }
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultPos, defaultZoom));

        // 布局参数：右上角，FLAG_NOT_FOCUSABLE
        layoutParams = new WindowManager.LayoutParams(
                normalWidth, normalHeight,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        layoutParams.gravity = Gravity.TOP | Gravity.START;
        // 初始位置：右上角
        int[] screen = getScreenSize();
        layoutParams.x = screen[0] - normalWidth - 10;
        layoutParams.y = 10;

        // 触摸处理：拖拽 + 点击切换大小
        setupTouchListener();
    }

    private void setupTouchListener() {
        containerView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    dragInitial[0] = layoutParams.x;
                    dragInitial[1] = layoutParams.y;
                    dragInitial[2] = event.getRawX();
                    dragInitial[3] = event.getRawY();
                    isDragging = false;
                    return true;

                case MotionEvent.ACTION_MOVE:
                    float deltaX = event.getRawX() - dragInitial[2];
                    float deltaY = event.getRawY() - dragInitial[3];

                    if (Math.abs(deltaX) > DRAG_THRESHOLD || Math.abs(deltaY) > DRAG_THRESHOLD) {
                        isDragging = true;
                    }

                    if (isDragging) {
                        layoutParams.x = (int) (dragInitial[0] + deltaX);
                        layoutParams.y = (int) (dragInitial[1] + deltaY);

                        // 限制在屏幕范围内
                        int[] screen = getScreenSize();
                        int viewW = isMinimized ? miniWidth : normalWidth;
                        int viewH = isMinimized ? miniHeight : normalHeight;
                        layoutParams.x = Math.max(0, Math.min(layoutParams.x, screen[0] - viewW));
                        layoutParams.y = Math.max(0, Math.min(layoutParams.y, screen[1] - viewH));

                        try {
                            windowManager.updateViewLayout(containerView, layoutParams);
                        } catch (Exception e) {
                            Log.e(TAG, "updateViewLayout error: " + e.getMessage());
                        }
                    }
                    return true;

                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (!isDragging) {
                        // 点击切换到全屏地图
                        openFullScreenMap();
                    }
                    isDragging = false;
                    return true;
            }
            return false;
        });
    }

    /**
     * 点击悬浮窗时，隐藏悬浮窗并打开全屏地图 Activity
     */
    private void openFullScreenMap() {
        try {
            Intent intent = new Intent(appContext, LocationMapActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            intent.putExtra("lat", lastLat);
            intent.putExtra("lon", lastLon);
            if (aMap != null) {
                intent.putExtra("zoom", aMap.getCameraPosition().zoom);
            } else {
                intent.putExtra("zoom", 16f);
            }
            // 先隐藏悬浮窗
            hide();
            appContext.startActivity(intent);
            Log.d(TAG, "Opened full screen map activity");
        } catch (Exception e) {
            Log.e(TAG, "Failed to open full screen map: " + e.getMessage());
        }
    }

    public void show() {
        if (isAdded) return;
        try {
            windowManager.addView(containerView, layoutParams);
            isAdded = true;
            mapView.onResume();
            Log.d(TAG, "Location map float shown, start polling");
            // 延迟 2 秒后开始轮询，等地图初始化完成
            polling = true;
            pollHandler.postDelayed(pollRunnable, 2000);
            // 开始获取本机位置
            startMyLocationUpdates();
        } catch (Exception e) {
            Log.e(TAG, "Failed to show: " + e.getMessage());
        }
    }

    public void hide() {
        stopPolling();
        stopMyLocationUpdates();
        try {
            if (containerView != null && isAdded) {
                mapView.onPause();
                windowManager.removeView(containerView);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to hide: " + e.getMessage());
        }
        isAdded = false;
    }

    public boolean isVisible() {
        return isAdded;
    }

    /**
     * 获取设备轨迹点列表（GCJ02 坐标），供 LocationMapActivity 使用
     */
    public List<LatLng> getTrackPoints() {
        return trackPoints;
    }

    // ========== 位置轮询 ==========

    private void startPolling() {
        if (polling) return;
        polling = true;
        pollHandler.post(pollRunnable);
    }

    private void stopPolling() {
        polling = false;
        pollHandler.removeCallbacks(pollRunnable);
    }

    private final Runnable pollRunnable = new Runnable() {
        @Override
        public void run() {
            if (!polling) return;
            fetchLocation();
            pollHandler.postDelayed(this, Constants.LOCATION_POLL_INTERVAL_MS);
        }
    };

    private void fetchLocation() {
        new Thread(() -> {
            try {
                URL url = new URL(Constants.getLocationApiUrl(appContext));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int code = conn.getResponseCode();
                Log.d(TAG, "HTTP response code: " + code);
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        sb.append(line);
                    }
                    reader.close();
                    String body = sb.toString();
                    Log.d(TAG, "HTTP body: " + body);
                    parseLocation(body);
                } else {
                    Log.d(TAG, "HTTP " + code);
                }
                conn.disconnect();
            } catch (Exception e) {
                Log.e(TAG, "fetchLocation error: " + e.getMessage(), e);
            }
        }).start();
    }

    private void parseLocation(String json) {
        try {
            Log.d(TAG, "Location JSON: " + json);
            JSONObject obj = new JSONObject(json);

            // 尝试多种字段名兼容
            double lat = obj.optDouble("lat", Double.NaN);
            double lon = obj.optDouble("lon", Double.NaN);
            if (Double.isNaN(lat)) lat = obj.optDouble("latitude", 0);
            if (Double.isNaN(lon)) lon = obj.optDouble("longitude", 0);
            // 有些接口用 lng 代替 lon
            if (lon == 0 && obj.has("lng")) lon = obj.optDouble("lng", 0);

            double speed = obj.optDouble("speed", 0);
            int satellites = obj.optInt("satellites", 0);
            double altitude = obj.optDouble("altitude", 0);

            Log.d(TAG, String.format("Parsed lat=%.6f lon=%.6f speed=%.1f sat=%d alt=%.0f",
                    lat, lon, speed, satellites, altitude));

            if (lat == 0 && lon == 0) {
                Log.d(TAG, "No GPS fix");
                return;
            }

            // WGS84 转 GCJ02
            double[] gcj = wgs84ToGcj02(lat, lon);
            lastLat = gcj[0];
            lastLon = gcj[1];

            Log.d(TAG, String.format("GCJ02 lat=%.6f lon=%.6f", gcj[0], gcj[1]));
            updateMapMarker(gcj[0], gcj[1]);
        } catch (Exception e) {
            Log.e(TAG, "parseLocation error: " + e.getMessage());
        }
    }

    private void updateMapMarker(double lat, double lon) {
        pollHandler.post(() -> {
            if (aMap == null) return;
            LatLng pos = new LatLng(lat, lon);

            // 添加轨迹点并绘制轨迹线
            trackPoints.add(pos);
            updateTrackPolyline();

            if (locationMarker == null) {
                locationMarker = aMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title("Device")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        .snippet(String.format("%.6f, %.6f", lat, lon)));
            } else {
                locationMarker.setPosition(pos);
                locationMarker.setSnippet(String.format("%.6f, %.6f", lat, lon));
            }
            aMap.animateCamera(CameraUpdateFactory.newLatLngZoom(pos, 16));
        });
    }

    /**
     * 更新设备运动轨迹线
     */
    private void updateTrackPolyline() {
        if (aMap == null || trackPoints.size() < 2) return;
        if (trackPolyline != null) {
            trackPolyline.setPoints(trackPoints);
        } else {
            trackPolyline = aMap.addPolyline(new PolylineOptions()
                    .addAll(trackPoints)
                    .width(8)
                    .color(0xFF0000FF)); // 蓝色轨迹线
        }
    }

    // ========== WGS84 转 GCJ02 ==========

    // ========== 本机位置 ==========

    private void startMyLocationUpdates() {
        if (locationManager == null) return;
        try {
            // 尝试 GPS
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        3000, 0, locationListener, Looper.getMainLooper());
            }
            // 同时使用 Network 定位作为后备
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                        3000, 0, locationListener, Looper.getMainLooper());
            }
            // 立即获取最后已知位置
            Location lastKnown = null;
            try {
                if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                    lastKnown = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER);
                }
            } catch (SecurityException ignored) {}
            if (lastKnown == null) {
                try {
                    if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                        lastKnown = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                    }
                } catch (SecurityException ignored) {}
            }
            if (lastKnown != null) {
                updateMyLocation(lastKnown.getLatitude(), lastKnown.getLongitude());
            }
        } catch (SecurityException e) {
            Log.e(TAG, "No location permission: " + e.getMessage());
        }
    }

    private void stopMyLocationUpdates() {
        if (locationManager != null) {
            try {
                locationManager.removeUpdates(locationListener);
            } catch (Exception ignored) {}
        }
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            if (location != null) {
                updateMyLocation(location.getLatitude(), location.getLongitude());
            }
        }
        @Override
        public void onStatusChanged(String provider, int status, Bundle extras) {}
        @Override
        public void onProviderEnabled(String provider) {}
        @Override
        public void onProviderDisabled(String provider) {}
    };

    private void updateMyLocation(double lat, double lon) {
        myLat = lat;
        myLon = lon;
        // WGS84 → GCJ02
        double[] gcj = wgs84ToGcj02(lat, lon);
        pollHandler.post(() -> {
            if (aMap == null) return;
            LatLng pos = new LatLng(gcj[0], gcj[1]);
            if (myLocationMarker == null) {
                myLocationMarker = aMap.addMarker(new MarkerOptions()
                        .position(pos)
                        .title("My Location")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
                        .snippet(String.format("%.6f, %.6f", lat, lon)));
            } else {
                myLocationMarker.setPosition(pos);
                myLocationMarker.setSnippet(String.format("%.6f, %.6f", lat, lon));
            }
        });
    }

    private static final double PI = Math.PI;
    private static final double A = 6378245.0;
    private static final double EE = 0.00669342162296594323;

    private static boolean outOfChina(double lat, double lon) {
        return lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271;
    }

    private static double transformLat(double x, double y) {
        double ret = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(y * PI) + 40.0 * Math.sin(y / 3.0 * PI)) * 2.0 / 3.0;
        ret += (160.0 * Math.sin(y / 12.0 * PI) + 320.0 * Math.sin(y * PI / 30.0)) * 2.0 / 3.0;
        return ret;
    }

    private static double transformLon(double x, double y) {
        double ret = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * Math.sqrt(Math.abs(x));
        ret += (20.0 * Math.sin(6.0 * x * PI) + 20.0 * Math.sin(2.0 * x * PI)) * 2.0 / 3.0;
        ret += (20.0 * Math.sin(x * PI) + 40.0 * Math.sin(x / 3.0 * PI)) * 2.0 / 3.0;
        ret += (150.0 * Math.sin(x / 12.0 * PI) + 300.0 * Math.sin(x / 30.0 * PI)) * 2.0 / 3.0;
        return ret;
    }

    /**
     * WGS84 转 GCJ02（高德坐标系）
     */
    public static double[] wgs84ToGcj02(double wgsLat, double wgsLon) {
        if (outOfChina(wgsLat, wgsLon)) {
            return new double[]{wgsLat, wgsLon};
        }
        double dLat = transformLat(wgsLon - 105.0, wgsLat - 35.0);
        double dLon = transformLon(wgsLon - 105.0, wgsLat - 35.0);
        double radLat = wgsLat / 180.0 * PI;
        double magic = Math.sin(radLat);
        magic = 1 - EE * magic * magic;
        double sqrtMagic = Math.sqrt(magic);
        dLat = (dLat * 180.0) / ((A * (1 - EE)) / (magic * sqrtMagic) * PI);
        dLon = (dLon * 180.0) / (A / sqrtMagic * Math.cos(radLat) * PI);
        return new double[]{wgsLat + dLat, wgsLon + dLon};
    }

    // ========== 生命周期 ==========

    public void release() {
        stopPolling();
        stopMyLocationUpdates();
        try {
            if (windowManager != null && containerView != null && isAdded) {
                windowManager.removeView(containerView);
            }
            if (mapView != null) {
                mapView.onDestroy();
            }
        } catch (Exception e) {
            Log.e(TAG, "Error in release: " + e.getMessage());
        } finally {
            isAdded = false;
            instance = null;
            containerView = null;
            mapView = null;
            aMap = null;
            locationMarker = null;
            myLocationMarker = null;
            trackPoints.clear();
            trackPolyline = null;
        }
    }
}
