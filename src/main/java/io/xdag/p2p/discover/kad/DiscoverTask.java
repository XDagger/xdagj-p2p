package io.xdag.p2p.discover.kad;

import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.kad.table.KademliaOptions;
import io.xdag.p2p.utils.NetUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.tuweni.bytes.Bytes;

@Slf4j(topic = "net")
public class DiscoverTask {

  private final ScheduledExecutorService discoverer =
      Executors.newSingleThreadScheduledExecutor(
          new BasicThreadFactory.Builder().namingPattern("discover-task").build());

  private final KadService kadService;

  private int loopNum = 0;
  private Bytes nodeId;

  public DiscoverTask(KadService kadService) {
    this.kadService = kadService;
  }

  public void init() {
    discoverer.scheduleWithFixedDelay(
        () -> {
          try {
            loopNum++;
            if (loopNum % KademliaOptions.MAX_LOOP_NUM == 0) {
              loopNum = 0;
              nodeId = Bytes.wrap(kadService.getPublicHomeNode().getId());
            } else {
              nodeId = Bytes.wrap(NetUtils.getNodeId());
            }
            discover(nodeId, 0, new ArrayList<>());
          } catch (Exception e) {
            log.error("DiscoverTask fails to be executed", e);
          }
        },
        1,
        KademliaOptions.DISCOVER_CYCLE,
        TimeUnit.MILLISECONDS);
    log.debug("DiscoverTask started");
  }

  private void discover(Bytes nodeId, int round, List<Node> prevTriedNodes) {

    List<Node> closest = kadService.getTable().getClosestNodes(nodeId);
    List<Node> tried = new ArrayList<>();
    for (Node n : closest) {
      if (!tried.contains(n) && !prevTriedNodes.contains(n)) {
        try {
          kadService.getNodeHandler(n).sendFindNode(nodeId.toArray());
          tried.add(n);
        } catch (Exception e) {
          log.error("Unexpected Exception occurred while sending FindNodeMessage", e);
        }
      }

      if (tried.size() == KademliaOptions.ALPHA) {
        break;
      }
    }

    try {
      Thread.sleep(KademliaOptions.WAIT_TIME);
    } catch (InterruptedException e) {
      log.warn("Discover task interrupted");
      Thread.currentThread().interrupt();
    }

    if (tried.isEmpty()) {
      return;
    }

    if (++round == KademliaOptions.MAX_STEPS) {
      return;
    }
    tried.addAll(prevTriedNodes);
    discover(nodeId, round, tried);
  }

  public void close() {
    discoverer.shutdownNow();
  }
}
