package com.mylife.pacs.infrastructure.netty.handler;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.infrastructure.netty.DicomClientBootstrap;
import com.mylife.pacs.infrastructure.netty.message.DicomCommandType;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import com.mylife.pacs.infrastructure.netty.message.DicomMessages;
import com.mylife.pacs.infrastructure.storage.ObjectStorageService;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@ChannelHandler.Sharable
public class CMoveScpHandler extends SimpleChannelInboundHandler<DicomMessage> {

    private final DicomApplicationService dicomApplicationService;
    private final AetApplicationService aetApplicationService;
    private final DicomClientBootstrap dicomClientBootstrap;
    private final ObjectStorageService objectStorageService;

    public CMoveScpHandler(
            DicomApplicationService dicomApplicationService,
            AetApplicationService aetApplicationService,
            DicomClientBootstrap dicomClientBootstrap,
            ObjectStorageService objectStorageService
    ) {
        this.dicomApplicationService = dicomApplicationService;
        this.aetApplicationService = aetApplicationService;
        this.dicomClientBootstrap = dicomClientBootstrap;
        this.objectStorageService = objectStorageService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, DicomMessage message) {
        if (message.commandType() != DicomCommandType.C_MOVE_REQUEST) {
            context.fireChannelRead(message);
            return;
        }
        String destinationAet = firstNonBlank(
                message.attributes().get("MoveDestination"),
                message.attributes().get("00000600")
        );
        if (destinationAet == null) {
            context.writeAndFlush(DicomMessages.error(message, 400, "Missing MoveDestination"));
            return;
        }
        AetNode destination = aetApplicationService.findByAet(destinationAet);
        List<PacsInstance> instances = dicomApplicationService.findInstances(new InstanceQueryCriteria(
                firstNonBlank(message.attributes().get("studyInstanceUid"), message.attributes().get("0020000D")),
                firstNonBlank(message.attributes().get("seriesInstanceUid"), message.attributes().get("0020000E")),
                firstNonBlank(message.attributes().get("sopInstanceUid"), message.attributes().get("00080018"))
        ));
        int movedCount = 0;
        for (PacsInstance instance : instances) {
            DicomMessage response = dicomClientBootstrap.store(
                    destination.host(),
                    destination.port(),
                    message.calledAet(),
                    destination.aet(),
                    moveAttributes(instance),
                    objectStorageService.read(instance)
            );
            if (response.commandType() != DicomCommandType.C_STORE_RESPONSE || response.status() != 0) {
                context.writeAndFlush(DicomMessages.error(
                        message,
                        502,
                        "Failed to forward instance " + instance.sopInstanceUid() + ": " + response.commandType() + " / " + response.message()
                ));
                return;
            }
            movedCount++;
        }
        context.writeAndFlush(DicomMessages.cMoveResponse(message, movedCount));
    }

    private Map<String, String> moveAttributes(PacsInstance instance) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.putAll(instance.extraTags());
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

    private String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        context.writeAndFlush(DicomMessages.error(null, 400, cause.getMessage()));
    }
}
