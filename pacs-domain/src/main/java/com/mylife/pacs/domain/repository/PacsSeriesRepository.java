package com.mylife.pacs.domain.repository;

import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.service.SeriesQueryCriteria;

import java.util.List;
import java.util.Optional;

public interface PacsSeriesRepository {

    Optional<PacsSeries> findBySeriesInstanceUid(String seriesInstanceUid);

    PacsSeries save(PacsSeries series);

    List<PacsSeries> search(SeriesQueryCriteria criteria);
}
