package vn.uth.lms.model;

public class BookCopy {
    private int copyId;
    private int bookId;
    private String barcode;
    private CopyStatus status;
    private String shelfLocation;

    public int getCopyId() { return copyId; }
    public void setCopyId(int copyId) { this.copyId = copyId; }
    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public CopyStatus getStatus() { return status; }
    public void setStatus(CopyStatus status) { this.status = status; }
    public String getShelfLocation() { return shelfLocation; }
    public void setShelfLocation(String shelfLocation) { this.shelfLocation = shelfLocation; }

    public boolean isAvailable() { return status == CopyStatus.AVAILABLE; }
    public void updateStatus(CopyStatus newStatus) { this.status = newStatus; }
}
