package vn.uth.lms.model;

import vn.uth.lms.service.ReportService;
import vn.uth.lms.service.StaffService;

public class Admin extends Librarian {
    private int adminLevel;

    public int getAdminLevel() { return adminLevel; }
    public void setAdminLevel(int adminLevel) { this.adminLevel = adminLevel; }

    @Override public Role getRole() { return Role.ADMIN; }
    public StaffService manageStaff(StaffService staff) { return staff; }
    public ReportService viewReports(ReportService reports) { return reports; }
}
