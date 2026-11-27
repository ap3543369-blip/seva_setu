package com.foodshare.medishare;

public enum MediRequestStatus {
    PENDING("Pending"), APPROVED("Approved"), REJECTED("Rejected"), COLLECTED("Collected"), CANCELLED("Cancelled");

    private final String label;

    MediRequestStatus(String label) { this.label = label; }

    public String label() { return label; }
}
