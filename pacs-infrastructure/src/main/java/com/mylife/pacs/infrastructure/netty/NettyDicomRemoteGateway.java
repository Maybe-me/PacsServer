package com.mylife.pacs.infrastructure.netty;

import com.mylife.pacs.application.RemoteDicomGateway;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.infrastructure.netty.message.DicomCommandType;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class NettyDicomRemoteGateway implements RemoteDicomGateway {

    private final DicomClientBootstrap dicomClientBootstrap;

    public NettyDicomRemoteGateway(DicomClientBootstrap dicomClientBootstrap) {
        this.dicomClientBootstrap = dicomClientBootstrap;
    }

    @Override
    public boolean echo(AetNode targetNode, String callingAet) {
        return dicomClientBootstrap.echo(targetNode.host(), targetNode.port(), callingAet, targetNode.aet());
    }

    @Override
    public List<Map<String, String>> find(AetNode targetNode, String callingAet, Map<String, String> criteria) {
        DicomMessage response = dicomClientBootstrap.find(
                targetNode.host(),
                targetNode.port(),
                callingAet,
                targetNode.aet(),
                criteria
        );
        if (response.commandType() != DicomCommandType.C_FIND_RESPONSE || response.status() != 0) {
            throw new IllegalStateException("Remote C-FIND failed: " + response.commandType() + " / " + response.message());
        }
        return response.results();
    }

    @Override
    public int move(AetNode targetNode, String callingAet, Map<String, String> criteria, String destinationAet) {
        java.util.LinkedHashMap<String, String> payload = new java.util.LinkedHashMap<>(criteria);
        payload.put("MoveDestination", destinationAet);
        DicomMessage response = dicomClientBootstrap.move(
                targetNode.host(),
                targetNode.port(),
                callingAet,
                targetNode.aet(),
                payload
        );
        if (response.commandType() != DicomCommandType.C_MOVE_RESPONSE || response.status() != 0) {
            throw new IllegalStateException("Remote C-MOVE failed: " + response.commandType() + " / " + response.message());
        }
        return Integer.parseInt(response.attributes().getOrDefault("movedCount", "0"));
    }
}
