package com.mylife.pacs.domain.repository;

import com.mylife.pacs.domain.model.AetNode;

import java.util.List;
import java.util.Optional;

public interface AetNodeRepository {

    boolean existsByAet(String aet);

    Optional<AetNode> findByAet(String aet);

    AetNode save(AetNode node);

    List<AetNode> findAll();

    List<AetNode> findEnabled();

    void deleteByAet(String aet);
}
