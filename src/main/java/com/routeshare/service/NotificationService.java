package com.routeshare.service;

import com.routeshare.model.Notification;
import com.routeshare.model.User;
import com.routeshare.model.enums.NotificationType;
import com.routeshare.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * NotificationService is the single publication point for in-app notifications.
 *
 * Demonstrates:
 * - Observer Pattern (GoF Behavioral / Ch. 9): Domain services (booking lifecycle,
 *   rating submission) publish events here; the user inbox is the subscriber view.
 * - Separation of Concerns (SE Principle 2): Event delivery is isolated from the
 *   business rules that raise the events.
 * - Anticipation of Change (SE Principle 5): Swapping in e-mail or push delivery
 *   later only requires extending this service, not its callers.
 */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /**
     * Publishes a notification to a user.
     *
     * @param recipient the user to notify
     * @param type      the event category
     * @param message   human-readable message shown in the inbox
     * @return the persisted Notification
     */
    public Notification notify(User recipient, NotificationType type, String message) {
        Notification notification = new Notification(recipient, type, message);
        return notificationRepository.save(notification);
    }

    /** Returns all notifications for a user, newest first. */
    public List<Notification> findByRecipient(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    /** Returns the number of unread notifications for a user (for the UI badge). */
    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadIsFalse(userId);
    }

    /** Marks a single notification as read. */
    public Notification markRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + notificationId));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

    /** Marks every unread notification of a user as read. */
    @Transactional
    public int markAllRead(Long userId) {
        List<Notification> unread = notificationRepository.findByRecipientIdAndReadIsFalse(userId);
        for (Notification notification : unread) {
            notification.setRead(true);
        }
        notificationRepository.saveAll(unread);
        return unread.size();
    }
}
