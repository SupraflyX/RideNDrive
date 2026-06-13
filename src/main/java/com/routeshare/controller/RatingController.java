package com.routeshare.controller;

import com.routeshare.model.Rating;
import com.routeshare.service.RatingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

/**
 * RatingController exposes REST endpoints for managing Ratings.
 *
 * Demonstrates:
 * - MVC Architectural Pattern: Translates web requests for submitting feedback to the RatingService.
 */
@RestController
@RequestMapping("/api/ratings")
public class RatingController {

    private final RatingService ratingService;

    @Autowired
    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @GetMapping
    public List<Rating> getAllRatings() {
        return ratingService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Rating> getRatingById(@PathVariable Long id) {
        return ratingService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public Rating createRating(@RequestBody Rating rating) {
        return ratingService.save(rating);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteRating(@PathVariable Long id) {
        ratingService.delete(id);
        return ResponseEntity.ok().build();
    }
}
