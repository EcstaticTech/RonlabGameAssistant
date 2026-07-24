package com.ronlab.rga.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

public final class WorldNameValidator {

    private static final Pattern PATH_TRAVERSAL = Pattern.compile("\\.\\.");
    private static final Pattern OS_ILLEGAL_CHARS = Pattern.compile("[/\\\\<>:\"|?*]");
    private static final Pattern STRICT_WHITELIST = Pattern.compile("^[a-zA-Z0-9_.-]{1,64}$");
    private static final Pattern SANITIZE_PATTERN = Pattern.compile("[^a-zA-Z0-9_.-]");

    private WorldNameValidator() {}

    public static class ValidationResult {
        private final boolean valid;
        private final String errorMessage;
        private final List<String> warnings;

        private ValidationResult(boolean valid, String errorMessage, List<String> warnings) {
            this.valid = valid;
            this.errorMessage = errorMessage;
            this.warnings = warnings != null ? warnings : Collections.emptyList();
        }

        public static ValidationResult reject(String reason) {
            return new ValidationResult(false, reason, Collections.emptyList());
        }

        public static ValidationResult accept(List<String> warnings) {
            return new ValidationResult(true, null, warnings);
        }

        public boolean isValid() { return valid; }
        public String getErrorMessage() { return errorMessage; }
        public List<String> getWarnings() { return warnings; }
        public boolean hasWarnings() { return !warnings.isEmpty(); }
    }

    public static ValidationResult validate(String name, boolean strict) {
        if (name == null || name.trim().isEmpty()) {
            return ValidationResult.reject("World name cannot be null or empty.");
        }
        if (name.equals(".") || name.equals("..")) {
            return ValidationResult.reject("World name cannot be '.' or '..'.");
        }
        if (name.length() > 255) {
            return ValidationResult.reject("World name exceeds maximum length (255 characters).");
        }

        // 1. Hard Security Checks (Always Enforced)
        if (PATH_TRAVERSAL.matcher(name).find()) {
            return ValidationResult.reject("Security Violation: Path traversal sequence ('..') detected.");
        }
        if (OS_ILLEGAL_CHARS.matcher(name).find()) {
            return ValidationResult.reject("Security Violation: Character not permitted on host filesystem.");
        }

        // 2. Strict Mode Convention Enforcement
        if (strict && !STRICT_WHITELIST.matcher(name).matches()) {
            return ValidationResult.reject(
                "Format Violation: Name does not meet strict conventions (alphanumeric, underscores, hyphens, dots, max 64 chars)."
            );
        }

        // 3. Cosmetic Warnings (Returned on Accept for non-strict/config contexts)
        List<String> warnings = new ArrayList<>();
        if (name.startsWith(".")) {
            warnings.add("Name begins with a dot (may create a hidden directory on Unix systems).");
        }
        if (name.contains(" ")) {
            warnings.add("Name contains spaces (may require special handling in unquoted command arguments).");
        }

        return ValidationResult.accept(warnings);
    }

    /**
     * Backward compatibility convenience check using non-strict mode.
     */
    public static boolean isValid(String worldName) {
        return validate(worldName, false).isValid();
    }

    /**
     * Sanitizes minigame IDs or base names for filesystem & command safety.
     * Preserves dots, hyphens, underscores, and alphanumerics.
     */
    public static String sanitizeForFilesystem(String input) {
        if (input == null) return "unnamed";
        return SANITIZE_PATTERN.matcher(input).replaceAll("_");
    }
}
