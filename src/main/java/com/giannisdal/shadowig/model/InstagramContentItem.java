package com.giannisdal.shadowig.model;

import java.time.Instant;

public record InstagramContentItem(
        String id,
        InstagramContentType type,
        String permalink,
        Instant publishedAt
) {
}
