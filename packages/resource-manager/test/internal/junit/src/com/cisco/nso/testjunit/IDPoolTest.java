package com.tailf.pkg.testjunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.fail;

import org.junit.*;

import com.tailf.pkg.idpool.Allocation;
import com.tailf.pkg.idpool.IDPool;
import com.tailf.pkg.idpool.Range;
import com.tailf.pkg.idpool.exceptions.AllocationException;

import java.util.HashSet;

public class IDPoolTest {

    @Test
    public void testAllocateAndRelease() {
        IDPool pool = new IDPool("test-pool",
                                 new HashSet<Range>(),
                                 new HashSet<Range>(),
                                 new HashSet<Allocation>());
        Allocation a1 = null;

        try {
            pool.setRange(new Range(1, 10));
            a1 = pool.allocate("occupant", 5);
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }

        assertNotNull(a1);
        assertEquals((Integer)5, a1.getAllocated());

        try {
            pool.release(a1);
        } catch (AllocationException e) {
            fail("Unexpected exception: " + e);
        }

        try {
            pool.release(a1);
            fail("Expected an AllocationException to be thrown");
        } catch (AllocationException e) {
            assertEquals("allocation " + a1 + " is not allocated " +
                         "from the pool test-pool", e.getMessage());
        }
    }
}
