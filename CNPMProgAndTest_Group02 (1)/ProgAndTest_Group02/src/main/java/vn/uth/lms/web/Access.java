package vn.uth.lms.web;

/** Who may call a route. STAFF = librarians and admins; ADMIN = admins only. */
public enum Access { PUBLIC, AUTHENTICATED, MEMBER, STAFF, ADMIN }
