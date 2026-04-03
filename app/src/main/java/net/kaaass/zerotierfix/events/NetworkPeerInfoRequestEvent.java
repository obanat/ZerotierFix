package net.kaaass.zerotierfix.events;

import lombok.Data;

/**
 * 请求指定网络的 Peer 信息事件
 *
 * @author kaaass
 */
@Data
public class NetworkPeerInfoRequestEvent {
    private final long nwid;

    public NetworkPeerInfoRequestEvent(long nwid) {
        this.nwid = nwid;
    }
}
