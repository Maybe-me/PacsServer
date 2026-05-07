package com.mylife.pacs.infrastructure.rest.admin;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.model.AetRole;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/aets")
public class AetAdminController {

    private final AetApplicationService aetApplicationService;

    public AetAdminController(AetApplicationService aetApplicationService) {
        this.aetApplicationService = aetApplicationService;
    }

    @GetMapping
    public List<AetNode> listAll() {
        return aetApplicationService.listAll();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AetNode create(@RequestBody AetNodeRequest request) {
        return aetApplicationService.register(request.toDomain(null));
    }

    @PutMapping("/{aet}")
    public AetNode update(@PathVariable("aet") String aet, @RequestBody AetNodeRequest request) {
        AetNode existing = aetApplicationService.findByAet(aet);
        return aetApplicationService.update(request.toDomain(existing.id()));
    }

    @DeleteMapping("/{aet}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable("aet") String aet) {
        aetApplicationService.remove(aet);
    }

    public record AetNodeRequest(
            String aet,
            String host,
            Integer port,
            AetRole role,
            String nodeName,
            String description,
            Boolean enabled
    ) {
        public AetNode toDomain(Long id) {
            return new AetNode(
                    id,
                    aet,
                    host,
                    port,
                    role == null ? AetRole.REMOTE : role,
                    nodeName,
                    description,
                    enabled == null || enabled,
                    null,
                    null,
                    null
            );
        }
    }
}
