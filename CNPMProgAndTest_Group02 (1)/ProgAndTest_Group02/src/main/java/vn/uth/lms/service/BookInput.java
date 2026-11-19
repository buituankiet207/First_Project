package vn.uth.lms.service;

import java.util.List;

/** Data of the "Manage books" add / update forms. Category, publisher and authors are given by name. */
public record BookInput(String isbn, String title, List<String> authors, String category, String publisher,
                        Integer publishYear, String description, Integer copies, String shelfLocation) {}
