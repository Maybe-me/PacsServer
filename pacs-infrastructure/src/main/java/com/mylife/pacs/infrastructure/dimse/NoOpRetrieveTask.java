package com.mylife.pacs.infrastructure.dimse;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Commands;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.RetrieveTask;

import java.io.IOException;

final class NoOpRetrieveTask implements RetrieveTask {

    private final Association association;
    private final PresentationContext presentationContext;
    private final Attributes request;

    NoOpRetrieveTask(Association association, PresentationContext presentationContext, Attributes request) {
        this.association = association;
        this.presentationContext = presentationContext;
        this.request = request;
    }

    @Override
    public void run() {
        Attributes response = Commands.mkCMoveRSP(request, Status.Success);
        Commands.initNumberOfSuboperations(response, 0);
        try {
            association.writeDimseRSP(presentationContext, response);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to send empty C-MOVE response", exception);
        }
    }

    @Override
    public void onCancelRQ(Association association) {
    }
}
