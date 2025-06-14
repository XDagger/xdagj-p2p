package io.xdag.p2p.discover.kad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.Node;
import io.xdag.p2p.handler.discover.UdpEvent;
import io.xdag.p2p.message.discover.kad.PingMessage;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
public class KadServiceTest {

  private P2pConfig p2pConfig;
  private KadService kadService;

  private Node homeNode;
  @Mock private Node remoteNode;

  @BeforeEach
  public void setUp() {
    p2pConfig = new P2pConfig();
    p2pConfig.setDiscoverEnable(false); // Disable discovery task for unit tests
    p2pConfig.setNodeID(Bytes.random(64));
    kadService = new KadService(p2pConfig);
    kadService.init();

    homeNode = kadService.getPublicHomeNode();

    // Setup mock remoteNode
    InetSocketAddress remoteAddress = new InetSocketAddress("127.0.0.1", 22222);
    lenient().when(remoteNode.getId()).thenReturn(Bytes.random(64));
    lenient().when(remoteNode.getInetSocketAddressV4()).thenReturn(remoteAddress);
    lenient().when(remoteNode.getPreferInetSocketAddress()).thenReturn(remoteAddress);
  }

  @Test
  public void testInit() {
    assertNotNull(kadService.getPublicHomeNode());
    assertNotNull(kadService.getTable());
    assertEquals(0, kadService.getBootNodes().size()); // No bootnodes in default config
  }

  @Test
  public void testGetNodeHandler_new() {
    NodeHandler handler = kadService.getNodeHandler(remoteNode);
    assertNotNull(handler);
    assertEquals(1, kadService.getAllNodes().size());
    assertEquals(remoteNode, handler.getNode());
  }

  @Test
  public void testGetNodeHandler_existing() {
    NodeHandler handler1 = kadService.getNodeHandler(remoteNode);
    // Ensure the same node object is passed to get the same handler
    NodeHandler handler2 = kadService.getNodeHandler(remoteNode);
    assertSame(handler1, handler2, "Should return the same handler instance for the same node");
    assertEquals(1, kadService.getAllNodes().size());
  }

  @Test
  public void testHandleEvent_ping() {
    // Spy on the real KadService to verify internal method calls
    KadService spiedService = spy(kadService);
    NodeHandler mockNodeHandler = mock(NodeHandler.class);

    // When getNodeHandler is called, return our mock handler
    doAnswer(
            invocation -> {
              Node node = invocation.getArgument(0);
              // Ensure the node passed to getNodeHandler is the one from the message
              assertEquals(remoteNode.getId(), node.getId());
              return mockNodeHandler;
            })
        .when(spiedService)
        .getNodeHandler(any(Node.class));

    when(mockNodeHandler.getNode()).thenReturn(remoteNode);

    PingMessage ping = new PingMessage(p2pConfig, remoteNode, homeNode);
    UdpEvent event = new UdpEvent(ping, remoteNode.getInetSocketAddressV4());

    spiedService.handleEvent(event);

    // Verify that the mock handler's handlePing method was called
    verify(mockNodeHandler).handlePing(ping);
    // Verify that the node was "touched"
    verify(mockNodeHandler).getNode();
    verify(remoteNode).touch();
  }

  @Test
  public void testSendOutbound() {
    // Mock the message sender
    @SuppressWarnings("unchecked")
    Consumer<UdpEvent> sender = mock(Consumer.class);
    kadService.setMessageSender(sender);
    kadService.getP2pConfig().setDiscoverEnable(true); // Enable discovery for this test

    PingMessage ping = new PingMessage(p2pConfig, homeNode, remoteNode);
    UdpEvent event = new UdpEvent(ping, remoteNode.getInetSocketAddressV4());

    kadService.sendOutbound(event);

    ArgumentCaptor<UdpEvent> captor = ArgumentCaptor.forClass(UdpEvent.class);
    verify(sender).accept(captor.capture());
    assertEquals(event, captor.getValue());
  }

  @Test
  public void testSendOutbound_disabled() {
    // Mock the message sender
    @SuppressWarnings("unchecked")
    Consumer<UdpEvent> sender = mock(Consumer.class);
    kadService.setMessageSender(sender);
    kadService.getP2pConfig().setDiscoverEnable(false); // Ensure discovery is disabled

    PingMessage ping = new PingMessage(p2pConfig, homeNode, remoteNode);
    UdpEvent event = new UdpEvent(ping, remoteNode.getInetSocketAddressV4());

    kadService.sendOutbound(event);

    // Verify sender was not called
    verify(sender, never()).accept(any());
  }

  @Test
  public void testChannelActivated() {
    // Set up bootnodes
    List<InetSocketAddress> bootnodeAddresses = new ArrayList<>();
    bootnodeAddresses.add(new InetSocketAddress("127.0.0.1", 30301));
    bootnodeAddresses.add(new InetSocketAddress("127.0.0.1", 30302));
    p2pConfig.setSeedNodes(bootnodeAddresses);

    // Re-initialize KadService to pick up the new config
    kadService = new KadService(p2pConfig);
    kadService.init();

    assertEquals(2, kadService.getBootNodes().size());
    assertEquals(0, kadService.getAllNodes().size()); // No handlers yet

    kadService.channelActivated();

    assertTrue(kadService.isInited());
    assertEquals(2, kadService.getAllNodes().size()); // Handlers should be created for bootnodes
  }
}
