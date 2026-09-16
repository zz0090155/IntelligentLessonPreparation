package com.example.backend.utils;

import org.mindrot.jbcrypt.BCrypt;

public class HashUtils {

    private static final String FIXED_SALT = "$2a$12$9k7z8X9s7m6b5v4c3x2b1n0";

    public static String hashPasswordWithFixedSalt(String rawPassword) {
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            throw new IllegalArgumentException("密码不能为空");
        }
        return BCrypt.hashpw(rawPassword, FIXED_SALT);
    }

    public static boolean verifyPasswordWithFixedSalt(String rawPassword, String hashedPassword) {
        if (rawPassword == null || hashedPassword == null) {
            return false;
        }
        try {
            return BCrypt.checkpw(rawPassword, hashedPassword);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
