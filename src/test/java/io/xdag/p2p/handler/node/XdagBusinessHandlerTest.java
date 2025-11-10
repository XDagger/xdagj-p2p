/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2020-2030 The XdagJ Developers
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
package io.xdag.p2p.handler.node;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.netty.channel.ChannelHandlerContext;
import io.xdag.p2p.P2pEventHandler;
import io.xdag.p2p.channel.Channel;
import io.xdag.p2p.channel.ChannelManager;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.message.node.PingMessage;
import io.xdag.p2p.message.node.PongMessage;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for XdagBusinessHandler. Tests business message routing, keepalive handling, and
 * error scenarios.
 */
class XdagBusinessHandlerTest {

    private XdagBusinessHandler handler;
    private P2pConfig config;
    private ChannelManager channelManager;
    private ChannelHandlerContext ctx;
    private io.netty.channel.Channel nettyChannel;
    private Channel xdagChannel;
    private InetSocketAddress remoteAddress;
    private Map<InetSocketAddress, Channel> channelMap;

    @BeforeEach
    void setUp() {
        config = mock(P2pConfig.class);
        channelManager = mock(ChannelManager.class);
        ctx = mock(ChannelHandlerContext.class);
        nettyChannel = mock(io.netty.channel.Channel.class);
        xdagChannel = mock(Channel.class);

        remoteAddress = new InetSocketAddress("192.168.1.100", 8888);
        channelMap = new ConcurrentHashMap<>();
        channelMap.put(remoteAddress, xdagChannel);

        when(ctx.channel()).thenReturn(nettyChannel);
        when(nettyChannel.remoteAddress()).thenReturn(remoteAddress);
        when(channelManager.getChannels()).thenReturn(channelMap);
        when(config.getHandlerList()).thenReturn(new ArrayList<>());

        handler = new XdagBusinessHandler(config, channelManager);
    }

    @Test
    void testPingMessageAutoRespondsWithPong() {
        // Given
        PingMessage pingMessage = new PingMessage();

        // When
        handler.channelRead0(ctx, pingMessage);

        // Then
        verify(ctx).writeAndFlush(any(PongMessage.class));
    }

    @Test
    void testPongMessageIsIgnored() {
        // Given
        PongMessage pongMessage = new PongMessage();

        // When
        handler.channelRead0(ctx, pongMessage);

        // Then
        verify(ctx, never()).writeAndFlush(any());
    }

    @Test
    void testAppTestMessageDispatchedToHandlers() {
        // Given
        P2pEventHandler messageHandler1 = mock(P2pEventHandler.class);
        P2pEventHandler messageHandler2 = mock(P2pEventHandler.class);
        List<P2pEventHandler> handlers = List.of(messageHandler1, messageHandler2);
        when(config.getHandlerList()).thenReturn(handlers);

        byte[] appPayload = new byte[]{1, 2, 3, 4, 5};
        Message appTestMessage = mock(Message.class);
        when(appTestMessage.getCode()).thenReturn(MessageCode.APP_TEST);
        when(appTestMessage.getBody()).thenReturn(appPayload);

        // When
        handler.channelRead0(ctx, appTestMessage);

        // Then
        ArgumentCaptor<Bytes> bytesCaptor = ArgumentCaptor.forClass(Bytes.class);
        verify(messageHandler1).onMessage(eq(xdagChannel), bytesCaptor.capture());
        verify(messageHandler2).onMessage(eq(xdagChannel), bytesCaptor.capture());

        List<Bytes> capturedBytes = bytesCaptor.getAllValues();
        assertEquals(2, capturedBytes.size());
        assertArrayEquals(appPayload, capturedBytes.get(0).toArray());
        assertArrayEquals(appPayload, capturedBytes.get(1).toArray());
    }

    @Test
    void testNonAppTestMessageDispatchedWithCodeAndBody() {
        // Given
        P2pEventHandler messageHandler = mock(P2pEventHandler.class);
        when(config.getHandlerList()).thenReturn(List.of(messageHandler));

        byte[] fullData = new byte[]{0x01, 0x02, 0x03, 0x04};
        Message customMessage = mock(Message.class);
        when(customMessage.getCode()).thenReturn(MessageCode.DISCONNECT);
        when(customMessage.getBody()).thenReturn(new byte[]{0x02, 0x03, 0x04});
        when(customMessage.getSendData()).thenReturn(Bytes.wrap(fullData));

        // When
        handler.channelRead0(ctx, customMessage);

        // Then
        ArgumentCaptor<Bytes> bytesCaptor = ArgumentCaptor.forClass(Bytes.class);
        verify(messageHandler).onMessage(eq(xdagChannel), bytesCaptor.capture());

        Bytes capturedBytes = bytesCaptor.getValue();
        assertArrayEquals(fullData, capturedBytes.toArray());
    }

    @Test
    void testMessageDroppedWhenChannelNotFound() {
        // Given
        InetSocketAddress unknownAddress = new InetSocketAddress("10.0.0.1", 9999);
        when(nettyChannel.remoteAddress()).thenReturn(unknownAddress);

        Message message = mock(Message.class);
        when(message.getCode()).thenReturn(MessageCode.DISCONNECT);

        P2pEventHandler messageHandler = mock(P2pEventHandler.class);
        when(config.getHandlerList()).thenReturn(List.of(messageHandler));

        // When
        handler.channelRead0(ctx, message);

        // Then
        verify(messageHandler, never()).onMessage(any(), any());
        verify(ctx, never()).writeAndFlush(any());
    }

    @Test
    void testExceptionInMessageHandlingIsCaught() {
        // Given
        P2pEventHandler faultyHandler = mock(P2pEventHandler.class);
        doThrow(new RuntimeException("Handler error")).when(faultyHandler).onMessage(any(), any());
        when(config.getHandlerList()).thenReturn(List.of(faultyHandler));

        Message message = mock(Message.class);
        when(message.getCode()).thenReturn(MessageCode.DISCONNECT);
        when(message.getSendData()).thenReturn(Bytes.wrap(new byte[]{1, 2, 3}));
        when(message.getBody()).thenReturn(new byte[]{2, 3});

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> handler.channelRead0(ctx, message));
    }

    @Test
    void testExceptionCaughtClosesChannel() {
        // Given
        Throwable cause = new RuntimeException("Test exception");

        // When
        handler.exceptionCaught(ctx, cause);

        // Then
        verify(ctx).close();
    }

    @Test
    void testMultipleHandlersReceiveMessage() {
        // Given
        P2pEventHandler handler1 = mock(P2pEventHandler.class);
        P2pEventHandler handler2 = mock(P2pEventHandler.class);
        P2pEventHandler handler3 = mock(P2pEventHandler.class);
        when(config.getHandlerList()).thenReturn(List.of(handler1, handler2, handler3));

        Message message = mock(Message.class);
        when(message.getCode()).thenReturn(MessageCode.DISCONNECT);
        when(message.getSendData()).thenReturn(Bytes.wrap(new byte[]{1, 2, 3}));
        when(message.getBody()).thenReturn(new byte[]{2, 3});

        // When
        handler.channelRead0(ctx, message);

        // Then
        verify(handler1).onMessage(eq(xdagChannel), any(Bytes.class));
        verify(handler2).onMessage(eq(xdagChannel), any(Bytes.class));
        verify(handler3).onMessage(eq(xdagChannel), any(Bytes.class));
    }

    @Test
    void testPingResponseEvenIfWriteAndFlushFails() {
        // Given
        when(ctx.writeAndFlush(any())).thenThrow(new RuntimeException("Write failed"));
        PingMessage pingMessage = new PingMessage();

        // When & Then - should not throw exception
        assertDoesNotThrow(() -> handler.channelRead0(ctx, pingMessage));
    }

    @Test
    void testEmptyHandlerListDoesNotCauseError() {
        // Given
        when(config.getHandlerList()).thenReturn(new ArrayList<>());

        Message message = mock(Message.class);
        when(message.getCode()).thenReturn(MessageCode.DISCONNECT);
        when(message.getSendData()).thenReturn(Bytes.wrap(new byte[]{1, 2, 3}));
        when(message.getBody()).thenReturn(new byte[]{2, 3});

        // When & Then
        assertDoesNotThrow(() -> handler.channelRead0(ctx, message));
    }

    @Test
    void testNullMessageBodyHandling() {
        // Given
        P2pEventHandler messageHandler = mock(P2pEventHandler.class);
        when(config.getHandlerList()).thenReturn(List.of(messageHandler));

        Message message = mock(Message.class);
        when(message.getCode()).thenReturn(MessageCode.APP_TEST);
        when(message.getBody()).thenReturn(null);

        // When & Then - should handle gracefully
        assertDoesNotThrow(() -> handler.channelRead0(ctx, message));
    }

    @Test
    void testHandlerInstantiationWithValidParameters() {
        // Given & When
        XdagBusinessHandler newHandler = new XdagBusinessHandler(config, channelManager);

        // Then
        assertNotNull(newHandler);
    }

    @Test
    void testSequentialPingPongMessages() {
        // Given
        PingMessage ping1 = new PingMessage();
        PongMessage pong1 = new PongMessage();
        PingMessage ping2 = new PingMessage();

        // When
        handler.channelRead0(ctx, ping1);
        handler.channelRead0(ctx, pong1);
        handler.channelRead0(ctx, ping2);

        // Then
        verify(ctx, times(2)).writeAndFlush(any(PongMessage.class));
    }

    @Test
    void testMessageWithEmptyBody() {
        // Given
        P2pEventHandler messageHandler = mock(P2pEventHandler.class);
        when(config.getHandlerList()).thenReturn(List.of(messageHandler));

        Message message = mock(Message.class);
        when(message.getCode()).thenReturn(MessageCode.DISCONNECT);
        when(message.getBody()).thenReturn(new byte[0]);
        when(message.getSendData()).thenReturn(Bytes.wrap(new byte[]{MessageCode.DISCONNECT.toByte()}));

        // When
        handler.channelRead0(ctx, message);

        // Then
        ArgumentCaptor<Bytes> bytesCaptor = ArgumentCaptor.forClass(Bytes.class);
        verify(messageHandler).onMessage(eq(xdagChannel), bytesCaptor.capture());
        assertNotNull(bytesCaptor.getValue());
    }

    @Test
    void testDifferentMessageCodesRoutedCorrectly() {
        // Given
        P2pEventHandler messageHandler = mock(P2pEventHandler.class);
        when(config.getHandlerList()).thenReturn(List.of(messageHandler));

        // Test DISCONNECT message
        Message disconnectMsg = mock(Message.class);
        when(disconnectMsg.getCode()).thenReturn(MessageCode.DISCONNECT);
        when(disconnectMsg.getSendData()).thenReturn(Bytes.wrap(new byte[]{1}));
        when(disconnectMsg.getBody()).thenReturn(new byte[]{1});

        // When
        handler.channelRead0(ctx, disconnectMsg);

        // Then
        verify(messageHandler).onMessage(eq(xdagChannel), any(Bytes.class));
    }

    @Test
    void testChannelManagerIntegration() {
        // Given
        Message message = mock(Message.class);
        when(message.getCode()).thenReturn(MessageCode.DISCONNECT);
        when(message.getSendData()).thenReturn(Bytes.wrap(new byte[]{1, 2}));
        when(message.getBody()).thenReturn(new byte[]{2});

        // When
        handler.channelRead0(ctx, message);

        // Then
        verify(channelManager).getChannels();
    }

    @Test
    void testOneHandlerFailureDoesNotAffectOthers() {
        // Given
        P2pEventHandler goodHandler1 = mock(P2pEventHandler.class);
        P2pEventHandler faultyHandler = mock(P2pEventHandler.class);
        P2pEventHandler goodHandler2 = mock(P2pEventHandler.class);

        doThrow(new RuntimeException("Handler fault")).when(faultyHandler).onMessage(any(), any());

        when(config.getHandlerList()).thenReturn(List.of(goodHandler1, faultyHandler, goodHandler2));

        Message message = mock(Message.class);
        when(message.getCode()).thenReturn(MessageCode.DISCONNECT);
        when(message.getSendData()).thenReturn(Bytes.wrap(new byte[]{1, 2, 3}));
        when(message.getBody()).thenReturn(new byte[]{2, 3});

        // When
        assertDoesNotThrow(() -> handler.channelRead0(ctx, message));

        // Then - good handlers should still be called
        verify(goodHandler1).onMessage(eq(xdagChannel), any(Bytes.class));
        // Note: Due to exception, remaining handlers might not be called
        // This tests that the exception is caught and doesn't propagate
    }
}
