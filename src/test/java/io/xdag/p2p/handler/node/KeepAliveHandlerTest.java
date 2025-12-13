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
import static org.mockito.Mockito.*;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.xdag.p2p.channel.XdagFrame;
import io.xdag.p2p.message.MessageCode;
import java.net.InetSocketAddress;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Unit tests for KeepAliveHandler. Tests keepalive ping/pong mechanism and idle state handling.
 */
class KeepAliveHandlerTest {

    private KeepAliveHandler handler;
    private ChannelHandlerContext ctx;

    @BeforeEach
    void setUp() {
        handler = new KeepAliveHandler();
        ctx = mock(ChannelHandlerContext.class);
        Channel channel = mock(Channel.class);
        ChannelFuture channelFuture = mock(ChannelFuture.class);
        when(ctx.channel()).thenReturn(channel);
        when(channel.remoteAddress()).thenReturn(new InetSocketAddress("127.0.0.1", 8080));
        when(ctx.writeAndFlush(any())).thenReturn(channelFuture);
        when(channelFuture.addListener(any())).thenReturn(channelFuture);
    }

    @Test
    void testWriterIdleTriggersping() throws Exception {
        // Given
        IdleStateEvent idleEvent = mock(IdleStateEvent.class);
        when(idleEvent.state()).thenReturn(IdleState.WRITER_IDLE);

        // When
        handler.userEventTriggered(ctx, idleEvent);

        // Then
        ArgumentCaptor<XdagFrame> frameCaptor = ArgumentCaptor.forClass(XdagFrame.class);
        verify(ctx).writeAndFlush(frameCaptor.capture());

        XdagFrame sentFrame = frameCaptor.getValue();
        assertNotNull(sentFrame);
        assertEquals(MessageCode.PING.toByte(), sentFrame.getPacketType());
        assertEquals(XdagFrame.VERSION, sentFrame.getVersion());
        assertEquals(XdagFrame.COMPRESS_NONE, sentFrame.getCompressType());

        // Verify timestamp is reasonable (within test execution window)
        assertTrue(sentFrame.getBody().length >= 8, "PING message should contain timestamp");
    }

    @Test
    void testReaderIdleAlsoTriggersPing() throws Exception {
        // Given - READER_IDLE should also trigger ping (same as WRITER_IDLE)
        IdleStateEvent idleEvent = mock(IdleStateEvent.class);
        when(idleEvent.state()).thenReturn(IdleState.READER_IDLE);

        // When
        handler.userEventTriggered(ctx, idleEvent);

        // Then - READER_IDLE should also send ping
        ArgumentCaptor<XdagFrame> frameCaptor = ArgumentCaptor.forClass(XdagFrame.class);
        verify(ctx).writeAndFlush(frameCaptor.capture());

        XdagFrame sentFrame = frameCaptor.getValue();
        assertNotNull(sentFrame);
        assertEquals(MessageCode.PING.toByte(), sentFrame.getPacketType());
    }

    @Test
    void testAllIdleDoesNotTriggerPing() throws Exception {
        // Given
        IdleStateEvent idleEvent = mock(IdleStateEvent.class);
        when(idleEvent.state()).thenReturn(IdleState.ALL_IDLE);

        // When
        handler.userEventTriggered(ctx, idleEvent);

        // Then
        verify(ctx, never()).writeAndFlush(any());
    }

    @Test
    void testNonIdleEventPassesThrough() throws Exception {
        // Given
        Object customEvent = new Object();

        // When
        handler.userEventTriggered(ctx, customEvent);

        // Then - should not throw exception and not send any frames
        verify(ctx, never()).writeAndFlush(any());
    }

    @Test
    void testChannelReadPassesThrough() throws Exception {
        // Given
        Object msg = new Object();

        // When
        handler.channelRead(ctx, msg);

        // Then
        verify(ctx).fireChannelRead(msg);
        verify(ctx, never()).writeAndFlush(any());
    }

    @Test
    void testMultipleWriterIdleEventsSendMultiplePings() throws Exception {
        // Given
        IdleStateEvent idleEvent = mock(IdleStateEvent.class);
        when(idleEvent.state()).thenReturn(IdleState.WRITER_IDLE);

        // When
        handler.userEventTriggered(ctx, idleEvent);
        Thread.sleep(10); // Small delay to ensure different timestamps
        handler.userEventTriggered(ctx, idleEvent);
        Thread.sleep(10);
        handler.userEventTriggered(ctx, idleEvent);

        // Then
        verify(ctx, times(3)).writeAndFlush(any(XdagFrame.class));
    }

    @Test
    void testPingFrameStructure() throws Exception {
        // Given
        IdleStateEvent idleEvent = mock(IdleStateEvent.class);
        when(idleEvent.state()).thenReturn(IdleState.WRITER_IDLE);

        // When
        handler.userEventTriggered(ctx, idleEvent);

        // Then
        ArgumentCaptor<XdagFrame> frameCaptor = ArgumentCaptor.forClass(XdagFrame.class);
        verify(ctx).writeAndFlush(frameCaptor.capture());

        XdagFrame frame = frameCaptor.getValue();
        assertEquals(XdagFrame.VERSION, frame.getVersion());
        assertEquals(XdagFrame.COMPRESS_NONE, frame.getCompressType());
        assertEquals(MessageCode.PING.toByte(), frame.getPacketType());
        assertEquals(0, frame.getPacketId());
        assertEquals(frame.getBody().length, frame.getBodySize());
        assertEquals(frame.getBody().length, frame.getPacketSize());
        assertNotNull(frame.getBody());
        assertTrue(frame.getBody().length >= 8, "PING body should contain at least 8 bytes for timestamp");
    }

    @Test
    void testChannelReadWithNullMessage() throws Exception {
        // Given
        Object nullMsg = null;

        // When
        handler.channelRead(ctx, nullMsg);

        // Then
        verify(ctx).fireChannelRead(null);
    }

    @Test
    void testMixedEventSequence() throws Exception {
        // Given
        IdleStateEvent writerIdle = mock(IdleStateEvent.class);
        when(writerIdle.state()).thenReturn(IdleState.WRITER_IDLE);

        IdleStateEvent readerIdle = mock(IdleStateEvent.class);
        when(readerIdle.state()).thenReturn(IdleState.READER_IDLE);

        Object customEvent = new Object();
        Object message = new Object();

        // When - mixed sequence of events
        handler.userEventTriggered(ctx, writerIdle);
        handler.userEventTriggered(ctx, readerIdle);
        handler.channelRead(ctx, message);
        handler.userEventTriggered(ctx, customEvent);
        handler.userEventTriggered(ctx, writerIdle);

        // Then - WRITER_IDLE and READER_IDLE both trigger ping (2 + 1 = 3)
        verify(ctx, times(3)).writeAndFlush(any(XdagFrame.class));
        verify(ctx).fireChannelRead(message);
    }

    @Test
    void testContextNotNullBeforeWrite() throws Exception {
        // Given
        IdleStateEvent idleEvent = mock(IdleStateEvent.class);
        when(idleEvent.state()).thenReturn(IdleState.WRITER_IDLE);

        // When
        handler.userEventTriggered(ctx, idleEvent);

        // Then - verify context is actually used for write operation
        verify(ctx).writeAndFlush(any(XdagFrame.class));
        // Also verifies ctx.channel() is called for logging
        verify(ctx, atLeastOnce()).channel();
    }

    @Test
    void testKeepAliveHandlerInstantiation() {
        // Given & When
        KeepAliveHandler newHandler = new KeepAliveHandler();

        // Then
        assertNotNull(newHandler);
    }
}
