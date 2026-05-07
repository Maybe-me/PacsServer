package com.mylife.pacs.infrastructure.persistence;

import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.repository.AetNodeRepository;
import com.mylife.pacs.infrastructure.persistence.entity.AetNodeEntity;
import com.mylife.pacs.infrastructure.persistence.springdata.AetNodeEntityRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
@Transactional
public class JpaAetNodeRepository implements AetNodeRepository {

    private final AetNodeEntityRepository repository;

    public JpaAetNodeRepository(AetNodeEntityRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean existsByAet(String aet) {
        return repository.existsByAet(aet);
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<AetNode> findByAet(String aet) {
        return repository.findByAet(aet).map(PersistenceMapper::toDomain);
    }

    @Override
    public AetNode save(AetNode node) {
        AetNodeEntity entity = node.id() == null
                ? new AetNodeEntity()
                : repository.findById(node.id()).orElseGet(AetNodeEntity::new);
        entity.setId(node.id());
        entity.setAet(node.aet());
        entity.setHost(node.host());
        entity.setPort(node.port());
        entity.setRole(node.role());
        entity.setNodeName(node.nodeName());
        entity.setDescription(node.description());
        entity.setEnabled(node.enabled());
        entity.setLastVerifiedAt(node.lastVerifiedAt());
        return PersistenceMapper.toDomain(repository.save(entity));
    }

    @Override
    @Transactional(readOnly = true)
    public List<AetNode> findAll() {
        return repository.findAll().stream().map(PersistenceMapper::toDomain).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<AetNode> findEnabled() {
        return repository.findByEnabledTrue().stream().map(PersistenceMapper::toDomain).toList();
    }

    @Override
    public void deleteByAet(String aet) {
        repository.deleteByAet(aet);
    }
}
