package com.mylife.pacs.infrastructure.netty.handler;

import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.infrastructure.dicom.AttributesConverter;
import com.mylife.pacs.infrastructure.netty.message.DicomCommandType;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import com.mylife.pacs.infrastructure.netty.message.DicomMessages;
import com.mylife.pacs.infrastructure.storage.ObjectStorageService;
import com.mylife.pacs.infrastructure.storage.StoredFile;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

import java.util.Base64;

@Component
@ChannelHandler.Sharable
public class CStoreScpHandler extends SimpleChannelInboundHandler<DicomMessage> {

    private final DicomApplicationService dicomApplicationService;
    private final ObjectStorageService objectStorageService;
    private final AttributesConverter attributesConverter;

    public CStoreScpHandler(
            DicomApplicationService dicomApplicationService,
            ObjectStorageService objectStorageService,
            AttributesConverter attributesConverter
    ) {
        this.dicomApplicationService = dicomApplicationService;
        this.objectStorageService = objectStorageService;
        this.attributesConverter = attributesConverter;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, DicomMessage message) {
        if (message.commandType() != DicomCommandType.C_STORE_REQUEST) {
            context.fireChannelRead(message);
            return;
        }
        byte[] payload = message.payloadBase64() == null ? new byte[0] : Base64.getDecoder().decode(message.payloadBase64());
        StoredFile storedFile = objectStorageService.store(message.attributes(), payload);
        dicomApplicationService.store(attributesConverter.toStoreRequest(message.attributes(), storedFile));
        context.writeAndFlush(DicomMessages.cStoreResponse(message, "Stored"));
    }
}
