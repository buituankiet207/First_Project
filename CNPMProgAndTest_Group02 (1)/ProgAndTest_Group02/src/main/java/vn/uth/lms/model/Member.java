package vn.uth.lms.model;

import java.time.LocalDate;
import java.util.List;
import vn.uth.lms.service.LoanService;
import vn.uth.lms.service.ReservationService;

public class Member extends User {
    private String address;
    private LocalDate dateOfBirth;
    private String cardNumber;
    private LocalDate cardExpiry;

    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public LocalDate getDateOfBirth() { return dateOfBirth; }
    public void setDateOfBirth(LocalDate dateOfBirth) { this.dateOfBirth = dateOfBirth; }
    public String getCardNumber() { return cardNumber; }
    public void setCardNumber(String cardNumber) { this.cardNumber = cardNumber; }
    public LocalDate getCardExpiry() { return cardExpiry; }
    public void setCardExpiry(LocalDate cardExpiry) { this.cardExpiry = cardExpiry; }

    @Override public Role getRole() { return Role.MEMBER; }
    public boolean isCardValid(LocalDate today) { return cardNumber != null && cardExpiry != null && !cardExpiry.isBefore(today); }

    public Reservation reserveBook(ReservationService reservations, int bookId) { return reservations.reserve(getUserId(), bookId); }
    public Loan renewLoan(LoanService loans, int loanId) { return loans.renew(getUserId(), loanId); }
    public List<java.util.Map<String, Object>> viewLoanHistory(LoanService loans, String statusFilter) { return loans.loansOfMember(getUserId(), statusFilter); }
}
