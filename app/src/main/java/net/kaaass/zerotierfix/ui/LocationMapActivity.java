package net.kaaass.zerotierfix.ui;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

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
 * 全屏地图 Activity
 * 从悬浮窗点击进入，支持拖拽、双指缩放等地图手势
 * 右上角 X 按钮关闭并恢复悬浮窗
 * 启动时匹配当前屏幕方向，支持旋转
 */
public class LocationMapActivity extends Activity {

    private static final String TAG = "LocationMapActivity";

    // 缓存地图位置的 SharedPreferences key
    private static final String PREF_NAME = "location_map_cache";
    private static final String KEY_CACHE_LAT = "cache_lat";
    private static final String KEY_CACHE_LON = "cache_lon";
    private static final String KEY_CACHE_ZOOM = "cache_zoom";

    private MapView mapView;
    private AMap aMap;
    private Marker locationMarker;       // 设备位置（远程 GPS）
    private Marker myLocationMarker;     // 本机位置
    private TextView infoText;
    private Handler pollHandler = new Handler(Looper.getMainLooper());
    private boolean polling = false;

    private double lastLat = 0;
    private double lastLon = 0;

    // 本机位置
    private LocationManager locationManager;

    // 设备运动轨迹
    private final List<LatLng> trackPoints = new ArrayList<>();
    private Polyline trackPolyline;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 初始化本机位置
        locationManager = (LocationManager) getSystemService(LOCATION_SERVICE);

        // 请求位置权限
        requestLocationPermissionIfNeeded();

        // 全屏 + 保持屏幕常亮
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        applyImmersive();

        // 优先使用传入坐标，否则从缓存读取，最后使用默认值
        double initLat = getIntent().getDoubleExtra("lat", 0);
        double initLon = getIntent().getDoubleExtra("lon", 0);
        float initZoom = getIntent().getFloatExtra("zoom", 0);

        if (initLat == 0 && initLon == 0) {
            SharedPreferences sp = getSharedPreferences(PREF_NAME, MODE_PRIVATE);
            initLat = sp.getFloat(KEY_CACHE_LAT, 35.0f);
            initLon = sp.getFloat(KEY_CACHE_LON, 105.0f);
            initZoom = sp.getFloat(KEY_CACHE_ZOOM, 5f);
        }
        if (initZoom == 0) initZoom = 16f;

        // 构建 UI
        FrameLayout root = new FrameLayout(this);

        // 地图
        AMapOptions mapOptions = new AMapOptions();
        mapOptions.zoomControlsEnabled(true);
        mapOptions.scaleControlsEnabled(true);
        mapOptions.logoPosition(AMapOptions.LOGO_POSITION_BOTTOM_LEFT);
        mapView = new MapView(this, mapOptions);
        mapView.onCreate(savedInstanceState);
        root.addView(mapView, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));

        // 初始化地图 - 开启所有手势
        aMap = mapView.getMap();
        aMap.getUiSettings().setAllGesturesEnabled(true);
        aMap.getUiSettings().setZoomControlsEnabled(true);
        aMap.setMapType(AMap.MAP_TYPE_NORMAL);

        // 移动到初始位置
        LatLng initPos = new LatLng(initLat, initLon);
        aMap.moveCamera(CameraUpdateFactory.newLatLngZoom(initPos, initZoom));

        // 如果有有效坐标，直接添加 marker
            if (initLat != 35.0 || initLon != 105.0) {
                locationMarker = aMap.addMarker(new MarkerOptions()
                        .position(initPos)
                        .title("Device")
                        .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
                        .snippet(String.format("%.6f, %.6f", initLat, initLon)));
            }

        // 从 FloatManager 恢复已有轨迹点
        LocationMapFloatManager floatMgr = LocationMapFloatManager.getInstanceIfAvailable();
        if (floatMgr != null) {
            List<LatLng> savedPoints = floatMgr.getTrackPoints();
            if (savedPoints != null && !savedPoints.isEmpty()) {
                trackPoints.addAll(savedPoints);
                if (trackPoints.size() >= 2) {
                    trackPolyline = aMap.addPolyline(new PolylineOptions()
                            .addAll(trackPoints)
                            .width(8)
                            .color(0xFF0000FF));
                }
            }
        }

        // GPS 信息文字（左上角）
        infoText = new TextView(this);
        infoText.setBackgroundColor(0xCC000000);
        infoText.setTextColor(0xFFFFFFFF);
        infoText.setTextSize(13);
        infoText.setPadding(12, 6, 12, 6);
        infoText.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        FrameLayout.LayoutParams infoParams = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START
        );
        infoParams.topMargin = 20;
        infoParams.leftMargin = 10;
        root.addView(infoText, infoParams);

        // 关闭按钮（右上角 X）
        int closeButtonSize = (int) (48 * getResources().getDisplayMetrics().density);
        ImageView closeButton = new ImageView(this);
        closeButton.setBackgroundColor(0xCC000000);
        closeButton.setImageResource(android.R.drawable.ic_menu_close_clear_cancel);
        closeButton.setColorFilter(0xFFFFFFFF);
        closeButton.setPadding(8, 8, 8, 8);
        closeButton.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        FrameLayout.LayoutParams closeParams = new FrameLayout.LayoutParams(
                closeButtonSize, closeButtonSize,
                Gravity.TOP | Gravity.END
        );
        closeParams.topMargin = 10;
        closeParams.rightMargin = 10;
        root.addView(closeButton, closeParams);

        closeButton.setOnClickListener(v -> {
            Log.d(TAG, "Close button clicked, returning to float window");
            finish();
        });

        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mapView != null) mapView.onResume();
        startPolling();
        // 仅在已有位置权限时才启动定位
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                startMyLocationUpdates();
            }
        } else {
            startMyLocationUpdates();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mapView != null) mapView.onPause();
        stopPolling();
        stopMyLocationUpdates();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyImmersive();
        Log.d(TAG, "Configuration changed: " + (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE ? "LANDSCAPE" : "PORTRAIT"));
    }

    private void applyImmersive() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    @Override
    protected void onDestroy() {
        stopPolling();
        // 缓存当前地图位置
        saveMapPosition();
        if (mapView != null) mapView.onDestroy();
        // 恢复悬浮窗
        LocationMapFloatManager floatMgr = LocationMapFloatManager.getInstanceIfAvailable();
        if (floatMgr != null) {
            // 同步轨迹点回 FloatManager
            floatMgr.getTrackPoints().clear();
            floatMgr.getTrackPoints().addAll(trackPoints);
            floatMgr.show();
        }
        super.onDestroy();
    }

    /**
     * 保存地图中心点坐标和缩放级别到缓存
     */
    private void saveMapPosition() {
        try {
            if (aMap != null) {
                LatLng target = aMap.getCameraPosition().target;
                float zoom = aMap.getCameraPosition().zoom;
                getSharedPreferences(PREF_NAME, MODE_PRIVATE)
                        .edit()
                        .putFloat(KEY_CACHE_LAT, (float) target.latitude)
                        .putFloat(KEY_CACHE_LON, (float) target.longitude)
                        .putFloat(KEY_CACHE_ZOOM, zoom)
                        .apply();
                Log.d(TAG, String.format("Saved map position: lat=%.6f lon=%.6f zoom=%.1f",
                        target.latitude, target.longitude, zoom));
            }
        } catch (Exception e) {
            Log.e(TAG, "saveMapPosition error: " + e.getMessage());
        }
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
                URL url = new URL(Constants.getLocationApiUrl(getApplicationContext()));
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int code = conn.getResponseCode();
                if (code == 200) {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
                    StringBuilder sb = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) sb.append(line);
                    reader.close();
                    parseLocation(sb.toString());
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
            JSONObject obj = new JSONObject(json);

            double lat = obj.optDouble("lat", Double.NaN);
            double lon = obj.optDouble("lon", Double.NaN);
            if (Double.isNaN(lat)) lat = obj.optDouble("latitude", 0);
            if (Double.isNaN(lon)) lon = obj.optDouble("longitude", 0);
            if (lon == 0 && obj.has("lng")) lon = obj.optDouble("lng", 0);

            double speed = obj.optDouble("speed", 0);
            int satellites = obj.optInt("satellites", 0);
            double altitude = obj.optDouble("altitude", 0);

            Log.d(TAG, String.format("Parsed lat=%.6f lon=%.6f speed=%.1f sat=%d alt=%.0f",
                    lat, lon, speed, satellites, altitude));

            if (lat == 0 && lon == 0) {
                updateInfo("No GPS fix");
                return;
            }

            // WGS84 转 GCJ02
            double[] gcj = LocationMapFloatManager.wgs84ToGcj02(lat, lon);
            lastLat = gcj[0];
            lastLon = gcj[1];

            String info = String.format("%.6f, %.6f | %.1f km/h | Sat:%d | Alt:%.0fm",
                    lat, lon, speed, satellites, altitude);
            updateInfo(info);
            updateMarker(gcj[0], gcj[1]);
        } catch (Exception e) {
            Log.e(TAG, "parseLocation error: " + e.getMessage());
        }
    }

    private void updateInfo(String text) {
        pollHandler.post(() -> {
            if (infoText != null) infoText.setText(text);
        });
    }

    private void updateMarker(double lat, double lon) {
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
                    .color(0xFF0000FF));
        }
    }

    // ========== 本机位置 ==========

    private void requestLocationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
                    || checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{
                        android.Manifest.permission.ACCESS_FINE_LOCATION,
                        android.Manifest.permission.ACCESS_COARSE_LOCATION
                }, 1001);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1001) {
            boolean granted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    break;
                }
            }
            if (granted) {
                startMyLocationUpdates();
            }
        }
    }

    private void startMyLocationUpdates() {
        if (locationManager == null) return;
        try {
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER,
                        3000, 0, locationListener, Looper.getMainLooper());
            }
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
        double[] gcj = LocationMapFloatManager.wgs84ToGcj02(lat, lon);
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
}
