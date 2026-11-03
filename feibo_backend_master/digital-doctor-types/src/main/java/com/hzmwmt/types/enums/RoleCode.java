package com.hzmwmt.types.enums;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Canonical platform role codes used by authentication and authorization.
 * Display names must not be used as authorities.
 */
public final class RoleCode {
    public static final String SUPER_ADMIN = "SUPER_ADMIN";
    public static final String ADMIN = "ADMIN";
    public static final String ORG_ADMIN = "ORG_ADMIN";
    public static final String EXPERT = "EXPERT";
    public static final String DOCTOR = "DOCTOR";
    public static final String DOCTOR_ASSISTANT = "DOCTOR_ASSISTANT";

    private static final String LEGACY_SUPER_ADMIN = "SUPERADMIN";
    private static final Pattern VALID_CODE = Pattern.compile("[A-Z][A-Z0-9_]{1,63}");
    private static final Set<String> ADMIN_CONSOLE_ROLES = Set.of(ADMIN, SUPER_ADMIN);

    private RoleCode() {
    }

    /**
     * Converts a stored role name to a canonical authority code. The historical
     * SUPERADMIN spelling remains a read-only migration alias.
     */
    public static Optional<String> canonicalize(String storedRoleName) {
        if (storedRoleName == null) {
            return Optional.empty();
        }

        String normalized = storedRoleName.trim().toUpperCase(Locale.ROOT);
        if (LEGACY_SUPER_ADMIN.equals(normalized)) {
            normalized = SUPER_ADMIN;
        }
        if (!VALID_CODE.matcher(normalized).matches()) {
            return Optional.empty();
        }
        return Optional.of(normalized);
    }

    public static boolean grantsAdminConsole(String storedRoleName) {
        return canonicalize(storedRoleName)
                .map(ADMIN_CONSOLE_ROLES::contains)
                .orElse(false);
    }
}
