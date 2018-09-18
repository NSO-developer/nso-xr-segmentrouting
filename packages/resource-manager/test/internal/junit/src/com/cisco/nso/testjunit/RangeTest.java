package com.tailf.pkg.testjunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import org.junit.Test;

import com.tailf.pkg.idpool.Range;

import java.util.LinkedList;

public class RangeTest {

    @Test
    public void testEquals() {
        Range r1 = new Range(1, 10);
        assertTrue(r1.equals(r1));

        Range r2 = new Range(r1);
        assertTrue(r1.equals(r2));

        assertFalse(r1.equals(null));
        assertFalse(r1.equals(new LinkedList<String>()));
        assertFalse(r1.equals(new Range(2, 10)));
        assertFalse(r1.equals(new Range(1, 11)));
    }

    @Test
    public void testToString() {
        Range r1 = new Range(1, 10);
        assertEquals("{\"start\":1,\"end\":10}", r1.toString());
    }
}
