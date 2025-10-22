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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import io.xdag.p2p.DnsException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.tree.Entry;
import io.xdag.p2p.discover.dns.tree.Tree;
import io.xdag.p2p.discover.dns.update.PublishConfig;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import org.junit.jupiter.api.Test;

public class TreeTest {

  private static final P2pConfig p2pConfig = new P2pConfig();

  public static DnsNode[] sampleNode() throws UnknownHostException {
    return new DnsNode[] {
      new DnsNode(null, "192.168.0.1", null, 10000),
      new DnsNode(null, "192.168.0.2", null, 10000),
      new DnsNode(null, "192.168.0.3", null, 10000),
      new DnsNode(null, "192.168.0.4", null, 10000),
      new DnsNode(null, "192.168.0.5", null, 10000),
      new DnsNode(null, "192.168.0.6", null, 10001),
      new DnsNode(null, "192.168.0.6", null, 10002),
      new DnsNode(null, "192.168.0.6", null, 10003),
      new DnsNode(null, "192.168.0.6", null, 10004),
      new DnsNode(null, "192.168.0.6", null, 10005),
      new DnsNode(null, "192.168.0.10", "fe80::0001", 10005),
      new DnsNode(null, "192.168.0.10", "fe80::0002", 10005),
      new DnsNode(null, null, "fe80::0001", 10000),
      new DnsNode(null, null, "fe80::0002", 10000),
      new DnsNode(null, null, "fe80::0003", 10001),
      new DnsNode(null, null, "fe80::0004", 10001),
    };
  }

  @Test
  public void testMerge() throws UnknownHostException {
    DnsNode[] nodes = sampleNode();
    List<DnsNode> nodeList = Arrays.asList(nodes);

    int maxMergeSize = new PublishConfig().getMaxMergeSize();
    List<String> enrs = Tree.merge(nodeList, maxMergeSize);
    int total = 0;
    for (String enr : enrs) {
      List<DnsNode> subList = null;
      subList = DnsNode.decompress(enr.substring(Entry.nodesPrefix.length()));
      assertTrue(subList.size() <= maxMergeSize);
      total += subList.size();
    }
    assertEquals(nodeList.size(), total);
  }

  @Test
  public void testTreeBuild() throws UnknownHostException {
    int seq = 0;

    DnsNode[] dnsNodes =
        new DnsNode[] {
          new DnsNode(null, "192.168.0.1", null, 10000),
          new DnsNode(null, "192.168.0.2", null, 10000),
          new DnsNode(null, "192.168.0.3", null, 10000),
          new DnsNode(null, "192.168.0.4", null, 10000),
          new DnsNode(null, "192.168.0.5", null, 10000),
          new DnsNode(null, "192.168.0.6", null, 10000),
          new DnsNode(null, "192.168.0.7", null, 10000),
          new DnsNode(null, "192.168.0.8", null, 10000),
          new DnsNode(null, "192.168.0.9", null, 10000),
          new DnsNode(null, "192.168.0.10", null, 10000),
          new DnsNode(null, "192.168.0.11", null, 10000),
          new DnsNode(null, "192.168.0.12", null, 10000),
          new DnsNode(null, "192.168.0.13", null, 10000),
          new DnsNode(null, "192.168.0.14", null, 10000),
          new DnsNode(null, "192.168.0.15", null, 10000),
          new DnsNode(null, "192.168.0.16", null, 10000),
          new DnsNode(null, "192.168.0.17", null, 10000),
          new DnsNode(null, "192.168.0.18", null, 10000),
          new DnsNode(null, "192.168.0.19", null, 10000),
          new DnsNode(null, "192.168.0.20", null, 10000),
          new DnsNode(null, "192.168.0.21", null, 10000),
          new DnsNode(null, "192.168.0.22", null, 10000),
          new DnsNode(null, "192.168.0.23", null, 10000),
          new DnsNode(null, "192.168.0.24", null, 10000),
          new DnsNode(null, "192.168.0.25", null, 10000),
          new DnsNode(null, "192.168.0.26", null, 10000),
          new DnsNode(null, "192.168.0.27", null, 10000),
          new DnsNode(null, "192.168.0.28", null, 10000),
          new DnsNode(null, "192.168.0.29", null, 10000),
          new DnsNode(null, "192.168.0.30", null, 10000),
          new DnsNode(null, "192.168.0.31", null, 10000),
          new DnsNode(null, "192.168.0.32", null, 10000),
          new DnsNode(null, "192.168.0.33", null, 10000),
          new DnsNode(null, "192.168.0.34", null, 10000),
          new DnsNode(null, "192.168.0.35", null, 10000),
          new DnsNode(null, "192.168.0.36", null, 10000),
          new DnsNode(null, "192.168.0.37", null, 10000),
          new DnsNode(null, "192.168.0.38", null, 10000),
          new DnsNode(null, "192.168.0.39", null, 10000),
          new DnsNode(null, "192.168.0.40", null, 10000),
        };

    String[] enrs = new String[dnsNodes.length];
    for (int i = 0; i < dnsNodes.length; i++) {
      DnsNode dnsNode = dnsNodes[i];
      List<DnsNode> nodeList = new ArrayList<>();
      nodeList.add(dnsNode);
      enrs[i] = Entry.nodesPrefix + DnsNode.compress(nodeList);
    }

    String[] links = new String[] {};

    // Expected branch hash values - updated for SimpleCodec serialization
    String branch0 = "tree-branch:BTEO6672SIATDLTRLSR3FZHVUE,KVWOVYARSND2N2GFXADWOKUJDU,FHULNSAGBBLZKTXD3ZUTFETQVQ,2BT32SRCVYHR6KDXKR5UZX7EUQ,A6XQD65SEK5UXRVVXMVJWIZEX4,JU6AEAFRXQRZ7DZU7QZKBW22YM,5IPSDTWXPJ2DQWYPDJKUPV3YCY,SUFA6JX2QYWXZOIKQ4OKJ6VCBA,O45QBMP3TOYFFVWDHNFRMZHW6A,LPDPPHKFZBPYGGTY24YUFIDVYM,KFGZ7XTXNOSV3HMHI6SWBW3G24,QBFFGT3UCEKZ355RV5HPFS7HUI,OMLDRHNX36K7ZEPVHOZLRECDPM";
    String branch1 = "tree-branch:";
    String branch2 = "tree-branch:ZVVWI7MQIQYRGP5TSDZOIFLNRA,3EAYOUW4W6DTUIGZFN3NMJP5IY,D6L3OQVLMLI3UBLBQAC4JJLJ7A,COQUJTM3E7QLY62VWNPPMY65MU,VQE3OHR6RG55WCG4PPS45MEESY,MCLTT65ZBIZ6NQHS4YER5O3FBI,SDELZDR46FLNXPITJO3FVD6E2Q,NRNRZKKXMKOZHZQO6HEYEGPSCA,T4LWZMTNHZUQ6VWL4ZEPEG4FTA,2GGURLZVDRN7FVP6VQWZWBD3SQ,X6VA4KWGRWKPQTYTSMAUBCYSLI,L33LMGDXMK5DQF54VSOHKWZE2M,UBHNVTCWUCL7JQC2BQXD6GER6M";
    String branch3 = "tree-branch:ECIRFXBJBLA4N63SMXF3CBXR3Q,K4DASN5MW2ITXFHWFYH4IVJ2UI,RVFJISVHGKCOGCNHOPOQP6EM4Y,OD575NVTP7U263RBRQ6CGIEGR4";
    String branch4 = "tree-branch:7T7KQQYWO45Y7HTDEV5SMZWDBM,JBCF625RWEACWT25PLDDJXZVGU,XB6YD3BCSJ7VKC33PP2YCO4HKM,BMD4IWDDHKGHSVHBX7XLK63ECI,2Q4QUYXSWWRA7BU6LHQ2IFTZVE,OAZCDFUHAS24VFL7MFGXA4YXO4,OVDB3ML57234YWGWS6ZVTAY544,XLD3UCDVEXMQU4IZR6TUIXUFJE,DY2SUXB3NYWJOESSETAPPVE2ZA,3NA2ETBGW6H7CWVWT6TAXMADTA,7BRNWV3ENXYYZXL7VAT6YEUPME,X7FMMDIPLIV2WCHLBBOKL6LOOA,5KHAZSMEFCKT33346KAAORRNJ4";

    String[] branches = new String[] {branch0, branch1, branch2, branch3, branch4};

    List<String> branchList = Arrays.asList(branches);
    List<String> enrList = Arrays.asList(enrs);
    List<String> linkList = Arrays.asList(links);

    Tree tree = new Tree(p2pConfig);
    try {
      tree.makeTree(seq, enrList, linkList, null);
    } catch (DnsException e) {
      fail();
    }

    /*
     * Tree structure with SimpleCodec serialization:
     *                                  b  r  a  n  c  h  4
     *                   /                 /           \          \
     *                /                  /               \           \
     *             /                    /                   \            \
     *          branch0 (root)        branch2              branch3        branch1 (empty)
     *        /      \              /       \             /       \
     *      node:01-13            node:14-26           node:27-39          node:40
     */

    assertEquals(branchList.size() + enrList.size() + linkList.size(), tree.getEntries().size());
    assertEquals(branchList.size(), tree.getBranchesEntry().size());
    assertEquals(enrList.size(), tree.getNodesEntry().size());
    assertEquals(linkList.size(), tree.getLinksEntry().size());

    for (String branch : tree.getBranchesEntry()) {
      assertTrue(branchList.contains(branch), "Branch not found: " + branch);
    }
    for (String nodeEntry : tree.getNodesEntry()) {
      assertTrue(enrList.contains(nodeEntry));
    }
    for (String link : tree.getLinksEntry()) {
      assertTrue(linkList.contains(link));
    }

    // Root hash assertions - these are the actual generated hash values for SimpleCodec serialization
    assertEquals("75VCAFX6SKFKXYC6TDY3IEHHHM", tree.getRootEntry().getERoot());
    assertEquals("G763M53MOPYWUVJSW6CGE27GE4", tree.getRootEntry().getLRoot());
    assertEquals(seq, tree.getSeq());
  }

  @Test
  public void testGroupAndMerge() throws UnknownHostException {
    Random random = new Random();
    // simulate some nodes
    int ipCount = 2000;
    int maxMergeSize = 5;
    List<DnsNode> dnsNodes = new ArrayList<>();
    Set<String> ipSet = new HashSet<>();
    int i = 0;
    while (i < ipCount) {
      i += 1;
      String ip =
          String.format(
              "%d.%d.%d.%d",
              random.nextInt(256), random.nextInt(256), random.nextInt(256), random.nextInt(256));
      if (ipSet.contains(ip)) {
        continue;
      }
      ipSet.add(ip);
      dnsNodes.add(new DnsNode(null, ip, null, 10000));
    }
    Set<String> enrSet1 = new HashSet<>(Tree.merge(dnsNodes, maxMergeSize));
    System.out.println("srcSize:" + enrSet1.size());

    // delete some node
    int deleteCount = 100;
    i = 0;
    while (i < deleteCount) {
      i += 1;
      int deleteIndex = random.nextInt(dnsNodes.size());
      dnsNodes.remove(deleteIndex);
    }

    // add some node
    int addCount = 100;
    i = 0;
    while (i < addCount) {
      i += 1;
      String ip =
          String.format(
              "%d.%d.%d.%d",
              random.nextInt(256), random.nextInt(256), random.nextInt(256), random.nextInt(256));
      if (ipSet.contains(ip)) {
        continue;
      }
      ipSet.add(ip);
      dnsNodes.add(new DnsNode(null, ip, null, 10000));
    }
    Set<String> enrSet2 = new HashSet<>(Tree.merge(dnsNodes, maxMergeSize));

    // calculate changes
    Set<String> enrSet3 = new HashSet<>(enrSet2);
    enrSet3.removeAll(enrSet1); // enrSet2 - enrSet1
    System.out.println("addSize:" + enrSet3.size());
    assertTrue(enrSet3.size() < enrSet1.size());

    Set<String> enrSet4 = new HashSet<>(enrSet1);
    enrSet4.removeAll(enrSet2); // enrSet1 - enrSet2
    System.out.println("deleteSize:" + enrSet4.size());
    assertTrue(enrSet4.size() < enrSet1.size());

    Set<String> enrSet5 = new HashSet<>(enrSet1);
    enrSet5.retainAll(enrSet2); // enrSet1 && enrSet2
    System.out.println("intersectionSize:" + enrSet5.size());
    assertTrue(enrSet5.size() < enrSet1.size());
  }
}
