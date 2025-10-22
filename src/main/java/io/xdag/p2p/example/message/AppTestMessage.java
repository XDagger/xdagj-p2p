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
/*
 * The MIT License (MIT)
 */
package io.xdag.p2p.example.message;

import io.xdag.p2p.message.Message;
import io.xdag.p2p.message.MessageCode;
import io.xdag.p2p.utils.SimpleEncoder;

/**
 * Application test message carried over P2P as MessageCode.APP_TEST.
 * Body layout: [appType (1 byte) | appBody...]
 */
public class AppTestMessage extends Message {

  public AppTestMessage(byte[] body) {
    super(MessageCode.APP_TEST, null);
    this.body = body != null ? body : new byte[0];
  }

  public AppTestMessage(byte appType, byte[] appBody) {
    super(MessageCode.APP_TEST, null);
    if (appBody == null) {
      this.body = new byte[] { appType };
    } else {
      this.body = new byte[1 + appBody.length];
      this.body[0] = appType;
      System.arraycopy(appBody, 0, this.body, 1, appBody.length);
    }
  }

  @Override
  public void encode(SimpleEncoder enc) {
    if (body != null && body.length > 0) {
      enc.writeBytes(body);
    }
  }
}


