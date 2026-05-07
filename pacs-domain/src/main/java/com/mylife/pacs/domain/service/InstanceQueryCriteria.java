package com.mylife.pacs.domain.service;

public record InstanceQueryCriteria(
        String studyInstanceUid,
        String seriesInstanceUid,
        String sopInstanceUid
) {
}
