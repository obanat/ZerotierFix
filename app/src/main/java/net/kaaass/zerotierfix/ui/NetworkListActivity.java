package net.kaaass.zerotierfix.ui;

import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.Fragment;

import net.kaaass.zerotierfix.R;

/**
 * 网络列表 fragment 的容器 activity
 */
public class NetworkListActivity extends SingleFragmentActivity {

    private static final String TAG = "NetworkListActivity";
    private boolean doubleBackToExitPressedOnce = false;
    private NetworkListFragment networkListFragment;

    @Override
    public Fragment createFragment() {
        networkListFragment = new NetworkListFragment();
        return networkListFragment;
    }

    @Override
    public void onCreate(android.os.Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // 兼容手势导航和系统返回
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                handleBackPress();
            }
        });
    }

    /**
     * 拦截虚拟按键/实体按键的 BACK
     */
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_BACK) {
            handleBackPress();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    /**
     * 统一的返回键处理逻辑
     */
    private void handleBackPress() {
        if (doubleBackToExitPressedOnce) {
            if (networkListFragment != null) {
                networkListFragment.exitApp();
            }
            finish();
            return;
        }

        doubleBackToExitPressedOnce = true;
        Toast.makeText(this, R.string.press_back_again_to_exit, Toast.LENGTH_SHORT).show();

        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            doubleBackToExitPressedOnce = false;
        }, 2000);
    }
}
