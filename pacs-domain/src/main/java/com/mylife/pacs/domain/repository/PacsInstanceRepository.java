package com.mylife.pacs.domain.repository;

import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;

import java.util.List;
import java.util.Optional;

public interface PacsInstanceRepository {

    Optional<PacsInstance> findBySopInstanceUid(String sopInstanceUid);

    PacsInstance save(PacsInstance instance);

    List<PacsInstance> search(InstanceQueryCriteria criteria);
}
