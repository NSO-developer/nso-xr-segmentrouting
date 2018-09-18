package com.tailf.pkg.testjunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.*;

import com.tailf.pkg.ipam.Allocation;
import com.tailf.pkg.ipam.IPAddressPool;
import com.tailf.pkg.ipam.Subnet;
import com.tailf.pkg.ipam.exceptions.AddressNotAllocatedException;
import com.tailf.pkg.ipam.exceptions.AddressPoolException;
import com.tailf.pkg.ipam.exceptions.AddressRequestNotAvailableException;
import com.tailf.pkg.ipam.exceptions.InvalidNetmaskException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.HashSet;

public class IPAddressPoolTest {

    @Test
    public void testAddRemoveNullNoException() {
        IPAddressPool pool =
            new IPAddressPool("test-pool",
                              new HashSet<Subnet>(),
                              new HashSet<Allocation>(),
                              new HashSet<Subnet>());

        try {
            pool.addToAvailable(null);
            pool.removeFromAvailable(null);
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testRemoveNonExistingSubnet() {
        IPAddressPool pool
            = new IPAddressPool("test-pool",
                                new HashSet<Subnet>(),
                                new HashSet<Allocation>(),
                                new HashSet<Subnet>());
        Subnet subnet = null;

        try {
            String addr = "2001:db8:85a3:0:0:8a2e:370:7334";
            InetAddress ipv6addr = InetAddress.getByName(addr);
            subnet = new Subnet(ipv6addr, 120);
            pool.removeFromAvailable(subnet);
            fail("Expected AddressRequestNotAvailableException to be thrown");
        } catch (AddressRequestNotAvailableException e) {
            String addr = "Address " + subnet + " is not an available " +
                          "subnet defined by the pool";
            assertEquals(addr, e.getMessage());
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testIPv6Allocation() {
        IPAddressPool pool = new IPAddressPool("test-pool",
                                               new HashSet<Subnet>(),
                                               new HashSet<Allocation>(),
                                               new HashSet<Subnet>());

        try {
            String addr = "2001:db8:85a3:0:0:8a2e:370:7300";
            InetAddress ipv6addr = InetAddress.getByName(addr);
            Subnet subnet = new Subnet(ipv6addr, 120);
            pool.addToAvailable(subnet);
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }

        try {
            String addr = "2001:db8:85a3:0:0:8a2e:370:7300";
            Allocation a1 = pool.allocate(128, "owner", "admin", "id");
            InetAddress expected = InetAddress.getByName(addr);
            assertEquals(new Subnet(expected, 128), a1.getAllocated());
            assertEquals("owner", a1.getOccupant());
            assertEquals("id", a1.getRequestId());
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testRelease() {
        IPAddressPool pool = new IPAddressPool("test-pool",
                                               new HashSet<Subnet>(),
                                               new HashSet<Allocation>(),
                                               new HashSet<Subnet>());
        Allocation a1 = null;
        InetAddress addr = null;

        try {
            a1 = new Allocation(new Subnet("10.0.0.1", 16), "TheOccupant", "admin", "id");
            pool.release(a1);
            fail("Expected AddressNotAllocatedException to be thrown");
        } catch (AddressNotAllocatedException e) {
            String msg = "Allocation " + a1 +
                         " was not allocated from the pool";
            assertEquals(msg, e.getMessage());
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }

        try {
            addr = InetAddress.getByName("1.2.3.4");
            pool.release(addr);
            fail("Expected AddressNotAllocatedException to be thrown");
        } catch (AddressNotAllocatedException e) {
            String msg = "Address " + addr + " was not allocated from the pool";
            assertEquals(msg, e.getMessage());
        } catch (Exception e) {
            fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testNotNetworkBroadcast()
        throws ClassNotFoundException, NoSuchMethodException,  InstantiationException,
               UnknownHostException, IllegalAccessException, InvalidNetmaskException,
               InvocationTargetException {

        // Private method, some magic is needed in order to access it.
        Class<?> c = Class.forName("com.tailf.pkg.ipam.IPAddressPool");
        Method m = c.getDeclaredMethod("notNetworkBroadcast",
                                       new Class[] { Subnet.class, int.class });
        m.setAccessible(true);
        IPAddressPool i =
            new IPAddressPool("test-pool",
                              new HashSet<Subnet>(),
                              new HashSet<Allocation>(),
                              new HashSet<Subnet>(Arrays.asList(new Subnet("192.168.1.0", 25),
                                                                new Subnet("10.0.0.0", 8),
                                                                new Subnet("1:2::", 32),
                                                                new Subnet("3:4:5::", 48))));

        Assert.assertTrue((Boolean)m.invoke(i, new Object[] {new Subnet("192.168.1.20", 32), 32}));
        Assert.assertTrue((Boolean)m.invoke(i, new Object[] {new Subnet("192.167.1.20", 32), 32}));
        Assert.assertTrue((Boolean)m.invoke(i, new Object[] {new Subnet("10.0.0.0", 8), 8}));
        Assert.assertTrue((Boolean)m.invoke(i, new Object[] {new Subnet("3:4:5::", 48), 48}));
        Assert.assertTrue((Boolean)m.invoke(i, new Object[] {new Subnet("1:2::", 32), 32}));
        Assert.assertFalse((Boolean)m.invoke(i, new Object[] {new Subnet("192.168.1.0", 32), 32}));
        Assert.assertFalse((Boolean)m.invoke(i, new Object[] {new Subnet("192.168.1.127", 32), 32}));
        Assert.assertFalse((Boolean)m.invoke(i, new Object[]
            {new Subnet("1:2:ffff:ffff:ffff:ffff:ffff:ffff", 128), 128}));
    }
}
