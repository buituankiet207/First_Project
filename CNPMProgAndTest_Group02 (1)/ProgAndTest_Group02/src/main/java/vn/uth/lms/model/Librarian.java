package vn.uth.lms.model;

import java.time.LocalDate;
import java.util.List;
import vn.uth.lms.service.*;

public class Librarian extends User {
    private String staffCode;
    private LocalDate hireDate;

    public String getStaffCode() { return staffCode; }
    public void setStaffCode(String staffCode) { this.staffCode = staffCode; }
    public LocalDate getHireDate() { return hireDate; }
    public void setHireDate(LocalDate hireDate) { this.hireDate = hireDate; }

    @Override public Role getRole() { return Role.LIBRARIAN; }

    public List<Loan> lendBook(LoanService loans, String cardNumber, List<String> barcodes) { return loans.lend(this, cardNumber, barcodes); }
    public LoanService.ReturnResult returnBook(LoanService loans, String barcode, String condition, Double price) { return loans.returnBook(this, barcode, condition, price); }
    public Fine collectFine(FineService fines, int fineId) { return fines.collectFine(this, fineId); }
    public CatalogService manageBooks(CatalogService catalog) { return catalog; }
    public MemberService manageMembers(MemberService members) { return members; }
}
