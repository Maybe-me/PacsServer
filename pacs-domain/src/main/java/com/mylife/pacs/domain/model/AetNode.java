package com.mylife.pacs.domain.model;

import java.time.Instant;

public record AetNode(
        Long id,
        String aet,
        String host,
        Integer port,
        AetRole role,
        String nodeName,
        String description,
        boolean enabled,
        Instant lastVerifiedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
