package com.tailf.pkg.testjunit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    IPAllocationTest.class,
    IPAllocationsSetTest.class,
    AvailablesSetTest.class,
    IPAddressPoolTest.class,
    SubnetTest.class,
    InetAddressRangeTest.class
})

public class IpAddressAllocatorSuite {}
