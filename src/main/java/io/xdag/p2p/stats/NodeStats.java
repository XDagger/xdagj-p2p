package io.xdag.p2p.stats;

import io.xdag.p2p.discover.Node;
import io.xdag.p2p.message.node.StatusMessage;
import java.net.InetSocketAddress;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NodeStats {
  private int totalCount;
  private long lastDetectTime;
  private long lastSuccessDetectTime;
  private StatusMessage statusMessage;
  private Node node;
  private InetSocketAddress socketAddress;

  public NodeStats(Node node) {
    this.node = node;
    this.socketAddress = node.getPreferInetSocketAddress();
  }

  public boolean finishDetect() {
    return this.lastDetectTime == this.lastSuccessDetectTime;
  }
}
