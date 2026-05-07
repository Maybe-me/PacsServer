package com.mylife.pacs.infrastructure.netty.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageEncoder;

import java.util.List;

public class DicomPartEncoder extends MessageToMessageEncoder<Object> {

    @Override
    protected void encode(ChannelHandlerContext context, Object message, List<Object> out) {
        out.add(message);
    }
}
