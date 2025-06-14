package io.xdag.p2p.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.message.node.PingMessage;
import io.xdag.p2p.proto.Connect;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.Test;

public class ProtoUtilsTest {
  private final P2pConfig p2pConfig = new P2pConfig();

  @Test
  public void testCompressMessage() throws Exception {
    PingMessage p1 = new PingMessage(p2pConfig);

    Connect.CompressMessage message = ProtoUtils.compressMessageBytes(p1.getData());

    Bytes d1 = ProtoUtils.uncompressMessageBytes(message);

    PingMessage p2 = new PingMessage(p2pConfig, d1);

    assertEquals(p1.getTimeStamp(), p2.getTimeStamp());

    Connect.CompressMessage m2 = ProtoUtils.compressMessageBytes(Bytes.wrap(new byte[1000]));

    Bytes d2 = ProtoUtils.uncompressMessageBytes(m2);

    assertEquals(1000, d2.size());
    assertEquals(0, d2.get(0));
  }
}
