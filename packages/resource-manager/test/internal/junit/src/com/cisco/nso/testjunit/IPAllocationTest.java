package com.tailf.pkg.testjunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;
import com.tailf.pkg.ipam.Allocation;
import com.tailf.pkg.ipam.Subnet;
import org.junit.Test;

import java.util.LinkedList;

public class IPAllocationTest {

    @Test
    public void testEquals() {
        try {
            Allocation a1 = new Allocation(new Subnet("10.0.0.1", 16),
                                           "TheOccupant",
                                           "admin",
                                           "id");
            assertTrue(a1.equals(a1));

            Allocation a2 = new Allocation(a1);
            assertTrue(a1.equals(a2));

            assertFalse(a1.equals(null));
            assertFalse(a1.equals(new LinkedList<String>()));
            assertFalse(a1.equals(new Allocation(new Subnet("10.1.0.1", 16),
                                                 "TheOccupant",
                                                 "admin",
                                                 "id")));
            assertFalse(a1.equals(new Allocation(new Subnet("10.0.0.1", 16),
                                                 "TheOccupant2",
                                                 "admin",
                                                 "id")));
            assertFalse(a1.equals(new Allocation(new Subnet("10.0.0.1", 16),
                                                 "TheOccupant",
                                                 "admin",
                                                 "id2")));
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testToString() {
        try {
            Subnet subnet = new Subnet("10.0.0.1", 16);
            Allocation a1 = new Allocation(subnet, "TheOccupant", "admin", "id");
            String partA = "{\"allocated\": " + subnet +
                           ", \"occupant\": TheOccupant, \"username\": admin, \"requestId\": id}";
            assertEquals(partA, a1.toString());
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }
}
