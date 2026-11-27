package com.foodshare.web;

import com.foodshare.dto.Dtos.RedeemResponse;
import com.foodshare.dto.Dtos.RedeemedRewardDto;
import com.foodshare.dto.Dtos.RewardsResponse;
import com.foodshare.model.Role;
import com.foodshare.service.AuthService;
import com.foodshare.service.RewardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rewards")
public class RewardController {

    private final RewardService rewards;
    private final AuthService auth;

    public RewardController(RewardService rewards, AuthService auth) {
        this.rewards = rewards;
        this.auth = auth;
    }

    @GetMapping
    public RewardsResponse catalog(HttpSession session) {
        return rewards.catalog(auth.require(session, Role.DONOR));
    }

    @PostMapping("/{code}/redeem")
    public RedeemResponse redeem(@PathVariable String code, HttpSession session) {
        return rewards.redeem(auth.require(session, Role.DONOR), code);
    }

    @GetMapping("/mine")
    public List<RedeemedRewardDto> mine(HttpSession session) {
        return rewards.mine(auth.require(session, Role.DONOR));
    }

    /** Demo helper used by the "reset points and rewards" link. */
    @PostMapping("/reset")
    public Map<String, Integer> reset(HttpSession session) {
        return Map.of("points", rewards.resetDemo(auth.require(session, Role.DONOR)));
    }
}
