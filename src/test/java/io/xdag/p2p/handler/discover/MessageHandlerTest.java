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
package io.xdag.p2p.handler.discover;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MessageHandlerTest {

    private MessageHandler messageHandler;
    private NioDatagramChannel mockChannel;
    private EventHandler mockEventHandler;
    private ChannelHandlerContext mockCtx;

    @BeforeEach
    void setUp() {
        mockChannel = mock(NioDatagramChannel.class);
        mockEventHandler = mock(EventHandler.class);
        mockCtx = mock(ChannelHandlerContext.class);

        messageHandler = new MessageHandler(mockChannel, mockEventHandler);
    }

    // ==================== Constructor Tests ====================

    @Test
    void testConstructor() {
        assertNotNull(messageHandler, "MessageHandler should be created");
    }

    @Test
    void testConstructorWithValidParams() {
        MessageHandler handler = new MessageHandler(mockChannel, mockEventHandler);
        assertNotNull(handler, "Should create handler with valid parameters");
    }

    // ==================== channelActive Tests ====================

    @Test
    void testChannelActive() {
        messageHandler.channelActive(mockCtx);

        verify(mockEventHandler, times(1)).channelActivated();
    }

    @Test
    void testChannelActiveCalledMultipleTimes() {
        messageHandler.channelActive(mockCtx);
        messageHandler.channelActive(mockCtx);
        messageHandler.channelActive(mockCtx);

        verify(mockEventHandler, times(3)).channelActivated();
    }

    // ==================== channelRead0 Tests ====================

    @Test
    void testChannelRead0WithValidUdpEvent() {
        // Create mock message
        Message mockMessage = mock(Message.class);
        when(mockMessage.getType()).thenReturn(MessageCode.KAD_PING);
        Bytes mockData = Bytes.wrap(new byte[]{1, 2, 3, 4, 5});
        when(mockMessage.getSendData()).thenReturn(mockData);

        // Create UdpEvent
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10000);
        UdpEvent udpEvent = new UdpEvent(mockMessage, address);

        // Call channelRead0
        messageHandler.channelRead0(mockCtx, udpEvent);

        // Verify eventHandler.handleEvent was called
        verify(mockEventHandler, times(1)).handleEvent(udpEvent);
    }

    @Test
    void testChannelRead0WithDifferentMessageCodes() {
        InetSocketAddress address = new InetSocketAddress("192.168.1.100", 16783);

        // Test PING message
        Message pingMessage = mock(Message.class);
        when(pingMessage.getType()).thenReturn(MessageCode.KAD_PING);
        when(pingMessage.getSendData()).thenReturn(Bytes.wrap(new byte[10]));
        UdpEvent pingEvent = new UdpEvent(pingMessage, address);
        messageHandler.channelRead0(mockCtx, pingEvent);

        // Test PONG message
        Message pongMessage = mock(Message.class);
        when(pongMessage.getType()).thenReturn(MessageCode.KAD_PONG);
        when(pongMessage.getSendData()).thenReturn(Bytes.wrap(new byte[15]));
        UdpEvent pongEvent = new UdpEvent(pongMessage, address);
        messageHandler.channelRead0(mockCtx, pongEvent);

        // Test FIND_NODE message
        Message findNodeMessage = mock(Message.class);
        when(findNodeMessage.getType()).thenReturn(MessageCode.KAD_FIND_NODE);
        when(findNodeMessage.getSendData()).thenReturn(Bytes.wrap(new byte[20]));
        UdpEvent findNodeEvent = new UdpEvent(findNodeMessage, address);
        messageHandler.channelRead0(mockCtx, findNodeEvent);

        verify(mockEventHandler, times(3)).handleEvent(any(UdpEvent.class));
    }

    @Test
    void testChannelRead0WithEmptyData() {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getType()).thenReturn(MessageCode.KAD_PING);
        when(mockMessage.getSendData()).thenReturn(Bytes.EMPTY);

        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10000);
        UdpEvent udpEvent = new UdpEvent(mockMessage, address);

        messageHandler.channelRead0(mockCtx, udpEvent);

        verify(mockEventHandler, times(1)).handleEvent(udpEvent);
    }

    @Test
    void testChannelRead0WithLargeData() {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getType()).thenReturn(MessageCode.KAD_NEIGHBORS);
        byte[] largeData = new byte[1500]; // MTU size
        when(mockMessage.getSendData()).thenReturn(Bytes.wrap(largeData));

        InetSocketAddress address = new InetSocketAddress("10.0.0.1", 8080);
        UdpEvent udpEvent = new UdpEvent(mockMessage, address);

        messageHandler.channelRead0(mockCtx, udpEvent);

        verify(mockEventHandler, times(1)).handleEvent(udpEvent);
    }

    // ==================== accept (Consumer) Tests ====================

    @Test
    void testAcceptSendsPacket() {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getType()).thenReturn(MessageCode.KAD_PING);
        Bytes sendData = Bytes.wrap(new byte[]{10, 20, 30});
        when(mockMessage.getSendData()).thenReturn(sendData);

        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 16783);
        UdpEvent udpEvent = new UdpEvent(mockMessage, address);

        messageHandler.accept(udpEvent);

        verify(mockChannel, times(1)).write(any());
        verify(mockChannel, times(1)).flush();
    }

    @Test
    void testAcceptWithDifferentAddresses() {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getType()).thenReturn(MessageCode.KAD_PONG);
        when(mockMessage.getSendData()).thenReturn(Bytes.wrap(new byte[]{1, 2, 3}));

        // Test localhost
        UdpEvent event1 = new UdpEvent(mockMessage, new InetSocketAddress("127.0.0.1", 10000));
        messageHandler.accept(event1);

        // Test LAN address
        UdpEvent event2 = new UdpEvent(mockMessage, new InetSocketAddress("192.168.1.100", 16783));
        messageHandler.accept(event2);

        // Test public IP
        UdpEvent event3 = new UdpEvent(mockMessage, new InetSocketAddress("8.8.8.8", 53));
        messageHandler.accept(event3);

        verify(mockChannel, times(3)).write(any());
        verify(mockChannel, times(3)).flush();
    }

    @Test
    void testAcceptWithEmptyMessage() {
        Message mockMessage = mock(Message.class);
        when(mockMessage.getType()).thenReturn(MessageCode.KAD_PING);
        when(mockMessage.getSendData()).thenReturn(Bytes.EMPTY);

        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10000);
        UdpEvent udpEvent = new UdpEvent(mockMessage, address);

        messageHandler.accept(udpEvent);

        verify(mockChannel, times(1)).write(any());
        verify(mockChannel, times(1)).flush();
    }

    // ==================== sendPacketFromBytes Tests ====================

    @Test
    void testSendPacketFromBytes() {
        Bytes wireBytes = Bytes.wrap(new byte[]{1, 2, 3, 4, 5});
        InetSocketAddress address = new InetSocketAddress("192.168.1.1", 16783);

        messageHandler.sendPacketFromBytes(wireBytes, address);

        verify(mockChannel, times(1)).write(any());
        verify(mockChannel, times(1)).flush();
    }

    @Test
    void testSendPacketFromBytesWithEmptyBytes() {
        Bytes wireBytes = Bytes.EMPTY;
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10000);

        messageHandler.sendPacketFromBytes(wireBytes, address);

        verify(mockChannel, times(1)).write(any());
        verify(mockChannel, times(1)).flush();
    }

    @Test
    void testSendPacketFromBytesWithLargePayload() {
        byte[] largeData = new byte[1500]; // MTU size
        Bytes wireBytes = Bytes.wrap(largeData);
        InetSocketAddress address = new InetSocketAddress("10.0.0.1", 8080);

        messageHandler.sendPacketFromBytes(wireBytes, address);

        verify(mockChannel, times(1)).write(any());
        verify(mockChannel, times(1)).flush();
    }

    @Test
    void testSendPacketFromBytesMultipleTimes() {
        Bytes wireBytes = Bytes.wrap(new byte[]{1, 2, 3});
        InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10000);

        messageHandler.sendPacketFromBytes(wireBytes, address);
        messageHandler.sendPacketFromBytes(wireBytes, address);
        messageHandler.sendPacketFromBytes(wireBytes, address);

        verify(mockChannel, times(3)).write(any());
        verify(mockChannel, times(3)).flush();
    }

    // ==================== channelReadComplete Tests ====================

    @Test
    void testChannelReadComplete() {
        messageHandler.channelReadComplete(mockCtx);

        verify(mockCtx, times(1)).flush();
    }

    @Test
    void testChannelReadCompleteCalledMultipleTimes() {
        messageHandler.channelReadComplete(mockCtx);
        messageHandler.channelReadComplete(mockCtx);
        messageHandler.channelReadComplete(mockCtx);

        verify(mockCtx, times(3)).flush();
    }

    // ==================== exceptionCaught Tests ====================

    @Test
    void testExceptionCaught() {
        Throwable testException = new RuntimeException("Test exception");

        messageHandler.exceptionCaught(mockCtx, testException);

        // No exception should be thrown, just logged
        // This test mainly verifies the method handles exceptions gracefully
    }

    @Test
    void testExceptionCaughtWithDifferentExceptionTypes() {
        // RuntimeException
        messageHandler.exceptionCaught(mockCtx, new RuntimeException("Runtime error"));

        // NullPointerException
        messageHandler.exceptionCaught(mockCtx, new NullPointerException("Null pointer"));

        // IllegalArgumentException
        messageHandler.exceptionCaught(mockCtx, new IllegalArgumentException("Invalid arg"));

        // No exceptions should be thrown
    }

    @Test
    void testExceptionCaughtWithNullCause() {
        messageHandler.exceptionCaught(mockCtx, null);

        // Should handle null gracefully (though this is unlikely in practice)
    }

    // ==================== Integration Tests ====================

    @Test
    void testCompleteMessageFlow() {
        // Simulate receiving a message
        Message receiveMessage = mock(Message.class);
        when(receiveMessage.getType()).thenReturn(MessageCode.KAD_PING);
        when(receiveMessage.getSendData()).thenReturn(Bytes.wrap(new byte[]{1, 2, 3}));
        InetSocketAddress receiveAddr = new InetSocketAddress("192.168.1.100", 10000);
        UdpEvent receiveEvent = new UdpEvent(receiveMessage, receiveAddr);

        messageHandler.channelRead0(mockCtx, receiveEvent);

        // Simulate sending a response
        Message sendMessage = mock(Message.class);
        when(sendMessage.getType()).thenReturn(MessageCode.KAD_PONG);
        when(sendMessage.getSendData()).thenReturn(Bytes.wrap(new byte[]{4, 5, 6}));
        InetSocketAddress sendAddr = new InetSocketAddress("192.168.1.100", 10000);
        UdpEvent sendEvent = new UdpEvent(sendMessage, sendAddr);

        messageHandler.accept(sendEvent);

        // Verify both operations
        verify(mockEventHandler, times(1)).handleEvent(receiveEvent);
        verify(mockChannel, times(1)).write(any());
        verify(mockChannel, times(1)).flush();
    }

    @Test
    void testHighVolumeMessageHandling() {
        // Simulate handling 100 messages
        for (int i = 0; i < 100; i++) {
            Message mockMessage = mock(Message.class);
            when(mockMessage.getType()).thenReturn(MessageCode.KAD_PING);
            when(mockMessage.getSendData()).thenReturn(Bytes.wrap(new byte[]{(byte) i}));

            InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10000 + i);
            UdpEvent udpEvent = new UdpEvent(mockMessage, address);

            messageHandler.channelRead0(mockCtx, udpEvent);
        }

        verify(mockEventHandler, times(100)).handleEvent(any(UdpEvent.class));
    }

    @Test
    void testHighVolumeSending() {
        // Simulate sending 100 messages
        for (int i = 0; i < 100; i++) {
            Message mockMessage = mock(Message.class);
            when(mockMessage.getType()).thenReturn(MessageCode.KAD_PONG);
            when(mockMessage.getSendData()).thenReturn(Bytes.wrap(new byte[]{(byte) i}));

            InetSocketAddress address = new InetSocketAddress("127.0.0.1", 10000 + i);
            UdpEvent udpEvent = new UdpEvent(mockMessage, address);

            messageHandler.accept(udpEvent);
        }

        verify(mockChannel, times(100)).write(any());
        verify(mockChannel, times(100)).flush();
    }

    @Test
    void testChannelLifecycle() {
        // 1. Channel becomes active
        messageHandler.channelActive(mockCtx);
        verify(mockEventHandler, times(1)).channelActivated();

        // 2. Receive some messages
        Message msg1 = mock(Message.class);
        when(msg1.getType()).thenReturn(MessageCode.KAD_PING);
        when(msg1.getSendData()).thenReturn(Bytes.wrap(new byte[]{1}));
        messageHandler.channelRead0(mockCtx, new UdpEvent(msg1, new InetSocketAddress("127.0.0.1", 10000)));

        // 3. Complete reading
        messageHandler.channelReadComplete(mockCtx);
        verify(mockCtx, times(1)).flush();

        // 4. Send response
        Message msg2 = mock(Message.class);
        when(msg2.getType()).thenReturn(MessageCode.KAD_PONG);
        when(msg2.getSendData()).thenReturn(Bytes.wrap(new byte[]{2}));
        messageHandler.accept(new UdpEvent(msg2, new InetSocketAddress("127.0.0.1", 10000)));

        // 5. Handle exception (shouldn't affect channel)
        messageHandler.exceptionCaught(mockCtx, new RuntimeException("Test error"));

        // All operations should complete successfully
        verify(mockEventHandler, times(1)).handleEvent(any(UdpEvent.class));
        verify(mockChannel, times(1)).write(any());
        verify(mockChannel, times(1)).flush();
    }
}
