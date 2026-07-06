package com.routeshare.service.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.routeshare.exception.MapApiException;
import com.routeshare.service.integration.MappingService;
import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

@Service
@Primary
public class GoogleMapsMappingService
implements MappingService {
    @Value(value="${google.maps.api-key:}")
    private String apiKey;
    private static final Logger log = LoggerFactory.getLogger(GoogleMapsMappingService.class);
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @PostConstruct
    public void init() {
        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            log.info("GoogleMapsMappingService initialized WITHOUT an API key \u2014 using local distance approximation fallback.");
        } else {
            log.info("GoogleMapsMappingService initialized with API key '{}****' (masked).", (Object)this.apiKey.substring(0, Math.min((int)6, (int)this.apiKey.length())));
        }
    }

    @Override
    public double getDistanceKm(String origin, String destination) {
        if (origin == null || destination == null || origin.equalsIgnoreCase(destination)) {
            return 0.0;
        }
        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            return this.getFallbackDistanceKm(origin, destination);
        }
        try {
            JsonNode element = this.fetchDistanceMatrixElement(origin, destination);
            if (element != null) {
                String elementStatus = element.path("status").asText();
                if ("OK".equalsIgnoreCase(elementStatus)) {
                    double meters = element.path("distance").path("value").asDouble();
                    return meters / 1000.0;
                }
                if ("ZERO_RESULTS".equalsIgnoreCase(elementStatus)) {
                    throw new MapApiException("No route could be resolved between '" + origin + "' and '" + destination + "'.");
                }
                if ("NOT_FOUND".equalsIgnoreCase(elementStatus)) {
                    throw new MapApiException("One of the addresses could not be geocoded: '" + origin + "' or '" + destination + "'.");
                }
                throw new MapApiException("Google Maps Distance Matrix element error (" + elementStatus + ") for route: '" + origin + "' to '" + destination + "'.");
            }
        }
        catch (MapApiException e) {
            throw e;
        }
        catch (Exception e) {
            throw new MapApiException("Error calculating distance: " + e.getMessage());
        }
        return this.getFallbackDistanceKm(origin, destination);
    }

    @Override
    public int getTravelTimeMinutes(String origin, String destination) {
        if (origin == null || destination == null || origin.equalsIgnoreCase(destination)) {
            return 0;
        }
        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            return this.getFallbackTravelTimeMinutes(origin, destination);
        }
        try {
            JsonNode element = this.fetchDistanceMatrixElement(origin, destination);
            if (element != null) {
                String elementStatus = element.path("status").asText();
                if ("OK".equalsIgnoreCase(elementStatus)) {
                    int seconds = element.path("duration").path("value").asInt();
                    return seconds / 60;
                }
                if ("ZERO_RESULTS".equalsIgnoreCase(elementStatus)) {
                    throw new MapApiException("No route could be resolved between '" + origin + "' and '" + destination + "'.");
                }
                if ("NOT_FOUND".equalsIgnoreCase(elementStatus)) {
                    throw new MapApiException("One of the addresses could not be geocoded: '" + origin + "' or '" + destination + "'.");
                }
                throw new MapApiException("Google Maps Distance Matrix element error (" + elementStatus + ") for route: '" + origin + "' to '" + destination + "'.");
            }
        }
        catch (MapApiException e) {
            throw e;
        }
        catch (Exception e) {
            throw new MapApiException("Error calculating travel time: " + e.getMessage());
        }
        return this.getFallbackTravelTimeMinutes(origin, destination);
    }

    /*
     * Enabled force condition propagation
     * Lifted jumps to return sites
     */
    private JsonNode fetchDistanceMatrixElement(String origin, String destination) throws Exception {
        JsonNode elements;
        HttpResponse response;
        if (this.apiKey == null || this.apiKey.trim().isEmpty()) {
            System.err.println("[Google Maps API] Key is missing or empty.");
            return null;
        }
        String encodedOrigin = URLEncoder.encode((String)origin, (Charset)StandardCharsets.UTF_8);
        String encodedDest = URLEncoder.encode((String)destination, (Charset)StandardCharsets.UTF_8);
        String urlString = String.format((String)"https://maps.googleapis.com/maps/api/distancematrix/json?origins=%s&destinations=%s&key=%s", (Object[])new Object[]{encodedOrigin, encodedDest, this.apiKey.trim()});
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create((String)urlString)).GET().build();
        try {
            response = this.httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }
        catch (Exception e) {
            throw new MapApiException("Failed to connect to Google Maps API: " + e.getMessage());
        }
        if (response.statusCode() != 200) throw new MapApiException("Google Maps API HTTP error (status " + response.statusCode() + "): " + (String)response.body());
        JsonNode root = this.objectMapper.readTree((String)response.body());
        String status = root.path("status").asText();
        if (!"OK".equalsIgnoreCase(status)) throw new MapApiException("Google Maps API error: " + status + " - " + root.path("error_message").asText(""));
        JsonNode rows = root.path("rows");
        if (!rows.isArray() || rows.size() <= 0 || !(elements = rows.get(0).path("elements")).isArray() || elements.size() <= 0) return null;
        return elements.get(0);
    }

    private double getFallbackDistanceKm(String origin, String destination) {
        int hash = Math.abs((int)(origin + "->" + destination).hashCode());
        return 1.0 + (double)(hash % 150) / 10.0;
    }

    private int getFallbackTravelTimeMinutes(String origin, String destination) {
        int hash = Math.abs((int)(origin + "->" + destination).hashCode());
        return 3 + hash % 31;
    }
}
