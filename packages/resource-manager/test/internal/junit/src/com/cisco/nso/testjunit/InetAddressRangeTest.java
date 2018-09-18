package com.tailf.pkg.testjunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import org.junit.*;

import com.tailf.pkg.ipam.Allocation;
import com.tailf.pkg.ipam.IPAddressPool;
import com.tailf.pkg.ipam.Subnet;
import com.tailf.pkg.ipam.util.InetAddressRange;
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
import java.util.List;

public class InetAddressRangeTest {

    @Test
    public void testGetSubnets() throws UnknownHostException
    {
        InetAddressRange r = new InetAddressRange(InetAddress.getByName("10.0.0.255"),
                                                  InetAddress.getByName("10.0.1.5"));
        List<Subnet> nets = r.getSubnets();
        Assert.assertEquals(3, nets.size());
        int i = 0;
        for (Subnet s : nets) {
            if (s.getAddress().getHostAddress().equals("10.0.0.255")) {
                Assert.assertEquals(32, s.getCIDRMask());
                i++;
            }
            if (s.getAddress().getHostAddress().equals("10.0.1.0")) {
                Assert.assertEquals(30, s.getCIDRMask());
                i++;
            }
            if (s.getAddress().getHostAddress().equals("10.0.1.4")) {
                Assert.assertEquals(31, s.getCIDRMask());
                i++;
            }
        }
        Assert.assertEquals(3, i);

        InetAddressRange r2 = new InetAddressRange(InetAddress.getByName("0.0.0.0"),
                                                   InetAddress.getByName("255.255.255.254"));
        Assert.assertEquals(32, r2.getSubnets().size());

        InetAddressRange r3 = new InetAddressRange(InetAddress.getByName("0.0.0.0"),
                                                   InetAddress.getByName("255.255.255.255"));
        Assert.assertEquals(1, r3.getSubnets().size());
    }

    @Test
    public void testGetSubnetsipv6() throws UnknownHostException
    {
        InetAddressRange r = new InetAddressRange(InetAddress.getByName("0::1:7FFF"),
                                                  InetAddress.getByName("0::3:0003"));
        List<Subnet> nets = r.getSubnets();
        Assert.assertEquals(4, nets.size());
        int i = 0;
        for (Subnet s : nets) {
            if (s.getAddress().getHostAddress().equals("0:0:0:0:0:0:1:7fff")) {
                Assert.assertEquals(128, s.getCIDRMask());
                i++;
            }
            if (s.getAddress().getHostAddress().equals("0:0:0:0:0:0:1:8000")) {
                Assert.assertEquals(113, s.getCIDRMask());
                i++;
            }
            if (s.getAddress().getHostAddress().equals("0:0:0:0:0:0:2:0")) {
                Assert.assertEquals(112, s.getCIDRMask());
                i++;
            }
            if (s.getAddress().getHostAddress().equals("0:0:0:0:0:0:3:0")) {
                Assert.assertEquals(126, s.getCIDRMask());
                i++;
            }
        }
        Assert.assertEquals(4, i);
    }
}
