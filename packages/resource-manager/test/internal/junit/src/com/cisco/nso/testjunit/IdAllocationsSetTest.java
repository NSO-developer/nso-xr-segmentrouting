package com.tailf.pkg.testjunit;

import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import org.junit.Test;
import org.mockito.Mockito;

import com.tailf.pkg.idallocator.AllocationsSet;
import com.tailf.pkg.idpool.Allocation;
import com.tailf.cdb.CdbSession;

public class IdAllocationsSetTest {

    @Test
    public void testAddAndRemove() {
        CdbSession mockedCdb = Mockito.mock(CdbSession.class);
        AllocationsSet set = new AllocationsSet(mockedCdb, "hello");
        Allocation a1 = new Allocation(11);

        assertTrue(set.add(a1));
        assertFalse(set.add(a1));
        assertTrue(set.contains(a1));

        assertTrue(set.remove(a1));
        assertFalse(set.remove(a1));
        assertFalse(set.contains(a1));
    }

    @Test
    public void testClear() {
        CdbSession mockedCdb = Mockito.mock(CdbSession.class);
        AllocationsSet set = new AllocationsSet(mockedCdb, "hello");
        Allocation a1 = new Allocation(11);

        assertTrue(set.add(a1));
        assertFalse(set.isEmpty());
        set.clear();
        assertTrue(set.isEmpty());
    }
}
