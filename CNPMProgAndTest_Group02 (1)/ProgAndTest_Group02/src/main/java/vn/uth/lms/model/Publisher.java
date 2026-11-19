package vn.uth.lms.model;

public class Publisher implements NamedEntity {
    private int publisherId;
    private String name;
    private String address;

    public int getPublisherId() { return publisherId; }
    public void setPublisherId(int publisherId) { this.publisherId = publisherId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }

    @Override public int getId() { return publisherId; }
    @Override public void setId(int id) { this.publisherId = id; }
}
