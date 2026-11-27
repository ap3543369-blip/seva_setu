package com.foodshare.bookshare;

public enum BookStatus {
    AVAILABLE("Available"), RESERVED("Reserved"), DONATED("Donated"), CANCELLED("Cancelled");

    private final String label;

    BookStatus(String label) { this.label = label; }

    public String label() { return label; }
}
