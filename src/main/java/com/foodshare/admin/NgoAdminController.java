package com.foodshare.admin;

import com.foodshare.model.Role;
import com.foodshare.ngo.NgoService;
import com.foodshare.ngo.NgoService.NgoApplicationDto;
import com.foodshare.ngo.NgoService.ReviewBody;
import com.foodshare.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Admin: look at NGO applications, open the uploaded certificate, approve or send back for changes. */
@RestController
@RequestMapping("/api/admin/ngo-applications")
public class NgoAdminController {
    private final NgoService ngos;
    private final AuthService auth;

    public NgoAdminController(NgoService ngos, AuthService auth) {
        this.ngos = ngos;
        this.auth = auth;
    }

    @GetMapping
    public List<NgoApplicationDto> list(@RequestParam(required = false) String status, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return ngos.list(status);
    }

    @GetMapping("/{id}")
    public NgoApplicationDto detail(@PathVariable Long id, HttpSession s) {
        auth.require(s, Role.ADMIN);
        return ngos.detail(id);
    }

    @GetMapping("/{id}/document")
    public ResponseEntity<Resource> document(@PathVariable Long id, HttpSession s) {
        auth.require(s, Role.ADMIN);
        Resource r = ngos.document(id);
        String name = r.getFilename();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(com.foodshare.ngo.NgoDocumentStorage.contentType(name)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"certificate-" + name + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(r);
    }

    @PostMapping("/{id}/review")
    public NgoApplicationDto review(@PathVariable Long id, @RequestBody @Valid ReviewBody body, HttpSession s) {
        return ngos.review(auth.require(s, Role.ADMIN), id, body);
    }
}
