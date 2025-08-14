package com.cms.clubmanagementsystem.utils;

import org.mindrot.jbcrypt.BCrypt;

public class PasswordUtils {

    //generate a hashed password
    public static String hashPassword(String plainPassword){
        return BCrypt.hashpw(plainPassword, BCrypt.gensalt());
    }

    //verify a password against a stored hash
    public static Boolean verifyPassword(String plainPassword, String hashedPassword){
        return BCrypt.checkpw(plainPassword, hashedPassword);
    }

    // Check password strength
    public static boolean isPasswordStrong(String password) {
        return password != null &&
                password.length() >= 8 &&
                password.matches(".*[A-Z].*") &&  // At least 1 uppercase
                password.matches(".*[!@#$%^&*].*"); // At least 1 special char
    }
}
