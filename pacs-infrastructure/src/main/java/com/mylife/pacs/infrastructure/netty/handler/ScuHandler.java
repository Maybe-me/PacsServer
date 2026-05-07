package com.mylife.pacs.infrastructure.netty.handler;

import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class ScuHandler extends SimpleChannelInboundHandler<DicomMessage> {

    private final BlockingQueue<DicomMessage> responses = new LinkedBlockingQueue<>();

    @Override
    protected void channelRead0(ChannelHandlerContext context, DicomMessage message) {
        responses.offer(message);
    }

    public DicomMessage awaitResponse(Duration timeout) {
        try {
            DicomMessage response = responses.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (response == null) {
                throw new IllegalStateException("Timed out waiting for DICOM response");
            }
            return response;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for DICOM response", exception);
        }
    }
}
