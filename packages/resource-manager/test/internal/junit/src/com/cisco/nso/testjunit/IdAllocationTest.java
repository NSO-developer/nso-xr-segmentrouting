package com.tailf.pkg.testjunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

import com.tailf.pkg.idpool.Allocation;

import java.util.LinkedList;

public class IdAllocationTest {

    @Test
    public void testEquals() {
        Allocation a1 = new Allocation(1);
        assertTrue(a1.equals(a1));

        Allocation a2 = new Allocation(a1);
        assertTrue(a1.equals(a2));

        assertFalse(a1.equals(null));
        assertFalse(a1.equals(new LinkedList<String>()));
        assertFalse(a1.equals(new Allocation(2)));
        assertTrue(a1.equals(new Allocation(1)));
    }

    @Test
    public void testToString() {
        Allocation a1 = new Allocation(1);
        assertEquals("{\"segment\":1}", a1.toString());
    }
}
