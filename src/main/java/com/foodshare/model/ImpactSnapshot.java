package com.foodshare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Platform-wide numbers for the Impact page / home page, one row per range (month, year, all). */
@Entity
@Table(name = "impact_snapshots")
@Getter
@Setter
@NoArgsConstructor
public class ImpactSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "range_key", nullable = false, unique = true)
    private String rangeKey;

    private long meals;
    private long people;
    private long co2;
    private long donors;
    private long ngos;

    /** Comma separated chart labels / values. */
    private String labels;
    private String series;
    private String caption;

    public ImpactSnapshot(String rangeKey, long meals, long people, long co2, long donors, long ngos,
                          String labels, String series, String caption) {
        this.rangeKey = rangeKey;
        this.meals = meals;
        this.people = people;
        this.co2 = co2;
        this.donors = donors;
        this.ngos = ngos;
        this.labels = labels;
        this.series = series;
        this.caption = caption;
    }
}
