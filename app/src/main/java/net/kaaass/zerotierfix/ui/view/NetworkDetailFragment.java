package net.kaaass.zerotierfix.ui.view;

import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TableRow;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.zerotier.sdk.Peer;
import com.zerotier.sdk.PeerPhysicalPath;
import com.zerotier.sdk.VirtualNetworkConfig;
import net.kaaass.zerotierfix.util.StringUtils;

import net.kaaass.zerotierfix.R;
import net.kaaass.zerotierfix.model.Network;
import net.kaaass.zerotierfix.model.NetworkConfig;
import net.kaaass.zerotierfix.model.type.DNSMode;
import net.kaaass.zerotierfix.model.type.NetworkStatus;
import net.kaaass.zerotierfix.model.type.NetworkType;
import net.kaaass.zerotierfix.ui.NetworkListFragment;
import net.kaaass.zerotierfix.ui.viewmodel.NetworkDetailModel;
import net.kaaass.zerotierfix.util.Constants;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class NetworkDetailFragment extends Fragment {
    private static final String TAG = "NetworkDetailView";

    private NetworkDetailModel viewModel;
    private TextView idView;
    private TextView nameView;
    private TextView statusView;
    private TextView typeView;
    private TextView macView;
    private TextView mtuView;
    private TextView broadcastView;
    private TextView bridgingView;
    private TextView dnsModeView;
    private CheckBox routeViaZtView;
    private TextView ipAddressesView;
    private TableRow dnsView;
    private TextView dnsServersView;

    private SwipeRefreshLayout peerSwipeRefresh;
    private RecyclerView peerRecyclerView;
    private TextView peerEmptyView;
    private PeerListAdapter peerAdapter;
    private final List<Peer> peerList = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.viewModel = (NetworkDetailModel) new ViewModelProvider(this).get(NetworkDetailModel.class);
        if (getArguments() != null) {
            long networkId = getArguments().getLong(NetworkListFragment.NETWORK_ID_MESSAGE);
            viewModel.doRetrieveDetail(networkId);
        } else {
            Log.e(TAG, "Network ID is not set!");
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_network_detail, container, false);

        this.idView = view.findViewById(R.id.network_detail_network_id);
        this.nameView = view.findViewById(R.id.network_detail_network_name);
        this.statusView = view.findViewById(R.id.network_status_textview);
        this.typeView = view.findViewById(R.id.network_type_textview);
        this.macView = view.findViewById(R.id.network_mac_textview);
        this.mtuView = view.findViewById(R.id.network_mtu_textview);
        this.broadcastView = view.findViewById(R.id.network_broadcast_textview);
        this.bridgingView = view.findViewById(R.id.network_bridging_textview);
        this.dnsModeView = view.findViewById(R.id.network_dns_mode_textview);
        this.routeViaZtView = view.findViewById(R.id.network_default_route);
        this.ipAddressesView = view.findViewById(R.id.network_ipaddresses_textview);
        this.dnsView = view.findViewById(R.id.custom_dns_row);
        this.dnsServersView = view.findViewById(R.id.network_dns_textview);

        this.peerSwipeRefresh = view.findViewById(R.id.swipe_refresh_network_peers);
        this.peerRecyclerView = view.findViewById(R.id.network_peer_list);
        this.peerEmptyView = view.findViewById(R.id.network_peer_no_data);
        this.peerAdapter = new PeerListAdapter(this.peerList);
        this.peerRecyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        this.peerRecyclerView.setAdapter(this.peerAdapter);
        this.peerSwipeRefresh.setOnRefreshListener(() -> {
            viewModel.doRetrieveNetworkPeers();
            peerSwipeRefresh.setRefreshing(false);
        });

        this.routeViaZtView.setOnCheckedChangeListener((buttonView, isChecked) ->
                viewModel.doUpdateRouteViaZeroTier(isChecked));

        viewModel.getNetwork().observe(getViewLifecycleOwner(), this::updateNetwork);
        viewModel.getNetworkConfig().observe(getViewLifecycleOwner(), this::updateNetworkConfig);
        viewModel.getVirtualNetworkConfig().observe(getViewLifecycleOwner(), this::updateVirtualNetworkConfig);
        viewModel.getNetworkPeers().observe(getViewLifecycleOwner(), this::updateNetworkPeers);

        return view;
    }

    @UiThread
    private void updateNetwork(Network network) {
        if (network == null) {
            return;
        }
        this.idView.setText(network.getNetworkIdStr());
        if (network.getNetworkName() != null && !network.getNetworkName().isEmpty()) {
            this.nameView.setText(network.getNetworkName());
        } else {
            this.nameView.setText(getString(R.string.empty_network_name));
        }
    }

    @UiThread
    private void updateNetworkConfig(NetworkConfig networkConfig) {
        if (networkConfig == null) {
            return;
        }
        var dnsMode = DNSMode.fromInt(networkConfig.getDnsMode());
        this.dnsModeView.setText(dnsMode.toStringId());
        this.dnsView.setVisibility(dnsMode == DNSMode.NETWORK_DNS ? View.VISIBLE : View.INVISIBLE);
        this.routeViaZtView.setChecked(networkConfig.getRouteViaZeroTier());
    }

    @UiThread
    private void updateVirtualNetworkConfig(VirtualNetworkConfig virtualNetworkConfig) {
        if (virtualNetworkConfig == null) {
            return;
        }
        var ztType = virtualNetworkConfig.getType();
        var type = NetworkType.fromVirtualNetworkType(ztType);
        this.typeView.setText(type.toStringId());

        var ztStatus = virtualNetworkConfig.getStatus();
        var status = NetworkStatus.fromVirtualNetworkStatus(ztStatus);
        this.statusView.setText(status.toStringId());

        this.macView.setText(com.zerotier.sdk.util.StringUtils.macAddressToString(virtualNetworkConfig.getMac()));
        this.mtuView.setText(String.valueOf(virtualNetworkConfig.getMtu()));
        this.broadcastView.setText(booleanToLocalString(virtualNetworkConfig.isBroadcastEnabled()));
        this.bridgingView.setText(booleanToLocalString(virtualNetworkConfig.isBridge()));

        var addresses = virtualNetworkConfig.getAssignedAddresses();
        var strAssignedAddresses = new StringBuilder();
        for (int i = 0; i < addresses.length; i++) {
            strAssignedAddresses.append(inetSocketAddressToString(addresses[i]));
            if (i < addresses.length - 1) {
                strAssignedAddresses.append('\n');
            }
        }
        this.ipAddressesView.setText(strAssignedAddresses.toString());

        var dns = virtualNetworkConfig.getDns();
        if (dns != null) {
            var dnsServers = dns.getServers();
            var strDnsServers = new StringBuilder();
            for (int i = 0; i < dnsServers.size(); i++) {
                strDnsServers.append(inetSocketAddressToString(dnsServers.get(i)));
                if (i < dnsServers.size() - 1) {
                    strDnsServers.append('\n');
                }
            }
            this.dnsServersView.setText(strDnsServers.toString());
        } else {
            this.dnsServersView.setText("");
        }
    }

    private String booleanToLocalString(boolean z) {
        return z ? getString(R.string.enabled) : getString(R.string.disabled);
    }

    private String inetSocketAddressToString(InetSocketAddress inetSocketAddress) {
        if (inetSocketAddress == null) {
            return null;
        }
        boolean disableIpv6 = PreferenceManager
                .getDefaultSharedPreferences(getActivity())
                .getBoolean(Constants.PREF_NETWORK_DISABLE_IPV6, false);
        try {
            InetAddress address = inetSocketAddress.getAddress();
            if (address instanceof Inet6Address && disableIpv6) {
                return null;
            }
            var strAddress = address.toString();
            if (strAddress.startsWith("/")) {
                strAddress = strAddress.substring(1);
            }
            return strAddress + "/" + inetSocketAddress.getPort();
        } catch (Exception ignored) {
        }
        return null;
    }

    @UiThread
    private void updateNetworkPeers(Peer[] peers) {
        this.peerList.clear();
        if (peers != null) {
            Collections.addAll(this.peerList, peers);
        }
        this.peerAdapter.notifyDataSetChanged();
        boolean empty = this.peerList.isEmpty();
        this.peerEmptyView.setVisibility(empty ? View.VISIBLE : View.GONE);
        this.peerRecyclerView.setVisibility(empty ? View.GONE : View.VISIBLE);
    }

    private class PeerListAdapter extends RecyclerView.Adapter<PeerListAdapter.ViewHolder> {

        private final List<Peer> mValues;

        public PeerListAdapter(List<Peer> items) {
            mValues = items;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.list_item_peer, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(final ViewHolder holder, int position) {
            Peer peer = mValues.get(position);
            holder.mAddress.setText(com.zerotier.sdk.util.StringUtils.addressToString(peer.getAddress()));
            holder.mRole.setVisibility(View.GONE);
            holder.mLatency.setText(String.format(getString(R.string.peer_lat), peer.getLatency()));
            String clientVersion = getString(R.string.unknown_version);
            if (peer.getVersionMajor() > 0) {
                clientVersion = StringUtils.peerVersionString(peer);
            }
            holder.mVersion.setText(clientVersion);
            PeerPhysicalPath preferred = null;
            if (peer.getPaths() != null) {
                for (PeerPhysicalPath path : peer.getPaths()) {
                    if (path.isPreferred()) {
                        preferred = path;
                        break;
                    }
                }
            }
            String strPreferred = getString(R.string.peer_relay);
            if (preferred != null) {
                strPreferred = StringUtils.toString(preferred.getAddress());
            }
            holder.mPath.setText(strPreferred);
        }

        @Override
        public int getItemCount() {
            return mValues.size();
        }

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final TextView mAddress;
            public final TextView mRole;
            public final TextView mVersion;
            public final TextView mLatency;
            public final TextView mPath;

            public ViewHolder(View view) {
                super(view);
                mAddress = view.findViewById(R.id.list_peer_addr);
                mRole = view.findViewById(R.id.list_peer_role);
                mVersion = view.findViewById(R.id.list_peer_ver);
                mLatency = view.findViewById(R.id.list_peer_lat);
                mPath = view.findViewById(R.id.list_peer_path);
            }
        }
    }
}
