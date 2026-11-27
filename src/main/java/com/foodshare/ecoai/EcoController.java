package com.foodshare.ecoai;

import com.foodshare.common.ModuleAccessService;
import com.foodshare.common.PlatformModule;
import com.foodshare.ecoai.EcoService.*;
import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.service.AuthService;
import com.foodshare.web.ApiException;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** EcoAI REST API: donors request pickups, recyclers run them and keep records, forecast uses those records. */
@RestController
@RequestMapping("/api/ecoai")
public class EcoController {
    private final EcoService eco;
    private final AuthService auth;
    private final ModuleAccessService modules;

    public EcoController(EcoService eco, AuthService auth, ModuleAccessService modules) {
        this.eco = eco;
        this.auth = auth;
        this.modules = modules;
    }

    private User user(HttpSession s) {
        User u = auth.requireUser(s);
        modules.require(u, PlatformModule.ECOAI);
        return u;
    }

    private User withRole(HttpSession s, Role... roles) {
        User u = user(s);
        for (Role r : roles) if (u.getRole() == r) return u;
        throw ApiException.forbidden("Your account type cannot do this in EcoAI.");
    }

    @GetMapping("/categories")
    public List<String> categories(HttpSession s) {
        user(s);
        return EcoService.CATEGORIES;
    }

    @GetMapping("/dashboard")
    public Map<String, Object> dashboard(HttpSession s) {
        return eco.dashboard(withRole(s, Role.DONOR, Role.RECYCLER, Role.ADMIN));
    }

    // ---- donor
    @PostMapping("/pickups")
    @ResponseStatus(HttpStatus.CREATED)
    public PickupDto request(@RequestBody @Valid PickupForm body, HttpSession s) {
        return eco.request(withRole(s, Role.DONOR), body);
    }

    @GetMapping("/pickups/mine")
    public List<PickupDto> mine(HttpSession s) {
        return eco.mine(withRole(s, Role.DONOR));
    }

    @DeleteMapping("/pickups/{id}")
    public PickupDto cancel(@PathVariable Long id, HttpSession s) {
        return eco.cancel(withRole(s, Role.DONOR), id);
    }

    // ---- recycler
    @GetMapping("/pickups/open")
    public List<PickupDto> open(HttpSession s) {
        return eco.open(withRole(s, Role.RECYCLER));
    }

    @GetMapping("/pickups/assigned")
    public List<PickupDto> assigned(HttpSession s) {
        return eco.assigned(withRole(s, Role.RECYCLER));
    }

    @PatchMapping("/pickups/{id}")
    public PickupDto act(@PathVariable Long id, @RequestBody @Valid PickupAction body, HttpSession s) {
        return eco.act(withRole(s, Role.RECYCLER), id, body);
    }

    @GetMapping("/records")
    public List<RecordDto> records(HttpSession s) {
        return eco.records(withRole(s, Role.RECYCLER));
    }

    @PostMapping("/records")
    public RecordDto saveRecord(@RequestBody @Valid RecordForm body, HttpSession s) {
        return eco.saveRecord(withRole(s, Role.RECYCLER), body);
    }

    @DeleteMapping("/records/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteRecord(@PathVariable Long id, HttpSession s) {
        eco.deleteRecord(withRole(s, Role.RECYCLER), id);
    }

    // ---- forecast (recycler: own data, admin: whole platform)
    @GetMapping("/forecast")
    public Map<String, Object> forecast(@RequestParam(defaultValue = "6") int horizon, @RequestParam(required = false) String category,
                                        @RequestParam(required = false) Double capacityKg, HttpSession s) {
        return eco.forecast(withRole(s, Role.RECYCLER, Role.ADMIN), horizon, category, capacityKg);
    }
}
