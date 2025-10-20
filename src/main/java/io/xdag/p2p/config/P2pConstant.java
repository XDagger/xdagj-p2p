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

import java.util.Arrays;
import java.util.List;

public class P2pConstant {

  public static final List<String> ipV4Urls =
      Arrays.asList("http://checkip.amazonaws.com", "https://ifconfig.me/", "https://4.ipw.cn/");
  public static final List<String> ipV6Urls =
      Arrays.asList("https://v6.ident.me", "http://6.ipw.cn/");
  public static final String ipV4Hex = "00000000"; // 32 bit
  public static final String ipV6Hex = "00000000000000000000000000000000"; // 128 bit

  public static final short MAINNET_ID = 0;
  public static final short TESTNET_ID = 1;
  public static final short DEVNET_ID = 2;

  public static final short MAINNET_VERSION = 0;
  public static final short TESTNET_VERSION = 0;
  public static final short DEVNET_VERSION = 0;

  public static int version = 1;
  // 0 means using Netty default: cores * 2
  public static final int TCP_NETTY_WORK_THREAD_NUM = 0;
  public static final int UDP_NETTY_WORK_THREAD_NUM = 1;
  public static final int NODE_CONNECTION_TIMEOUT = 2000;
  public static final int PING_TIMEOUT = 20_000;
  public static final long DEFAULT_BAN_TIME = 60_000;
  public static final int MAX_MESSAGE_LENGTH = 5 * 1024 * 1024;

  public static final Long MESSAGE_QUEUE_SEND_PERIOD = 10L;
}
