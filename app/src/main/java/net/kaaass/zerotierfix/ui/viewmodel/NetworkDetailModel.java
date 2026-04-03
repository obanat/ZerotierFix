package net.kaaass.zerotierfix.ui.viewmodel;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.zerotier.sdk.Peer;
import com.zerotier.sdk.VirtualNetworkConfig;

import net.kaaass.zerotierfix.ZerotierFixApplication;
import net.kaaass.zerotierfix.events.DefaultRouteChangedEvent;
import net.kaaass.zerotierfix.events.NetworkConfigChangedByUserEvent;
import net.kaaass.zerotierfix.events.NetworkPeerInfoReplyEvent;
import net.kaaass.zerotierfix.events.NetworkPeerInfoRequestEvent;
import net.kaaass.zerotierfix.events.VirtualNetworkConfigChangedEvent;
import net.kaaass.zerotierfix.events.VirtualNetworkConfigReplyEvent;
import net.kaaass.zerotierfix.events.VirtualNetworkConfigRequestEvent;
import net.kaaass.zerotierfix.model.Network;
import net.kaaass.zerotierfix.model.NetworkConfig;
import net.kaaass.zerotierfix.model.NetworkDao;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

/**
 * 网络详情的 ViewModel
 */
public class NetworkDetailModel extends AndroidViewModel {
    private static final String TAG = "NetworkDetailViewModel";
    private final MutableLiveData<Network> network = new MutableLiveData<>();
    private final MutableLiveData<NetworkConfig> networkConfig = new MutableLiveData<>();
    private final MutableLiveData<VirtualNetworkConfig> virtualNetworkConfig = new MutableLiveData<>();
    private final MutableLiveData<Peer[]> networkPeers = new MutableLiveData<>();
    private final EventBus eventBus = EventBus.getDefault();
    private long networkId = -1;

    public NetworkDetailModel(@NonNull Application application) {
        super(application);
        this.eventBus.register(this);
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        this.eventBus.unregister(this);
    }

    public void doRetrieveDetail(long networkId) {
        this.networkId = networkId;
        doRetrieveNetworkAndConfig();
        doRetrieveVirtualNetworkConfig();
        doRetrieveNetworkPeers();
    }

    private void doRetrieveNetworkAndConfig() {
        var networkDao = ((ZerotierFixApplication) getApplication())
                .getDaoSession().getNetworkDao();
        var queryResult = networkDao.queryBuilder()
                .where(NetworkDao.Properties.NetworkId.eq(this.networkId))
                .build().forCurrentThread().list();
        if (queryResult.size() > 1) {
            Log.e(TAG, "Data inconsistency error.  More than one network with a single ID!");
            return;
        } else if (queryResult.size() < 1) {
            Log.e(TAG, "Network not found!");
            return;
        }
        Network network = queryResult.get(0);
        this.network.setValue(network);
        this.networkConfig.setValue(network.getNetworkConfig());
    }

    private void doRetrieveVirtualNetworkConfig() {
        this.eventBus.post(new VirtualNetworkConfigRequestEvent(this.networkId));
    }

    public void doRetrieveNetworkPeers() {
        this.eventBus.post(new NetworkPeerInfoRequestEvent(this.networkId));
    }

    public void doUpdateRouteViaZeroTier(boolean routeViaZeroTier) {
        var networkConfig = this.networkConfig.getValue();
        if (networkConfig == null) {
            Log.e(TAG, "Network config not found!");
            return;
        }
        networkConfig.setRouteViaZeroTier(routeViaZeroTier);
        networkConfig.update();
        this.eventBus.post(new DefaultRouteChangedEvent(this.networkId, routeViaZeroTier));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onVirtualNetworkConfigReply(VirtualNetworkConfigReplyEvent event) {
        var config = event.getVirtualNetworkConfig();
        if (config == null) {
            Log.e(TAG, "Virtual network config not found!");
            return;
        }
        if (config.getNwid() != this.networkId) {
            return;
        }
        this.virtualNetworkConfig.setValue(config);
        var network = this.network.getValue();
        this.network.setValue(network);
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onVirtualNetworkConfigChanged(VirtualNetworkConfigChangedEvent event) {
        this.onVirtualNetworkConfigReply(new VirtualNetworkConfigReplyEvent(event.getVirtualNetworkConfig()));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNetworkConfigChangedByUser(NetworkConfigChangedByUserEvent event) {
        this.networkConfig.setValue(event.getNetwork().getNetworkConfig());
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onNetworkPeerInfoReply(NetworkPeerInfoReplyEvent event) {
        this.networkPeers.setValue(event.getPeers());
    }

    public LiveData<Network> getNetwork() {
        return network;
    }

    public LiveData<NetworkConfig> getNetworkConfig() {
        return networkConfig;
    }

    public LiveData<VirtualNetworkConfig> getVirtualNetworkConfig() {
        return virtualNetworkConfig;
    }

    public LiveData<Peer[]> getNetworkPeers() {
        return networkPeers;
    }
}
