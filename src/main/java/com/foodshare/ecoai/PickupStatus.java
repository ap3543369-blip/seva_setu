package com.foodshare.ecoai;

public enum PickupStatus {
    REQUESTED("Requested"), SCHEDULED("Scheduled"), COLLECTED("Collected"), CANCELLED("Cancelled");

    private final String label;

    PickupStatus(String label) { this.label = label; }

    public String label() { return label; }
}
