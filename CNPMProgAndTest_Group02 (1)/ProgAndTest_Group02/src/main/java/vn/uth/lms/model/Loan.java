package vn.uth.lms.model;

import java.time.LocalDate;

public class Loan {
    private int loanId;
    private int memberId;
    private int copyId;
    private int librarianId;
    private LocalDate loanDate;
    private LocalDate dueDate;
    private LocalDate returnDate;
    private int renewCount;
    private LoanStatus status;

    public int getLoanId() { return loanId; }
    public void setLoanId(int loanId) { this.loanId = loanId; }
    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }
    public int getCopyId() { return copyId; }
    public void setCopyId(int copyId) { this.copyId = copyId; }
    public int getLibrarianId() { return librarianId; }
    public void setLibrarianId(int librarianId) { this.librarianId = librarianId; }
    public LocalDate getLoanDate() { return loanDate; }
    public void setLoanDate(LocalDate loanDate) { this.loanDate = loanDate; }
    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public void setReturnDate(LocalDate returnDate) { this.returnDate = returnDate; }
    public int getRenewCount() { return renewCount; }
    public void setRenewCount(int renewCount) { this.renewCount = renewCount; }
    public LoanStatus getStatus() { return status; }
    public void setStatus(LoanStatus status) { this.status = status; }

    public boolean isOverdue(LocalDate today) { return status == LoanStatus.BORROWED && today.isAfter(dueDate); }

    public boolean canRenew(LocalDate today) { return status == LoanStatus.BORROWED && !isOverdue(today) && renewCount < Rules.MAX_RENEWALS; }

    /** Extends the due date by RENEW_DAYS and counts the renewal. */
    public void renew() {
        dueDate = dueDate.plusDays(Rules.RENEW_DAYS);
        renewCount++;
    }

    public long overdueDays(LocalDate until) {
        long d = java.time.temporal.ChronoUnit.DAYS.between(dueDate, until);
        return Math.max(0, d);
    }

    /** Overdue fine = overdue days x rate per day (VND). */
    public double calculateFine(LocalDate until, long ratePerDay) { return overdueDays(until) * (double) ratePerDay; }
}
