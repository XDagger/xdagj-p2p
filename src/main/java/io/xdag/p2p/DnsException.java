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
package io.xdag.p2p;

import lombok.Getter;

@Getter
public class DnsException extends Exception {

  private final TypeEnum type;

  public DnsException(TypeEnum type, String errMsg) {
    super(type.desc + ", " + errMsg);
    this.type = type;
  }

  public DnsException(TypeEnum type, Throwable throwable) {
    super(throwable);
    this.type = type;
  }

  public DnsException(TypeEnum type, String errMsg, Throwable throwable) {
    super(errMsg, throwable);
    this.type = type;
  }

  @Getter
  public enum TypeEnum {
    LOOK_UP_ROOT_FAILED(0, "look up root failed"),
    // Resolver/sync errors
    NO_ROOT_FOUND(1, "no valid root found"),
    NO_ENTRY_FOUND(2, "no valid tree entry found"),
    HASH_MISS_MATCH(3, "hash miss match"),
    NODES_IN_LINK_TREE(4, "nodes entry in link tree"),
    LINK_IN_NODES_TREE(5, "link entry in nodes tree"),

    // Entry parse errors
    UNKNOWN_ENTRY(6, "unknown entry type"),
    NO_PUBLIC_KEY(7, "missing public key"),
    BAD_PUBLIC_KEY(8, "invalid public key"),
    INVALID_NODES(9, "invalid node list"),
    INVALID_CHILD(10, "invalid child hash"),
    INVALID_SIGNATURE(11, "invalid base64 signature"),
    INVALID_ROOT(12, "invalid DnsRoot proto"),
    INVALID_SCHEME_URL(13, "invalid scheme url"),

    // Publish error
    DEPLOY_DOMAIN_FAILED(14, "failed to deploy domain"),

    OTHER_ERROR(15, "other error");

    private final Integer value;
    private final String desc;

    TypeEnum(Integer value, String desc) {
      this.value = value;
      this.desc = desc;
    }

    @Override
    public String toString() {
      return value + "-" + desc;
    }
  }
}
