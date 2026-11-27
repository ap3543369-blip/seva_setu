package com.foodshare.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Success story / testimonial shown on the home and Impact pages. */
@Entity
@Table(name = "stories")
@Getter
@Setter
@NoArgsConstructor
public class Story {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;
    private String role;

    @Column(length = 600)
    private String quote;

    /** File name (without extension) in assets/images/users, or empty for an initials avatar. */
    private String avatar;

    public Story(String name, String role, String quote, String avatar) {
        this.name = name;
        this.role = role;
        this.quote = quote;
        this.avatar = avatar;
    }
}
