package vn.uth.lms.model;

/** Reference data (category, author, publisher) that is identified by id and name. */
public interface NamedEntity {
    int getId();
    void setId(int id);
    String getName();
    void setName(String name);
}
