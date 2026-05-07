package com.mylife.pacs.application;

import com.mylife.pacs.domain.model.AetNode;

import java.util.List;
import java.util.Map;

public interface RemoteDicomGateway {

    boolean echo(AetNode targetNode, String callingAet);

    List<Map<String, String>> find(AetNode targetNode, String callingAet, Map<String, String> criteria);

    int move(AetNode targetNode, String callingAet, Map<String, String> criteria, String destinationAet);
}
