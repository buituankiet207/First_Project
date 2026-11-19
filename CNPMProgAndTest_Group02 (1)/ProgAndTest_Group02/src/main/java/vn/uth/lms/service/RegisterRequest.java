package vn.uth.lms.service;

/** Data of the "Register account" use case. */
public record RegisterRequest(String fullName, String dateOfBirth, String address, String phone, String email,
                              String username, String password, String confirmPassword) {}
