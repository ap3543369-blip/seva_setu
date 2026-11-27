package com.foodshare.medishare;

public enum MediStatus {
    PENDING_REVIEW("Pending admin review"), LISTED("Listed"), REJECTED("Rejected"),
    FULLY_ALLOCATED("Fully allocated"), WITHDRAWN("Withdrawn"), SUSPENDED("Suspended");

    private final String label;

    MediStatus(String label) { this.label = label; }

    public String label() { return label; }
}
