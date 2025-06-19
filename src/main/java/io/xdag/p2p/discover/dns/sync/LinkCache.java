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
package io.xdag.p2p.discover.dns.sync;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j(topic = "net")
public class LinkCache {

  @Getter Map<String, Set<String>> backrefs;
  @Getter @Setter private boolean changed; // if data in backrefs changes, we need to rebuild trees

  public LinkCache() {
    backrefs = new HashMap<>();
    changed = false;
  }

  // check if the urlScheme occurs in other trees
  public boolean isContainInOtherLink(String urlScheme) {
    return backrefs.containsKey(urlScheme) && !backrefs.get(urlScheme).isEmpty();
  }

  /**
   * add the reference to backrefs
   *
   * @param parent the url tree that contains url tree `children`
   * @param children url tree
   */
  public void addLink(String parent, String children) {
    Set<String> refs = backrefs.getOrDefault(children, new HashSet<>());
    if (!refs.contains(parent)) {
      changed = true;
    }
    refs.add(parent);
    backrefs.put(children, refs);
  }

  /**
   * clears all links of the given tree.
   *
   * @param from tree's urlScheme
   * @param keep links contained in this tree
   */
  public void resetLinks(String from, final Set<String> keep) {
    List<String> stk = new ArrayList<>();
    stk.add(from);

    while (!stk.isEmpty()) {
      int size = stk.size();
      String item = stk.get(size - 1);
      stk = stk.subList(0, size - 1);

      Iterator<Entry<String, Set<String>>> it = backrefs.entrySet().iterator();
      while (it.hasNext()) {
        Entry<String, Set<String>> entry = it.next();
        String r = entry.getKey();
        Set<String> refs = entry.getValue();
        if ((keep != null && keep.contains(r)) || !refs.contains(item)) {
          continue;
        }
        this.changed = true;
        refs.remove(item);
        if (refs.isEmpty()) {
          it.remove();
          stk.add(r);
        }
      }
    }
  }
}
