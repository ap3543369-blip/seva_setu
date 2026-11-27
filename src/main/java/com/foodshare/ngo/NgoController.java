package com.foodshare.ngo;

import com.foodshare.model.Role;
import com.foodshare.model.User;
import com.foodshare.ngo.NgoService.NgoApplicationDto;
import com.foodshare.ngo.NgoService.NgoForm;
import com.foodshare.service.AuthService;
import com.foodshare.web.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/** NGO registration and the NGO's own application. Registration is public; everything else needs the NGO's login. */
@RestController
@RequestMapping("/api/ngo")
public class NgoController {
    private final NgoService ngos;
    private final AuthService auth;

    public NgoController(NgoService ngos, AuthService auth) {
        this.ngos = ngos;
        this.auth = auth;
    }

    /** multipart: part "data" = JSON form, part "certificate" = PDF / PNG / JPG (max 5 MB). */
    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public NgoApplicationDto register(@RequestPart("data") @Valid NgoForm data,
                                      @RequestPart(value = "certificate", required = false) MultipartFile certificate,
                                      HttpServletRequest request) {
        return ngos.register(data, certificate, request);
    }

    private User ngo(HttpSession s) {
        User u = auth.requireUser(s);
        if (u.getRole() != Role.NGO) throw ApiException.forbidden("Only NGO accounts can do this.");
        return u;
    }

    @GetMapping("/application")
    public NgoApplicationDto mine(HttpSession s) {
        return ngos.mine(ngo(s)).orElseThrow(() -> ApiException.notFound("No registration details found for this NGO yet."));
    }

    @PutMapping(value = "/application", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public NgoApplicationDto resubmit(@RequestPart("data") @Valid NgoForm data,
                                      @RequestPart(value = "certificate", required = false) MultipartFile certificate,
                                      HttpSession s) {
        return ngos.resubmit(ngo(s), data, certificate);
    }

    @GetMapping("/application/document")
    public ResponseEntity<Resource> myDocument(HttpSession s) {
        return file(ngos.myDocument(ngo(s)));
    }

    static ResponseEntity<Resource> file(Resource r) {
        String name = r.getFilename();
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(NgoDocumentStorage.contentType(name)))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"certificate-" + name + "\"")
                .header("X-Content-Type-Options", "nosniff")
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .body(r);
    }

    /** the organisation types offered in the form */
    @GetMapping("/org-kinds")
    public List<String> kinds() {
        return NgoService.ORG_KINDS;
    }
}
