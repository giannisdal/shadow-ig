package com.giannisdal.shadowig.notification;

import com.giannisdal.shadowig.model.InstagramContentItem;

import java.util.Optional;
import java.util.List;

public interface EmailNotificationService {

    Optional<String> sendNewContentNotification(String username, InstagramContentItem content);

    Optional<String> sendDailyDigest(String username, List<InstagramContentItem> contents);
}
