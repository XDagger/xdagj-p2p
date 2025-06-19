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

import com.google.protobuf.InvalidProtocolBufferException;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.socket.DatagramPacket;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.xdag.p2p.P2pException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.discover.Message;
import io.xdag.p2p.utils.BytesUtils;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.apache.tuweni.bytes.Bytes;

@Slf4j(topic = "net")
public class P2pPacketDecoder extends MessageToMessageDecoder<DatagramPacket> {

  public static final int MAXSIZE = 2048;

  private final P2pConfig p2pConfig;

  public P2pPacketDecoder(P2pConfig p2pConfig) {
    this.p2pConfig = p2pConfig;
  }

  @Override
  public void decode(ChannelHandlerContext ctx, DatagramPacket packet, List<Object> out) {
    ByteBuf buf = packet.content();
    int length = buf.readableBytes();
    if (length <= 1 || length >= MAXSIZE) {
      log.warn("UDP rcv bad packet, from {} length = {}", ctx.channel().remoteAddress(), length);
      return;
    }

    // Use Tuweni Bytes for more efficient byte handling
    byte[] encoded = new byte[length];
    buf.readBytes(encoded);
    Bytes encodedBytes = Bytes.wrap(encoded);

    try {
      UdpEvent event = new UdpEvent(Message.parse(p2pConfig, encodedBytes), packet.sender());
      out.add(event);
    } catch (P2pException pe) {
      if (pe.getType().equals(P2pException.TypeEnum.BAD_MESSAGE)) {
        log.error(
            "Message validation failed, type {}, len {}, address {}",
            encoded[0],
            encoded.length,
            packet.sender());
      } else {
        log.info(
            "Parse msg failed, type {}, len {}, address {}",
            encoded[0],
            encoded.length,
            packet.sender());
      }
    } catch (InvalidProtocolBufferException e) {
      log.warn(
          "An exception occurred while parsing the message, type {}, len {}, address {}, "
              + "data {}, cause: {}",
          encoded[0],
          encoded.length,
          packet.sender(),
          BytesUtils.toHexString(encodedBytes),
          e.getMessage());
    } catch (Exception e) {
      log.error(
          "An exception occurred while parsing the message, type {}, len {}, address {}, "
              + "data {}",
          encoded[0],
          encoded.length,
          packet.sender(),
          BytesUtils.toHexString(encodedBytes),
          e);
    }
  }
}
