package com.mylife.pacs.infrastructure.dimse;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicQueryTask;
import org.dcm4che3.net.service.DicomServiceException;

import java.util.Iterator;
import java.util.List;

final class LocalQueryTask extends BasicQueryTask {

    private final Iterator<Attributes> results;

    LocalQueryTask(Association association, PresentationContext presentationContext, Attributes request, Attributes keys, List<Attributes> results) {
        super(association, presentationContext, request, keys);
        this.results = results.iterator();
    }

    @Override
    protected boolean hasMoreMatches() {
        return results.hasNext();
    }

    @Override
    protected Attributes nextMatch() throws DicomServiceException {
        return results.next();
    }
}
