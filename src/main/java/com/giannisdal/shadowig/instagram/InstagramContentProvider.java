package com.giannisdal.shadowig.instagram;

import com.giannisdal.shadowig.config.InstagramMonitoringProperties;

public interface InstagramContentProvider {
    InstagramProviderResult fetchLatest(InstagramMonitoringProperties.AccountConfig account);
}
