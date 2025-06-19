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
   * Create network performance test message
   * Format: TEST_MSG|messageId|originSender|createTime|hopCount|maxHops|testType|content
   * 
   * @param messageId Unique message identifier
   * @param originSender Original sender node ID
   * @param createTime Message creation timestamp
   * @param hopCount Current hop count
   * @param maxHops Maximum allowed hops
   * @param testType Test type identifier
   * @param content Message content
   */
  public TestMessage(String messageId, String originSender, long createTime, 
                    int hopCount, int maxHops, String testType, String content) {
    this.type = MessageTypes.TEST;
    String networkTestMsg = String.format("TEST_MSG|%s|%s|%d|%d|%d|%s|%s",
        messageId, originSender, createTime, hopCount, maxHops, testType, content);
    this.data = networkTestMsg.getBytes(StandardCharsets.UTF_8);
  }

  /**
   * Get message content as string
   *
   * @return message content
   */
  public String getContentAsString() {
    return new String(data, StandardCharsets.UTF_8);
  }

  /**
   * Check if this is a network test message
   * 
   * @return true if this is a network test message
   */
  public boolean isNetworkTestMessage() {
    return getContentAsString().startsWith("TEST_MSG|");
  }

  /**
   * Parse network test message parts
   * 
   * @return array of message parts: [messageId, originSender, createTime, hopCount, maxHops, testType, content]
   */
  public String[] parseNetworkTestMessage() {
    if (!isNetworkTestMessage()) {
      return null;
    }
    String content = getContentAsString();
    return content.substring(9).split("\\|", 7); // Skip "TEST_MSG|" prefix
  }

  /**
   * Get message ID from network test message
   * 
   * @return message ID or null if not a network test message
   */
  public String getMessageId() {
    String[] parts = parseNetworkTestMessage();
    return parts != null && parts.length > 0 ? parts[0] : null;
  }

  /**
   * Get origin sender from network test message
   * 
   * @return origin sender or null if not a network test message
   */
  public String getOriginSender() {
    String[] parts = parseNetworkTestMessage();
    return parts != null && parts.length > 1 ? parts[1] : null;
  }

  /**
   * Get create time from network test message
   * 
   * @return create time or 0 if not a network test message
   */
  public long getCreateTime() {
    String[] parts = parseNetworkTestMessage();
    if (parts != null && parts.length > 2) {
      try {
        return Long.parseLong(parts[2]);
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }

  /**
   * Get hop count from network test message
   * 
   * @return hop count or 0 if not a network test message
   */
  public int getHopCount() {
    String[] parts = parseNetworkTestMessage();
    if (parts != null && parts.length > 3) {
      try {
        return Integer.parseInt(parts[3]);
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }

  /**
   * Get max hops from network test message
   * 
   * @return max hops or 0 if not a network test message
   */
  public int getMaxHops() {
    String[] parts = parseNetworkTestMessage();
    if (parts != null && parts.length > 4) {
      try {
        return Integer.parseInt(parts[4]);
      } catch (NumberFormatException e) {
        return 0;
      }
    }
    return 0;
  }

  /**
   * Get test type from network test message
   * 
   * @return test type or null if not a network test message
   */
  public String getTestType() {
    String[] parts = parseNetworkTestMessage();
    return parts != null && parts.length > 5 ? parts[5] : null;
  }

  /**
   * Get actual content from network test message
   * 
   * @return actual content or full content if not a network test message
   */
  public String getActualContent() {
    if (isNetworkTestMessage()) {
      String[] parts = parseNetworkTestMessage();
      return parts != null && parts.length > 6 ? parts[6] : "";
    }
    return getContentAsString();
  }

  /**
   * Create forwarded copy with incremented hop count
   * 
   * @param forwardingNodeId Node that is forwarding this message
   * @return Forwarded copy or null if not a network test message or expired
   */
  public TestMessage createForwardCopy(String forwardingNodeId) {
    if (!isNetworkTestMessage()) {
      return null;
    }
    
    int currentHops = getHopCount();
    int maxHops = getMaxHops();
    
    if (currentHops >= maxHops) {
      return null; // Message expired
    }
    
    return new TestMessage(
        getMessageId(),
        getOriginSender(),
        getCreateTime(),
        currentHops + 1,
        maxHops,
        getTestType(),
        getActualContent()
    );
  }

  /**
   * Check if message has expired (exceeded max hops)
   * 
   * @return true if expired
   */
  public boolean isExpired() {
    if (!isNetworkTestMessage()) {
      return false;
    }
    return getHopCount() >= getMaxHops();
  }

  /**
   * Get message age in milliseconds
   * 
   * @return age in milliseconds
   */
  public long getAge() {
    if (!isNetworkTestMessage()) {
      return 0;
    }
    return System.currentTimeMillis() - getCreateTime();
  }

  @Override
  public String toString() {
    if (isNetworkTestMessage()) {
      return String.format("TestMessage{type=%s, messageId='%s', sender='%s', hops=%d/%d, age=%dms, content='%s'}",
          type, getMessageId(), getOriginSender(), getHopCount(), getMaxHops(), getAge(), getActualContent());
    }
    return "TestMessage{" + "type=" + type + ", content='" + getContentAsString() + '\'' + '}';
  }
}
