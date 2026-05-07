package com.mylife.pacs.domain.service;

import com.mylife.pacs.domain.model.AetNode;

import java.util.List;

public interface AetManager {

    AetNode register(AetNode node);

    AetNode update(AetNode node);

    AetNode findByAet(String aet);

    List<AetNode> listAll();

    List<AetNode> listEnabled();

    void remove(String aet);
}
