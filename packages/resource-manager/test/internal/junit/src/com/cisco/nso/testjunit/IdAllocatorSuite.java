package com.tailf.pkg.testjunit;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

@RunWith(Suite.class)
@Suite.SuiteClasses({
    IdAllocationTest.class,
    IdAllocationsSetTest.class,
    RangeTest.class,
    IDPoolTest.class
})

public class IdAllocatorSuite {
}
