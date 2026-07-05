package com.routeshare.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * ConfigController exposes non-secret runtime configuration to the SPA.
 *
 * The Google Maps browser key is injected via the GOOGLE_MAPS_API_KEY
 * environment variable (12-factor configuration management) instead of being
 * hardcoded in the frontend sources. When the key is absent the SPA falls
 * back to its built-in route visualization, so no functionality is lost.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @Value("${google.maps.api-key:}")
    private String mapsApiKey;

    @GetMapping
    public ResponseEntity<Map<String, Object>> config() {
        return ResponseEntity.ok(Map.of(
                "mapsApiKey", mapsApiKey == null ? "" : mapsApiKey
        ));
    }
}
