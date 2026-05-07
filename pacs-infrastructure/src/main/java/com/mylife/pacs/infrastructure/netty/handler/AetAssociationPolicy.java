package com.mylife.pacs.infrastructure.netty.handler;

import com.mylife.pacs.common.config.DicomPacsProperties;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class AetAssociationPolicy {

    private final DicomPacsProperties properties;

    public AetAssociationPolicy(DicomPacsProperties properties) {
        this.properties = properties;
    }

    public boolean accepts(String callingAet, String calledAet) {
        if (callingAet == null || callingAet.isBlank() || calledAet == null || calledAet.isBlank()) {
            return false;
        }
        if (!properties.getLocalAet().equals(calledAet)) {
            return false;
        }
        List<String> whitelist = properties.getSecurity().getAetWhitelist();
        return whitelist == null || whitelist.isEmpty() || whitelist.contains(callingAet);
    }
}
