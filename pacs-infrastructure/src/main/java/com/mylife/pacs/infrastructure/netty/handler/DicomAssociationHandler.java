package com.mylife.pacs.infrastructure.netty.handler;

import com.mylife.pacs.infrastructure.netty.message.DicomCommandType;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import com.mylife.pacs.infrastructure.netty.message.DicomMessages;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

@Component
@ChannelHandler.Sharable
public class DicomAssociationHandler extends SimpleChannelInboundHandler<DicomMessage> {

    private final AetAssociationPolicy associationPolicy;

    public DicomAssociationHandler(AetAssociationPolicy associationPolicy) {
        this.associationPolicy = associationPolicy;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, DicomMessage message) {
        if (message.commandType() == DicomCommandType.ASSOCIATE_REQUEST) {
            handleAssociation(context, message);
            return;
        }
        if (!Boolean.TRUE.equals(context.channel().attr(DicomChannelAttributes.ASSOCIATED).get())) {
            context.writeAndFlush(DicomMessages.error(message, 400, "Association required"));
            return;
        }
        context.fireChannelRead(message);
    }

    private void handleAssociation(ChannelHandlerContext context, DicomMessage message) {
        if (!associationPolicy.accepts(message.callingAet(), message.calledAet())) {
            context.writeAndFlush(DicomMessages.associateReject(message, 403, "Association rejected"))
                    .addListener(future -> context.close());
            return;
        }
        context.channel().attr(DicomChannelAttributes.ASSOCIATED).set(Boolean.TRUE);
        context.channel().attr(DicomChannelAttributes.CALLING_AET).set(message.callingAet());
        context.channel().attr(DicomChannelAttributes.CALLED_AET).set(message.calledAet());
        context.writeAndFlush(DicomMessages.associateAccept(message));
    }
}
