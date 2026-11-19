package vn.uth.lms;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import vn.uth.lms.model.*;
import vn.uth.lms.service.Report;
import vn.uth.lms.util.AppException;

/** Use case: View reports. */
class ReportServiceTest {
    Fixture f;
    String from, to;

    @BeforeEach
    void setUp() {
        f = new Fixture();
        from = f.today().minusDays(100).toString();
        to = f.today().toString();
    }

    private AppException fails(Runnable r) { return assertThrows(AppException.class, r::run); }

    @Test
    void mostBorrowedListsBooksByNumberOfLoans() {
        Report r = f.app.reports.generate("most-borrowed", from, to);
        assertEquals("Don't Make Me Think", r.getRows().get(0).get(1));
        assertEquals(2L, r.getRows().get(0).get(3));
        assertEquals(6, r.getRows().size());
    }

    @Test
    void periodWithoutLoansGivesAnEmptyReport() {
        Report r = f.app.reports.generate("most-borrowed", "2020-01-01", "2020-01-31");
        assertTrue(r.isEmpty());
        assertEquals(true, r.toMap().get("empty"));
    }

    @Test
    void overdueReportShowsTheDaysOverdue() {
        Report r = f.app.reports.generate("overdue", from, to);
        assertEquals(2, r.getRows().size());
        boolean found = r.getRows().stream().anyMatch(row -> "Hoang Van E".equals(row.get(0)) && Long.valueOf(6).equals(row.get(6)));
        assertTrue(found);
    }

    @Test
    void finesReportTotalsThePaidFines() {
        assertTrue(f.app.reports.generate("fines", from, to).isEmpty());
        Fine fine = f.store.finesOfMember(f.member("member06").getUserId()).get(0);
        f.app.fines.collectFine(f.librarian(), fine.getFineId());
        Report r = f.app.reports.generate("fines", from, to);
        assertEquals(1, r.getRows().size());
        assertEquals(14000.0, r.toMap().get("summary") instanceof java.util.Map<?, ?> m ? m.get("Total collected (VND)") : null);
    }

    @Test
    void memberStatisticsCountMembersAndLoans() {
        Report r = f.app.reports.generate("members", from, to);
        assertEquals(6L, r.getRows().get(0).get(1));
        assertEquals(4L, r.getRows().get(1).get(1));
        assertEquals(6L, r.getRows().get(2).get(1));
        assertEquals(7L, r.getRows().get(4).get(1));
    }

    @Test
    void startDateMustNotBeAfterTheEndDate() {
        AppException e = fails(() -> f.app.reports.generate("most-borrowed", to, from));
        assertTrue(e.getMessage().contains("Invalid period"));
    }

    @Test
    void unknownTypeOrMissingDatesAreRefused() {
        assertEquals(400, fails(() -> f.app.reports.generate("secret", from, to)).getStatus());
        assertEquals(400, fails(() -> f.app.reports.generate("fines", "", to)).getStatus());
        assertEquals("from", fails(() -> f.app.reports.generate("fines", "31/10/2026", to)).getField());
    }

    @Test
    void csvHasHeaderAndEscapesCommas() {
        Report r = new Report("t", java.util.List.of("A", "B")).row("x,y", "say \"hi\"");
        assertEquals("\uFEFFA,B\r\n\"x,y\",\"say \"\"hi\"\"\"\r\n", r.toCsv());
        assertTrue(f.app.reports.generate("members", from, to).toCsv().contains("Metric,Value"));
    }
}
