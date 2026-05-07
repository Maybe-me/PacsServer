package com.mylife.pacs.infrastructure.netty.message;

public enum DicomCommandType {
    ASSOCIATE_REQUEST,
    ASSOCIATE_ACCEPT,
    ASSOCIATE_REJECT,
    C_ECHO_REQUEST,
    C_ECHO_RESPONSE,
    C_STORE_REQUEST,
    C_STORE_RESPONSE,
    C_FIND_REQUEST,
    C_FIND_RESPONSE,
    C_MOVE_REQUEST,
    C_MOVE_RESPONSE,
    ERROR
}
