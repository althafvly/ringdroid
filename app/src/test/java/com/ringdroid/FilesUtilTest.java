package com.ringdroid;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class FilesUtilTest {
    @Test
    public void testGetStackTrace() {
        Exception e = new RuntimeException("Test Exception");
        String stackTrace = FilesUtil.getStackTrace(e);
        
        assertNotNull(stackTrace);
        assertTrue(stackTrace.contains("java.lang.RuntimeException: Test Exception"));
        assertTrue(stackTrace.contains("com.ringdroid.FilesUtilTest.testGetStackTrace"));
    }
}
