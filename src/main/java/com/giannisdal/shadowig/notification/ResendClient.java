package com.giannisdal.shadowig.notification;

import com.giannisdal.shadowig.exception.NotificationDeliveryException;

public interface ResendClient {
    String sendEmail(String from, String to, String subject, String htmlBody) throws NotificationDeliveryException;
}
