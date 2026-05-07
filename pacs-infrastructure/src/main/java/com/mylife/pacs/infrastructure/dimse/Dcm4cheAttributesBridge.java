package com.mylife.pacs.infrastructure.dimse;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.ElementDictionary;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.util.TagUtils;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Dcm4cheAttributesBridge {

    private static final String PRESENCE_MARKER = "__present__";
    private static final Set<Integer> PRESENCE_ONLY_TAGS = Set.of(
            Tag.PixelData,
            Tag.FloatPixelData,
            Tag.DoubleFloatPixelData
    );

    private static final Map<String, Integer> ALIASES = Map.ofEntries(
            Map.entry("patientId", Tag.PatientID),
            Map.entry("issuerOfPatientId", Tag.IssuerOfPatientID),
            Map.entry("patientName", Tag.PatientName),
            Map.entry("patientSex", Tag.PatientSex),
            Map.entry("patientBirthDate", Tag.PatientBirthDate),
            Map.entry("studyInstanceUid", Tag.StudyInstanceUID),
            Map.entry("accessionNo", Tag.AccessionNumber),
            Map.entry("studyDate", Tag.StudyDate),
            Map.entry("studyTime", Tag.StudyTime),
            Map.entry("studyDescription", Tag.StudyDescription),
            Map.entry("modalitiesInStudy", Tag.ModalitiesInStudy),
            Map.entry("referringDoctor", Tag.ReferringPhysicianName),
            Map.entry("seriesInstanceUid", Tag.SeriesInstanceUID),
            Map.entry("modality", Tag.Modality),
            Map.entry("seriesDescription", Tag.SeriesDescription),
            Map.entry("bodyPartExamined", Tag.BodyPartExamined),
            Map.entry("seriesNumber", Tag.SeriesNumber),
            Map.entry("sopInstanceUid", Tag.SOPInstanceUID),
            Map.entry("sopClassUid", Tag.SOPClassUID),
            Map.entry("transferSyntaxUid", Tag.TransferSyntaxUID),
            Map.entry("instanceNumber", Tag.InstanceNumber),
            Map.entry("QueryRetrieveLevel", Tag.QueryRetrieveLevel),
            Map.entry("MoveDestination", Tag.MoveDestination)
    );

    private Dcm4cheAttributesBridge() {
    }

    public static Map<String, String> toAttributeMap(Attributes attributes) {
        LinkedHashMap<String, String> mapped = new LinkedHashMap<>();
        try {
            attributes.accept((attrs, tag, vr, value) -> {
                if (vr == VR.SQ) {
                    return true;
                }
                if (PRESENCE_ONLY_TAGS.contains(tag) && attrs.contains(tag)) {
                    mapped.put(TagUtils.toHexString(tag).toUpperCase(Locale.ROOT), PRESENCE_MARKER);
                    return true;
                }
                String stringValue = attrs.getString(tag);
                if (stringValue != null && !stringValue.isBlank()) {
                    mapped.put(TagUtils.toHexString(tag).toUpperCase(Locale.ROOT), stringValue);
                }
                return true;
            }, false);
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to convert DICOM attributes", exception);
        }
        return Map.copyOf(mapped);
    }

    public static Attributes toQueryAttributes(Map<String, String> values) {
        Attributes attributes = new Attributes();
        if (values == null || values.isEmpty()) {
            return attributes;
        }

        String studyDateFrom = values.get("studyDateFrom");
        String studyDateTo = values.get("studyDateTo");
        if ((studyDateFrom != null && !studyDateFrom.isBlank()) || (studyDateTo != null && !studyDateTo.isBlank())) {
            attributes.setString(Tag.StudyDate, VR.DA, range(studyDateFrom, studyDateTo));
        }

        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            if ("studyDateFrom".equals(entry.getKey()) || "studyDateTo".equals(entry.getKey())) {
                continue;
            }
            Integer tag = resolveTag(entry.getKey());
            if (tag == null) {
                continue;
            }
            attributes.setString(tag, vrOf(tag), entry.getValue());
        }
        addReturnKeys(attributes);
        return attributes;
    }

    public static Attributes toDataset(Map<String, String> values) {
        Attributes attributes = new Attributes();
        if (values == null) {
            return attributes;
        }
        for (Map.Entry<String, String> entry : values.entrySet()) {
            if (entry.getValue() == null || entry.getValue().isBlank()) {
                continue;
            }
            Integer tag = resolveTag(entry.getKey());
            if (tag == null || tag == Tag.TransferSyntaxUID || PRESENCE_ONLY_TAGS.contains(tag) || PRESENCE_MARKER.equals(entry.getValue())) {
                continue;
            }
            attributes.setString(tag, vrOf(tag), entry.getValue());
        }
        return attributes;
    }

    private static Integer resolveTag(String key) {
        Integer aliasTag = ALIASES.get(key);
        if (aliasTag != null) {
            return aliasTag;
        }
        if (key.matches("[0-9A-Fa-f]{8}")) {
            return TagUtils.intFromHexString(key);
        }
        return null;
    }

    private static VR vrOf(int tag) {
        VR vr = ElementDictionary.vrOf(tag, null);
        return vr == null ? VR.LO : vr;
    }

    private static void addReturnKeys(Attributes attributes) {
        String level = attributes.getString(Tag.QueryRetrieveLevel, "STUDY");
        switch (level.toUpperCase(Locale.ROOT)) {
            case "PATIENT" -> {
                ensureReturnKey(attributes, Tag.PatientID);
                ensureReturnKey(attributes, Tag.IssuerOfPatientID);
                ensureReturnKey(attributes, Tag.PatientName);
                ensureReturnKey(attributes, Tag.PatientSex);
                ensureReturnKey(attributes, Tag.PatientBirthDate);
            }
            case "SERIES" -> {
                ensureReturnKey(attributes, Tag.StudyInstanceUID);
                ensureReturnKey(attributes, Tag.SeriesInstanceUID);
                ensureReturnKey(attributes, Tag.Modality);
                ensureReturnKey(attributes, Tag.SeriesDescription);
                ensureReturnKey(attributes, Tag.BodyPartExamined);
                ensureReturnKey(attributes, Tag.SeriesNumber);
                ensureReturnKey(attributes, Tag.NumberOfSeriesRelatedInstances);
            }
            default -> {
                ensureReturnKey(attributes, Tag.StudyInstanceUID);
                ensureReturnKey(attributes, Tag.AccessionNumber);
                ensureReturnKey(attributes, Tag.StudyDate);
                ensureReturnKey(attributes, Tag.StudyTime);
                ensureReturnKey(attributes, Tag.StudyDescription);
                ensureReturnKey(attributes, Tag.ModalitiesInStudy);
                ensureReturnKey(attributes, Tag.ReferringPhysicianName);
                ensureReturnKey(attributes, Tag.NumberOfStudyRelatedSeries);
                ensureReturnKey(attributes, Tag.NumberOfStudyRelatedInstances);
            }
        }
    }

    private static void ensureReturnKey(Attributes attributes, int tag) {
        if (!attributes.contains(tag)) {
            attributes.setNull(tag, vrOf(tag));
        }
    }

    private static String range(String from, String to) {
        String start = from == null ? "" : from;
        String end = to == null ? "" : to;
        return start + "-" + end;
    }
}
