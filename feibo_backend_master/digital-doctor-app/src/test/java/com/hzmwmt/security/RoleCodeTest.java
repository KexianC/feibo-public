package com.hzmwmt.security;

import com.hzmwmt.types.enums.RoleCode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RoleCodeTest {

    @Test
    void onlyExactAdminCodesGrantConsoleAccess() {
        assertTrue(RoleCode.grantsAdminConsole("ADMIN"));
        assertTrue(RoleCode.grantsAdminConsole("SUPER_ADMIN"));
        assertTrue(RoleCode.grantsAdminConsole("SUPERADMIN"));

        assertFalse(RoleCode.grantsAdminConsole("NOT_SUPER_ADMIN"));
        assertFalse(RoleCode.grantsAdminConsole("SYSTEM_ADMIN"));
        assertFalse(RoleCode.grantsAdminConsole("CONTENT_MANAGER"));
        assertFalse(RoleCode.grantsAdminConsole(null));
    }

    @Test
    void legacySuperAdminSpellingCanonicalizesWithoutBroadSubstringMatching() {
        assertEquals(RoleCode.SUPER_ADMIN, RoleCode.canonicalize("superadmin").orElseThrow());
        assertEquals(RoleCode.SUPER_ADMIN, RoleCode.canonicalize("SUPER_ADMIN").orElseThrow());
        assertEquals("NOT_SUPER_ADMIN", RoleCode.canonicalize("NOT_SUPER_ADMIN").orElseThrow());
    }
}
