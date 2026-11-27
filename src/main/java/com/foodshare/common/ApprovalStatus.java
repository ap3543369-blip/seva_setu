package com.foodshare.common;

/** Admin approval of a donor's submission. A null value in the database means "created before approvals existed" = approved. */
public enum ApprovalStatus {
    PENDING("Awaiting approval"), APPROVED("Approved"), REJECTED("Rejected");

    private final String label;

    ApprovalStatus(String label) { this.label = label; }

    public String label() { return label; }
}
