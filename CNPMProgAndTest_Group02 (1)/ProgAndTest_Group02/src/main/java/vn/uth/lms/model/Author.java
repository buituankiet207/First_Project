package vn.uth.lms.model;

public class Author implements NamedEntity {
    private int authorId;
    private String name;

    public int getAuthorId() { return authorId; }
    public void setAuthorId(int authorId) { this.authorId = authorId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @Override public int getId() { return authorId; }
    @Override public void setId(int id) { this.authorId = id; }
    public Author() {}
    public Author(int id, String name) { this.authorId = id; this.name = name; }
}
