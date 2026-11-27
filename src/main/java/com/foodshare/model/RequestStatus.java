package com.foodshare.model;

public enum RequestStatus {
    PENDING("Pending"), ACCEPTED("Accepted"), DELIVERED("Delivered"), DECLINED("Declined");

    private final String label;

    RequestStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
