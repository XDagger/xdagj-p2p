package io.xdag.p2p.stats;

public class P2pStatsManager {

  public P2pStats getP2pStats() {
    P2pStats stats = new P2pStats();
    stats.setTcpInPackets(TrafficStats.getTcp().getInPackets().get());
    stats.setTcpOutPackets(TrafficStats.getTcp().getOutPackets().get());
    stats.setTcpInSize(TrafficStats.getTcp().getInSize().get());
    stats.setTcpOutSize(TrafficStats.getTcp().getOutSize().get());
    stats.setUdpInPackets(TrafficStats.getUdp().getInPackets().get());
    stats.setUdpOutPackets(TrafficStats.getUdp().getOutPackets().get());
    stats.setUdpInSize(TrafficStats.getUdp().getInSize().get());
    stats.setUdpOutSize(TrafficStats.getUdp().getOutSize().get());
    return stats;
  }
}
