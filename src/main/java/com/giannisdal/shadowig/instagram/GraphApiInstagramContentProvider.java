package com.giannisdal.shadowig.instagram;

import com.fasterxml.jackson.databind.JsonNode;
import com.giannisdal.shadowig.config.InstagramApiProperties;
import com.giannisdal.shadowig.config.InstagramMonitoringProperties;
import com.giannisdal.shadowig.exception.InstagramProviderException;
import com.giannisdal.shadowig.model.InstagramContentItem;
import com.giannisdal.shadowig.model.InstagramContentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
public class GraphApiInstagramContentProvider implements InstagramContentProvider {

    private static final Logger log = LoggerFactory.getLogger(GraphApiInstagramContentProvider.class);

    private final RestClient restClient;
    private final InstagramApiProperties apiProperties;
    private final InstagramMonitoringProperties monitoringProperties;

    public GraphApiInstagramContentProvider(
            RestClient restClient,
            InstagramApiProperties apiProperties,
            InstagramMonitoringProperties monitoringProperties
    ) {
        this.restClient = restClient;
        this.apiProperties = apiProperties;
        this.monitoringProperties = monitoringProperties;
    }

    @Override
    public InstagramProviderResult fetchLatest(InstagramMonitoringProperties.AccountConfig account) {
        var unsupportedTypes = new HashSet<InstagramContentType>();
        if (account.stories()) {
            unsupportedTypes.add(InstagramContentType.STORY);
            log.warn("Stories for @{} are not supported via Instagram Graph API Business Discovery", account.username());
        }

        String fields = "business_discovery.username(" + account.username() + "){username,media.limit(" +
                monitoringProperties.maxItemsPerAccount() +
                "){id,media_type,media_product_type,permalink,timestamp}}";

        String uri = UriComponentsBuilder.fromUriString(apiProperties.baseUrl())
                .pathSegment(apiProperties.igUserId())
                .queryParam("fields", fields)
                .queryParam("access_token", apiProperties.accessToken())
                .build()
                .toUriString();

        try {
            JsonNode body = restClient.get().uri(uri).retrieve().body(JsonNode.class);
            if (body == null || body.has("error")) {
                throw new InstagramProviderException("Instagram Graph API returned an error response");
            }

            List<InstagramContentItem> items = new ArrayList<>();
            JsonNode mediaNodes = body.path("business_discovery").path("media").path("data");
            if (!mediaNodes.isArray()) {
                return new InstagramProviderResult(List.of(), unsupportedTypes);
            }

            for (JsonNode mediaNode : mediaNodes) {
                var contentType = resolveType(mediaNode.path("media_product_type").asText(), mediaNode.path("media_type").asText());
                if (contentType == InstagramContentType.POST && !account.posts()) {
                    continue;
                }
                if (contentType == InstagramContentType.REEL && !account.reels()) {
                    continue;
                }
                items.add(new InstagramContentItem(
                        mediaNode.path("id").asText(),
                        contentType,
                        mediaNode.path("permalink").asText(),
                        Instant.parse(mediaNode.path("timestamp").asText())
                ));
            }

            return new InstagramProviderResult(items, unsupportedTypes);
        } catch (Exception exception) {
            throw new InstagramProviderException("Failed to read Instagram content for @" + account.username(), exception);
        }
    }

    private InstagramContentType resolveType(String mediaProductType, String mediaType) {
        if ("REELS".equalsIgnoreCase(mediaProductType)) {
            return InstagramContentType.REEL;
        }
        if ("VIDEO".equalsIgnoreCase(mediaType) && "FEED".equalsIgnoreCase(mediaProductType)) {
            return InstagramContentType.POST;
        }
        return InstagramContentType.POST;
    }
}
