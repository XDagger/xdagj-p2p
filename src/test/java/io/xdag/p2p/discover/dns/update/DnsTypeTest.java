package io.xdag.p2p.discover.dns.update;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** Unit tests for DnsType enum. Tests DNS service provider enumeration functionality. */
class DnsTypeTest {

  @Test
  void testAliYunValues() {
    DnsType aliYun = DnsType.AliYun;
    assertEquals(0, aliYun.getValue());
    assertEquals("aliyun dns server", aliYun.getDesc());
  }

  @Test
  void testAwsRoute53Values() {
    DnsType awsRoute53 = DnsType.AwsRoute53;
    assertEquals(1, awsRoute53.getValue());
    assertEquals("aws route53 server", awsRoute53.getDesc());
  }

  @Test
  void testEnumValues() {
    DnsType[] values = DnsType.values();
    assertEquals(2, values.length);
    assertEquals(DnsType.AliYun, values[0]);
    assertEquals(DnsType.AwsRoute53, values[1]);
  }

  @Test
  void testValueOf() {
    assertEquals(DnsType.AliYun, DnsType.valueOf("AliYun"));
    assertEquals(DnsType.AwsRoute53, DnsType.valueOf("AwsRoute53"));
  }

  @Test
  void testValueOfInvalidName() {
    assertThrows(IllegalArgumentException.class, () -> DnsType.valueOf("InvalidType"));
  }

  @Test
  void testEnumOrder() {
    DnsType[] values = DnsType.values();
    assertTrue(values[0].getValue() < values[1].getValue());
  }
} 