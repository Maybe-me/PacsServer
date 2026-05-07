package com.mylife.pacs.infrastructure.sync;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.common.sync.SyncMetadataKeys;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.infrastructure.netty.DicomClientBootstrap;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import com.mylife.pacs.infrastructure.storage.ObjectStorageService;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class RemoteDicomPushService {

    private final AetApplicationService aetApplicationService;
    private final DicomApplicationService dicomApplicationService;
    private final DicomClientBootstrap dicomClientBootstrap;
    private final ObjectStorageService objectStorageService;
    private final DicomPacsProperties properties;

    public RemoteDicomPushService(
            AetApplicationService aetApplicationService,
            DicomApplicationService dicomApplicationService,
            DicomClientBootstrap dicomClientBootstrap,
            ObjectStorageService objectStorageService,
            DicomPacsProperties properties
    ) {
        this.aetApplicationService = aetApplicationService;
        this.dicomApplicationService = dicomApplicationService;
        this.dicomClientBootstrap = dicomClientBootstrap;
        this.objectStorageService = objectStorageService;
        this.properties = properties;
    }

    public PushResult pushToRemote(
            String targetAet,
            Map<String, String> criteria,
            boolean skipRemoteDuplicates,
            boolean preventLoopToSource
    ) {
        return pushToRemote(targetAet, criteria, new PushOptions(skipRemoteDuplicates, preventLoopToSource, 0, List.of(), List.of()));
    }

    public PushResult pushToRemote(
            String targetAet,
            Map<String, String> criteria,
            PushOptions options
    ) {
        AetNode targetNode = aetApplicationService.findByAet(targetAet);
        List<PacsInstance> instances = dicomApplicationService.findInstances(new InstanceQueryCriteria(
                criteria == null ? null : criteria.get("0020000D"),
                criteria == null ? null : criteria.get("0020000E"),
                criteria == null ? null : criteria.get("00080018")
        ));

        PushOptions normalizedOptions = options == null ? new PushOptions(true, true, 0, List.of(), List.of()) : options.normalize();
        int pushedCount = 0;
        int duplicateSkipCount = 0;
        int sourceLoopSkipCount = 0;
        for (PacsInstance instance : instances) {
            if (normalizedOptions.maxInstancesToPush() > 0 && pushedCount >= normalizedOptions.maxInstancesToPush()) {
                break;
            }
            if (shouldSkipForSourcePolicy(instance, targetNode.aet(), normalizedOptions)) {
                sourceLoopSkipCount++;
                continue;
            }
            DicomMessage response = dicomClientBootstrap.store(
                    targetNode.host(),
                    targetNode.port(),
                    properties.getLocalAet(),
                    targetNode.aet(),
                    buildStoreAttributes(instance),
                    objectStorageService.read(instance)
            );
            if (response.status() == 0) {
                pushedCount++;
                continue;
            }
            if (normalizedOptions.skipRemoteDuplicates() && isDuplicateResponse(response)) {
                duplicateSkipCount++;
                continue;
            }
            throw new IllegalStateException("Remote C-STORE failed for instance " + instance.sopInstanceUid() + ": " + response.message());
        }
        return new PushResult(instances.size(), pushedCount, duplicateSkipCount, sourceLoopSkipCount);
    }

    private boolean shouldSkipForSourcePolicy(PacsInstance instance, String targetAet, PushOptions options) {
        String sourceAet = instance.extraTags().get(SyncMetadataKeys.SOURCE_AET);
        if (sourceAet == null || sourceAet.isBlank()) {
            return false;
        }
        if (options.preventLoopToSource() && sourceAet.equalsIgnoreCase(targetAet)) {
            return true;
        }
        Set<String> blockList = normalize(options.sourceAetBlockList());
        if (blockList.contains(sourceAet.toUpperCase())) {
            return true;
        }
        Set<String> allowList = normalize(options.sourceAetAllowList());
        return !allowList.isEmpty() && !allowList.contains(sourceAet.toUpperCase());
    }

    private boolean isDuplicateResponse(DicomMessage response) {
        String errorComment = response.attributes().getOrDefault("errorComment", "");
        String message = response.message() == null ? "" : response.message();
        return errorComment.contains("Duplicate SOP Instance UID") || message.contains("Duplicate SOP Instance UID");
    }

    private Map<String, String> buildStoreAttributes(PacsInstance instance) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>(instance.extraTags());
        attributes.put("00080018", instance.sopInstanceUid());
        if (instance.sopClassUid() != null) {
            attributes.put("00080016", instance.sopClassUid());
        }
        if (instance.transferSyntaxUid() != null) {
            attributes.put("00020010", instance.transferSyntaxUid());
        }
        if (instance.instanceNumber() != null) {
            attributes.put("00200013", Integer.toString(instance.instanceNumber()));
        }
        return Map.copyOf(attributes);
    }

    public record PushResult(
            int totalCandidates,
            int pushedCount,
            int duplicateSkipCount,
            int sourceLoopSkipCount
    ) {
    }

    public record PushOptions(
            boolean skipRemoteDuplicates,
            boolean preventLoopToSource,
            int maxInstancesToPush,
            List<String> sourceAetAllowList,
            List<String> sourceAetBlockList
    ) {
        PushOptions normalize() {
            return new PushOptions(
                    skipRemoteDuplicates,
                    preventLoopToSource,
                    Math.max(0, maxInstancesToPush),
                    sourceAetAllowList == null ? List.of() : List.copyOf(sourceAetAllowList),
                    sourceAetBlockList == null ? List.of() : List.copyOf(sourceAetBlockList)
            );
        }
    }

    private Set<String> normalize(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Set.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(value -> value.trim().toUpperCase())
                .collect(Collectors.toUnmodifiableSet());
    }
}
