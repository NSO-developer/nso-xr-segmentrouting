package com.tailf.pkg.testjunit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

import org.junit.Test;

import com.tailf.pkg.resourcemanager.ErrorCode;
import com.tailf.pkg.resourcemanager.ResourceException;

public class ErrorCodeTest {

    @Test
    public void testGetValue() {
        assertEquals(0, ErrorCode.UNDEFINED.getValue());
    }

    @Test
    public void testValueOf() {
        assertEquals(ErrorCode.UNDEFINED, ErrorCode.valueOf(0));
        assertEquals(ErrorCode.UNDEFINED, ErrorCode.valueOf("UNDEFINED"));
        assertEquals(null, ErrorCode.valueOf(-1));
    }

    @Test
    public void testEqualsTo() {
        assertTrue(ErrorCode.UNDEFINED.equalsTo(0));
        assertFalse(ErrorCode.UNDEFINED.equalsTo(1));
    }

    @Test
    public void testStringValue() {
        assertEquals("undefined", ErrorCode.UNDEFINED.stringValue());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void testResourceException() throws InstantiationException,
                                               IllegalAccessException,
                                               IllegalArgumentException,
                                               InvocationTargetException,
                                               ClassNotFoundException,
                                               NoSuchMethodException,
                                               SecurityException {
        String classString = "com.cisco.resourcemanager.ResourceException";
        Class<ResourceException> c
            = (Class<ResourceException>) Class.forName(classString);
        Constructor<ResourceException> cr
            = (Constructor<ResourceException>) c.getDeclaredConstructor(String.class,
                                                                        ErrorCode.class);
        cr.setAccessible(true);
        ResourceException ex = cr.newInstance("Test",
                                              ErrorCode.WAIT_FOR_REDEPLOY);
        assertEquals(ErrorCode.WAIT_FOR_REDEPLOY, ex.getErrorCode());
    }
}
