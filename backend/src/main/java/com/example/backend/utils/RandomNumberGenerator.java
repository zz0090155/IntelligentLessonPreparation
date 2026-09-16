package com.example.backend.utils;

import java.security.SecureRandom;

public class RandomNumberGenerator {
    private static final SecureRandom secureRandom = new SecureRandom();

    private static final String SYMBOLS = "0123456789";


    public static String generateValidateCode() {
        char[] nonceChars = new char[6];
        for (int i = 0; i < nonceChars.length; i++) {
            nonceChars[i] = SYMBOLS.charAt(secureRandom.nextInt(SYMBOLS.length()));
        }
        return new String(nonceChars);
    }


    public static String generateNumber(int length) {
        char[] nonceChars = new char[length];
        for (int i = 0; i < nonceChars.length; i++) {
            nonceChars[i] = SYMBOLS.charAt(secureRandom.nextInt(SYMBOLS.length()));
        }
        return new String(nonceChars);
    }


    public static int generateIntCode() {
        return secureRandom.nextInt(900000) + 100000;
    }
}