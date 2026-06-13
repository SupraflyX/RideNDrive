package com.routeshare.model;

import jakarta.persistence.*;

/**
 * Rating represents a two-way feedback event (driver-to-passenger or passenger-to-driver) in the platform.
 *
 * Demonstrates:
 * - Domain Associations: Linking reviewer and reviewee users.
 * - Integrity Constraints: Score must be constrained to the interval [1, 5] (Quality Attribute: Robustness).
 */
@Entity
@Table(name = "ratings")
public class Rating {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private User reviewer;

    @ManyToOne(optional = false)
    @JoinColumn(name = "reviewee_id", nullable = false)
    private User reviewee;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private String type; // "DRIVER_RATED" or "PASSENGER_RATED"

    public Rating() {
    }

    public Rating(User reviewer, User reviewee, int score, String type) {
        this.reviewer = reviewer;
        this.reviewee = reviewee;
        this.score = score;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getReviewer() {
        return reviewer;
    }

    public void setReviewer(User reviewer) {
        this.reviewer = reviewer;
    }

    public User getReviewee() {
        return reviewee;
    }

    public void setReviewee(User reviewee) {
        this.reviewee = reviewee;
    }

    public int getScore() {
        return score;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
