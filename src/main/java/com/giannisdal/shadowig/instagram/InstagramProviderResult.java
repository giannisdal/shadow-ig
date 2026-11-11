package com.giannisdal.shadowig.instagram;

import com.giannisdal.shadowig.model.InstagramContentItem;
import com.giannisdal.shadowig.model.InstagramContentType;

import java.util.List;
import java.util.Set;

public record InstagramProviderResult(
        List<InstagramContentItem> items,
        Set<InstagramContentType> unsupportedTypes
) {
    public InstagramProviderResult {
        items = List.copyOf(items);
        unsupportedTypes = Set.copyOf(unsupportedTypes);
    }
}
