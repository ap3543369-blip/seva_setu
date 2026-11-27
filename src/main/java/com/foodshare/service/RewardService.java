package com.foodshare.service;

import com.foodshare.dto.Dtos.RedeemResponse;
import com.foodshare.dto.Dtos.RedeemedRewardDto;
import com.foodshare.dto.Dtos.RewardOfferDto;
import com.foodshare.dto.Dtos.RewardsResponse;
import com.foodshare.model.RedeemedReward;
import com.foodshare.model.RewardOffer;
import com.foodshare.model.User;
import com.foodshare.repo.RedeemedRewardRepository;
import com.foodshare.repo.RewardOfferRepository;
import com.foodshare.repo.UserRepository;
import com.foodshare.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.List;

@Service
public class RewardService {

    /** Points every donor starts the demo with ("reset" restores this). */
    public static final int DEMO_POINTS = 1250;

    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private final SecureRandom random = new SecureRandom();

    private final RewardOfferRepository offers;
    private final RedeemedRewardRepository redeemed;
    private final UserRepository users;

    public RewardService(RewardOfferRepository offers, RedeemedRewardRepository redeemed, UserRepository users) {
        this.offers = offers;
        this.redeemed = redeemed;
        this.users = users;
    }

    @Transactional(readOnly = true)
    public RewardsResponse catalog(User user) {
        int points = users.findById(user.getId()).orElseThrow().getPoints();
        List<RewardOfferDto> list = offers.findAllByOrderByCostAscIdAsc().stream()
                .map(o -> new RewardOfferDto(o.getCode(), o.getBrand(), o.getLogo(), o.getAmount(), o.getCost()))
                .toList();
        return new RewardsResponse(points, list);
    }

    @Transactional
    public RedeemResponse redeem(User user, String offerCode) {
        RewardOffer offer = offers.findByCode(offerCode)
                .orElseThrow(() -> ApiException.notFound("That reward does not exist."));
        User u = users.findById(user.getId()).orElseThrow();
        if (u.getPoints() < offer.getCost()) {
            throw ApiException.badRequest("You need " + (offer.getCost() - u.getPoints()) + " more points for this reward.");
        }
        u.setPoints(u.getPoints() - offer.getCost());
        users.save(u);

        RedeemedReward r = new RedeemedReward();
        r.setUser(u);
        r.setBrand(offer.getBrand());
        r.setLogo(offer.getLogo());
        r.setAmount(offer.getAmount());
        r.setCost(offer.getCost());
        r.setCode("FS-" + token() + "-" + token());
        redeemed.save(r);
        return new RedeemResponse(toDto(r), u.getPoints());
    }

    @Transactional(readOnly = true)
    public List<RedeemedRewardDto> mine(User user) {
        return redeemed.findByUserOrderByRedeemedAtDesc(user).stream().map(RewardService::toDto).toList();
    }

    /** Demo helper: forget rewards redeemed during the demo and restore the starting points. */
    @Transactional
    public int resetDemo(User user) {
        redeemed.deleteByUserAndSeededFalse(user);
        User u = users.findById(user.getId()).orElseThrow();
        u.setPoints(DEMO_POINTS);
        users.save(u);
        return u.getPoints();
    }

    private String token() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            sb.append(ALPHABET.charAt(random.nextInt(ALPHABET.length())));
        }
        return sb.toString();
    }

    private static RedeemedRewardDto toDto(RedeemedReward r) {
        return new RedeemedRewardDto(r.getId(), r.getBrand(), r.getLogo(), r.getAmount(), r.getCost(),
                r.getCode(), r.getRedeemedAt());
    }
}
