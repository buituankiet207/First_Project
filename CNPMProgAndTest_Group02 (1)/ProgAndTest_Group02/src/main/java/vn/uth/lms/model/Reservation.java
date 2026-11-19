package vn.uth.lms.model;

import java.time.LocalDateTime;

public class Reservation {
    private int reservationId;
    private int memberId;
    private int bookId;
    private LocalDateTime reservedAt;
    private LocalDateTime expiresAt;
    private ReservationStatus status;

    public int getReservationId() { return reservationId; }
    public void setReservationId(int reservationId) { this.reservationId = reservationId; }
    public int getMemberId() { return memberId; }
    public void setMemberId(int memberId) { this.memberId = memberId; }
    public int getBookId() { return bookId; }
    public void setBookId(int bookId) { this.bookId = bookId; }
    public LocalDateTime getReservedAt() { return reservedAt; }
    public void setReservedAt(LocalDateTime reservedAt) { this.reservedAt = reservedAt; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
    public ReservationStatus getStatus() { return status; }
    public void setStatus(ReservationStatus status) { this.status = status; }

    public Reservation create(LocalDateTime now) {
        this.reservedAt = now;
        this.expiresAt = null;
        this.status = ReservationStatus.WAITING;
        return this;
    }

    public void cancel() { this.status = ReservationStatus.CANCELLED; }

    /** A READY reservation expires when its holding period has passed. */
    public boolean isExpired(LocalDateTime now) { return status == ReservationStatus.READY && expiresAt != null && now.isAfter(expiresAt); }

    public boolean isActive() { return status == ReservationStatus.WAITING || status == ReservationStatus.READY; }
}
