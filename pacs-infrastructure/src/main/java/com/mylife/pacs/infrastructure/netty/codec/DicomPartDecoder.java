package com.mylife.pacs.infrastructure.netty.codec;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToMessageDecoder;

import java.util.List;

public class DicomPartDecoder extends MessageToMessageDecoder<Object> {

    @Override
    protected void decode(ChannelHandlerContext context, Object message, List<Object> out) {
        out.add(message);
    }
}
