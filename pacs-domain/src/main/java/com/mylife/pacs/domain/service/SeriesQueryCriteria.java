package com.mylife.pacs.domain.service;

public record SeriesQueryCriteria(
        String studyInstanceUid,
        String seriesInstanceUid,
        String modality
) {
}
