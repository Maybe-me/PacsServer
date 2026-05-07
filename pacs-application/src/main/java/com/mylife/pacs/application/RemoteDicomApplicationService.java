package com.mylife.pacs.application;

import com.mylife.pacs.domain.model.AetNode;

import java.util.List;
import java.util.Map;

public class RemoteDicomApplicationService {

    private final AetApplicationService aetApplicationService;
    private final RemoteDicomGateway remoteDicomGateway;

    public RemoteDicomApplicationService(
            AetApplicationService aetApplicationService,
            RemoteDicomGateway remoteDicomGateway
    ) {
        this.aetApplicationService = aetApplicationService;
        this.remoteDicomGateway = remoteDicomGateway;
    }

    public boolean echo(String targetAet, String callingAet) {
        AetNode targetNode = aetApplicationService.findByAet(targetAet);
        return remoteDicomGateway.echo(targetNode, callingAet);
    }

    public List<Map<String, String>> findStudies(String targetAet, String callingAet, Map<String, String> criteria) {
        AetNode targetNode = aetApplicationService.findByAet(targetAet);
        return remoteDicomGateway.find(targetNode, callingAet, criteriaWithLevel(criteria, "STUDY"));
    }

    public List<Map<String, String>> findSeries(String targetAet, String callingAet, Map<String, String> criteria) {
        AetNode targetNode = aetApplicationService.findByAet(targetAet);
        return remoteDicomGateway.find(targetNode, callingAet, criteriaWithLevel(criteria, "SERIES"));
    }

    public int pullFromRemote(String targetAet, String callingAet, Map<String, String> criteria, String destinationAet) {
        AetNode targetNode = aetApplicationService.findByAet(targetAet);
        return remoteDicomGateway.move(targetNode, callingAet, criteriaWithLevel(criteria, "STUDY"), destinationAet);
    }

    private Map<String, String> criteriaWithLevel(Map<String, String> criteria, String level) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>();
        if (criteria != null) {
            payload.putAll(criteria);
        }
        payload.putIfAbsent("QueryRetrieveLevel", level);
        return Map.copyOf(payload);
    }
}
