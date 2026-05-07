package com.mylife.pacs.infrastructure.netty.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;

public class DicomMessageEncoder extends MessageToByteEncoder<DicomMessage> {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    protected void encode(ChannelHandlerContext context, DicomMessage message, ByteBuf out) throws Exception {
        byte[] payload = objectMapper.writeValueAsBytes(message);
        out.writeInt(payload.length);
        out.writeBytes(payload);
    }
}
