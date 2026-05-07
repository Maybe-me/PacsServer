package com.mylife.pacs.application;

import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.service.AetManager;

import java.util.List;

public class AetApplicationService {

    private final AetManager aetManager;

    public AetApplicationService(AetManager aetManager) {
        this.aetManager = aetManager;
    }

    public AetNode register(AetNode node) {
        return aetManager.register(node);
    }

    public AetNode update(AetNode node) {
        return aetManager.update(node);
    }

    public AetNode findByAet(String aet) {
        return aetManager.findByAet(aet);
    }

    public List<AetNode> listAll() {
        return aetManager.listAll();
    }

    public List<AetNode> listEnabled() {
        return aetManager.listEnabled();
    }

    public void remove(String aet) {
        aetManager.remove(aet);
    }
}
