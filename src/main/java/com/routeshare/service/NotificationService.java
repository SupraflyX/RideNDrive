package com.routeshare.service;

import com.routeshare.model.Notification;
import com.routeshare.model.User;
import com.routeshare.model.enums.NotificationType;
import com.routeshare.repository.NotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/* everything that creates an in-app notification goes through here, so if we ever
   want email or push as well there's only one place to change */
@Service
public class NotificationService {

    private final NotificationRepository notificationRepository;

    @Autowired
    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public Notification notify(User recipient, NotificationType type, String message) {
        Notification notification = new Notification(recipient, type, message);
        return notificationRepository.save(notification);
    }

    public List<Notification> findByRecipient(Long userId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(userId);
    }

    public long unreadCount(Long userId) {
        return notificationRepository.countByRecipientIdAndReadIsFalse(userId);
    }

    public Notification markRead(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new RuntimeException("Notification not found with id: " + notificationId));
        notification.setRead(true);
        return notificationRepository.save(notification);
    }

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
