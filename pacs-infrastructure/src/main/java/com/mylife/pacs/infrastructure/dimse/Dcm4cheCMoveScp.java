package com.mylife.pacs.infrastructure.dimse;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.infrastructure.storage.ObjectStorageService;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.Dimse;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCMoveSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.dcm4che3.net.service.InstanceLocator;
import org.dcm4che3.net.service.RetrieveTask;
import org.springframework.stereotype.Component;

import java.util.LinkedHashSet;
import java.util.List;

@Component
public class Dcm4cheCMoveScp extends BasicCMoveSCP {

    private final DicomApplicationService dicomApplicationService;
    private final AetApplicationService aetApplicationService;
    private final Dcm4cheAssociationFactory associationFactory;
    private final ObjectStorageService objectStorageService;
    private final DicomPacsProperties properties;

    public Dcm4cheCMoveScp(
            DicomApplicationService dicomApplicationService,
            AetApplicationService aetApplicationService,
            Dcm4cheAssociationFactory associationFactory,
            ObjectStorageService objectStorageService,
            DicomPacsProperties properties
    ) {
        super(UID.StudyRootQueryRetrieveInformationModelMove);
        this.dicomApplicationService = dicomApplicationService;
        this.aetApplicationService = aetApplicationService;
        this.associationFactory = associationFactory;
        this.objectStorageService = objectStorageService;
        this.properties = properties;
    }

    @Override
    protected RetrieveTask calculateMatches(Association association, PresentationContext presentationContext, Attributes request, Attributes keys)
            throws DicomServiceException {
        if (!properties.getScp().isMoveEnabled()) {
            throw new DicomServiceException(Status.SOPclassNotSupported, "C-MOVE SCP is disabled");
        }

        String destinationAet = request.getString(Tag.MoveDestination);
        if (destinationAet == null || destinationAet.isBlank()) {
            throw new DicomServiceException(Status.MoveDestinationUnknown, "Missing MoveDestination");
        }

        AetNode destination;
        try {
            destination = aetApplicationService.findByAet(destinationAet);
        } catch (RuntimeException exception) {
            throw new DicomServiceException(Status.MoveDestinationUnknown, exception).setErrorComment(exception.getMessage());
        }

        List<PacsInstance> instances = dicomApplicationService.findInstances(new InstanceQueryCriteria(
                keys.getString(Tag.StudyInstanceUID),
                keys.getString(Tag.SeriesInstanceUID),
                keys.getString(Tag.SOPInstanceUID)
        ));
        if (instances.isEmpty()) {
            return new NoOpRetrieveTask(association, presentationContext, request);
        }

        List<InstanceLocator> locators = instances.stream()
                .map(this::toLocator)
                .toList();

        Dcm4cheAssociationContext storeContext = associationFactory.openAssociation(
                association.getCalledAET(),
                destination.host(),
                destination.port(),
                destination.aet(),
                associateRQ -> {
                    LinkedHashSet<String> negotiated = new LinkedHashSet<>();
                    for (InstanceLocator locator : locators) {
                        String key = locator.cuid + "|" + locator.tsuid;
                        if (negotiated.add(key)) {
                            associateRQ.addPresentationContextFor(locator.cuid, locator.tsuid);
                        }
                    }
                }
        );

        return new LocalMoveTask(
                Dimse.C_MOVE_RQ,
                association,
                presentationContext,
                request,
                locators,
                storeContext,
                objectStorageService
        );
    }

    private InstanceLocator toLocator(PacsInstance instance) {
        String transferSyntaxUid = instance.transferSyntaxUid() == null || instance.transferSyntaxUid().isBlank()
                ? UID.ExplicitVRLittleEndian
                : instance.transferSyntaxUid();
        return new InstanceLocator(instance.sopClassUid(), instance.sopInstanceUid(), transferSyntaxUid, instance.storageKey())
                .setObject(instance);
    }
}
