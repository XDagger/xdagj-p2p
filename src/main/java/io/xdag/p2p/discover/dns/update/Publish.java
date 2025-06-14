package io.xdag.p2p.discover.dns.update;

import io.xdag.p2p.discover.dns.tree.Tree;
import java.util.Map;

/**
 * Interface for DNS publishing operations. Provides methods to deploy, manage and query DNS tree
 * records.
 *
 * @param <T> the type of DNS records
 */
public interface Publish<T> {

  /** Default TTL for root DNS records (10 minutes) */
  int rootTTL = 10 * 60;

  /** Default TTL for tree node DNS records (7 days) */
  int treeNodeTTL = 7 * 24 * 60 * 60;

  /**
   * Test the connection to the DNS service.
   *
   * @throws Exception if connection test fails
   */
  void testConnect() throws Exception;

  /**
   * Deploy a DNS tree to the specified domain.
   *
   * @param domainName the domain name to deploy to
   * @param t the DNS tree to deploy
   * @throws Exception if deployment fails
   */
  void deploy(String domainName, Tree t) throws Exception;

  /**
   * Delete all records for the specified domain.
   *
   * @param domainName the domain name to delete
   * @return true if deletion was successful, false otherwise
   * @throws Exception if deletion operation fails
   */
  boolean deleteDomain(String domainName) throws Exception;

  /**
   * Collect all DNS records for the specified domain.
   *
   * @param domainName the domain name to query
   * @return map of record names to record values
   * @throws Exception if record collection fails
   */
  Map<String, T> collectRecords(String domainName) throws Exception;
}
