package com.routeshare.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/* hands the browser the bits of config it needs. the maps key comes from an env
   variable rather than being written into the frontend. without it the SPA just
   draws its own simple route view instead */
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
