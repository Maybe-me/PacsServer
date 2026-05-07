package com.mylife.pacs.infrastructure.netty.codec;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageDecoder;

import java.util.List;

public class DicomMessageDecoder extends ByteToMessageDecoder {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();

    @Override
    protected void decode(ChannelHandlerContext context, ByteBuf in, List<Object> out) throws Exception {
        if (in.readableBytes() < Integer.BYTES) {
            return;
        }
        in.markReaderIndex();
        int length = in.readInt();
        if (in.readableBytes() < length) {
            in.resetReaderIndex();
            return;
        }
        byte[] payload = new byte[length];
        in.readBytes(payload);
        out.add(objectMapper.readValue(payload, DicomMessage.class));
    }
}
