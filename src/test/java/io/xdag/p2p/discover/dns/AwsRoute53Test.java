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
import io.xdag.p2p.discover.dns.tree.Tree;
import io.xdag.p2p.discover.dns.update.AwsClient;
import io.xdag.p2p.discover.dns.update.AwsClient.RecordSet;
import io.xdag.p2p.discover.dns.update.PublishConfig;
import io.xdag.p2p.utils.EncodeUtilsTest;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;

public class AwsRoute53Test {

  private final P2pConfig p2pConfig = new P2pConfig();

  @Test
  public void testChangeSort() {

    Map<String, RecordSet> existing = new HashMap<>();
    existing.put(
        "n",
        new RecordSet(
            new String[] {
              "tree-root-v1:CjoKGlVKQU9JQlMyUFlZMjJYUU1WRlNXT1RZSlhVEhpGRFhOM1NONjdOQTVES0E0SjJHT0s3QlZRSRgIEldBTE5aWHEyRkk5Ui1ubjdHQk9HdWJBRFVPakZ2MWp5TjZiUHJtSWNTNks0ZE0wc1dKMUwzT2paWFRGei1KcldDenZZVHJId2RMSTlUczRPZ2Q4TXlJUnM"
            },
            AwsClient.rootTTL));
    existing.put(
        "2kfjogvxdqtxxugbh7gs7naaai.n",
        new RecordSet(
            new String[] {
              "nodes:-HW4QO1ml1DdXLeZLsUxewnthhUy8eROqkDyoMTyavfks9JlYQIlMFEUoM78PovJDPQrAkrb3LRJ-",
              "vtrymDguKCOIAWAgmlkgnY0iXNlY3AyNTZrMaEDffaGfJzgGhUif1JqFruZlYmA31HzathLSWxfbq_QoQ4"
            },
            3333));
    existing.put(
        "fdxn3sn67na5dka4j2gok7bvqi.n",
        new RecordSet(new String[] {"tree-branch:"}, AwsClient.treeNodeTTL));

    Map<String, String> newRecords = new HashMap<>();
    newRecords.put(
        "n",
        "tree-root-v1:CjoKGkZEWE4zU042N05BNURLQTRKMkdPSzdCVlFJEhpGRFhOM1NONjdOQTVES0E0SjJHT0s3QlZRSRgJElc5aDU4d1cyajUzdlBMeHNBSGN1cDMtV0ZEM2lvZUk4SkJrZkdYSk93dmI0R0lHR01pQVAxRkJVVGc4bHlORERleXJkck9uSDdSbUNUUnJRVGxqUm9UaHM");
    newRecords.put(
        "c7hrfpf3blgf3yr4dy5kx3smbe.n",
        "tree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@morenodes.example.org");
    newRecords.put(
        "jwxydbpxywg6fx3gmdibfa6cj4.n",
        "tree-branch:2XS2367YHAXJFGLZHVAWLQD4ZY,H4FHT4B454P6UXFD7JCYQ5PWDY,MHTDO6TMUBRIA2XWG5LUDACK24");
    newRecords.put(
        "2xs2367yhaxjfglzhvawlqd4zy.n",
        "nodes:-HW4QOFzoVLaFJnNhbgMoDXPnOvcdVuj7pDpqRvh6BRDO68aVi5ZcjB3vzQRZH2IcLBGHzo8uUN3snqmgTiE56CH3AMBgmlkgnY0iXNlY3AyNTZrMaECC2_24YYkYHEgdzxlSNKQEnHhuNAbNlMlWJxrJxbAFvA");
    newRecords.put(
        "h4fht4b454p6uxfd7jcyq5pwdy.n",
        "nodes:-HW4QAggRauloj2SDLtIHN1XBkvhFZ1vtf1raYQp9TBW2RD5EEawDzbtSmlXUfnaHcvwOizhVYLtr7e6vw7NAf6mTuoCgmlkgnY0iXNlY3AyNTZrMaECjrXI8TLNXU0f8cthpAMxEshUyQlK-AM0PW2wfrnacNI");
    newRecords.put(
        "mhtdo6tmubria2xwg5ludack24.n",
        "nodes:-HW4QLAYqmrwllBEnzWWs7I5Ev2IAs7x_dZlbYdRdMUx5EyKHDXp7AV5CkuPGUPdvbv1_Ms1CPfhcGCvSElSosZmyoqAgmlkgnY0iXNlY3AyNTZrMaECriawHKWdDRk2xeZkrOXBQ0dfMFLHY4eENZwdufn1S1o");

    AwsClient publish;
    try {
      publish =
          new AwsClient(
              p2pConfig,
              "random1",
              "random2",
              "random3",
              "us-east-1",
              p2pConfig.getPublishConfig().getChangeThreshold());
    } catch (DnsException e) {
      fail();
      return;
    }
    List<Change> changes = publish.computeChanges("n", newRecords, existing);

    Change[] wantChanges =
        new Change[] {
          publish.newTXTChange(
              ChangeAction.CREATE,
              "2xs2367yhaxjfglzhvawlqd4zy.n",
              AwsClient.treeNodeTTL,
              "\"nodes:-HW4QOFzoVLaFJnNhbgMoDXPnOvcdVuj7pDpqRvh6BRDO68aVi5ZcjB3vzQRZH2IcLBGHzo8uUN3snqmgTiE56CH3AMBgmlkgnY0iXNlY3AyNTZrMaECC2_24YYkYHEgdzxlSNKQEnHhuNAbNlMlWJxrJxbAFvA\""),
          publish.newTXTChange(
              ChangeAction.CREATE,
              "c7hrfpf3blgf3yr4dy5kx3smbe.n",
              AwsClient.treeNodeTTL,
              "\"tree://AM5FCQLWIZX2QFPNJAP7VUERCCRNGRHWZG3YYHIUV7BVDQ5FDPRT2@morenodes.example.org\""),
          publish.newTXTChange(
              ChangeAction.CREATE,
              "h4fht4b454p6uxfd7jcyq5pwdy.n",
              AwsClient.treeNodeTTL,
              "\"nodes:-HW4QAggRauloj2SDLtIHN1XBkvhFZ1vtf1raYQp9TBW2RD5EEawDzbtSmlXUfnaHcvwOizhVYLtr7e6vw7NAf6mTuoCgmlkgnY0iXNlY3AyNTZrMaECjrXI8TLNXU0f8cthpAMxEshUyQlK-AM0PW2wfrnacNI\""),
          publish.newTXTChange(
              ChangeAction.CREATE,
              "jwxydbpxywg6fx3gmdibfa6cj4.n",
              AwsClient.treeNodeTTL,
              "\"tree-branch:2XS2367YHAXJFGLZHVAWLQD4ZY,H4FHT4B454P6UXFD7JCYQ5PWDY,MHTDO6TMUBRIA2XWG5LUDACK24\""),
          publish.newTXTChange(
              ChangeAction.CREATE,
              "mhtdo6tmubria2xwg5ludack24.n",
              AwsClient.treeNodeTTL,
              "\"nodes:-HW4QLAYqmrwllBEnzWWs7I5Ev2IAs7x_dZlbYdRdMUx5EyKHDXp7AV5CkuPGUPdvbv1_Ms1CPfhcGCvSElSosZmyoqAgmlkgnY0iXNlY3AyNTZrMaECriawHKWdDRk2xeZkrOXBQ0dfMFLHY4eENZwdufn1S1o\""),
          publish.newTXTChange(
              ChangeAction.UPSERT,
              "n",
              AwsClient.rootTTL,
              "\"tree-root-v1:CjoKGkZEWE4zU042N05BNURLQTRKMkdPSzdCVlFJEhpGRFhOM1NONjdOQTVES0E0SjJHT0s3QlZRSRgJElc5aDU4d1cyajUzdlBMeHNBSGN1cDMtV0ZEM2lvZUk4SkJrZkdYSk93dmI0R0lHR01pQVAxRkJVVGc4bHlORERleXJkck9uSDdSbUNUUnJRVGxqUm9UaHM\""),
          publish.newTXTChange(
              ChangeAction.DELETE,
              "2kfjogvxdqtxxugbh7gs7naaai.n",
              3333,
              "nodes:-HW4QO1ml1DdXLeZLsUxewnthhUy8eROqkDyoMTyavfks9JlYQIlMFEUoM78PovJDPQrAkrb3LRJ-",
              "vtrymDguKCOIAWAgmlkgnY0iXNlY3AyNTZrMaEDffaGfJzgGhUif1JqFruZlYmA31HzathLSWxfbq_QoQ4"),
          publish.newTXTChange(
              ChangeAction.DELETE,
              "fdxn3sn67na5dka4j2gok7bvqi.n",
              AwsClient.treeNodeTTL,
              "tree-branch:")
        };

    assertEquals(wantChanges.length, changes.size());
    for (int i = 0; i < changes.size(); i++) {
      assertTrue(wantChanges[i].equalsBySdkFields(changes.get(i)));
      assertTrue(AwsClient.isSameChange(wantChanges[i], changes.get(i)));
    }
  }

  @Test
  public void testPublish() throws UnknownHostException {

    DnsNode[] nodes = TreeTest.sampleNode();
    List<DnsNode> nodeList = Arrays.asList(nodes);
    List<String> enrList = Tree.merge(nodeList, new PublishConfig().getMaxMergeSize());

    String[] links =
        new String[] {
          "tree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@example1.org",
          "tree://AKA3AM6LPBYEUDMVNU3BSVQJ5AD45Y7YPOHJLEF6W26QOE4VTUDPE@example2.org"
        };
    List<String> linkList = Arrays.asList(links);

    Tree tree = new Tree(p2pConfig);
    try {
      tree.makeTree(1, enrList, linkList, EncodeUtilsTest.privateKey);
    } catch (DnsException e) {
      fail();
    }

  }
}
