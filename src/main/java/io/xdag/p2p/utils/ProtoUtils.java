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
package io.xdag.p2p.utils;

import com.google.protobuf.ByteString;
import io.xdag.p2p.P2pException;
import io.xdag.p2p.config.P2pConstant;
import io.xdag.p2p.proto.Connect;
import java.io.IOException;
import org.apache.tuweni.bytes.Bytes;
import org.xerial.snappy.Snappy;

public class ProtoUtils {

  /**
   * Compress message data using Tuweni Bytes
   *
   * @param data the data to compress as Tuweni Bytes
   * @return compressed message
   * @throws IOException if compression fails
   */
  public static Connect.CompressMessage compressMessageBytes(Bytes data) throws IOException {
    Connect.CompressMessage.CompressType type = Connect.CompressMessage.CompressType.uncompress;
    Bytes resultBytes = data;

    byte[] compressedData = Snappy.compress(data.toArray());
    if (compressedData.length < data.size()) {
      type = Connect.CompressMessage.CompressType.snappy;
      resultBytes = Bytes.wrap(compressedData);
    }

    return Connect.CompressMessage.newBuilder()
        .setData(bytesToByteString(resultBytes))
        .setType(type)
        .build();
  }

  /**
   * Uncompress message data returning Tuweni Bytes
   *
   * @param message the compressed message
   * @return uncompressed data as Tuweni Bytes
   * @throws IOException if decompression fails
   * @throws P2pException if message is too big
   */
  public static Bytes uncompressMessageBytes(Connect.CompressMessage message)
      throws IOException, P2pException {
    Bytes data = byteStringToBytes(message.getData());
    if (message.getType().equals(Connect.CompressMessage.CompressType.uncompress)) {
      return data;
    }

    int length = Snappy.uncompressedLength(data.toArray());
    if (length >= P2pConstant.MAX_MESSAGE_LENGTH) {
      throw new P2pException(
          P2pException.TypeEnum.BIG_MESSAGE, "message is too big, len=" + length);
    }

    byte[] uncompressedData = Snappy.uncompress(data.toArray());
    if (uncompressedData.length >= P2pConstant.MAX_MESSAGE_LENGTH) {
      throw new P2pException(
          P2pException.TypeEnum.BIG_MESSAGE,
          "uncompressed is too big, len=" + uncompressedData.length);
    }
    return Bytes.wrap(uncompressedData);
  }

  /** Helper method to convert ByteString to Tuweni Bytes */
  public static Bytes byteStringToBytes(ByteString byteString) {
    return Bytes.wrap(byteString.toByteArray());
  }

  /** Helper method to convert Tuweni Bytes to ByteString */
  public static ByteString bytesToByteString(Bytes bytes) {
    return ByteString.copyFrom(bytes.toArray());
  }
}
