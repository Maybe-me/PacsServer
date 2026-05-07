package com.mylife.pacs.domain.service;

import com.mylife.pacs.common.exception.PacsException;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.repository.AetNodeRepository;

import java.util.List;

public class DefaultAetManager implements AetManager {

    private final AetNodeRepository repository;

    public DefaultAetManager(AetNodeRepository repository) {
        this.repository = repository;
    }

    @Override
    public AetNode register(AetNode node) {
        validateAet(node.aet());
        if (repository.existsByAet(node.aet())) {
            throw new PacsException("AE Title already exists: " + node.aet());
        }
        return repository.save(node);
    }

    @Override
    public AetNode update(AetNode node) {
        validateAet(node.aet());
        findByAet(node.aet());
        return repository.save(node);
    }

    @Override
    public AetNode findByAet(String aet) {
        return repository.findByAet(aet)
                .orElseThrow(() -> new PacsException("AE Title not found: " + aet));
    }

    @Override
    public List<AetNode> listAll() {
        return repository.findAll();
    }

    @Override
    public List<AetNode> listEnabled() {
        return repository.findEnabled();
    }

    @Override
    public void remove(String aet) {
        findByAet(aet);
        repository.deleteByAet(aet);
    }

    private void validateAet(String aet) {
        if (aet == null || aet.isBlank()) {
            throw new PacsException("AE Title must not be blank");
        }
        if (aet.length() > 16) {
            throw new PacsException("AE Title must be 16 characters or fewer: " + aet);
        }
    }
}
