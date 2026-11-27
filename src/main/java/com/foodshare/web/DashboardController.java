package com.foodshare.web;

import com.foodshare.dto.Dtos.DonorDashboard;
import com.foodshare.dto.Dtos.NgoChart;
import com.foodshare.dto.Dtos.NgoDashboard;
import com.foodshare.model.Role;
import com.foodshare.service.AuthService;
import com.foodshare.service.DashboardService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboards;
    private final AuthService auth;

    public DashboardController(DashboardService dashboards, AuthService auth) {
        this.dashboards = dashboards;
        this.auth = auth;
    }

    @GetMapping("/donor")
    public DonorDashboard donor(HttpSession session) {
        return dashboards.donor(auth.require(session, Role.DONOR));
    }

    @GetMapping("/ngo")
    public NgoDashboard ngo(HttpSession session) {
        return dashboards.ngo(auth.require(session, Role.NGO));
    }

    @GetMapping("/ngo/chart")
    public NgoChart chart(@RequestParam(required = false) String month, HttpSession session) {
        return dashboards.chart(auth.require(session, Role.NGO), month);
    }
}
