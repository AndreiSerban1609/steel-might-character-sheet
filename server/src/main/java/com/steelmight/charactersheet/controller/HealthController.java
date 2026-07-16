package com.steelmight.charactersheet.controller;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Liveness probe for the frontend's offline detection (Story 3.3) and for
 * deploy healthchecks (Story 4.2). Deliberately does not touch the database —
 * "the app answers HTTP" is the signal the clients need.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "up");
    }
}
