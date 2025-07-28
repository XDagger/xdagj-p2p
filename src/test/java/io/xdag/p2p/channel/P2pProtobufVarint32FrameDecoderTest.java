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
package io.xdag.p2p.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.CorruptedFrameException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.message.node.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for P2pProtobufVarint32FrameDecoder class. Tests protobuf varint32 frame decoding
 * functionality using Netty's EmbeddedChannel.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class P2pProtobufVarint32FrameDecoderTest {

  @Mock private P2pConfig p2pConfig;

  @Mock private Channel channel;

  private EmbeddedChannel embeddedChannel;

  @BeforeEach
  void setUp() {
    P2pProtobufVarint32FrameDecoder decoder = new P2pProtobufVarint32FrameDecoder(p2pConfig,
        channel);
    embeddedChannel = new EmbeddedChannel(decoder);
  }

  @Test
  void testConstructor() {
    // Given & When
    P2pProtobufVarint32FrameDecoder frameDecoder =
        new P2pProtobufVarint32FrameDecoder(p2pConfig, channel);

    // Then
    assertNotNull(frameDecoder);
  }

  @Test
  void testDecodeValidSingleByteLength() {
    // Given
    ByteBuf input = Unpooled.buffer();
    input.writeByte(5); // Length = 5
    input.writeBytes("hello".getBytes()); // 5 bytes of data

    // When
    assertTrue(embeddedChannel.writeInbound(input));

    // Then
    ByteBuf output = embeddedChannel.readInbound();
    assertNotNull(output);
    assertEquals(5, output.readableBytes());

    byte[] result = new byte[5];
    output.readBytes(result);
    assertEquals("hello", new String(result));

    output.release();
    assertFalse(embeddedChannel.finish());
  }

  @Test
  void testDecodeValidMultiByteLength() {
    // Given
    ByteBuf input = Unpooled.buffer();
    writeVarint32(input, 300); // Length = 300
    byte[] data = new byte[300];
    for (int i = 0; i < 300; i++) {
      data[i] = (byte) (i % 256);
    }
    input.writeBytes(data);

    // When
    assertTrue(embeddedChannel.writeInbound(input));

    // Then
    ByteBuf output = embeddedChannel.readInbound();
    assertNotNull(output);
    assertEquals(300, output.readableBytes());

    output.release();
    assertFalse(embeddedChannel.finish());
  }

  @Test
  void testDecodeInsufficientData() {
    // Given
    ByteBuf input = Unpooled.buffer();
    input.writeByte(10); // Length = 10
    input.writeBytes("hello".getBytes()); // Only 5 bytes of data (insufficient)

    // When
    assertFalse(embeddedChannel.writeInbound(input));

    // Then
    ByteBuf output = embeddedChannel.readInbound();
    assertNull(output); // No output due to insufficient data

    assertFalse(embeddedChannel.finish());
  }

  @Test
  void testDecodeZeroLength() {
    // Given
    ByteBuf input = Unpooled.buffer();
    input.writeByte(0); // Length = 0

    // When
    assertTrue(embeddedChannel.writeInbound(input));

    // Then
    ByteBuf output = embeddedChannel.readInbound();
    assertNotNull(output);
    assertEquals(0, output.readableBytes());

    output.release();
    assertFalse(embeddedChannel.finish());
  }

  @Test
  void testDecodeNegativeLength() {
    // Given
    ByteBuf input = Unpooled.buffer();
    // Write negative varint32
    input.writeByte(0xFF);
    input.writeByte(0xFF);
    input.writeByte(0xFF);
    input.writeByte(0xFF);
    input.writeByte(0x0F);

    // When & Then
    assertThrows(
        CorruptedFrameException.class,
        () -> embeddedChannel.writeInbound(input));

    embeddedChannel.finish();
  }

  @Test
  void testDecodeMaxMessageLength() {
    // Given
    ByteBuf input = Unpooled.buffer();
    writeVarint32(input, P2pConstant.MAX_MESSAGE_LENGTH);

    // When
    assertFalse(embeddedChannel.writeInbound(input));

    // Then
    verify(channel).send(any(Message.class)); // Should send the disconnect message
    verify(channel).close(); // Should close the channel

    ByteBuf output = embeddedChannel.readInbound();
    assertNull(output); // No output due to max length exceeded

    embeddedChannel.finish();
  }

  @Test
  void testDecodeEmptyBuffer() {
    // Given
    ByteBuf input = Unpooled.buffer();

    // When
    assertFalse(embeddedChannel.writeInbound(input));

    // Then
    ByteBuf output = embeddedChannel.readInbound();
    assertNull(output);

    assertFalse(embeddedChannel.finish());
  }

  @Test
  void testDecodePartialVarint() {
    // Given - Send partial varint (continuation bit set but no next byte)
    ByteBuf input = Unpooled.buffer();
    input.writeByte(0x80); // Continuation bit set, but no more data

    // When
    assertFalse(embeddedChannel.writeInbound(input));

    // Then
    ByteBuf output = embeddedChannel.readInbound();
    assertNull(output); // Should wait for more data

    assertFalse(embeddedChannel.finish());
  }

  @Test
  void testDecodeCorruptedVarint() {
    // Given - Write malformed varint (too many continuation bytes)
    ByteBuf input = Unpooled.buffer();
    for (int i = 0; i < 5; i++) {
      input.writeByte(0x80); // 5 bytes with the continuation bit set
    }

    // When & Then
    assertThrows(
        CorruptedFrameException.class,
        () -> embeddedChannel.writeInbound(input));

    embeddedChannel.finish();
  }

  @Test
  void testDecodeMultipleMessages() {
    // Given
    ByteBuf input = Unpooled.buffer();

    // First message
    input.writeByte(3);
    input.writeBytes("abc".getBytes());

    // Second message
    input.writeByte(2);
    input.writeBytes("xy".getBytes());

    // When
    assertTrue(embeddedChannel.writeInbound(input));

    // Then
    // the First message
    ByteBuf output1 = embeddedChannel.readInbound();
    assertNotNull(output1);
    assertEquals(3, output1.readableBytes());
    output1.release();

    // Second message
    ByteBuf output2 = embeddedChannel.readInbound();
    assertNotNull(output2);
    assertEquals(2, output2.readableBytes());
    output2.release();

    assertFalse(embeddedChannel.finish());
  }

  @Test
  void testDecodeFragmentedInput() {
    // Given - Send data in fragments
    ByteBuf fragment1 = Unpooled.buffer();
    fragment1.writeByte(5); // Length = 5
    fragment1.writeBytes("he".getBytes()); // Partial data

    ByteBuf fragment2 = Unpooled.buffer();
    fragment2.writeBytes("llo".getBytes()); // Remaining data

    // When
    assertFalse(embeddedChannel.writeInbound(fragment1)); // Not enough data yet
    assertTrue(embeddedChannel.writeInbound(fragment2)); // Now complete

    // Then
    ByteBuf output = embeddedChannel.readInbound();
    assertNotNull(output);
    assertEquals(5, output.readableBytes());

    byte[] result = new byte[5];
    output.readBytes(result);
    assertEquals("hello", new String(result));

    output.release();
    assertFalse(embeddedChannel.finish());
  }

  /** Helper method to write varint32 to ByteBuf */
  private void writeVarint32(ByteBuf buffer, int value) {
    while ((value & 0xFFFFFF80) != 0) {
      buffer.writeByte((value & 0x7F) | 0x80);
      value >>>= 7;
    }
    buffer.writeByte(value & 0x7F);
  }
}
