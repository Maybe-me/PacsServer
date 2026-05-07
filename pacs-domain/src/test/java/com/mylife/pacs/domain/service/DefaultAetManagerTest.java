package com.mylife.pacs.domain.service;

import com.mylife.pacs.common.exception.PacsException;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.model.AetRole;
import com.mylife.pacs.domain.repository.AetNodeRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DefaultAetManagerTest {

    private final InMemoryAetNodeRepository repository = new InMemoryAetNodeRepository();
    private final DefaultAetManager manager = new DefaultAetManager(repository);

    @Test
    void shouldFindRegisteredNode() {
        manager.register(node("LOCAL_A"));

        assertThat(manager.findByAet("LOCAL_A").aet()).isEqualTo("LOCAL_A");
    }

    @Test
    void shouldRejectDuplicateAet() {
        manager.register(node("LOCAL_A"));

        assertThatThrownBy(() -> manager.register(node("LOCAL_A")))
                .isInstanceOf(PacsException.class)
                .hasMessageContaining("AE Title already exists");
    }

    private AetNode node(String aet) {
        return new AetNode(null, aet, "127.0.0.1", 11112, AetRole.BOTH, "Local", null, true, Instant.now(), null, null);
    }

    private static final class InMemoryAetNodeRepository implements AetNodeRepository {
        private final AtomicLong ids = new AtomicLong();
        private final Map<String, AetNode> nodes = new LinkedHashMap<>();

        @Override
        public boolean existsByAet(String aet) {
            return nodes.containsKey(aet);
        }

        @Override
        public Optional<AetNode> findByAet(String aet) {
            return Optional.ofNullable(nodes.get(aet));
        }

        @Override
        public AetNode save(AetNode node) {
            AetNode saved = new AetNode(
                    node.id() == null ? ids.incrementAndGet() : node.id(),
                    node.aet(),
                    node.host(),
                    node.port(),
                    node.role(),
                    node.nodeName(),
                    node.description(),
                    node.enabled(),
                    node.lastVerifiedAt(),
                    Instant.now(),
                    Instant.now()
            );
            nodes.put(saved.aet(), saved);
            return saved;
        }

        @Override
        public List<AetNode> findAll() {
            return nodes.values().stream().toList();
        }

        @Override
        public List<AetNode> findEnabled() {
            return nodes.values().stream().filter(AetNode::enabled).toList();
        }

        @Override
        public void deleteByAet(String aet) {
            nodes.remove(aet);
        }
    }
}
