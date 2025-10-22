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

import static org.junit.jupiter.api.Assertions.*;

import io.xdag.p2p.DnsException;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.route53.model.Change;
import software.amazon.awssdk.services.route53.model.ChangeAction;
import software.amazon.awssdk.services.route53.model.RRType;
import software.amazon.awssdk.services.route53.model.ResourceRecord;
import software.amazon.awssdk.services.route53.model.ResourceRecordSet;

import java.util.ArrayList;
import java.util.List;

public class AwsClientTest {

    // ==================== Constructor Tests ====================

    @Test
    public void testConstructorWithMissingAccessKey() {
        // When/Then
        DnsException exception = assertThrows(DnsException.class, () -> {
            new AwsClient(null, "secret", "zone123", "us-east-1", 0.3);
        });
        assertTrue(exception.getMessage().contains("Access Key"));
    }

    @Test
    public void testConstructorWithMissingSecret() {
        // When/Then
        DnsException exception = assertThrows(DnsException.class, () -> {
            new AwsClient("AKIAIOSFODNN7EXAMPLE", null, "zone123", "us-east-1", 0.3);
        });
        assertTrue(exception.getMessage().contains("Access Key"));
    }

    @Test
    public void testConstructorWithEmptyAccessKey() {
        // When/Then
        DnsException exception = assertThrows(DnsException.class, () -> {
            new AwsClient("", "secret", "zone123", "us-east-1", 0.3);
        });
        assertTrue(exception.getMessage().contains("Access Key"));
    }

    @Test
    public void testConstructorWithEmptySecret() {
        // When/Then
        DnsException exception = assertThrows(DnsException.class, () -> {
            new AwsClient("AKIAIOSFODNN7EXAMPLE", "", "zone123", "us-east-1", 0.3);
        });
        assertTrue(exception.getMessage().contains("Access Key"));
    }

    // ==================== isSubdomain() Tests ====================

    @Test
    public void testIsSubdomainWithExactMatch() {
        assertTrue(AwsClient.isSubdomain("example.com", "example.com"));
    }

    @Test
    public void testIsSubdomainWithValidSubdomain() {
        assertTrue(AwsClient.isSubdomain("sub.example.com", "example.com"));
    }

    @Test
    public void testIsSubdomainWithDeepSubdomain() {
        assertTrue(AwsClient.isSubdomain("a.b.c.example.com", "example.com"));
    }

    @Test
    public void testIsSubdomainWithTrailingDot() {
        assertTrue(AwsClient.isSubdomain("sub.example.com.", "example.com"));
    }

    @Test
    public void testIsSubdomainWithBothTrailingDots() {
        assertTrue(AwsClient.isSubdomain("sub.example.com.", "example.com."));
    }

    @Test
    public void testIsSubdomainWithRootTrailingDot() {
        assertTrue(AwsClient.isSubdomain("sub.example.com", "example.com."));
    }

    @Test
    public void testIsSubdomainNotASubdomain() {
        assertFalse(AwsClient.isSubdomain("other.com", "example.com"));
    }

    @Test
    public void testIsSubdomainPartialMatch() {
        assertFalse(AwsClient.isSubdomain("notexample.com", "example.com"));
    }

    @Test
    public void testIsSubdomainReversedRelationship() {
        assertFalse(AwsClient.isSubdomain("example.com", "sub.example.com"));
    }

    // ==================== sortChanges() Tests ====================

    @Test
    public void testSortChangesEmpty() {
        List<Change> changes = new ArrayList<>();
        AwsClient.sortChanges(changes);
        assertEquals(0, changes.size());
    }

    @Test
    public void testSortChangesSingleItem() {
        List<Change> changes = new ArrayList<>();
        changes.add(createChange(ChangeAction.CREATE, "a.example.com", 300));

        AwsClient.sortChanges(changes);

        assertEquals(1, changes.size());
    }

    @Test
    public void testSortChangesOrdersCREATEFirst() {
        List<Change> changes = new ArrayList<>();
        changes.add(createChange(ChangeAction.DELETE, "a.example.com", 300));
        changes.add(createChange(ChangeAction.CREATE, "b.example.com", 300));
        changes.add(createChange(ChangeAction.UPSERT, "c.example.com", 300));

        AwsClient.sortChanges(changes);

        assertEquals(ChangeAction.CREATE, changes.get(0).action());
        assertEquals(ChangeAction.UPSERT, changes.get(1).action());
        assertEquals(ChangeAction.DELETE, changes.get(2).action());
    }

    @Test
    public void testSortChangesAlphabeticalWithinSameAction() {
        List<Change> changes = new ArrayList<>();
        changes.add(createChange(ChangeAction.CREATE, "c.example.com", 300));
        changes.add(createChange(ChangeAction.CREATE, "a.example.com", 300));
        changes.add(createChange(ChangeAction.CREATE, "b.example.com", 300));

        AwsClient.sortChanges(changes);

        assertEquals("a.example.com", changes.get(0).resourceRecordSet().name());
        assertEquals("b.example.com", changes.get(1).resourceRecordSet().name());
        assertEquals("c.example.com", changes.get(2).resourceRecordSet().name());
    }

    @Test
    public void testSortChangesComplexScenario() {
        List<Change> changes = new ArrayList<>();
        // Add in random order
        changes.add(createChange(ChangeAction.DELETE, "z.example.com", 300));
        changes.add(createChange(ChangeAction.CREATE, "b.example.com", 300));
        changes.add(createChange(ChangeAction.UPSERT, "m.example.com", 300));
        changes.add(createChange(ChangeAction.CREATE, "a.example.com", 300));
        changes.add(createChange(ChangeAction.DELETE, "a.example.com", 300));
        changes.add(createChange(ChangeAction.UPSERT, "c.example.com", 300));

        AwsClient.sortChanges(changes);

        // Verify order: CREATE (a,b) -> UPSERT (c,m) -> DELETE (a,z)
        assertEquals(ChangeAction.CREATE, changes.get(0).action());
        assertEquals("a.example.com", changes.get(0).resourceRecordSet().name());

        assertEquals(ChangeAction.CREATE, changes.get(1).action());
        assertEquals("b.example.com", changes.get(1).resourceRecordSet().name());

        assertEquals(ChangeAction.UPSERT, changes.get(2).action());
        assertEquals("c.example.com", changes.get(2).resourceRecordSet().name());

        assertEquals(ChangeAction.UPSERT, changes.get(3).action());
        assertEquals("m.example.com", changes.get(3).resourceRecordSet().name());

        assertEquals(ChangeAction.DELETE, changes.get(4).action());
        assertEquals("a.example.com", changes.get(4).resourceRecordSet().name());

        assertEquals(ChangeAction.DELETE, changes.get(5).action());
        assertEquals("z.example.com", changes.get(5).resourceRecordSet().name());
    }

    // ==================== isSameChange() Tests ====================

    @Test
    public void testIsSameChangeIdenticalChanges() {
        Change c1 = createChange(ChangeAction.CREATE, "example.com", 300, "value1");
        Change c2 = createChange(ChangeAction.CREATE, "example.com", 300, "value1");

        assertTrue(AwsClient.isSameChange(c1, c2));
    }

    @Test
    public void testIsSameChangeDifferentAction() {
        Change c1 = createChange(ChangeAction.CREATE, "example.com", 300, "value1");
        Change c2 = createChange(ChangeAction.DELETE, "example.com", 300, "value1");

        assertFalse(AwsClient.isSameChange(c1, c2));
    }

    @Test
    public void testIsSameChangeDifferentTTL() {
        Change c1 = createChange(ChangeAction.CREATE, "example.com", 300, "value1");
        Change c2 = createChange(ChangeAction.CREATE, "example.com", 600, "value1");

        assertFalse(AwsClient.isSameChange(c1, c2));
    }

    @Test
    public void testIsSameChangeDifferentName() {
        Change c1 = createChange(ChangeAction.CREATE, "a.example.com", 300, "value1");
        Change c2 = createChange(ChangeAction.CREATE, "b.example.com", 300, "value1");

        assertFalse(AwsClient.isSameChange(c1, c2));
    }

    @Test
    public void testIsSameChangeDifferentRecordCount() {
        Change c1 = createChange(ChangeAction.CREATE, "example.com", 300, "value1");
        Change c2 = createChange(ChangeAction.CREATE, "example.com", 300, "value1", "value2");

        assertFalse(AwsClient.isSameChange(c1, c2));
    }

    @Test
    public void testIsSameChangeDifferentValues() {
        Change c1 = createChange(ChangeAction.CREATE, "example.com", 300, "value1");
        Change c2 = createChange(ChangeAction.CREATE, "example.com", 300, "value2");

        assertFalse(AwsClient.isSameChange(c1, c2));
    }

    @Test
    public void testIsSameChangeMultipleIdenticalValues() {
        Change c1 = createChange(ChangeAction.UPSERT, "example.com", 300, "val1", "val2", "val3");
        Change c2 = createChange(ChangeAction.UPSERT, "example.com", 300, "val1", "val2", "val3");

        assertTrue(AwsClient.isSameChange(c1, c2));
    }

    // ==================== RecordSet Tests ====================

    @Test
    public void testRecordSetCreation() {
        String[] values = {"value1", "value2"};
        long ttl = 300;

        AwsClient.RecordSet recordSet = new AwsClient.RecordSet(values, ttl);

        assertArrayEquals(values, recordSet.values);
        assertEquals(ttl, recordSet.ttl);
    }

    @Test
    public void testRecordSetWithEmptyValues() {
        String[] values = {};
        long ttl = 600;

        AwsClient.RecordSet recordSet = new AwsClient.RecordSet(values, ttl);

        assertEquals(0, recordSet.values.length);
        assertEquals(ttl, recordSet.ttl);
    }

    @Test
    public void testRecordSetWithSingleValue() {
        String[] values = {"single-value"};
        long ttl = 3600;

        AwsClient.RecordSet recordSet = new AwsClient.RecordSet(values, ttl);

        assertEquals(1, recordSet.values.length);
        assertEquals("single-value", recordSet.values[0]);
        assertEquals(ttl, recordSet.ttl);
    }

    // ==================== Helper Methods ====================

    private Change createChange(ChangeAction action, String name, long ttl, String... values) {
        List<ResourceRecord> records = new ArrayList<>();
        for (String value : values) {
            records.add(ResourceRecord.builder().value(value).build());
        }

        ResourceRecordSet recordSet = ResourceRecordSet.builder()
            .name(name)
            .type(RRType.TXT)
            .ttl(ttl)
            .resourceRecords(records)
            .build();

        return Change.builder()
            .action(action)
            .resourceRecordSet(recordSet)
            .build();
    }
}
