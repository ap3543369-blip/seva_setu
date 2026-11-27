package com.foodshare.ngo;

public enum NgoApplicationStatus {
    SUBMITTED("Under review"), APPROVED("Approved"), REJECTED("Changes needed");

    private final String label;

    NgoApplicationStatus(String label) { this.label = label; }

    public String label() { return label; }
}
