package com.foodshare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "reward_offers")
@Getter
@Setter
@NoArgsConstructor
public class RewardOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Stable public id used by the frontend, e.g. "amz100". */
    @Column(nullable = false, unique = true)
    private String code;

    private String brand;
    /** File name (without extension) in assets/images/rewards. */
    private String logo;
    private int amount;
    private int cost;

    public RewardOffer(String code, String brand, String logo, int amount, int cost) {
        this.code = code;
        this.brand = brand;
        this.logo = logo;
        this.amount = amount;
        this.cost = cost;
    }
}
