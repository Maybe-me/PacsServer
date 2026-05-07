package com.mylife.pacs.infrastructure.dimse;

import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.common.sync.SyncMetadataKeys;
import com.mylife.pacs.infrastructure.dicom.AttributesConverter;
import com.mylife.pacs.infrastructure.storage.ObjectStorageService;
import com.mylife.pacs.infrastructure.storage.StoredFile;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;
import org.dcm4che3.net.Association;
import org.dcm4che3.net.PDVInputStream;
import org.dcm4che3.net.Status;
import org.dcm4che3.net.pdu.PresentationContext;
import org.dcm4che3.net.service.BasicCStoreSCP;
import org.dcm4che3.net.service.DicomServiceException;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class Dcm4cheCStoreScp extends BasicCStoreSCP {

    private final DicomApplicationService dicomApplicationService;
    private final ObjectStorageService objectStorageService;
    private final AttributesConverter attributesConverter;
    private final DicomPacsProperties properties;

    public Dcm4cheCStoreScp(
            DicomApplicationService dicomApplicationService,
            ObjectStorageService objectStorageService,
            AttributesConverter attributesConverter,
            DicomPacsProperties properties
    ) {
        super(StorageSopClasses.all());
        this.dicomApplicationService = dicomApplicationService;
        this.objectStorageService = objectStorageService;
        this.attributesConverter = attributesConverter;
        this.properties = properties;
    }

    @Override
    protected void store(Association association, PresentationContext presentationContext, Attributes request, PDVInputStream data, Attributes response)
            throws IOException {
        if (!properties.getScp().isStoreEnabled()) {
            throw new DicomServiceException(Status.SOPclassNotSupported, "C-STORE SCP is disabled");
        }

        try (DicomInputStream inputStream = new DicomInputStream(data, presentationContext.getTransferSyntax())) {
            Attributes dataset = inputStream.readDataset(-1, -1);
            String sopInstanceUid = dataset.getString(Tag.SOPInstanceUID, request.getString(Tag.AffectedSOPInstanceUID));
            String sopClassUid = dataset.getString(Tag.SOPClassUID, request.getString(Tag.AffectedSOPClassUID));
            Attributes fileMetaInformation = Attributes.createFileMetaInformation(sopInstanceUid, sopClassUid, presentationContext.getTransferSyntax());
            byte[] payload = toPayload(fileMetaInformation, dataset, presentationContext.getTransferSyntax());
            Map<String, String> attributeMap = normalizeAttributes(
                    withSourceMetadata(
                            withTransferSyntax(
                                    Dcm4cheAttributesBridge.toAttributeMap(dataset),
                                    presentationContext.getTransferSyntax()
                            ),
                            association
                    )
            );
            StoredFile storedFile = objectStorageService.store(attributeMap, payload);
            dicomApplicationService.store(attributesConverter.toStoreRequest(attributeMap, storedFile));
            response.setInt(Tag.Status, org.dcm4che3.data.VR.US, Status.Success);
        } catch (RuntimeException exception) {
            throw new DicomServiceException(Status.ProcessingFailure, exception).setErrorComment(exception.getMessage());
        }
    }

    private byte[] toPayload(Attributes fileMetaInformation, Attributes dataset, String transferSyntaxUid) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (DicomOutputStream dicomOutputStream = new DicomOutputStream(outputStream, UID.ExplicitVRLittleEndian)) {
            dicomOutputStream.writeDataset(fileMetaInformation, dataset);
        }
        return outputStream.toByteArray();
    }

    private Map<String, String> withTransferSyntax(Map<String, String> attributeMap, String transferSyntaxUid) {
        LinkedHashMap<String, String> enriched = new LinkedHashMap<>(attributeMap);
        enriched.put("00020010", transferSyntaxUid);
        enriched.put("transferSyntaxUid", transferSyntaxUid);
        return Map.copyOf(enriched);
    }

    private Map<String, String> withSourceMetadata(Map<String, String> attributeMap, Association association) {
        LinkedHashMap<String, String> enriched = new LinkedHashMap<>(attributeMap);
        String callingAet = association.getCallingAET();
        if (callingAet != null && !callingAet.isBlank()) {
            enriched.put(SyncMetadataKeys.SOURCE_AET, callingAet);
            enriched.put(SyncMetadataKeys.SOURCE_TYPE, SyncMetadataKeys.SOURCE_TYPE_DIMSE_STORE);
        }
        return Map.copyOf(enriched);
    }

    private Map<String, String> normalizeAttributes(Map<String, String> attributeMap) {
        LinkedHashMap<String, String> normalized = new LinkedHashMap<>(attributeMap);
        String studyInstanceUid = firstNonBlank(normalized.get("studyInstanceUid"), normalized.get("0020000D"));
        if (studyInstanceUid == null) {
            studyInstanceUid = generatedUid("study", normalized);
            normalized.put("studyInstanceUid", studyInstanceUid);
            normalized.put("0020000D", studyInstanceUid);
        }
        String seriesInstanceUid = firstNonBlank(normalized.get("seriesInstanceUid"), normalized.get("0020000E"));
        if (seriesInstanceUid == null) {
            seriesInstanceUid = generatedUid("series:" + studyInstanceUid, normalized);
            normalized.put("seriesInstanceUid", seriesInstanceUid);
            normalized.put("0020000E", seriesInstanceUid);
        }
        String patientId = firstNonBlank(normalized.get("patientId"), normalized.get("00100020"));
        if (patientId == null) {
            patientId = generatedPatientId(normalized, studyInstanceUid);
            normalized.put("patientId", patientId);
            normalized.put("00100020", patientId);
        }
        return Map.copyOf(normalized);
    }

    private String generatedPatientId(Map<String, String> attributeMap, String studyInstanceUid) {
        String seed = firstNonBlank(
                studyInstanceUid,
                attributeMap.get("sopInstanceUid"),
                attributeMap.get("00080018"),
                UUID.randomUUID().toString()
        );
        UUID generated = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        return "AUTO-" + HexFormat.of().formatHex(asBytes(generated));
    }

    private String generatedUid(String namespace, Map<String, String> attributeMap) {
        String seed = namespace + ":" + firstNonBlank(
                attributeMap.get("studyInstanceUid"),
                attributeMap.get("0020000D"),
                attributeMap.get("seriesInstanceUid"),
                attributeMap.get("0020000E"),
                attributeMap.get("sopInstanceUid"),
                attributeMap.get("00080018"),
                UUID.randomUUID().toString()
        );
        UUID generated = UUID.nameUUIDFromBytes(seed.getBytes(StandardCharsets.UTF_8));
        return "2.25." + new BigInteger(1, asBytes(generated));
    }

    private byte[] asBytes(UUID uuid) {
        java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        return buffer.array();
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }
}
