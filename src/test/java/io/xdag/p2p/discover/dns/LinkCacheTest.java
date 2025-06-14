package io.xdag.p2p.discover.dns;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.xdag.p2p.discover.dns.sync.LinkCache;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;

public class LinkCacheTest {

  @Test
  public void testLinkCache() {
    LinkCache lc = new LinkCache();

    lc.addLink("1", "2");
    assertTrue(lc.isChanged());

    lc.setChanged(false);
    lc.addLink("1", "2");
    assertFalse(lc.isChanged());

    lc.addLink("2", "3");
    lc.addLink("3", "1");
    lc.addLink("2", "4");

    for (String key : lc.getBackrefs().keySet()) {
      System.out.println(key + "：" + StringUtils.join(lc.getBackrefs().get(key), ","));
    }
    assertTrue(lc.isContainInOtherLink("3"));
    assertFalse(lc.isContainInOtherLink("6"));

    lc.resetLinks("1", null);
    assertTrue(lc.isChanged());
    assertEquals(0, lc.getBackrefs().size());
  }
}
