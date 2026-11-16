package com.giannisdal.shadowig.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;

import static org.assertj.core.api.Assertions.assertThat;

class InstagramAccountListBindingTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withUserConfiguration(BindingConfiguration.class)
            .withInitializer(context -> {
                try {
                    var sources = new YamlPropertySourceLoader()
                            .load("application", new ClassPathResource("application.yml"));
                    sources.forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (java.io.IOException exception) {
                    throw new IllegalStateException(exception);
                }
            });

    @Test
    void bindsCommaSeparatedUsernamesAndTrimsWhitespace() {
        contextRunner.withPropertyValues("INSTAGRAM_USERNAMES= alpha , beta , gamma ",
                        "INSTAGRAM_USERNAME=legacy")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    var properties = context.getBean(InstagramMonitoringProperties.class);
                    assertThat(properties.enabledAccounts())
                            .extracting(InstagramMonitoringProperties.AccountConfig::username)
                            .containsExactly("alpha", "beta", "gamma");
                    assertThat(properties.accounts()).allSatisfy(account -> {
                        assertThat(account.enabled()).isTrue();
                        assertThat(account.posts()).isTrue();
                        assertThat(account.stories()).isTrue();
                        assertThat(account.reels()).isFalse();
                    });
                });
    }

    @Test
    void supportsLegacyUsernameVariable() {
        contextRunner.withPropertyValues("INSTAGRAM_USERNAME=legacy")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(InstagramMonitoringProperties.class).enabledAccounts())
                            .extracting(InstagramMonitoringProperties.AccountConfig::username)
                            .containsExactly("legacy");
                });
    }

    @Test
    void rejectsEmptyAccountList() {
        contextRunner.withPropertyValues("INSTAGRAM_USERNAMES=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(InstagramMonitoringProperties.class)
    @Import(InstagramAccountConverter.class)
    static class BindingConfiguration {
    }
}
