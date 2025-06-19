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
