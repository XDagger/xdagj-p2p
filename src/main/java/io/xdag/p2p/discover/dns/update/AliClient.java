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

import com.aliyun.alidns20150109.Client;
import com.aliyun.alidns20150109.models.*;
import com.aliyun.alidns20150109.models.DescribeDomainRecordsResponseBody.DescribeDomainRecordsResponseBodyDomainRecordsRecord;
import com.aliyun.teaopenapi.models.Config;
import io.xdag.p2p.DnsException;
import io.xdag.p2p.config.P2pConfig;
import io.xdag.p2p.discover.dns.DnsNode;
import io.xdag.p2p.discover.dns.tree.Entry;
import io.xdag.p2p.discover.dns.tree.LinkEntry;
import io.xdag.p2p.discover.dns.tree.NodesEntry;
import io.xdag.p2p.discover.dns.tree.RootEntry;
import io.xdag.p2p.discover.dns.tree.Tree;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Alibaba Cloud DNS client for publishing DNS tree records. Implements DNS publishing functionality
 * using Alibaba Cloud DNS service.
 */
@Slf4j(topic = "net")
public class AliClient implements Publish<DescribeDomainRecordsResponseBodyDomainRecordsRecord> {

  private final P2pConfig p2pConfig;

  /** Page size for domain records queries */
  private final Long domainRecordsPageSize = 20L;

  /** Maximum number of retry attempts for failed operations */
  private final int maxRetryCount = 3;

  /** Expected success status code for API responses */
  private final int successCode = 200;

  /** Wait time between retries in seconds */
  private final long retryWaitTime = 30;

  /** TTL for tree node records (24 hours) */
  private final int treeNodeTTL = 24 * 60 * 60;

  /** Last sequence number from the root entry */
  private int lastSeq = 0;

  /** Set of nodes currently published on the DNS server */
  private Set<DnsNode> serverNodes;

  /** Alibaba Cloud DNS client instance */
  private final Client aliDnsClient;

  /** Threshold for triggering DNS tree updates */
  private final double changeThreshold;

  /** Root record identifier for Alibaba Cloud DNS */
  public static final String aliyunRoot = "@";

  /**
   * Constructor for Alibaba Cloud DNS client.
   *
   * @param endpoint the Alibaba Cloud DNS endpoint
   * @param accessKeyId the access key ID for authentication
   * @param accessKeySecret the access key secret for authentication
   * @param changeThreshold the threshold for triggering DNS updates
   * @throws Exception if client initialization fails
   */
  public AliClient(
      P2pConfig p2pConfig,
      String endpoint,
      String accessKeyId,
      String accessKeySecret,
      double changeThreshold)
      throws Exception {
    this.p2pConfig = p2pConfig;
    Config config = new Config();
    config.accessKeyId = accessKeyId;
    config.accessKeySecret = accessKeySecret;
    config.endpoint = endpoint;
    this.changeThreshold = changeThreshold;
    this.serverNodes = new HashSet<>();
    aliDnsClient = new Client(config);
  }

  /** Test the connection to Alibaba Cloud DNS service. Currently a no-op implementation. */
  @Override
  public void testConnect() {}

  @Override
  public void deploy(String domainName, Tree t) throws DnsException {
    try {
      Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> existing =
          collectRecords(domainName);
      log.info(
          "Find {} TXT records, {} nodes for {}", existing.size(), serverNodes.size(), domainName);
      String represent = LinkEntry.buildRepresent(t.getBase32PublicKey(), domainName);
      log.info("Trying to publish {}", represent);
      t.setSeq(this.lastSeq + 1);
      t.sign(); // seq changed, wo need to sign again
      Map<String, String> records = t.toTXT(null);

      Set<DnsNode> treeNodes = new HashSet<>(t.getDnsNodes());
      treeNodes.removeAll(serverNodes); // tree - dns
      int addNodeSize = treeNodes.size();

      Set<DnsNode> set1 = new HashSet<>(serverNodes);
      treeNodes = new HashSet<>(t.getDnsNodes());
      set1.removeAll(treeNodes); // dns - tree
      int deleteNodeSize = set1.size();

      if (serverNodes.isEmpty()
          || (addNodeSize + deleteNodeSize) / (double) serverNodes.size() >= changeThreshold) {
        String comment = String.format("Tree update of %s at seq %d", domainName, t.getSeq());
        log.info(comment);
        submitChanges(domainName, records, existing);
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
    } catch (Exception e) {
      throw new DnsException(DnsException.TypeEnum.DEPLOY_DOMAIN_FAILED, e);
    }
  }

  @Override
  public boolean deleteDomain(String domainName) throws Exception {
    DeleteSubDomainRecordsRequest request = new DeleteSubDomainRecordsRequest();
    request.setDomainName(domainName);
    DeleteSubDomainRecordsResponse response = aliDnsClient.deleteSubDomainRecords(request);
    return response.statusCode == successCode;
  }

  /**
   * Collects all TXT records below the given domain name. Also updates the lastSeq field from the
   * root entry.
   *
   * @param domain the domain name to query
   * @return map of record names to record objects
   * @throws Exception if record collection fails
   */
  @Override
  public Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> collectRecords(
      String domain) throws Exception {
    Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> records = new HashMap<>();

    String rootContent = null;
    Set<DnsNode> collectServerNodes = new HashSet<>();
    try {
      DescribeDomainRecordsRequest request = new DescribeDomainRecordsRequest();
      request.setDomainName(domain);
      request.setType("TXT");
      request.setPageSize(domainRecordsPageSize);
      Long currentPageNum = 1L;
      while (true) {
        request.setPageNumber(currentPageNum);
        DescribeDomainRecordsResponse response = aliDnsClient.describeDomainRecords(request);
        if (response.statusCode == successCode) {
          for (DescribeDomainRecordsResponseBodyDomainRecordsRecord r :
              response.getBody().getDomainRecords().getRecord()) {
            String name = StringUtils.stripEnd(r.getRR(), ".");
            records.put(name, r);
            if (aliyunRoot.equalsIgnoreCase(name)) {
              rootContent = r.value;
            }
            if (StringUtils.isNotEmpty(r.value) && r.value.startsWith(Entry.nodesPrefix)) {
              NodesEntry nodesEntry;
              try {
                nodesEntry = NodesEntry.parseEntry(p2pConfig, r.value);
                List<DnsNode> dnsNodes = nodesEntry.nodes();
                collectServerNodes.addAll(dnsNodes);
              } catch (DnsException e) {
                // ignore
                log.error("Parse nodeEntry failed: {}", e.getMessage());
              }
            }
          }
          if (currentPageNum * domainRecordsPageSize >= response.getBody().getTotalCount()) {
            break;
          }
          currentPageNum++;
        } else {
          throw new Exception("Failed to request domain records");
        }
      }
    } catch (Exception e) {
      log.warn("Failed to collect domain records, error msg: {}", e.getMessage());
      throw e;
    }

    if (rootContent != null) {
      RootEntry rootEntry = RootEntry.parseEntry(rootContent);
      this.lastSeq = rootEntry.getSeq();
    }
    this.serverNodes = collectServerNodes;
    return records;
  }

  /**
   * Submit DNS record changes to Alibaba Cloud DNS.
   *
   * @param domainName the domain name to update
   * @param records the new records to deploy
   * @param existing the existing records on the server
   * @throws Exception if submission fails
   */
  private void submitChanges(
      String domainName,
      Map<String, String> records,
      Map<String, DescribeDomainRecordsResponseBodyDomainRecordsRecord> existing)
      throws Exception {
    long ttl;
    long addCount = 0;
    long updateCount = 0;
    long deleteCount = 0;
    for (Map.Entry<String, String> entry : records.entrySet()) {
      boolean result = true;
      ttl = treeNodeTTL;
      if (entry.getKey().equals(aliyunRoot)) {
        ttl = rootTTL;
      }
      if (!existing.containsKey(entry.getKey())) {
        result = addRecord(domainName, entry.getKey(), entry.getValue(), ttl);
        addCount++;
      } else if (!entry.getValue().equals(existing.get(entry.getKey()).getValue())
          || existing.get(entry.getKey()).getTTL() != ttl) {
        result =
            updateRecord(
                existing.get(entry.getKey()).getRecordId(), entry.getKey(), entry.getValue(), ttl);
        updateCount++;
      }

      if (!result) {
        throw new Exception("Adding or updating record failed");
      }
    }

    for (String key : existing.keySet()) {
      if (!records.containsKey(key)) {
        deleteRecord(existing.get(key).getRecordId());
        deleteCount++;
      }
    }
    log.info(
        "Published successfully, add count:{}, update count:{}, delete count:{}",
        addCount,
        updateCount,
        deleteCount);
  }

  /**
   * Add a new DNS TXT record.
   *
   * @param domainName the domain name
   * @param RR the resource record name
   * @param value the record value
   * @param ttl the time-to-live in seconds
   * @return true if addition was successful, false otherwise
   * @throws Exception if the operation fails
   */
  public boolean addRecord(String domainName, String RR, String value, long ttl) throws Exception {
    AddDomainRecordRequest request = new AddDomainRecordRequest();
    request.setDomainName(domainName);
    request.setRR(RR);
    request.setType("TXT");
    request.setValue(value);
    request.setTTL(ttl);
    int retryCount = 0;
    while (true) {
      AddDomainRecordResponse response = aliDnsClient.addDomainRecord(request);
      if (response.statusCode == successCode) {
        break;
      } else if (retryCount < maxRetryCount) {
        retryCount++;
        Thread.sleep(retryWaitTime);
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * Update an existing DNS TXT record.
   *
   * @param recId the record ID
   * @param RR the resource record name
   * @param value the new record value
   * @param ttl the time-to-live in seconds
   * @return true if update was successful, false otherwise
   * @throws Exception if the operation fails
   */
  public boolean updateRecord(String recId, String RR, String value, long ttl) throws Exception {
    UpdateDomainRecordRequest request = new UpdateDomainRecordRequest();
    request.setRecordId(recId);
    request.setRR(RR);
    request.setType("TXT");
    request.setValue(value);
    request.setTTL(ttl);
    int retryCount = 0;
    while (true) {
      UpdateDomainRecordResponse response = aliDnsClient.updateDomainRecord(request);
      if (response.statusCode == successCode) {
        break;
      } else if (retryCount < maxRetryCount) {
        retryCount++;
        Thread.sleep(retryWaitTime);
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * Delete a DNS record by record ID.
   *
   * @param recId the record ID to delete
   * @return true if deletion was successful, false otherwise
   * @throws Exception if the operation fails
   */
  public boolean deleteRecord(String recId) throws Exception {
    DeleteDomainRecordRequest request = new DeleteDomainRecordRequest();
    request.setRecordId(recId);
    int retryCount = 0;
    while (true) {
      DeleteDomainRecordResponse response = aliDnsClient.deleteDomainRecord(request);
      if (response.statusCode == successCode) {
        break;
      } else if (retryCount < maxRetryCount) {
        retryCount++;
        Thread.sleep(retryWaitTime);
      } else {
        return false;
      }
    }
    return true;
  }

  /**
   * Get the record ID for a specific resource record.
   *
   * @param domainName the domain name
   * @param RR the resource record name
   * @return the record ID if found, null otherwise
   */
  public String getRecId(String domainName, String RR) {
    String recId = null;
    try {
      DescribeDomainRecordsRequest request = new DescribeDomainRecordsRequest();
      request.setDomainName(domainName);
      request.setRRKeyWord(RR);
      DescribeDomainRecordsResponse response = aliDnsClient.describeDomainRecords(request);
      if (response.getBody().getTotalCount() > 0) {
        List<DescribeDomainRecordsResponseBodyDomainRecordsRecord> recs =
            response.getBody().getDomainRecords().getRecord();
        for (DescribeDomainRecordsResponseBodyDomainRecordsRecord rec : recs) {
          if (rec.getRR().equalsIgnoreCase(RR)) {
            recId = rec.getRecordId();
            break;
          }
        }
      }
    } catch (Exception e) {
      log.warn("Failed to get record id, error msg: {}", e.getMessage());
    }
    return recId;
  }

  /**
   * Update or add a DNS record (creates if not exists, updates if exists).
   *
   * @param DomainName the domain name
   * @param RR the resource record name
   * @param value the record value
   * @param ttl the time-to-live in seconds
   * @return the record ID of the updated/created record
   */
  public String update(String DomainName, String RR, String value, long ttl) {
    String type = "TXT";
    String recId = null;
    try {
      String existRecId = getRecId(DomainName, RR);
      if (existRecId == null || existRecId.isEmpty()) {
        AddDomainRecordRequest request = new AddDomainRecordRequest();
        request.setDomainName(DomainName);
        request.setRR(RR);
        request.setType(type);
        request.setValue(value);
        request.setTTL(ttl);
        AddDomainRecordResponse response = aliDnsClient.addDomainRecord(request);
        recId = response.getBody().getRecordId();
      } else {
        UpdateDomainRecordRequest request = new UpdateDomainRecordRequest();
        request.setRecordId(existRecId);
        request.setRR(RR);
        request.setType(type);
        request.setValue(value);
        request.setTTL(ttl);
        UpdateDomainRecordResponse response = aliDnsClient.updateDomainRecord(request);
        recId = response.getBody().getRecordId();
      }
    } catch (Exception e) {
      log.warn("Failed to update or add domain record, error mag: {}", e.getMessage());
    }

    return recId;
  }
}
