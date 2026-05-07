package com.mylife.pacs.infrastructure.dimse;

import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.infrastructure.storage.ObjectStorageService;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.DataWriter;
import org.dcm4che3.net.DataWriterAdapter;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicRetrieveTask;
import org.dcm4che3.net.service.InstanceLocator;

import java.util.List;

final class LocalMoveTask extends BasicRetrieveTask<InstanceLocator> {

    private final ObjectStorageService objectStorageService;
    private final Dcm4cheAssociationContext storeContext;

    LocalMoveTask(
            Dimse dimse,
            Association requestAssociation,
            PresentationContext presentationContext,
            org.dcm4che3.data.Attributes requestCommand,
            List<InstanceLocator> instances,
            Dcm4cheAssociationContext storeContext,
            ObjectStorageService objectStorageService
    ) {
        super(dimse, requestAssociation, presentationContext, requestCommand, instances, storeContext.association());
        this.objectStorageService = objectStorageService;
        this.storeContext = storeContext;
    }

    @Override
    protected String selectTransferSyntaxFor(Association storeAssociation, InstanceLocator locator) {
        if (storeAssociation.getTransferSyntaxesFor(locator.cuid).contains(locator.tsuid)) {
            return locator.tsuid;
        }
        throw new IllegalStateException("Destination does not accept transfer syntax " + locator.tsuid + " for SOP class " + locator.cuid);
    }

    @Override
    protected DataWriter createDataWriter(InstanceLocator locator, String transferSyntaxUid) {
        PacsInstance instance = (PacsInstance) locator.getObject();
        byte[] payload = objectStorageService.read(instance);
        PreparedDicomObject prepared = PreparedDicomObject.fromStoredFile(payload, locator.cuid, locator.iuid, transferSyntaxUid);
        return DataWriterAdapter.forAttributes(prepared.dataset());
    }

    @Override
    protected void releaseStoreAssociation(Association association) {
        storeContext.close();
    }
}
