package com.foodshare.web;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Lets React Router handle deep links such as /donor/rewards or /donation/12 when the built React app is
 * served by Spring Boot: every path without a file extension is forwarded to index.html.
 * (/api/** and /uploads/** keep their own, more specific mappings.)
 */
@Controller
public class SpaController {

    @GetMapping({"/{a:[^\\.]*}", "/{a:[^\\.]*}/{b:[^\\.]*}", "/{a:[^\\.]*}/{b:[^\\.]*}/{c:[^\\.]*}"})
    public String forward() {
        return "forward:/index.html";
    }
}
