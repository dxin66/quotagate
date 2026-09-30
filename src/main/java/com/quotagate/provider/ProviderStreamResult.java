package com.quotagate.provider;

import com.quotagate.api.dto.ChatResponse;

public record ProviderStreamResult(
        String requestId,
        ChatResponse.Usage usage
) {
}
