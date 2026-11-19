package vn.uth.lms.service;

/** Data of the "Update profile" use case. Password fields are optional. */
public record ProfileUpdate(String fullName, String phone, String address, String dateOfBirth, String email,
                            String currentPassword, String newPassword) {}
