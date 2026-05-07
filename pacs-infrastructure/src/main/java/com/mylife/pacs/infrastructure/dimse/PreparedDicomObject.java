package com.mylife.pacs.infrastructure.dimse;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;

public final class PreparedDicomObject {

    private final Attributes dataset;
    private final String sopClassUid;
    private final String sopInstanceUid;
    private final String transferSyntaxUid;

    private PreparedDicomObject(Attributes dataset, String sopClassUid, String sopInstanceUid, String transferSyntaxUid) {
        this.dataset = dataset;
        this.sopClassUid = sopClassUid;
        this.sopInstanceUid = sopInstanceUid;
        this.transferSyntaxUid = transferSyntaxUid;
    }

    public static PreparedDicomObject fromPayload(Map<String, String> attributes, byte[] payload) {
        try (DicomInputStream inputStream = new DicomInputStream(new ByteArrayInputStream(payload))) {
            Attributes fileMetaInformation = inputStream.readFileMetaInformation();
            Attributes dataset = inputStream.readDataset(-1, -1);
            String transferSyntaxUid = fileMetaInformation != null
                    ? fileMetaInformation.getString(Tag.TransferSyntaxUID, UID.ExplicitVRLittleEndian)
                    : firstNonBlank(attribute(attributes, "transferSyntaxUid", "00020010"), UID.ExplicitVRLittleEndian);
            mergeMissingValues(dataset, attributes);
            return new PreparedDicomObject(
                    dataset,
                    firstNonBlank(dataset.getString(Tag.SOPClassUID), attribute(attributes, "sopClassUid", "00080016")),
                    firstNonBlank(dataset.getString(Tag.SOPInstanceUID), attribute(attributes, "sopInstanceUid", "00080018")),
                    transferSyntaxUid
            );
        } catch (IOException exception) {
            Attributes dataset = Dcm4cheAttributesBridge.toDataset(attributes);
            dataset.setBytes(Tag.PixelData, VR.OB, payload == null ? new byte[0] : payload);
            String transferSyntaxUid = firstNonBlank(attribute(attributes, "transferSyntaxUid", "00020010"), UID.ExplicitVRLittleEndian);
            return new PreparedDicomObject(
                    dataset,
                    attribute(attributes, "sopClassUid", "00080016"),
                    attribute(attributes, "sopInstanceUid", "00080018"),
                    transferSyntaxUid
            );
        }
    }

    public static PreparedDicomObject fromStoredFile(byte[] payload, String sopClassUid, String sopInstanceUid, String transferSyntaxUid) {
        try (DicomInputStream inputStream = new DicomInputStream(new ByteArrayInputStream(payload))) {
            Attributes fileMetaInformation = inputStream.readFileMetaInformation();
            Attributes dataset = inputStream.readDataset(-1, -1);
            return new PreparedDicomObject(
                    dataset,
                    firstNonBlank(sopClassUid, dataset.getString(Tag.SOPClassUID), mediaStorageSopClassUid(fileMetaInformation)),
                    firstNonBlank(sopInstanceUid, dataset.getString(Tag.SOPInstanceUID), mediaStorageSopInstanceUid(fileMetaInformation)),
                    firstNonBlank(transferSyntaxUid, transferSyntax(fileMetaInformation), inputStream.getTransferSyntax(), UID.ExplicitVRLittleEndian)
            );
        } catch (IOException exception) {
            throw new IllegalStateException("Stored file is not a valid DICOM object", exception);
        }
    }

    public Attributes dataset() {
        return dataset;
    }

    public String sopClassUid() {
        return sopClassUid;
    }

    public String sopInstanceUid() {
        return sopInstanceUid;
    }

    public String transferSyntaxUid() {
        return transferSyntaxUid;
    }

    private static void mergeMissingValues(Attributes dataset, Map<String, String> attributes) {
        if (attributes == null) {
            return;
        }
        merge(dataset, Tag.PatientID, attributes, "patientId", "00100020");
        merge(dataset, Tag.IssuerOfPatientID, attributes, "issuerOfPatientId", "00100021");
        merge(dataset, Tag.PatientName, attributes, "patientName", "00100010");
        merge(dataset, Tag.PatientSex, attributes, "patientSex", "00100040");
        merge(dataset, Tag.PatientBirthDate, attributes, "patientBirthDate", "00100030");
        merge(dataset, Tag.StudyInstanceUID, attributes, "studyInstanceUid", "0020000D");
        merge(dataset, Tag.AccessionNumber, attributes, "accessionNo", "00080050");
        merge(dataset, Tag.StudyDate, attributes, "studyDate", "00080020");
        merge(dataset, Tag.StudyTime, attributes, "studyTime", "00080030");
        merge(dataset, Tag.StudyDescription, attributes, "studyDescription", "00081030");
        merge(dataset, Tag.ModalitiesInStudy, attributes, "modalitiesInStudy", "00080061");
        merge(dataset, Tag.ReferringPhysicianName, attributes, "referringDoctor", "00080090");
        merge(dataset, Tag.SeriesInstanceUID, attributes, "seriesInstanceUid", "0020000E");
        merge(dataset, Tag.Modality, attributes, "modality", "00080060");
        merge(dataset, Tag.SeriesDescription, attributes, "seriesDescription", "0008103E");
        merge(dataset, Tag.BodyPartExamined, attributes, "bodyPartExamined", "00180015");
        merge(dataset, Tag.SeriesNumber, attributes, "seriesNumber", "00200011");
        merge(dataset, Tag.SOPClassUID, attributes, "sopClassUid", "00080016");
        merge(dataset, Tag.SOPInstanceUID, attributes, "sopInstanceUid", "00080018");
        merge(dataset, Tag.InstanceNumber, attributes, "instanceNumber", "00200013");
    }

    private static void merge(Attributes dataset, int tag, Map<String, String> attributes, String alias, String hexTag) {
        if (dataset.containsValue(tag)) {
            return;
        }
        String value = attribute(attributes, alias, hexTag);
        if (value != null && !value.isBlank()) {
            dataset.setString(tag, org.dcm4che3.data.ElementDictionary.vrOf(tag, null), value);
        }
    }

    private static String attribute(Map<String, String> attributes, String alias, String hexTag) {
        if (attributes == null) {
            return null;
        }
        String value = attributes.get(alias);
        if (value == null || value.isBlank()) {
            value = attributes.get(hexTag);
        }
        return value;
    }

    private static String firstNonBlank(String... values) {
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

    private static String mediaStorageSopClassUid(Attributes fileMetaInformation) {
        return fileMetaInformation == null ? null : fileMetaInformation.getString(Tag.MediaStorageSOPClassUID);
    }

    private static String mediaStorageSopInstanceUid(Attributes fileMetaInformation) {
        return fileMetaInformation == null ? null : fileMetaInformation.getString(Tag.MediaStorageSOPInstanceUID);
    }

    private static String transferSyntax(Attributes fileMetaInformation) {
        return fileMetaInformation == null ? null : fileMetaInformation.getString(Tag.TransferSyntaxUID);
    }
}
