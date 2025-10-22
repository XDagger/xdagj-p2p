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
package io.xdag.p2p.discover.dns;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.sync.Client;
import io.xdag.p2p.discover.dns.sync.RandomIterator;
import java.util.ArrayList;
import java.util.List;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RandomIterator functionality.
 * 
 * <p>These tests are optimized to avoid network dependencies and ensure fast, 
 * reliable execution in any environment including CI/CD pipelines.
 */
public class RandomTest {

  private P2pConfig p2pConfig;

  @BeforeEach
  void setUp() {
    p2pConfig = new P2pConfig();
    List<String> treeUrls = new ArrayList<>();
    treeUrls.add("tree://AKMQMNAJJBL73LXWPXDI4I5ZWWIZ4AWO34DWQ636QOBBXNFXH3LQS@nodes.example.org");
    p2pConfig.setTreeUrls(treeUrls);
  }

  @Test
  void testRandomIteratorConstruction() {
    Client syncClient = new Client(p2pConfig);
    
    assertDoesNotThrow(() -> {
      RandomIterator randomIterator = new RandomIterator(syncClient);
      
      // Verify all properties are properly initialized
      assertNotNull(randomIterator.getClient());
      assertNotNull(randomIterator.getClientTrees());
      assertNotNull(randomIterator.getLinkCache());
      assertNotNull(randomIterator.getRandom());
      
      randomIterator.close();
    });
  }

  @Test
  void testAddTree() {
    Client syncClient = new Client(p2pConfig);
    RandomIterator randomIterator = new RandomIterator(syncClient);
    
    assertDoesNotThrow(() -> {
      randomIterator.addTree("tree://AKMQMNAJJBL73LXWPXDI4I5ZWWIZ4AWO34DWQ636QOBBXNFXH3LQS@nodes.example.org");
      
      // Verify that the link cache is marked as changed
      assertTrue(randomIterator.getLinkCache().isChanged());
      
      randomIterator.close();
    });
  }

  @Test
  void testDnsNodeCreation() {
    assertDoesNotThrow(() -> {
      // Test creating a DnsNode with IPv4
      DnsNode dnsNodeV4 = new DnsNode(
          Bytes.fromHexString("0x1234567890abcdef").toHexString(),
          "192.168.1.100",
          null,
          8080
      );
      assertNotNull(dnsNodeV4);
      assertNotNull(dnsNodeV4.format());

      // Test creating a DnsNode with IPv6
      DnsNode dnsNodeV6 = new DnsNode(
          Bytes.fromHexString("0xabcdef1234567890").toHexString(),
          null,
          "2001:db8::1",
          9090
      );
      assertNotNull(dnsNodeV6);
      assertNotNull(dnsNodeV6.format());
    });
  }

  @Test
  void testNewIterator() {
    Client syncClient = new Client(p2pConfig);

    assertDoesNotThrow(() -> {
      RandomIterator randomIterator = syncClient.newIterator();
      
      assertNotNull(randomIterator);
      assertNotNull(randomIterator.getClient());
      
      randomIterator.close();
    });
        }

  @Test
  void testClose() {
    Client syncClient = new Client(p2pConfig);
    RandomIterator randomIterator = new RandomIterator(syncClient);
    
    // Close should not throw any exceptions
    assertDoesNotThrow(randomIterator::close);
  }
}
