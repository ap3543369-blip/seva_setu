package com.foodshare.service;

import com.foodshare.dto.Dtos.ImpactDto;
import com.foodshare.model.ImpactSnapshot;
import com.foodshare.repo.DonationRepository;
import com.foodshare.repo.ImpactSnapshotRepository;
import com.foodshare.web.ApiException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

@Service
public class ImpactService {

    private final ImpactSnapshotRepository snapshots;
    private final DonationRepository donations;

    public ImpactService(ImpactSnapshotRepository snapshots, DonationRepository donations) {
        this.snapshots = snapshots;
        this.donations = donations;
    }

    /** Platform totals for "month", "year" or "all" = published baseline + meals donated through this app. */
    @Transactional(readOnly = true)
    public ImpactDto impact(String range) {
        String key = range == null || range.isBlank() ? "all" : range.toLowerCase();
        ImpactSnapshot s = snapshots.findByRangeKey(key)
                .orElseThrow(() -> ApiException.badRequest("Range must be month, year or all."));

        LocalDate today = LocalDate.now();
        LocalDateTime since = switch (key) {
            case "month" -> today.withDayOfMonth(1).atStartOfDay();
            case "year" -> today.withDayOfYear(1).atStartOfDay();
            default -> LocalDateTime.of(2000, 1, 1, 0, 0);
        };
        long live = donations.sumLiveMealsSince(since);

        List<String> labels = Arrays.asList(s.getLabels().split(","));
        List<Long> series = new ArrayList<>();
        for (String v : s.getSeries().split(",")) {
            series.add(Long.parseLong(v.trim()));
        }
        if (!series.isEmpty()) {
            int last = series.size() - 1;
            series.set(last, series.get(last) + live);
        }
        String caption = switch (key) {
            case "month" -> today.format(DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH));
            case "year" -> String.valueOf(today.getYear());
            default -> s.getCaption();
        };
        return new ImpactDto(s.getMeals() + live, s.getPeople(), s.getCo2(), s.getDonors(), s.getNgos(),
                labels, series, caption);
    }
}
