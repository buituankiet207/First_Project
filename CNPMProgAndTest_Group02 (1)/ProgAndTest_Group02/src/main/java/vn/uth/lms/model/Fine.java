package vn.uth.lms.model;

import java.time.LocalDateTime;

public class Fine {
    private int fineId;
    private int loanId;
    private double amount;
    private FineReason reason;
    private FineStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime paidAt;

    public int getFineId() { return fineId; }
    public void setFineId(int fineId) { this.fineId = fineId; }
    public int getLoanId() { return loanId; }
    public void setLoanId(int loanId) { this.loanId = loanId; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public FineReason getReason() { return reason; }
    public void setReason(FineReason reason) { this.reason = reason; }
    public FineStatus getStatus() { return status; }
    public void setStatus(FineStatus status) { this.status = status; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
    public LocalDateTime getPaidAt() { return paidAt; }
    public void setPaidAt(LocalDateTime paidAt) { this.paidAt = paidAt; }

    public double calculateAmount(long overdueDays, long ratePerDay) {
        this.amount = overdueDays * (double) ratePerDay;
        return amount;
    }

    public void markPaid(LocalDateTime now) {
        this.status = FineStatus.PAID;
        this.paidAt = now;
    }
}
