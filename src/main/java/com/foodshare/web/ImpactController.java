package com.foodshare.web;

import com.foodshare.dto.Dtos.ImpactDto;
import com.foodshare.service.ImpactService;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/impact")
public class ImpactController {

    private final ImpactService impact;
    private final com.foodshare.service.AdminService admin;

    public ImpactController(ImpactService impact, com.foodshare.service.AdminService admin) {
        this.impact = impact;
        this.admin = admin;
    }

    @GetMapping("/leaderboard")
    public java.util.List<com.foodshare.dto.Dtos.LeaderboardEntry> leaderboard() {
        return admin.leaderboard();
    }

    @GetMapping
    public ImpactDto get(@RequestParam(defaultValue = "all") String range) {
        return impact.impact(range);
    }
}
