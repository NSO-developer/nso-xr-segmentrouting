package com.tailf.pkg.testjunit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.mockito.Mockito;

import com.tailf.pkg.ipam.Allocation;
import com.tailf.pkg.ipam.Subnet;
import com.tailf.pkg.ipaddressallocator.AllocationsSet;
import com.tailf.cdb.CdbSession;

public class IPAllocationsSetTest {

    @Test
    public void testAddAndRemove() {
        CdbSession mockedCdb = Mockito.mock(CdbSession.class);
        AllocationsSet set = new AllocationsSet(mockedCdb, "hello");

        try {
            Allocation a1 = new Allocation(new Subnet("10.0.0.1", 16),
                                           "TheOccupant",
                                           "admin",
                                           "id");

            assertTrue(set.add(a1));
            assertFalse(set.add(a1));
            assertTrue(set.contains(a1));

            assertTrue(set.remove(a1));
            assertFalse(set.remove(a1));
            assertFalse(set.contains(a1));
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testClear() {
        CdbSession mockedCdb = Mockito.mock(CdbSession.class);
        AllocationsSet set = new AllocationsSet(mockedCdb, "hello");

        try {
            Allocation a1 = new Allocation(new Subnet("10.0.0.1", 16),
                                           "TheOccupant",
                                           "admin",
                                           "id");

            assertTrue(set.add(a1));
            assertFalse(set.isEmpty());
            set.clear();
            assertTrue(set.isEmpty());
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }
}
