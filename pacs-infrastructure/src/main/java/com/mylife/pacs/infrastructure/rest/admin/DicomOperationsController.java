package com.mylife.pacs.infrastructure.rest.admin;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.application.RemoteDicomApplicationService;
import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.infrastructure.sync.RemoteDicomPushService;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/dicom")
public class DicomOperationsController {

    private final RemoteDicomApplicationService remoteDicomApplicationService;
    private final DicomApplicationService dicomApplicationService;
    private final AetApplicationService aetApplicationService;
    private final RemoteDicomPushService remoteDicomPushService;
    private final DicomPacsProperties properties;

    public DicomOperationsController(
            RemoteDicomApplicationService remoteDicomApplicationService,
            DicomApplicationService dicomApplicationService,
            AetApplicationService aetApplicationService,
            RemoteDicomPushService remoteDicomPushService,
            DicomPacsProperties properties
    ) {
        this.remoteDicomApplicationService = remoteDicomApplicationService;
        this.dicomApplicationService = dicomApplicationService;
        this.aetApplicationService = aetApplicationService;
        this.remoteDicomPushService = remoteDicomPushService;
        this.properties = properties;
    }

    @PostMapping("/echo")
    public Map<String, Object> echo(@RequestBody TargetAetRequest request) {
        return Map.of(
                "targetAet", request.targetAet(),
                "reachable", remoteDicomApplicationService.echo(request.targetAet(), properties.getLocalAet())
        );
    }

    @PostMapping("/remote-find/studies")
    public List<Map<String, String>> findStudies(@RequestBody RemoteQueryRequest request) {
        return remoteDicomApplicationService.findStudies(request.targetAet(), properties.getLocalAet(), request.criteria());
    }

    @PostMapping("/remote-find/series")
    public List<Map<String, String>> findSeries(@RequestBody RemoteQueryRequest request) {
        return remoteDicomApplicationService.findSeries(request.targetAet(), properties.getLocalAet(), request.criteria());
    }

    @PostMapping("/remote-pull")
    public Map<String, Object> pullFromRemote(@RequestBody RemotePullRequest request) {
        int movedCount = remoteDicomApplicationService.pullFromRemote(
                request.targetAet(),
                properties.getLocalAet(),
                request.criteria(),
                request.destinationAet() == null || request.destinationAet().isBlank() ? properties.getLocalAet() : request.destinationAet()
        );
        return Map.of(
                "targetAet", request.targetAet(),
                "destinationAet", request.destinationAet() == null || request.destinationAet().isBlank() ? properties.getLocalAet() : request.destinationAet(),
                "movedCount", movedCount
        );
    }

    @PostMapping("/remote-push")
    public Map<String, Object> pushToRemote(@RequestBody RemotePushRequest request) {
        RemoteDicomPushService.PushResult pushResult = remoteDicomPushService.pushToRemote(
                request.targetAet(),
                request.criteria(),
                false,
                false
        );

        return Map.of(
                "targetAet", request.targetAet(),
                "pushedCount", pushResult.pushedCount()
        );
    }

    public record TargetAetRequest(String targetAet) {
    }

    public record RemoteQueryRequest(String targetAet, Map<String, String> criteria) {
    }

    public record RemotePullRequest(String targetAet, Map<String, String> criteria, String destinationAet) {
    }

    public record RemotePushRequest(String targetAet, Map<String, String> criteria) {
    }

}
