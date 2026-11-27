package com.foodshare.common;

/** The four SevaSetu modules. Named PlatformModule to avoid confusion with java.lang.Module. */
public enum PlatformModule {
    FOODSHARE("FoodShare", "Donate surplus food; NGOs browse, request and track collection.", "🍛"),
    BOOKSHARE("BookShare", "Donate used books, textbooks and study material to students in need.", "📚"),
    MEDISHARE("MediShare", "Authorised hospitals, pharmacies and NGOs coordinate eligible medicine and supply donations.", "💊"),
    ECOAI("EcoAI", "AI forecasts of e-waste volumes to plan collection and recycling.", "♻️");

    private final String label;
    private final String description;
    private final String icon;

    PlatformModule(String label, String description, String icon) {
        this.label = label;
        this.description = description;
        this.icon = icon;
    }

    public String label() { return label; }
    public String description() { return description; }
    public String icon() { return icon; }
    public String key() { return name().toLowerCase(); }

    /** "foodshare" / "FOODSHARE" -> FOODSHARE, or null when unknown. */
    public static PlatformModule fromKey(String key) {
        if (key == null) return null;
        try {
            return valueOf(key.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
