package com.giannisdal.shadowig.persistence;

import com.giannisdal.shadowig.model.InstagramContentType;
import com.giannisdal.shadowig.model.NotificationDeliveryStatus;
import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "processed_instagram_content",
        uniqueConstraints = @UniqueConstraint(name = "uq_username_content_type", columnNames = {"username", "content_id", "content_type"}))
public class ProcessedInstagramContentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(name = "content_id", nullable = false)
    private String contentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false)
    private InstagramContentType contentType;

    @Column(nullable = false)
    private String permalink;

    @Column(nullable = false)
    private Instant publishedAt;

    @Column(nullable = false)
    private Instant detectedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationDeliveryStatus status;

    @Column
    private String resendMessageId;

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getContentId() {
        return contentId;
    }

    public void setContentId(String contentId) {
        this.contentId = contentId;
    }

    public InstagramContentType getContentType() {
        return contentType;
    }

    public void setContentType(InstagramContentType contentType) {
        this.contentType = contentType;
    }

    public String getPermalink() {
        return permalink;
    }

    public void setPermalink(String permalink) {
        this.permalink = permalink;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }

    public NotificationDeliveryStatus getStatus() {
        return status;
    }

    public void setStatus(NotificationDeliveryStatus status) {
        this.status = status;
    }

    public String getResendMessageId() {
        return resendMessageId;
    }

    public void setResendMessageId(String resendMessageId) {
        this.resendMessageId = resendMessageId;
    }
}
