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
import io.xdag.p2p.message.discover.KadPacket;
import io.xdag.p2p.message.discover.KadPingMessage;
import io.xdag.p2p.message.discover.KadPongMessage;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;
import org.apache.tuweni.bytes.Bytes32;

/**
 * What this node knows about one other node, and the conversation with it.
 *
 * <p>Verification ("bonding"): a node is verified when it answers a ping of ours, sent to the endpoint we know
 * it by, with a pong that echoes the hash of that ping. Only verified nodes enter the table, are handed out
 * as peers, and get their FIND_NODE answered. A verification lasts {@link KadService#BOND_EXPIRATION_MS}.
 */
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
  private volatile long findNodeSentAt;
  private volatile int neighboursReceived;
  /** Hash of the ping we are waiting for an answer to. */
  private volatile Bytes32 pendingPingHash;
  /** When the node last answered a ping of ours (0: never). */
  private volatile long lastPongTime;

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
    this.state = State.DISCOVERED;
    log.debug("Creating NodeHandler for node: {}", node.getPreferInetSocketAddress());

    // Load existing reputation from persistence if available
    String nodeId = node.getId();
    if (nodeId != null && kadService.getReputationManager() != null) {
      int savedReputation = kadService.getReputationManager().getReputation(nodeId);
      reputation.set(savedReputation);
    }
  }

  /** Begins the conversation: pings the node to verify it. Called once the handler is registered. */
  void start() {
    if (node.getPreferInetSocketAddress() != null) {
      changeState(State.DISCOVERED);
    } else {
      log.debug("Node has no valid address, cannot send PING");
    }
  }

  /** Whether the node answered a ping of ours recently enough. */
  public boolean isVerified() {
    return lastPongTime > 0 && System.currentTimeMillis() - lastPongTime <= KadService.BOND_EXPIRATION_MS;
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
        // in the table now - or not wanted there (over a limit); either way there is nothing to challenge
        newState = kadService.getTable().contains(node) ? State.ACTIVE : State.ALIVE;
      } else {
        NodeHandler evictHandler = kadService.getNodeHandler(evictCandidate);
        if (evictHandler == null) {
          kadService.getTable().dropNode(evictCandidate);
          kadService.getTable().addNode(node);
          newState = State.ACTIVE;
        } else if (evictHandler.state != State.EVICTCANDIDATE) {
          evictHandler.challengeWith(this);
        }
      }
    }
    if (newState == State.ACTIVE) {
      if (oldState == State.ALIVE) {
        // new node won the challenge
        kadService.getTable().addNode(node);
      }
    }

    if (newState == State.DEAD) {
      if (oldState == State.EVICTCANDIDATE) {
        // lost the challenge
        kadService.getTable().dropNode(node);
        if (replaceCandidate != null) {
          replaceCandidate.changeState(State.ACTIVE);
        }
      }
    }

    if (newState == State.EVICTCANDIDATE) {
      // trying to survive, sending ping and waiting for pong
      sendPing();
    }
    state = newState;
  }

  /**
   * A ping from the node. It is always answered - a pong is about as long as a ping, so this cannot be used to
   * amplify traffic - and the node is pinged back if it is not verified yet.
   *
   * @param hash the signed hash of the ping packet, echoed in the pong
   */
  public void handlePing(KadPingMessage msg, Bytes32 hash) {
    log.trace("Received PING from node: {}", node.getPreferInetSocketAddress());
    if (!kadService.getTable().getNode().equals(node)) {
      sendPong(hash);
    }
    node.setNetworkId(msg.getNetworkId());
    node.setNetworkVersion(msg.getNetworkVersion());

    if (!isVerified() && !waitForPong && (state == State.DEAD || state == State.DISCOVERED)) {
      pingTrials.set(2);
      changeState(State.DISCOVERED);
    }
  }

  /** Kept for callers that have no packet hash; the pong then echoes nothing. */
  public void handlePing(KadPingMessage msg) {
    handlePing(msg, Bytes32.ZERO);
  }

  /**
   * A pong from the node. It counts only if it answers the ping we are waiting for: same endpoint (that is how
   * this handler was found) and the echo of that ping's hash.
   */
  public void handlePong(KadPongMessage msg) {
    log.trace("Received PONG from node: {}", node.getPreferInetSocketAddress());
    Bytes32 expected = pendingPingHash;
    if (!waitForPong || expected == null || !expected.equals(msg.getEcho())) {
      log.debug("Ignoring PONG from {} that answers no ping of ours", node.getPreferInetSocketAddress());
      return;
    }
    waitForPong = false;
    pendingPingHash = null;
    lastPongTime = System.currentTimeMillis();
    node.setNetworkId(msg.getNetworkId());
    node.setNetworkVersion(msg.getNetworkVersion());

    // Reward successful response
    adjustReputation(REPUTATION_PONG_RECEIVED_REWARD);

    if (!node.isConnectible(p2pConfig.getNetworkId())) {
      // verified, but not reachable at the port it listens on (or another network): kept out of the table
      if (state == State.EVICTCANDIDATE) {
        changeState(State.DEAD);
      } else {
        state = State.DISCOVERED;
      }
    } else if (state == State.EVICTCANDIDATE) {
      changeState(State.ACTIVE);
    } else if (state != State.ACTIVE) {
      changeState(State.ALIVE);
    }
  }

  /**
   * An answer to our FIND_NODE. The answer may come in several datagrams; they are accepted for a short while
   * after the question and up to a bucket's worth of nodes.
   */
  public void handleNeighbours(KadNeighborsMessage msg) {
    if (!waitForNeighbors || System.currentTimeMillis() - findNodeSentAt > NEIGHBORS_WINDOW_MS) {
      log.debug("Receive neighbors without send find nodes");
      waitForNeighbors = false;
      return;
    }
    log.trace("Received NEIGHBORS from node: {} ({} neighbors)",
             node.getPreferInetSocketAddress(), msg.getNeighbors().size());
    neighboursReceived += msg.getNeighbors().size();
    if (neighboursReceived >= KadNeighborsMessage.MAX_NEIGHBORS) {
      waitForNeighbors = false;
    }
    for (Node n : msg.getNeighbors()) {
      if (kadService.mayContact(n)) {
        kadService.getNodeHandler(n);
      }
    }
  }

  /** Answered only for a verified node: the answer is many times larger than the question. */
  public void handleFindNode(KadFindNodeMessage msg) {
    if (!isVerified()) {
      log.debug("Not answering FIND_NODE from unverified node {}", node.getPreferInetSocketAddress());
      return;
    }
    List<Node> closest = new ArrayList<>();
    for (Node n : kadService.getTable().getClosestNodes(msg.getTarget())) {
      if (!n.equals(node)) {
        closest.add(n);
      }
    }
    log.trace("Received FIND_NODE from node: {}, sending {} neighbors",
             node.getPreferInetSocketAddress(), closest.size());
    sendNeighbours(closest, msg.getTimestamp());
  }

  public void handleTimedOut() {
    waitForPong = false;
    pendingPingHash = null;

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
          log.debug("Node {} reputation too low ({}), marking as DEAD",
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
    log.trace("Node {} reputation: {} -> {} (delta: {})",
              node.getPreferInetSocketAddress(), oldRep, newRep, delta);

    // Persist the updated reputation
    String nodeId = node.getId();
    if (nodeId != null && kadService.getReputationManager() != null) {
      kadService.getReputationManager().setReputation(nodeId, newRep);
    }
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
    log.trace("Sending PING to node: {}", node.getPreferInetSocketAddress());
    KadPingMessage msg = new KadPingMessage(kadService.getPublicHomeNode(), getNode());
    pendingPingHash = KadPacket.hashOf(msg, p2pConfig.getNetworkId());
    waitForPong = true;
    sendMessage(msg);

    if (kadService.getPongTimer() == null || kadService.getPongTimer().isShutdown()) {
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

  public void sendPong(Bytes32 echo) {
    Message pong = new KadPongMessage(kadService.getPublicHomeNode(), echo);
    sendMessage(pong);
  }

  public void sendPong() {
    sendPong(Bytes32.ZERO);
  }

  public void sendFindNode(byte[] target) {
    log.trace("Sending FIND_NODE to node: {}", node.getPreferInetSocketAddress());
    waitForNeighbors = true;
    findNodeSentAt = System.currentTimeMillis();
    neighboursReceived = 0;
    KadFindNodeMessage msg =
        new KadFindNodeMessage(kadService.getPublicHomeNode(), Bytes.wrap(target));
    sendMessage(msg);
  }

  /** Most nodes in one NEIGHBORS datagram: keeps it under {@link KadPacket#MAX_LENGTH} even with IPv6 hosts. */
  public static final int NEIGHBORS_PER_PACKET = 8;
  /** How long after a FIND_NODE its answers are accepted. */
  static final long NEIGHBORS_WINDOW_MS = 5_000;

  /** Sends at most a bucket's worth of nodes, in datagrams small enough not to be fragmented. */
  public void sendNeighbours(List<Node> neighbours, long sequence) {
    List<Node> bounded = neighbours.size() > KadNeighborsMessage.MAX_NEIGHBORS
        ? neighbours.subList(0, KadNeighborsMessage.MAX_NEIGHBORS) : neighbours;
    for (int from = 0; from < bounded.size() || from == 0; from += NEIGHBORS_PER_PACKET) {
      List<Node> part = bounded.subList(from, Math.min(bounded.size(), from + NEIGHBORS_PER_PACKET));
      sendMessage(new KadNeighborsMessage(kadService.getPublicHomeNode(), new ArrayList<>(part)));
      if (bounded.isEmpty()) {
        break;
      }
    }
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
