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
package io.xdag.p2p.discover.kad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.xdag.p2p.discover.Node;
import io.xdag.p2p.discover.kad.table.KademliaOptions;
import org.apache.tuweni.bytes.Bytes;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class DiscoverTaskTest {

    private KadService kadService;
    private DiscoverTask discoverTask;

    @BeforeEach
    void setUp() {
        kadService = Mockito.mock(KadService.class);
        discoverTask = new DiscoverTask(kadService);
    }

    @Test
    void testNextTargetIdReturnsRandomWhenHomeIdEmpty() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(null);
        Mockito.when(kadService.getPublicHomeNode()).thenReturn(home);

        Bytes id1 = discoverTask.nextTargetId();
        Bytes id2 = discoverTask.nextTargetId();

        assertNotNull(id1);
        assertNotNull(id2);
        assertFalse(id1.isEmpty());
        assertFalse(id2.isEmpty());
    }

    @Test
    void testNextTargetIdReturnsHomeIdEveryMaxLoop() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        Mockito.when(kadService.getPublicHomeNode()).thenReturn(home);

        Bytes last = null;
        for (int i = 0; i < KademliaOptions.MAX_LOOP_NUM - 1; i++) {
            last = discoverTask.nextTargetId();
        }
        Bytes same = discoverTask.nextTargetId();

        assertNotNull(same);
        assertEquals(home.getId(), same.toUnprefixedHexString());
        assertNotNull(last);
        assertNotEquals(same, last);
    }

    @Test
    void testLoopCounterResetsAfterHomeId() {
        Node home = new Node(null, "127.0.0.1", null, 30303);
        home.setId(Bytes.random(20).toUnprefixedHexString());
        Mockito.when(kadService.getPublicHomeNode()).thenReturn(home);

        for (int i = 0; i < KademliaOptions.MAX_LOOP_NUM; i++) {
            discoverTask.nextTargetId();
        }
        Bytes next = discoverTask.nextTargetId();
        assertNotNull(next);
    }
}


