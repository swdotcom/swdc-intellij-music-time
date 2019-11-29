package com.softwareco.intellij.plugin

class SoftwareCoUtilsTest extends GroovyTestCase {
    void testGetOsUsername() {
        assertEquals("kkadu", SoftwareCoUtils.getOsUsername());
    }
}
