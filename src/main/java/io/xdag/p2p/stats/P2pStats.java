package io.xdag.p2p.stats;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class P2pStats {
  private long tcpOutSize;
  private long tcpInSize;
  private long tcpOutPackets;
  private long tcpInPackets;
  private long udpOutSize;
  private long udpInSize;
  private long udpOutPackets;
  private long udpInPackets;
}
