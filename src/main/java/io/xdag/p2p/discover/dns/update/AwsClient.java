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
package io.xdag.p2p.discover.dns.update;

import io.xdag.p2p.DnsException;
import io.xdag.p2p.DnsException.TypeEnum;
import io.xdag.p2p.discover.dns.DnsNode;
import io.xdag.p2p.discover.dns.tree.LinkEntry;
import io.xdag.p2p.discover.dns.tree.NodesEntry;
import io.xdag.p2p.discover.dns.tree.RootEntry;
import io.xdag.p2p.discover.dns.tree.Tree;
import io.xdag.p2p.discover.dns.update.AwsClient.RecordSet;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.route53.Route53Client;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.ChangeBatch;
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsResponse;
import software.amazon.awssdk.services.route53.model.ChangeStatus;
import software.amazon.awssdk.services.route53.model.GetChangeRequest;
import software.amazon.awssdk.services.route53.model.GetChangeResponse;
import software.amazon.awssdk.services.route53.model.HostedZone;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameRequest;
import software.amazon.awssdk.services.route53.model.ListHostedZonesByNameResponse;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsRequest;
import software.amazon.awssdk.services.route53.model.ListResourceRecordSetsResponse;
import software.amazon.awssdk.services.route53.model.RRType;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;

/**
 * AWS Route53 DNS client for publishing DNS tree records. Implements DNS publishing functionality
 * using Amazon Web Services Route53.
 */
@Slf4j(topic = "net")
public class AwsClient implements Publish<RecordSet> {

  /** Route53 change size limit (32k RDATA size) */
  public static final int route53ChangeSizeLimit = 32000;

  /** Route53 change count limit (1000 items, UPSERTs count double) */
  public static final int route53ChangeCountLimit = 1000;

  /** Maximum retry limit for operations */
  public static final int maxRetryLimit = 60;

  /** Last sequence number from the root entry */
  private int lastSeq = 0;

  /** AWS Route53 client instance */
  private final Route53Client route53Client;

  /** AWS Route53 hosted zone ID */
  private String zoneId;

  /** Set of nodes currently published on the DNS server */
  private Set<DnsNode> serverNodes;

  /** Quote symbol for TXT records */
  private static final String symbol = "\"";

  /** DNS record postfix */
  private static final String postfix = ".";

  /** Threshold for triggering DNS tree updates */
  private final double changeThreshold;

  /**
   * Constructor for the AWS Route53 DNS client.
   *
   * @param accessKey the AWS access key ID
   * @param accessKeySecret the AWS access key secret
   * @param zoneId the Route53 hosted zone ID (can be null will be auto-detected)
   * @param region the AWS region
   * @param changeThreshold the threshold for triggering DNS updates
   * @throws DnsException if client initialization fails
   */
  public AwsClient(
      final String accessKey,
      final String accessKeySecret,
      final String zoneId,
      final String region,
      double changeThreshold)
      throws DnsException {
    if (StringUtils.isEmpty(accessKey) || StringUtils.isEmpty(accessKeySecret)) {
      throw new DnsException(
          TypeEnum.DEPLOY_DOMAIN_FAILED, "Need Route53 Access Key ID and secret to proceed");
    }
    StaticCredentialsProvider staticCredentialsProvider =
        StaticCredentialsProvider.create(
            new AwsCredentials() {
              @Override
              public String accessKeyId() {
                return accessKey;
              }

              @Override
              public String secretAccessKey() {
                return accessKeySecret;
              }
            });
    route53Client =
        Route53Client.builder()
            .credentialsProvider(staticCredentialsProvider)
            .region(Region.of(region))
            .build();
    this.zoneId = zoneId;
    this.serverNodes = new HashSet<>();
    this.changeThreshold = changeThreshold;
  }

  /**
   * Check and auto-detect the Route53 zone ID if not provided.
   *
   * @param domain the domain to check
   */
  private void checkZone(String domain) {
    if (StringUtils.isEmpty(this.zoneId)) {
      this.zoneId = findZoneID(domain);
    }
  }

  /**
   * Find the Route53 hosted zone ID for the given domain.
   *
   * @param domain the domain to find zone ID for
   * @return the zone ID if found, null otherwise
   */
  private String findZoneID(String domain) {
    log.info("Finding Route53 Zone ID for {}", domain);
    ListHostedZonesByNameRequest.Builder request = ListHostedZonesByNameRequest.builder();
    while (true) {
      ListHostedZonesByNameResponse response = route53Client.listHostedZonesByName(request.build());
      for (HostedZone hostedZone : response.hostedZones()) {
        if (isSubdomain(domain, hostedZone.name())) {
          // example: /hostedzone/Z0404776204LVYA8EZNVH
          return hostedZone.id().split("/")[2];
        }
      }
      if (Boolean.FALSE.equals(response.isTruncated())) {
        break;
      }
      request.dnsName(response.dnsName());
      request.hostedZoneId(response.nextHostedZoneId());
    }
    return null;
  }

  /** Test the connection to AWS Route53 service. */
  @Override
  public void testConnect() {
    ListHostedZonesByNameRequest.Builder request = ListHostedZonesByNameRequest.builder();
    while (true) {
      ListHostedZonesByNameResponse response = route53Client.listHostedZonesByName(request.build());
      if (Boolean.FALSE.equals(response.isTruncated())) {
        break;
      }
      request.dnsName(response.dnsName());
      request.hostedZoneId(response.nextHostedZoneId());
    }
  }

  /**
   * Deploy the given DNS tree to Route53.
   *
   * @param domain the domain to deploy to
   * @param tree the DNS tree to deploy
   * @throws Exception if deployment fails
   */
  @Override
  public void deploy(String domain, Tree tree) throws Exception {
    checkZone(domain);

    Map<String, RecordSet> existing = collectRecords(domain);
    log.info("Find {} TXT records, {} nodes for {}", existing.size(), serverNodes.size(), domain);
    String represent = LinkEntry.buildRepresent(tree.getBase32PublicKey(), domain);
    log.info("Trying to publish {}", represent);

    tree.setSeq(this.lastSeq + 1);
    tree.sign(); // seq changed, wo need to sign again
    Map<String, String> records = tree.toTXT(domain);

    List<Change> changes = computeChanges(domain, records, existing);

    Set<DnsNode> treeNodes = new HashSet<>(tree.getDnsNodes());
    treeNodes.removeAll(serverNodes); // tree - dns
    int addNodeSize = treeNodes.size();

    Set<DnsNode> set1 = new HashSet<>(serverNodes);
    treeNodes = new HashSet<>(tree.getDnsNodes());
    set1.removeAll(treeNodes); // dns - tree
    int deleteNodeSize = set1.size();

    if (serverNodes.isEmpty()
        || (addNodeSize + deleteNodeSize) / (double) serverNodes.size() >= changeThreshold) {
      String comment = String.format("Tree update of %s at seq %d", domain, tree.getSeq());
      log.info(comment);
      submitChanges(changes, comment);
    } else {
      NumberFormat nf = NumberFormat.getNumberInstance();
      nf.setMaximumFractionDigits(4);
      double changePercent = (addNodeSize + deleteNodeSize) / (double) serverNodes.size();
      log.info(
          "Sum of node add & delete percent {} is below changeThreshold {}, skip this changes",
          nf.format(changePercent),
          changeThreshold);
    }
    serverNodes.clear();
  }

  /**
   * Remove all TXT records of the given domain.
   *
   * @param rootDomain the domain to delete records from
   * @return true if deletion was successful
   * @throws Exception if deletion fails
   */
  @Override
  public boolean deleteDomain(String rootDomain) throws Exception {
    checkZone(rootDomain);

    Map<String, RecordSet> existing = collectRecords(rootDomain);
    log.info("Find {} TXT records for {}", existing.size(), rootDomain);

    List<Change> changes = makeDeletionChanges(new HashMap<>(), existing);

    String comment = String.format("delete entree of %s", rootDomain);
    submitChanges(changes, comment);
    return true;
  }

  /**
   * Collect all TXT records below the given domain name. Also updates the lastSeq field from the
   * root entry.
   *
   * @param rootDomain the domain to collect records from
   * @return map of record names to RecordSet objects
   * @throws Exception if record collection fails
   */
  @Override
  public Map<String, RecordSet> collectRecords(String rootDomain) throws Exception {
    Map<String, RecordSet> existing = new HashMap<>();
    ListResourceRecordSetsRequest.Builder request = ListResourceRecordSetsRequest.builder();
    request.hostedZoneId(zoneId);
    int page = 0;

    String rootContent = null;
    Set<DnsNode> collectServerNodes = new HashSet<>();
    while (true) {
      log.info(
          "Loading existing TXT records from name:{} zoneId:{} page:{}", rootDomain, zoneId, page);
      ListResourceRecordSetsResponse response =
          route53Client.listResourceRecordSets(request.build());

      List<ResourceRecordSet> recordSetList = response.resourceRecordSets();
      for (ResourceRecordSet resourceRecordSet : recordSetList) {
        if (!isSubdomain(resourceRecordSet.name(), rootDomain)
            || resourceRecordSet.type() != RRType.TXT) {
          continue;
        }
        List<String> values = new ArrayList<>();
        for (ResourceRecord resourceRecord : resourceRecordSet.resourceRecords()) {
          values.add(resourceRecord.value());
        }
        RecordSet recordSet = new RecordSet(values.toArray(new String[0]), resourceRecordSet.ttl());
        String name = StringUtils.stripEnd(resourceRecordSet.name(), postfix);
        existing.put(name, recordSet);

        String content = StringUtils.join(values, "");
        content = StringUtils.strip(content, symbol);
        if (rootDomain.equalsIgnoreCase(name)) {
          rootContent = content;
        }
        if (content.startsWith(io.xdag.p2p.discover.dns.tree.Entry.nodesPrefix)) {
          NodesEntry nodesEntry;
          try {
            nodesEntry = NodesEntry.parseEntry(content);
            List<DnsNode> dnsNodes = nodesEntry.nodes();
            collectServerNodes.addAll(dnsNodes);
          } catch (DnsException e) {
            // ignore
            log.error("Parse nodeEntry failed: {}", e.getMessage());
          }
        }
        log.info("Find name: {}", name);
      }

      if (Boolean.FALSE.equals(response.isTruncated())) {
        break;
      }
      // Set the cursor to the next batch. From the AWS docs:
      //
      // To display the next page of results, get the values of NextRecordName,
      // NextRecordType, and NextRecordIdentifier (if any) from the response. Then submit
      // another ListResourceRecordSets request, and specify those values for
      // StartRecordName, StartRecordType, and StartRecordIdentifier.
      request.startRecordIdentifier(response.nextRecordIdentifier());
      request.startRecordName(response.nextRecordName());
      request.startRecordType(response.nextRecordType());
      page += 1;
    }

    if (rootContent != null) {
      RootEntry rootEntry = RootEntry.parseEntry(rootContent);
      this.lastSeq = rootEntry.getSeq();
    }
    this.serverNodes = collectServerNodes;
    return existing;
  }

  /**
   * Submit the given DNS changes to Route53.
   *
   * @param changes the list of DNS changes to submit
   * @param comment the comment for the change batch
   */
  public void submitChanges(List<Change> changes, String comment) {
    if (changes.isEmpty()) {
      log.info("No DNS changes needed");
      return;
    }

    List<List<Change>> batchChanges =
        splitChanges(changes, route53ChangeSizeLimit, route53ChangeCountLimit);

    ChangeResourceRecordSetsResponse[] responses =
        new ChangeResourceRecordSetsResponse[batchChanges.size()];
    for (int i = 0; i < batchChanges.size(); i++) {
      log.info("Submit {}/{} changes to Route53", i + 1, batchChanges.size());

      ChangeBatch.Builder builder = ChangeBatch.builder();
      builder.changes(batchChanges.get(i));
      builder.comment(comment + String.format(" (%d/%d)", i + 1, batchChanges.size()));

      ChangeResourceRecordSetsRequest.Builder request = ChangeResourceRecordSetsRequest.builder();
      request.changeBatch(builder.build());
      request.hostedZoneId(this.zoneId);

      responses[i] = route53Client.changeResourceRecordSets(request.build());
    }

    // Wait for all change batches to propagate.
    for (ChangeResourceRecordSetsResponse response : responses) {
      log.info("Waiting for change request {}", response.changeInfo().id());

      GetChangeRequest.Builder request = GetChangeRequest.builder();
      request.id(response.changeInfo().id());

      int count = 0;
      while (true) {
        GetChangeResponse changeResponse = route53Client.getChange(request.build());
        count += 1;
        if (changeResponse.changeInfo().status() == ChangeStatus.INSYNC || count >= maxRetryLimit) {
          break;
        }
        try {
          Thread.sleep(15 * 1000);
        } catch (InterruptedException ignored) {
        }
      }
    }
    log.info("Submit {} changes complete", changes.size());
  }

  /**
   * Compute DNS changes for the given set of DNS discovery records.
   *
   * @param domain the domain name
   * @param records the latest records to be put in Route53
   * @param existing the set of records that already exist on Route53
   * @return list of DNS changes to apply
   */
  public List<Change> computeChanges(
      String domain, Map<String, String> records, Map<String, RecordSet> existing) {

    List<Change> changes = new ArrayList<>();
    for (Entry<String, String> entry : records.entrySet()) {
      String path = entry.getKey();
      String value = entry.getValue();
      String newValue = splitTxt(value);

      // name's ttl in our domain will not changed,
      // but this ttl on public dns server will decrease with time after request it first time
      long ttl = path.equalsIgnoreCase(domain) ? rootTTL : treeNodeTTL;

      if (!existing.containsKey(path)) {
        log.info("Create {} = {}", path, value);
        Change change = newTXTChange(ChangeAction.CREATE, path, ttl, newValue);
        changes.add(change);
      } else {
        RecordSet recordSet = existing.get(path);
        String preValue = StringUtils.join(recordSet.values, "");

        if (!preValue.equalsIgnoreCase(newValue) || recordSet.ttl != ttl) {
          log.info("Updating {} from [{}] to [{}]", path, preValue, newValue);
          if (path.equalsIgnoreCase(domain)) {
            try {
              RootEntry oldRoot = RootEntry.parseEntry(StringUtils.strip(preValue, symbol));
              RootEntry newRoot = RootEntry.parseEntry(StringUtils.strip(newValue, symbol));
              log.info(
                  "Updating root from [eRoot={},lRoot={},seq={}] to [eRoot={},lRoot={},seq={}]",
                  oldRoot.getERoot(), oldRoot.getLRoot(), oldRoot.getSeq(),
                  newRoot.getERoot(), newRoot.getLRoot(), newRoot.getSeq());
            } catch (DnsException e) {
              // ignore
            }
          }
          Change change = newTXTChange(ChangeAction.UPSERT, path, ttl, newValue);
          changes.add(change);
        }
      }
    }

    List<Change> deleteChanges = makeDeletionChanges(records, existing);
    changes.addAll(deleteChanges);

    sortChanges(changes);
    return changes;
  }

  /**
   * Create record changes which delete all records not contained in 'keeps'.
   *
   * @param keeps the records to keep (not delete)
   * @param existing the existing records on the server
   * @return list of deletion changes
   */
  public List<Change> makeDeletionChanges(
      Map<String, String> keeps, Map<String, RecordSet> existing) {
    List<Change> changes = new ArrayList<>();
    for (Entry<String, RecordSet> entry : existing.entrySet()) {
      String path = entry.getKey();
      RecordSet recordSet = entry.getValue();
      if (!keeps.containsKey(path)) {
        log.info("Delete {} = {}", path, StringUtils.join(existing.get(path).values, ""));
        Change change = newTXTChange(ChangeAction.DELETE, path, recordSet.ttl, recordSet.values);
        changes.add(change);
      }
    }
    return changes;
  }

  /**
   * Sort DNS changes in optimal order: leaf-added -> root-changed -> leaf-deleted.
   *
   * @param changes the list of changes to sort
   */
  public static void sortChanges(List<Change> changes) {
    changes.sort(
        (o1, o2) -> {
          if (getChangeOrder(o1) == getChangeOrder(o2)) {
            return o1.resourceRecordSet().name().compareTo(o2.resourceRecordSet().name());
          } else {
            return getChangeOrder(o1) - getChangeOrder(o2);
          }
        });
  }

  /**
   * Get the order priority for a DNS change.
   *
   * @param change the DNS change
   * @return the order priority (1=CREATE, 2=UPSERT, 3=DELETE, 4=other)
   */
  private static int getChangeOrder(Change change) {
    return switch (change.action()) {
      case CREATE -> 1;
      case UPSERT -> 2;
      case DELETE -> 3;
      default -> 4;
    };
  }

  /**
   * Split DNS changes into batches that respect Route53 size and count limits.
   *
   * @param changes the list of changes to split
   * @param sizeLimit the maximum RDATA size per batch
   * @param countLimit the maximum number of changes per batch
   * @return list of change batches
   */
  private static List<List<Change>> splitChanges(
      List<Change> changes, int sizeLimit, int countLimit) {
    List<List<Change>> batchChanges = new ArrayList<>();

    List<Change> subChanges = new ArrayList<>();
    int batchSize = 0;
    int batchCount = 0;
    for (Change change : changes) {
      int changeCount = getChangeCount(change);
      int changeSize = getChangeSize(change) * changeCount;

      if (batchCount + changeCount <= countLimit && batchSize + changeSize <= sizeLimit) {
        subChanges.add(change);
        batchCount += changeCount;
        batchSize += changeSize;
      } else {
        batchChanges.add(subChanges);
        subChanges = new ArrayList<>();
        subChanges.add(change);
        batchSize = changeSize;
        batchCount = changeCount;
      }
    }
    if (!subChanges.isEmpty()) {
      batchChanges.add(subChanges);
    }
    return batchChanges;
  }

  /**
   * Calculate the RDATA size of a DNS change.
   *
   * @param change the DNS change
   * @return the total size of the change's resource records
   */
  private static int getChangeSize(Change change) {
    int dataSize = 0;
    for (ResourceRecord resourceRecord : change.resourceRecordSet().resourceRecords()) {
      dataSize += resourceRecord.value().length();
    }
    return dataSize;
  }

  /**
   * Get the count of operations for a DNS change. UPSERT counts as 2 operations, others count as 1.
   *
   * @param change the DNS change
   * @return the operation count
   */
  private static int getChangeCount(Change change) {
    if (change.action() == ChangeAction.UPSERT) {
      return 2;
    }
    return 1;
  }

  /**
   * Check if two DNS changes are identical.
   *
   * @param c1 the first change
   * @param c2 the second change
   * @return true if the changes are identical, false otherwise
   */
  public static boolean isSameChange(Change c1, Change c2) {
    boolean isSame =
        c1.action().equals(c2.action())
            && c1.resourceRecordSet().ttl().longValue() == c2.resourceRecordSet().ttl().longValue()
            && c1.resourceRecordSet().name().equals(c2.resourceRecordSet().name())
            && c1.resourceRecordSet().resourceRecords().size()
                == c2.resourceRecordSet().resourceRecords().size();
    if (!isSame) {
      return false;
    }
    List<ResourceRecord> list1 = c1.resourceRecordSet().resourceRecords();
    List<ResourceRecord> list2 = c2.resourceRecordSet().resourceRecords();
    for (int i = 0; i < list1.size(); i++) {
      if (!list1.get(i).equalsBySdkFields(list2.get(i))) {
        return false;
      }
    }
    return true;
  }

  /**
   * Create a DNS change for a TXT record.
   *
   * @param action the change action (CREATE, UPSERT, DELETE)
   * @param key the record name
   * @param ttl the time-to-live in seconds
   * @param values the record values
   * @return the DNS change object
   */
  public Change newTXTChange(ChangeAction action, String key, long ttl, String... values) {
    ResourceRecordSet.Builder builder =
        ResourceRecordSet.builder().name(key).type(RRType.TXT).ttl(ttl);
    List<ResourceRecord> resourceRecords = new ArrayList<>();
    for (String value : values) {
      ResourceRecord.Builder builder1 = ResourceRecord.builder();
      builder1.value(value);
      resourceRecords.add(builder1.build());
    }
    builder.resourceRecords(resourceRecords);

    Change.Builder builder2 = Change.builder();
    builder2.action(action);
    builder2.resourceRecordSet(builder.build());
    return builder2.build();
  }

  /**
   * Split a TXT record value into quoted 255-character strings. Only used in CREATE and UPSERT
   * operations.
   *
   * @param value the value to split
   * @return the formatted TXT record value
   */
  private String splitTxt(String value) {
    StringBuilder sb = new StringBuilder();
    while (value.length() > 253) {
      sb.append(symbol).append(value, 0, 253).append(symbol);
      value = value.substring(253);
    }
    if (!value.isEmpty()) {
      sb.append(symbol).append(value).append(symbol);
    }
    return sb.toString();
  }

  /**
   * Check if a domain is a subdomain of another domain.
   *
   * @param sub the potential subdomain
   * @param root the root domain
   * @return true if sub is a subdomain of root, false otherwise
   */
  public static boolean isSubdomain(String sub, String root) {
    String subNoSuffix = postfix + StringUtils.strip(sub, postfix);
    String rootNoSuffix = postfix + StringUtils.strip(root, postfix);
    return subNoSuffix.endsWith(rootNoSuffix);
  }

  /** Represents a DNS record set with values and TTL. */
  public static class RecordSet {

    /** The record values */
    String[] values;

    /** The time-to-live in seconds */
    long ttl;

    /**
     * Constructor for RecordSet.
     *
     * @param values the record values
     * @param ttl the time-to-live in seconds
     */
    public RecordSet(String[] values, long ttl) {
      this.values = values;
      this.ttl = ttl;
    }
  }
}
