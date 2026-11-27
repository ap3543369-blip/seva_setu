package com.foodshare.bookshare;

public enum BookRequestStatus {
    PENDING("Pending"), ACCEPTED("Accepted"), DECLINED("Declined"), COMPLETED("Completed"), CANCELLED("Cancelled");

    private final String label;

    BookRequestStatus(String label) { this.label = label; }

    public String label() { return label; }
}
