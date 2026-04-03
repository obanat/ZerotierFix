package net.kaaass.zerotierfix.events;

import com.zerotier.sdk.Peer;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 应答指定网络的 Peer 信息事件
 *
 * @author kaaass
 */
@Data
@AllArgsConstructor
public class NetworkPeerInfoReplyEvent {

    private final Peer[] peers;
}
