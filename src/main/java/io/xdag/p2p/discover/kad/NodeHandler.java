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

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.handler.discover.UdpEvent;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.discover.KadFindNodeMessage;
import io.xdag.p2p.message.discover.KadNeighborsMessage;
import io.xdag.p2p.message.discover.KadPingMessage;
import io.xdag.p2p.message.discover.KadPongMessage;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

@Getter
@Slf4j(topic = "net")
public class NodeHandler {
  private final P2pConfig p2pConfig;
  private final Node node;
  private volatile State state;
  private final KadService kadService;
  private NodeHandler replaceCandidate;
  private final AtomicInteger pingTrials = new AtomicInteger(2);
  private volatile boolean waitForPong = false;
  private volatile boolean waitForNeighbors = false;

  // Simple reputation system: tracks successful and failed interactions
  private final AtomicInteger reputation = new AtomicInteger(100); // Start with neutral reputation (0-200 range)
  private static final int REPUTATION_PING_TIMEOUT_PENALTY = -5;
  private static final int REPUTATION_PONG_RECEIVED_REWARD = 5;
  private static final int REPUTATION_MIN = 0;
  private static final int REPUTATION_MAX = 200;
  private static final int REPUTATION_DEAD_THRESHOLD = 20;

  public NodeHandler(Node node, KadService kadService) {
    this.p2pConfig = kadService.getP2pConfig();
    this.node = node;
    this.kadService = kadService;
    log.debug("Creating NodeHandler for node: {}", node.getPreferInetSocketAddress());
    // send ping only if IP stack is compatible
    if (node.getPreferInetSocketAddress() != null) {
      changeState(State.DISCOVERED);
    }
  }

  private void challengeWith(NodeHandler replaceCandidate) {
    this.replaceCandidate = replaceCandidate;
    changeState(State.EVICTCANDIDATE);
  }

  // Manages state transfers
  public void changeState(State newState) {
    State oldState = state;
    if (newState == State.DISCOVERED) {
      sendPing();
    }

    if (newState == State.ALIVE) {
      Node evictCandidate = kadService.getTable().addNode(this.node);
      if (evictCandidate == null) {
        newState = State.ACTIVE;
      } else {
        NodeHandler evictHandler = kadService.getNodeHandler(evictCandidate);
        if (evictHandler.state != State.EVICTCANDIDATE) {
          evictHandler.challengeWith(this);
        }
      }
    }
    if (newState == State.ACTIVE) {
      if (oldState == State.ALIVE) {
        // new node won the challenge
        kadService.getTable().addNode(node);
      } else if (oldState == State.EVICTCANDIDATE) {
        // nothing to do here the node is already in the table
      } else {
        // wrong state transition
      }
    }

    if (newState == State.DEAD) {
      if (oldState == State.EVICTCANDIDATE) {
        // lost the challenge
        // Removing ourselves from the table
        kadService.getTable().dropNode(node);
        // Congratulate the winner
        replaceCandidate.changeState(State.ACTIVE);
      } else if (oldState == State.ALIVE) {
        // ok, the old node was better, nothing to do here
      } else {
        // wrong state transition
      }
    }

    if (newState == State.EVICTCANDIDATE) {
      // trying to survive, sending ping and waiting for pong
      sendPing();
    }
    state = newState;
  }

  public void handlePing(KadPingMessage msg) {
    log.debug("handlePing from {}", node.getPreferInetSocketAddress());
    if (!kadService.getTable().getNode().equals(node)) {
      sendPong();
    }
    node.setNetworkId(msg.getNetworkId());
    node.setNetworkVersion(msg.getNetworkVersion());

    if (!node.isConnectible(p2pConfig.getNetworkId())) {
      changeState(State.DEAD);
    } else if (state.equals(State.DEAD)) {
      changeState(State.DISCOVERED);
    }
  }

  public void handlePong(KadPongMessage msg) {
    log.debug("handlePong from {}", node.getPreferInetSocketAddress());
    if (waitForPong) {
      waitForPong = false;
      node.setNetworkId(msg.getNetworkId());
      node.setNetworkVersion(msg.getNetworkVersion());

      // Reward successful response
      adjustReputation(REPUTATION_PONG_RECEIVED_REWARD);

      if (!node.isConnectible(p2pConfig.getNetworkId())) {
        changeState(State.DEAD);
      } else {
        changeState(State.ALIVE);
      }
    }
  }

  public void handleNeighbours(KadNeighborsMessage msg) {
    if (!waitForNeighbors) {
      log.warn("Receive neighbors without send find nodes");
      return;
    }
    waitForNeighbors = false;
    for (Node n : msg.getNeighbors()) {
      if (kadService.getPublicHomeNode().getId() == null || n.getId() == null ||
          !kadService.getPublicHomeNode().getId().equals(n.getId())) {
        kadService.getNodeHandler(n);
      }
    }
  }

  public void handleFindNode(KadFindNodeMessage msg) {
    List<Node> closest = kadService.getTable().getClosestNodes(msg.getTarget());
    sendNeighbours(closest, msg.getTimestamp());
  }

  public void handleTimedOut() {
    waitForPong = false;

    // Penalize timeout
    adjustReputation(REPUTATION_PING_TIMEOUT_PENALTY);

    if (pingTrials.getAndDecrement() > 0) {
      sendPing();
    } else {
      if (state == State.DISCOVERED || state == State.EVICTCANDIDATE) {
        changeState(State.DEAD);
      } else {
        // Node has a history but timed out - check reputation
        if (reputation.get() < REPUTATION_DEAD_THRESHOLD) {
          log.info("Node {} reputation too low ({}), marking as DEAD",
                   node.getPreferInetSocketAddress(), reputation.get());
          changeState(State.DEAD);
        } else {
          log.debug("Node {} timed out but has acceptable reputation ({}), keeping alive",
                    node.getPreferInetSocketAddress(), reputation.get());
        }
      }
    }
  }

  /**
   * Adjust the reputation score of this node.
   *
   * @param delta the amount to adjust (positive for reward, negative for penalty)
   */
  private void adjustReputation(int delta) {
    int oldRep = reputation.get();
    int newRep = Math.max(REPUTATION_MIN, Math.min(REPUTATION_MAX, oldRep + delta));
    reputation.set(newRep);
    log.debug("Node {} reputation: {} -> {} (delta: {})",
              node.getPreferInetSocketAddress(), oldRep, newRep, delta);
  }

  /**
   * Get the current reputation score of this node.
   *
   * @return reputation score (0-200, where 100 is neutral)
   */
  public int getReputationScore() {
    return reputation.get();
  }

  public void sendPing() {
    log.debug("Sending PING to node: {}", node.getPreferInetSocketAddress());
    KadPingMessage msg = new KadPingMessage(kadService.getPublicHomeNode(), getNode());
    waitForPong = true;
    sendMessage(msg);

    if (kadService.getPongTimer().isShutdown()) {
      return;
    }
    kadService
        .getPongTimer()
        .schedule(
            () -> {
              try {
                if (waitForPong) {
                  waitForPong = false;
                  handleTimedOut();
                }
              } catch (Exception e) {
                log.error("Unhandled exception in pong timer schedule", e);
              }
            },
            KadService.getPingTimeout(),
            TimeUnit.MILLISECONDS);
  }

  public void sendPong() {
    Message pong = new KadPongMessage();
    sendMessage(pong);
  }

  public void sendFindNode(byte[] target) {
    waitForNeighbors = true;
    KadFindNodeMessage msg =
        new KadFindNodeMessage(kadService.getPublicHomeNode(), Bytes.wrap(target));
    sendMessage(msg);
  }

  public void sendNeighbours(List<Node> neighbours, long sequence) {
    Message msg =
        new KadNeighborsMessage(kadService.getPublicHomeNode(), neighbours);
    sendMessage(msg);
  }

  private void sendMessage(Message msg) {
    kadService.sendOutbound(new UdpEvent(msg, node.getPreferInetSocketAddress()));
  }

  @Override
  public String toString() {
    return "NodeHandler[state: "
        + state
        + ", node: "
        + node.getHostKey()
        + ":"
        + node.getPort()
        + "]";
  }

  public enum State {
    /**
     * The new node was just discovered either by receiving it with the Neighbours message or by
     * receiving Ping from a new node In either case we are sending Ping and waiting for Pong If the
     * Pong is received the node becomes {@link #ALIVE} If the Pong was timed out the node becomes
     * {@link #DEAD}
     */
    DISCOVERED,
    /**
     * The node didn't send the Pong message back withing acceptable timeout This is the final state
     */
    DEAD,
    /**
     * The node responded with Pong and is now the candidate for inclusion to the table If the table
     * has bucket space for this node it is added to table and becomes {@link #ACTIVE} If the table
     * bucket is full this node is challenging with the old node from the bucket if it wins then old
     * node is dropped, and this node is added and becomes {@link #ACTIVE} else this node becomes
     * {@link #DEAD}
     */
    ALIVE,
    /**
     * The node is included in the table. It may become {@link #EVICTCANDIDATE} if a new node wants
     * to become Active but the table bucket is full.
     */
    ACTIVE,
    /**
     * This node is in the table but is currently challenging with a new Node candidate to survive
     * in the table bucket If it wins then returns back to {@link #ACTIVE} state, else is evicted
     * from the table and becomes {@link #DEAD}
     */
    EVICTCANDIDATE
  }
}
