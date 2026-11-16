package com.giannisdal.shadowig.config;

import org.springframework.boot.context.properties.ConfigurationPropertiesBinding;
import org.springframework.core.convert.converter.Converter;
import org.springframework.stereotype.Component;

@Component
@ConfigurationPropertiesBinding
public class InstagramAccountConverter implements Converter<String, InstagramMonitoringProperties.AccountConfig> {

    @Override
    public InstagramMonitoringProperties.AccountConfig convert(String username) {
        return new InstagramMonitoringProperties.AccountConfig(username.trim(), true, true, true, false);
    }
}
