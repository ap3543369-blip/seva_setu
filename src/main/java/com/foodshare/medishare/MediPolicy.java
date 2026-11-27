package com.foodshare.medishare;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * MediShare safety rules, editable by the admin (one row, id = 1).
 * The defaults below are conservative PLACEHOLDERS. They are not legal advice: have a pharmacist / legal adviser
 * confirm them for your jurisdiction before going live.
 */
@Entity
@Table(name = "medi_policy")
@Getter
@Setter
@NoArgsConstructor
public class MediPolicy {
    public static final String DEFAULT_CATEGORIES = "Tablets/Capsules,Oral liquids (sealed),Topicals (creams/ointments),First-aid supplies,Sterile consumables (sealed),Medical devices (non-implant),Diagnostic kits";
    public static final String DEFAULT_BLOCKED = "morphine,codeine,fentanyl,tramadol,methadone,pethidine,ketamine,opium,diazepam,alprazolam,clonazepam,lorazepam,zolpidem,narcotic,psychotropic,schedule x,schedule h1,controlled substance,vaccine,insulin,chemotherapy,cytotoxic";

    @Id
    private Long id = 1L;

    /** minimum remaining shelf life (days) at listing and at approval */
    private int minShelfLifeDays = 180;
    private boolean requireAdminReview = true;
    private boolean requireSealedPack = true;
    private boolean allowColdChain = false;

    @Column(length = 1000)
    private String allowedCategories = DEFAULT_CATEGORIES;
    @Column(length = 1500)
    private String blockedTerms = DEFAULT_BLOCKED;
}
