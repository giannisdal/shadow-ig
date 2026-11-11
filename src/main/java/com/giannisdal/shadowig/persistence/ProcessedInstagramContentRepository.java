package com.giannisdal.shadowig.persistence;

import com.giannisdal.shadowig.model.InstagramContentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedInstagramContentRepository extends JpaRepository<ProcessedInstagramContentEntity, Long> {

    boolean existsByUsernameAndContentIdAndContentType(String username, String contentId, InstagramContentType contentType);

    long countByUsername(String username);
}
