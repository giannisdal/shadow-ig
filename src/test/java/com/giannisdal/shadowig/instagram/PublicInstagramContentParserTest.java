package com.giannisdal.shadowig.instagram;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties.AccountConfig;
import com.giannisdal.shadowig.model.InstagramContentType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PublicInstagramContentParserTest {
    private final AccountConfig account = new AccountConfig("example", true, true, true, true);

    @Test
    void onlyConcreteStoriesForTheConfiguredAccountAreDetected() {
        assertThat(PublicInstagramContentParser.parse("/stories/example/123456789/", null, account, true))
                .hasValueSatisfying(item -> {
                    assertThat(item.id()).isEqualTo("123456789");
                    assertThat(item.type()).isEqualTo(InstagramContentType.STORY);
                    assertThat(item.publishedAt()).isNull();
                });
        for (String link : new String[]{"/stories/example/", "/stories/highlights/123/", "/stories/other/123/",
                "/accounts/login/", "https://evil.test/stories/example/123/"}) {
            assertThat(PublicInstagramContentParser.parse(link, null, account, true)).isEmpty();
        }
    }

    @Test
    void retainsNumericMediaIdentifiersAndHonorsContentToggles() {
        assertThat(PublicInstagramContentParser.parse("/p/BA/", "2026-11-11T10:30:00Z", account, false))
                .hasValueSatisfying(item -> {
                    assertThat(item.id()).isEqualTo("64");
                    assertThat(item.type()).isEqualTo(InstagramContentType.POST);
                    assertThat(item.publishedAt()).isEqualTo("2026-11-11T10:30:00Z");
                });
        assertThat(PublicInstagramContentParser.parse("/reel/BB/", "invalid", account, false))
                .hasValueSatisfying(item -> {
                    assertThat(item.type()).isEqualTo(InstagramContentType.REEL);
                    assertThat(item.publishedAt()).isNull();
                });
        var storiesOnly = new AccountConfig("example", true, false, true, false);
        assertThat(PublicInstagramContentParser.parse("/p/BA/", null, storiesOnly, false)).isEmpty();
        assertThat(PublicInstagramContentParser.parse("/reel/BA/", null, storiesOnly, false)).isEmpty();
        assertThat(PublicInstagramContentParser.parse("/other/p/BA/", null, account, false)).isEmpty();
        assertThat(PublicInstagramContentParser.parse("/p/BA/", null, account, true)).isEmpty();
    }
}
