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
public class CEchoScpHandler extends SimpleChannelInboundHandler<DicomMessage> {

    @Override
    protected void channelRead0(ChannelHandlerContext context, DicomMessage message) {
        if (message.commandType() != DicomCommandType.C_ECHO_REQUEST) {
            context.fireChannelRead(message);
            return;
        }
        context.writeAndFlush(DicomMessages.cEchoResponse(message));
    }
}
