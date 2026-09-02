package com.routeshare.controller;

import com.routeshare.model.Notification;
import com.routeshare.service.NotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    @Autowired
    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<Notification>> listForUser(@PathVariable Long userId) {
        return ResponseEntity.ok(notificationService.findByRecipient(userId));
    }

    @GetMapping("/user/{userId}/unread-count")
    public ResponseEntity<Map<String, Object>> unreadCount(@PathVariable Long userId) {
        return ResponseEntity.ok(Map.of("userId", userId, "unread", notificationService.unreadCount(userId)));
    }

    @PostMapping("/{id}/mark-read")
    public ResponseEntity<?> markRead(@PathVariable Long id) {
        try {
            Notification updated = notificationService.markRead(id);
            return ResponseEntity.ok(Map.of("id", updated.getId(), "read", updated.isRead()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/user/{userId}/mark-all-read")
    public ResponseEntity<Map<String, Object>> markAllRead(@PathVariable Long userId) {
        int updated = notificationService.markAllRead(userId);
        return ResponseEntity.ok(Map.of("userId", userId, "marked", updated));
    }
}
