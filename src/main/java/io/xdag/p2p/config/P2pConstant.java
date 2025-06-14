package io.xdag.p2p.config;

import java.util.Arrays;
import java.util.List;

public class P2pConstant {

  public static final int NODE_ID_LEN =
      64; // 64 bytes = 512 bits, keep original value for unit tests
  public static final List<String> ipV4Urls =
      Arrays.asList("http://checkip.amazonaws.com", "https://ifconfig.me/", "https://4.ipw.cn/");
  public static final List<String> ipV6Urls =
      Arrays.asList("https://v6.ident.me", "http://6.ipw.cn/");
  public static final String ipV4Hex = "00000000"; // 32 bit
  public static final String ipV6Hex = "00000000000000000000000000000000"; // 128 bit

  public static int version = 1;
  public static final int TCP_NETTY_WORK_THREAD_NUM = 0;
  public static final int UDP_NETTY_WORK_THREAD_NUM = 1;
  public static final int NODE_CONNECTION_TIMEOUT = 2000;
  public static final int KEEP_ALIVE_TIMEOUT = 20_000;
  public static final int PING_TIMEOUT = 20_000;
  public static final int NETWORK_TIME_DIFF = 1000;
  public static final long DEFAULT_BAN_TIME = 60_000;
  public static final int MAX_MESSAGE_LENGTH = 5 * 1024 * 1024;

  public static final long NODE_DETECT_THRESHOLD = 5 * 60 * 1000;
  public static final long NODE_DETECT_MIN_THRESHOLD = 30 * 1000;
  public static final long NODE_DETECT_TIMEOUT = 2 * 1000;
  public static final int MAX_NODE_SLOW_DETECT = 3;
  public static final int MAX_NODE_NORMAL_DETECT = 10;
  public static final int MAX_NODE_FAST_DETECT = 100;
  public static final int MAX_NODES = 300;
  public static final int MIN_NODES = 200;
}
