package com.giannisdal.shadowig.notification;

import com.giannisdal.shadowig.model.InstagramContentItem;

import java.util.Optional;

public interface EmailNotificationService {

    Optional<String> sendNewContentNotification(String username, InstagramContentItem content);
}
