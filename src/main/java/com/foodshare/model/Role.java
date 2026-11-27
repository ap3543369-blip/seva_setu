package com.foodshare.model;

public enum Role {
    DONOR, NGO, ADMIN,
    RECIPIENT, HOSPITAL, RECYCLER; // added for SevaSetu (BookShare / MediShare / EcoAI)

    /** Lower-case name used by the frontend ("donor" / "ngo"). */
    public String key() {
        return name().toLowerCase();
    }
}
