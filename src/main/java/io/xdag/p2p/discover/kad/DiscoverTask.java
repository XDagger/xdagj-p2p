/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2022-2030 The XdagJ Developers
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package io.xdag.p2p.discover.kad;

import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.kad.table.KademliaOptions;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.apache.tuweni.bytes.Bytes;

@Slf4j(topic = "net")
public class DiscoverTask {

  private final ScheduledExecutorService discoverer =
      Executors.newSingleThreadScheduledExecutor(
          BasicThreadFactory.builder().namingPattern("discover-task").build());

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
            nodeId = nextTargetId();
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

  Bytes nextTargetId() {
    loopNum++;
    if (loopNum % KademliaOptions.MAX_LOOP_NUM == 0) {
      loopNum = 0;
      String idHex = kadService.getPublicHomeNode().getId();
      return StringUtils.isNotEmpty(idHex)
          ? Bytes.fromHexStringLenient(idHex)
          : Bytes.random(64);
    }
    return Bytes.random(64);
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
