package com.foodshare.model;

public enum DonationStatus {
    PENDING("Pending"), ACCEPTED("Accepted"), DELIVERED("Delivered"), CANCELLED("Cancelled");

    private final String label;

    DonationStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
