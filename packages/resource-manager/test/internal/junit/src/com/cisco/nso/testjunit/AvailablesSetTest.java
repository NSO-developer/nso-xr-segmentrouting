package com.tailf.pkg.testjunit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.mockito.Mockito;

import com.tailf.pkg.ipaddressallocator.AvailablesSet;
import com.tailf.pkg.ipam.Subnet;
import com.tailf.pkg.ipam.SubnetComparator;
import com.tailf.cdb.CdbSession;

public class AvailablesSetTest {

    @Test
    public void testAddAndRemove() throws ClassNotFoundException {

        CdbSession mockedCdb = Mockito.mock(CdbSession.class);
        AvailablesSet set = new AvailablesSet(mockedCdb,
                                              "hello",
                                              new SubnetComparator());

        try {
            Subnet subnet = new Subnet("10.0.0.1", 16);

            assertTrue(set.add(subnet));
            assertFalse(set.add(subnet));
            assertTrue(set.contains(subnet));

            assertTrue(set.remove(subnet));
            assertFalse(set.remove(subnet));
            assertFalse(set.contains(subnet));
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testClear() {
        CdbSession mockedCdb = Mockito.mock(CdbSession.class);
        AvailablesSet set = new AvailablesSet(mockedCdb,
                                              "hello",
                                              new SubnetComparator());

        try {
            Subnet subnet = new Subnet("10.0.0.1", 16);

            assertTrue(set.add(subnet));
            assertFalse(set.isEmpty());
            set.clear();
            assertTrue(set.isEmpty());
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }
}
