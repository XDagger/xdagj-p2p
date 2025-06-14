package io.xdag.p2p.example.message;

import java.nio.charset.StandardCharsets;
import lombok.Data;

/** Test message for P2P communication examples */
@Data
public class TestMessage {
  private final MessageTypes type;
  private final byte[] data;

  public TestMessage(byte[] data) {
    this.type = MessageTypes.TEST;
    this.data = data;
  }

  public TestMessage(String message) {
    this(message.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Get message content as string
   *
   * @return message content
   */
  public String getContentAsString() {
    return new String(data, StandardCharsets.UTF_8);
  }

  @Override
  public String toString() {
    return "TestMessage{" + "type=" + type + ", content='" + getContentAsString() + '\'' + '}';
  }
}
