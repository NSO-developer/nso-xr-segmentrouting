package com.tailf.pkg.testjunit;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.JUnitCore;

import com.tailf.pkg.ipam.Subnet;
import com.tailf.pkg.ipam.exceptions.InvalidNetmaskException;

import org.junit.Assert;

/**
 * JUnit tests for the com.cicso.nso.ipam.Subnet class
 *
 * Subnet subnet is a static instance that can be
 * used across several test methods. This can be used
 * to simplify testing of a chain of actions to the Subnet
 * class. If this is not needed, use local instances.
 *
 */
public class SubnetTest {

    private static Subnet subnet;
    private static InetAddress ipv4Loopback; // 127.0.0.1
    private static InetAddress ipv4Invalid;  // 0.0.0.0

    public SubnetTest() {}

    @BeforeClass
    public static void setUp() throws UnknownHostException {
        subnet       = new Subnet();
        ipv4Loopback = InetAddress.getByName("127.0.0.1");
        ipv4Invalid  = InetAddress.getByName("0.0.0.0");
    }

    @AfterClass
    public static void tearDown() {
        subnet = null;
    }

    @Test
    public void initSize0() {
        Assert.assertEquals(subnet.size(), -1);
    }

    /*
     * A Subnet contains the max and min elements,
     * and elements in between.
     */
    @Test
    public void subnetContainsAddress() throws UnknownHostException,
                                               InvalidNetmaskException {

        Subnet localSubnet   = new Subnet("10.0.0.0/24");
        InetAddress hostMin  = InetAddress.getByName("10.0.0.0");
        InetAddress between  = InetAddress.getByName("10.0.0.124");
        InetAddress hostMax  = InetAddress.getByName("10.0.0.255");

        InetAddress outside1 = InetAddress.getByName("172.23.2.23");
        InetAddress outside2 = InetAddress.getByName("10.0.1.1");
        InetAddress outside3 = InetAddress.getByName("10.1.3.1");

        // Subnet should contain max, min and things in between
        Assert.assertTrue(localSubnet.contains(hostMin));
        Assert.assertTrue(localSubnet.contains(between));
        Assert.assertTrue(localSubnet.contains(hostMax));

        // Subnet should not contain addresses outside the subnet
        Assert.assertFalse(localSubnet.contains(outside1));
        Assert.assertFalse(localSubnet.contains(outside2));
        Assert.assertFalse(localSubnet.contains(outside3));

        // This subnet should not contain localhost or 0.0.0.0
        Assert.assertFalse(localSubnet.contains(ipv4Loopback));
        Assert.assertFalse(localSubnet.contains(ipv4Invalid));
    }

    /**
     * Subnets do correctly contain each other
     *
     * @throws UnknownHostException
     * @throws InvalidNetmaskException
     */
    @Test
    public void subnetContainsSubnet() throws UnknownHostException,
                                              InvalidNetmaskException {

        Subnet localSubnet = new Subnet("10.0.0.0",   "18");
        Subnet within1     = new Subnet("10.0.0.0",   "24");
        Subnet within2     = new Subnet("10.0.0.0",   "32");
        Subnet outside1    = new Subnet("10.0.0.0",   "8");
        Subnet outside2    = new Subnet("172.20.0.0", "16");

        Assert.assertTrue(localSubnet.contains(within1));
        Assert.assertTrue(localSubnet.contains(within2));
        Assert.assertTrue(within1.contains(within2));

        Subnet containsMaster = new Subnet("10.0.1.0",   "24");
        Subnet contains1      = new Subnet("10.0.1.0",   "25");
        Subnet contains2      = new Subnet("10.0.1.0",   "26");
        Subnet contains3      = new Subnet("10.0.1.0",   "27");
        Subnet contains4      = new Subnet("10.0.1.0",   "28");
        Subnet contains5      = new Subnet("10.0.1.0",   "29");
        Subnet contains6      = new Subnet("10.0.1.0",   "30");
        Subnet contains7      = new Subnet("10.0.1.0",   "31");
        Subnet contains8      = new Subnet("10.0.1.0",   "32");
        Subnet containsX      = new Subnet("10.0.1.128", "28");

        Assert.assertTrue(containsMaster.contains(contains1));
        Assert.assertTrue(containsMaster.contains(contains2));
        Assert.assertTrue(containsMaster.contains(contains3));
        Assert.assertTrue(containsMaster.contains(contains4));
        Assert.assertTrue(containsMaster.contains(contains5));
        Assert.assertTrue(containsMaster.contains(contains6));
        Assert.assertTrue(containsMaster.contains(contains7));
        Assert.assertTrue(containsMaster.contains(contains8));

        Assert.assertFalse(localSubnet.contains(outside1));
        Assert.assertFalse(localSubnet.contains(outside2));
        Assert.assertFalse(containsX.contains(containsMaster));
    }

    @Test
    public void testMask2Prefix() throws UnknownHostException,
                                         InvalidNetmaskException {

        InetAddress prefixNet1 = Subnet.prefix2mask4(8);
        InetAddress prefixNet2 = Subnet.prefix2mask4(16);
        InetAddress prefixNet3 = Subnet.prefix2mask4(24);
        InetAddress prefixNet4 = Subnet.prefix2mask4(25);

        int prefix1 = Subnet.mask2prefix(prefixNet1);
        int prefix2 = Subnet.mask2prefix(prefixNet2);
        int prefix3 = Subnet.mask2prefix(prefixNet3);
        int prefix4 = Subnet.mask2prefix(prefixNet4);

        Assert.assertEquals(prefix1, 8);
        Assert.assertEquals(prefix2, 16);
        Assert.assertEquals(prefix3, 24);
        Assert.assertEquals(prefix4, 25);
    }

    @Test(expected=InvalidNetmaskException.class)
    public void testMask2PrefixException1()
        throws UnknownHostException, InvalidNetmaskException {
        Subnet.mask2prefix(InetAddress.getByName("255.255.0.1"));
    }

    @Test(expected=InvalidNetmaskException.class)
    public void testMask2PrefixException2()
        throws UnknownHostException, InvalidNetmaskException {
        Subnet.mask2prefix(InetAddress.getByName("255.192.0.1"));
    }

    @Test(expected=InvalidNetmaskException.class)
    public void testMask2PrefixException3()
        throws UnknownHostException, InvalidNetmaskException {
        Subnet.mask2prefix(InetAddress.getByName("255.1.0.0"));
    }

    @Test
    public void testPrefixToMask() throws InvalidNetmaskException {

        // IPv4
        InetAddress a1   = Subnet.prefix2mask4(8);
        InetAddress a2   = Subnet.prefix2mask4(16);
        InetAddress a3   = Subnet.prefix2mask4(24);
        InetAddress a4   = Subnet.prefix2mask4(32);
        InetAddress v6_1 = Subnet.prefix2mask6(8);

        Assert.assertEquals("255.0.0.0",          a1.getHostAddress());
        Assert.assertEquals("255.255.0.0",        a2.getHostAddress());
        Assert.assertEquals("255.255.255.0",      a3.getHostAddress());
        Assert.assertEquals("255.255.255.255",    a4.getHostAddress());
        Assert.assertEquals("ff00:0:0:0:0:0:0:0", v6_1.getHostAddress());
    }

    @Test
    public void testNetworkOf() throws UnknownHostException {

        InetAddress a1          = InetAddress.getByName("10.2.3.1");
        InetAddress normalized1 = Subnet.networkOf(a1, 8);

        InetAddress a2          = InetAddress.getByName("10.2.3.1");
        InetAddress normalized2 = Subnet.networkOf(a2, 16);

        InetAddress a3          = InetAddress.getByName("192.168.0.199");
        InetAddress normalized3 = Subnet.networkOf(a3, 26);

        String a4Addr           = "ABCD:1234:BCDE::FF87:1234";
        InetAddress a4          = InetAddress.getByName(a4Addr);
        InetAddress normalized4 = Subnet.networkOf(a4, 105);

        Assert.assertEquals("10.0.0.0", normalized1.getHostAddress());
        Assert.assertEquals("10.2.0.0", normalized2.getHostAddress());
        Assert.assertEquals("192.168.0.192", normalized3.getHostAddress());
        Assert.assertEquals(InetAddress.getByName("abcd:1234:bcde::0:ff80:0")
                                       .getHostAddress(),
                            normalized4.getHostAddress());
    }

    @Test
    public void testGetBroadcast() throws UnknownHostException,
                                          InvalidNetmaskException {

        Subnet localSubnet = new Subnet("10.0.0.0", "24");
        String bcast       = localSubnet.getBroadcast().getHostAddress();

        Subnet localSubnet2 = new Subnet("10.0.0.0", "8");
        String bcast2       = localSubnet2.getBroadcast().getHostAddress();

        Assert.assertEquals(bcast,  "10.0.0.255");
        Assert.assertEquals(bcast2, "10.255.255.255");
    }

    /**
     * Contract: -1 if this is narrower, 0 if same, 1 if wider.
     *
     * @throws UnknownHostException
     * @throws InvalidNetmaskException
     */
    @Test
    public void testCompareWidth() throws UnknownHostException,
                                          InvalidNetmaskException {
        Subnet net1 = new Subnet("10.0.0.0",    "24");
        Subnet net2 = new Subnet("10.0.0.0",    "16");
        Subnet net3 = new Subnet("192.168.0.0", "8");
        Subnet net4 = new Subnet("10.0.0.0",    "8");
        // net2 is wider than net1
        Assert.assertEquals(net1.compareWidth(net2), -1);
        Assert.assertEquals(net2.compareWidth(net1), 1);
        // Same width
        Assert.assertEquals(net3.compareWidth(net4), 0);
        Assert.assertEquals(net4.compareWidth(net3), 0);
        // 3 is wider than 1
        Assert.assertEquals(net3.compareWidth(net1), 1);
        Assert.assertEquals(net1.compareWidth(net3), -1);
    }

    @Test
    public void testSize() throws UnknownHostException,
                                  InvalidNetmaskException {

        // IPv4
        Subnet net1 = new Subnet("10.0.0.0", "24");
        Assert.assertEquals(net1.size(), 254);
        Subnet net2 = new Subnet("10.0.0.0", "18");
        Assert.assertEquals(net2.size(), 16382);
        Subnet net3 = new Subnet("10.0.0.0", "16");
        Assert.assertEquals(net3.size(), 65534);
        Subnet net4 = new Subnet("10.0.0.0", "8");
        Assert.assertEquals(net4.size(), 16777214);
        Subnet net5 = new Subnet("10.0.0.0", "3");
        Assert.assertEquals(net5.size(), 536870910);

        /* IPv6, for now too large networks are
         * returning the size of Long.MAX.
         */
        Subnet net6 = new Subnet("2001:cdba::", "64");
        Assert.assertEquals(net6.size(), Long.MAX_VALUE);
        Subnet net7 = new Subnet("2001:cdba::3257:9652", "105");
        Assert.assertEquals(net7.size(), 8388606);
        Subnet net8 = new Subnet("2001:cdba::3257:9652", "113");
        Assert.assertEquals(net8.size(), 32766);
    }

    @Test
    public void testSplitSimple() throws UnknownHostException,
                                         InvalidNetmaskException {
        Subnet beforeSplit = new Subnet("10.0.0.0/24");
        Subnet[] parts = beforeSplit.split();
        Subnet pt1 = parts[0];
        Subnet pt2 = parts[1];
        // After splitting we should have 2 parts
        Assert.assertTrue(parts.length == 2);
        // Where part1 should contain half the original network
        Assert.assertTrue(pt1.contains(Inet4Address.getByName("10.0.0.0")));
        Assert.assertTrue(pt1.contains(Inet4Address.getByName("10.0.0.127")));
        // And part2 should contain the rest
        Assert.assertTrue(pt2.contains(Inet4Address.getByName("10.0.0.128")));
        Assert.assertTrue(pt2.contains(Inet4Address.getByName("10.0.0.255")));
    }

    @Test
    public void testSplit() throws UnknownHostException,
                                   InvalidNetmaskException {
        Subnet beforeSplit = new Subnet("10.0.0.0/18");
        Subnet[] parts = beforeSplit.split();
        Subnet pt1 = parts[0];
        Subnet pt2 = parts[1];
        // After splitting we should have 2 parts
        Assert.assertTrue(parts.length == 2);
        // Where part1 should contain half the original network
        Assert.assertTrue(pt1.contains(Inet4Address.getByName("10.0.0.0")));
        Assert.assertTrue(pt1.contains(Inet4Address.getByName("10.0.31.255")));
        // And part2 should contain the rest
        Assert.assertTrue(pt2.contains(Inet4Address.getByName("10.0.32.0")));
        Assert.assertTrue(pt2.contains(Inet4Address.getByName("10.0.63.255")));
    }

    /**
     * Nice to have when debug the split function.
     */
    @SuppressWarnings("unused")
    private void printSubnets(Subnet[] subnets) {
        for(Subnet x : subnets) {
            System.out.println(x.getAddress().getHostAddress());
        }
    }

    /**
     * If we try to split a subnet consisting of a single IP address,
     * then we should receive an exception.
     *
     * @throws UnknownHostException
     */
    @Test
    public void testSplitSmall() throws UnknownHostException {
        try {
            Subnet beforeSplit = new Subnet("10.0.0.0", "32");
            beforeSplit.split();
        } catch(InvalidNetmaskException e) {
            Assert.assertEquals(e.getMessage(), "Can't split subnet further");
        }
    }

    /**
     * Splitting an empty subnet should yield two /32 addresses.
     * (According to Subnet.java)
     */
    @Test
    public void testSplitSize0() throws UnknownHostException,
                                        InvalidNetmaskException {
        Subnet beforeSplit = new Subnet("10.0.0.0", "31");
        Subnet[] x = beforeSplit.split();
        Assert.assertTrue(x.length == 2);
        Assert.assertEquals(x[0].getAddress().getHostAddress(), "10.0.0.0");
        Assert.assertEquals(x[1].getAddress().getHostAddress(), "10.0.0.1");
    }

    /**
     * Splitting an empty ipv6 subnet should yield
     * two /128 addresses. (According to Subnet.java)
     */
    @Test
    public void testSplitSize0ipv6() throws UnknownHostException,
                                            InvalidNetmaskException {
        Subnet beforeSplit = new Subnet("a::a", "127");
        Subnet[] x = beforeSplit.split();
        Assert.assertEquals(2, x.length);
        Assert.assertEquals("a:0:0:0:0:0:0:a",
                            x[0].getAddress().getHostAddress());
        Assert.assertEquals("a:0:0:0:0:0:0:b",
                            x[1].getAddress().getHostAddress());
    }

    /**
     * Splitting a size 2 subnet should yield four /32 addresses.
     * (According to Subnet.java)
     */
    @Test
    public void testSplitSize2() throws UnknownHostException,
                                        InvalidNetmaskException {
        Subnet beforeSplit = new Subnet("10.0.0.0", "30");
        Subnet[] x = beforeSplit.split();
        Assert.assertTrue(x.length == 4);
        Assert.assertEquals(x[0].getAddress().getHostAddress(), "10.0.0.0");
        Assert.assertEquals(x[1].getAddress().getHostAddress(), "10.0.0.1");
        Assert.assertEquals(x[2].getAddress().getHostAddress(), "10.0.0.2");
        Assert.assertEquals(x[3].getAddress().getHostAddress(), "10.0.0.3");
    }

    /**
     * A subnet with size 1 or 2, should give 4 separate addresses
     * (According to Subnet.java)
     */
    @Test
    public void testSplit4into2() throws UnknownHostException,
                                         InvalidNetmaskException {
        Subnet beforeSplit = new Subnet("10.0.0.0", "30");
        Subnet[] x = beforeSplit.split4into2();
        Assert.assertTrue(x.length == 2);
        Assert.assertEquals(x[0].getAddress().getHostAddress(), "10.0.0.0");
        Assert.assertEquals(x[1].getAddress().getHostAddress(), "10.0.0.2");
    }

    /**
     * A subnet with size 1 or 2, should give 4 separate addresses
     * (According to Subnet.java)
     */
    @Test
    public void testSplit4into2_() throws UnknownHostException,
                                          InvalidNetmaskException {
        Subnet beforeSplit = new Subnet("12.0.0.0", "30");
        Subnet[] x = beforeSplit.split4into2();
        Assert.assertTrue(x.length == 2);
        Assert.assertEquals(x[0].getAddress().getHostAddress(), "12.0.0.0");
        Assert.assertEquals(x[1].getAddress().getHostAddress(), "12.0.0.2");
    }

    /**
     * Make sure that we can add a number of addresses
     * from a Subnet, by fetching them from the SubnetIterator.
     *
     * Then, create a new iterator that is reversed.
     * Remove all addresses encountered when iterating
     * in reversed mode. The result SHOULD be an empty set.
     */
    @Test
    public void testIterator() {
        try {
            /* Iterate over a network with hosts, from
             * 10.0.0.1 -> 10.0.0.254
             */
            Subnet net1 = new Subnet("10.0.0.0/16");
            Iterator<InetAddress> iterator = net1.iterator();
            Set<InetAddress> addresses = new HashSet<InetAddress>();
            while(iterator.hasNext()) {
                InetAddress next = iterator.next();
                Assert.assertTrue(next != null);
                addresses.add(next);
            }
            Assert.assertEquals(addresses.size(), 65534);
            /* Going reverse over the same subnet should give
             * the same count
             */
            Iterator<InetAddress> iterator2 = net1.iterator(true);
            while(iterator2.hasNext()) {
                InetAddress next = iterator2.next();
                Assert.assertTrue(next != null);
                addresses.remove(next);
            }
            Assert.assertTrue(addresses.isEmpty());
        } catch (Exception e) {
            Assert.fail("Unexpected exception: " + e);
        }
    }

    @Test
    public void testIterator2() throws UnknownHostException,
                                       InvalidNetmaskException {
        Subnet s = new Subnet("192.168.1.240/28");
        Iterator<InetAddress> iterator =
            s.iterator(InetAddress.getByName("192.168.1.254"));
        Assert.assertEquals("192.168.1.254", iterator.next().getHostAddress());
        Assert.assertEquals("192.168.1.255", iterator.next().getHostAddress());
        Assert.assertFalse(iterator.hasNext());
    }

    @Test
    public void testCompare() throws UnknownHostException,
                                     InvalidNetmaskException {
        Subnet net1       = new Subnet("10.0.0.1", "24");
        Subnet bcast      = new Subnet("10.0.1.255/" +
                                       net1.getSingleHostPrefix());
        InetAddress host1 = Inet4Address.getByName("127.127.127.127");
        Assert.assertTrue(Subnet.compare(ipv4Invalid, ipv4Invalid) == 0);
        Assert.assertTrue(Subnet.compare(host1, ipv4Invalid) != 0);
        Assert.assertTrue(Subnet.compare(ipv4Invalid, host1) != 0);
        int comp1 = Subnet.compare(InetAddress.getByName("::0.0.0.0"), host1);
        Assert.assertTrue(comp1 != 0);
        int comp2 = Subnet.compare(host1, InetAddress.getByName("::0.0.0.0"));
        Assert.assertTrue(comp2 != 0);
        Assert.assertTrue(net1.isBroadcastFor(bcast));
    }

    public static void main(String arg[]) {
        JUnitCore.main(SubnetTest.class.getName());
    }
}
