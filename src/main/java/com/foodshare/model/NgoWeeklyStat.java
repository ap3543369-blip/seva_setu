package com.foodshare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Meals an NGO received in week 1-4 of a month (feeds the bar chart on the NGO dashboard). */
@Entity
@Table(name = "ngo_weekly_stats",
        uniqueConstraints = @UniqueConstraint(columnNames = {"ngo_id", "month_key", "week_no"}))
@Getter
@Setter
@NoArgsConstructor
public class NgoWeeklyStat {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User ngo;

    /** yyyy-MM */
    @Column(name = "month_key", nullable = false)
    private String monthKey;

    @Column(name = "week_no", nullable = false)
    private int weekNo;

    private int meals;

    public NgoWeeklyStat(User ngo, String monthKey, int weekNo, int meals) {
        this.ngo = ngo;
        this.monthKey = monthKey;
        this.weekNo = weekNo;
        this.meals = meals;
    }
}
