package com.build4all.common.util;

public final class PhoneNumberValidator {

    private PhoneNumberValidator() {}

    public static String normalize(String raw) {
        if (raw == null) return null;

        String value = raw.trim();
        if (value.isEmpty()) return null;

        value = value
                .replace(" ", "")
                .replace("-", "")
                .replace("(", "")
                .replace(")", "")
                .replace(".", "");

        if (value.startsWith("00")) {
            value = "+" + value.substring(2);
        }

        // Lebanese local format -> normalize to +961
        if (!value.startsWith("+")) {
            if (value.matches("^(3|70|71|76|78|79|81)\\d{6}$")) {
                value = "+961" + value;
            }
        }

        // Normalize +9610XXXXXXXX -> +961XXXXXXXX
        if (value.startsWith("+9610")) {
            value = "+961" + value.substring(5);
        }

        return value;
    }

    public static boolean isValid(String raw) {
        String value = normalize(raw);
        if (value == null) return false;

        if (!value.startsWith("+")) {
            return false;
        }

        if (value.startsWith("+961")) {
            String national = value.substring(4);
            if (national.startsWith("0")) {
                national = national.substring(1);
            }
            return national.matches("^(3|70|71|76|78|79|81)\\d{6}$");
        }

        return value.matches("^\\+[1-9]\\d{7,14}$");
    }
}