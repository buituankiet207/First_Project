package vn.uth.lms.persistence;

import java.util.List;

/** Repository for simple reference data (categories, authors, publishers). */
public interface RefRepository<T> {
    List<T> list();
    T find(int id);
    T findByName(String name);
    T save(T entity);
    void delete(int id);
    /** Number of (non-deleted) books that use this entry. */
    int usageCount(int id);
}
