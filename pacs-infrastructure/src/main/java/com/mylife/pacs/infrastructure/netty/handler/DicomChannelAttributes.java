package com.mylife.pacs.infrastructure.netty.handler;

import io.netty.util.AttributeKey;

final class DicomChannelAttributes {

    static final AttributeKey<Boolean> ASSOCIATED = AttributeKey.valueOf("associated");
    static final AttributeKey<String> CALLING_AET = AttributeKey.valueOf("callingAet");
    static final AttributeKey<String> CALLED_AET = AttributeKey.valueOf("calledAet");

    private DicomChannelAttributes() {
    }
}
