package com.mylife.pacs.infrastructure.dicom;

import com.mylife.pacs.domain.service.StoreDicomRequest;
import com.mylife.pacs.infrastructure.storage.StoredFile;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class AttributesConverter {

    public StoreDicomRequest toStoreRequest(Map<String, String> attributes, StoredFile storedFile) {
        Map<String, String> patientExtras = extractExtras(attributes, "00100010", "00100020", "00100021", "00100030", "00100040");
        Map<String, String> studyExtras = extractExtras(attributes, "0020000D", "00080020", "00080030", "00080050", "00081030", "00080061", "00080090");
        Map<String, String> seriesExtras = extractExtras(attributes, "0020000E", "00080060", "0008103E", "00180015", "00200011");
        Map<String, String> instanceExtras = Map.copyOf(attributes);

        return new StoreDicomRequest(
                find(attributes, "patientId", "00100020"),
                findOptional(attributes, "issuerOfPatientId", "00100021"),
                findOptional(attributes, "patientName", "00100010"),
                findOptional(attributes, "patientSex", "00100040"),
                findOptional(attributes, "patientBirthDate", "00100030"),
                patientExtras,
                find(attributes, "studyInstanceUid", "0020000D"),
                findOptional(attributes, "accessionNo", "00080050"),
                findOptional(attributes, "studyDate", "00080020"),
                findOptional(attributes, "studyTime", "00080030"),
                findOptional(attributes, "studyDescription", "00081030"),
                findOptional(attributes, "modalitiesInStudy", "00080061"),
                findOptional(attributes, "referringDoctor", "00080090"),
                studyExtras,
                find(attributes, "seriesInstanceUid", "0020000E"),
                findOptional(attributes, "modality", "00080060"),
                findOptional(attributes, "seriesDescription", "0008103E"),
                findOptional(attributes, "bodyPartExamined", "00180015"),
                findInteger(attributes, "seriesNumber", "00200011"),
                seriesExtras,
                find(attributes, "sopInstanceUid", "00080018"),
                findOptional(attributes, "sopClassUid", "00080016"),
                findOptional(attributes, "transferSyntaxUid", "00020010"),
                findInteger(attributes, "instanceNumber", "00200013"),
                storedFile.relativePath(),
                storedFile.fileSize(),
                storedFile.fileMd5(),
                storedFile.storageType(),
                storedFile.storageBucket(),
                storedFile.storageKey(),
                instanceExtras
        );
    }

    private Map<String, String> extractExtras(Map<String, String> attributes, String... reservedKeys) {
        Map<String, String> extras = new LinkedHashMap<>(attributes);
        for (String reservedKey : reservedKeys) {
            extras.remove(reservedKey);
        }
        extras.remove("patientId");
        extras.remove("issuerOfPatientId");
        extras.remove("patientName");
        extras.remove("patientSex");
        extras.remove("patientBirthDate");
        extras.remove("studyInstanceUid");
        extras.remove("accessionNo");
        extras.remove("studyDate");
        extras.remove("studyTime");
        extras.remove("studyDescription");
        extras.remove("modalitiesInStudy");
        extras.remove("referringDoctor");
        extras.remove("seriesInstanceUid");
        extras.remove("modality");
        extras.remove("seriesDescription");
        extras.remove("bodyPartExamined");
        extras.remove("seriesNumber");
        extras.remove("sopInstanceUid");
        extras.remove("sopClassUid");
        extras.remove("transferSyntaxUid");
        extras.remove("instanceNumber");
        return Map.copyOf(extras);
    }

    private String find(Map<String, String> attributes, String alias, String tag) {
        String value = findOptional(attributes, alias, tag);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing required attribute: " + alias);
        }
        return value;
    }

    private String findOptional(Map<String, String> attributes, String alias, String tag) {
        String value = attributes.get(alias);
        if (value == null || value.isBlank()) {
            value = attributes.get(tag);
        }
        return value;
    }

    private Integer findInteger(Map<String, String> attributes, String alias, String tag) {
        String value = findOptional(attributes, alias, tag);
        return value == null || value.isBlank() ? null : Integer.parseInt(value);
    }
}
