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
package io.xdag.p2p.config;

import com.google.protobuf.InvalidProtocolBufferException;
import io.xdag.p2p.P2pException;
import io.xdag.p2p.P2pException.TypeEnum;
import io.xdag.p2p.proto.Connect.CompressMessage;
import io.xdag.p2p.utils.ProtoUtils;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;

/**
 * Controller for handling protocol version upgrades and data encoding/decoding. Supports
 * compression based on protocol version compatibility.
 */
public class UpgradeController {

  /**
   * Encode data for sending based on protocol version.
   *
   * @param version the protocol version
   * @param data the data to encode as Tuweni Bytes
   * @return encoded data as Tuweni Bytes
   * @throws IOException if encoding fails
   */
  public static Bytes codeSendData(int version, Bytes data) throws IOException {
    if (!supportCompress(version)) {
      return data;
    }
    CompressMessage compressed = ProtoUtils.compressMessageBytes(data);
    return Bytes.wrap(compressed.toByteArray());
  }

  /**
   * Decode received data based on protocol version.
   *
   * @param version the protocol version
   * @param data the data to decode as Tuweni Bytes
   * @return decoded data as Tuweni Bytes
   * @throws P2pException if parsing fails
   * @throws IOException if decoding fails
   */
  public static Bytes decodeReceiveData(int version, Bytes data) throws P2pException, IOException {
    if (!supportCompress(version)) {
      return data;
    }
    CompressMessage compressMessage;
    try {
      compressMessage = CompressMessage.parseFrom(data.toArray());
    } catch (InvalidProtocolBufferException e) {
      throw new P2pException(TypeEnum.PARSE_MESSAGE_FAILED, e);
    }
    return ProtoUtils.uncompressMessageBytes(compressMessage);
  }

  /**
   * Check if compression is supported for the given version.
   *
   * @param version the protocol version to check
   * @return true if compression is supported
   */
  private static boolean supportCompress(int version) {
    return P2pConstant.version >= 1 && version >= 1;
  }
}
