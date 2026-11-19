package vn.uth.lms.model;

public class Category implements NamedEntity {
    private int categoryId;
    private String name;

    public int getCategoryId() { return categoryId; }
    public void setCategoryId(int categoryId) { this.categoryId = categoryId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override public int getId() { return categoryId; }
    @Override public void setId(int id) { this.categoryId = id; }
    public Category() {}
    public Category(int id, String name) { this.categoryId = id; this.name = name; }
}
