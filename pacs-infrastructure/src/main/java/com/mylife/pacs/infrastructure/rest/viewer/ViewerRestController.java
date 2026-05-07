package com.mylife.pacs.infrastructure.rest.viewer;

import com.mylife.pacs.application.ViewerApplicationService;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.domain.service.SeriesQueryCriteria;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/viewer")
public class ViewerRestController {

    private final ViewerApplicationService viewerApplicationService;

    public ViewerRestController(ViewerApplicationService viewerApplicationService) {
        this.viewerApplicationService = viewerApplicationService;
    }

    @GetMapping("/studies")
    public List<Map<String, Object>> studies(
            @RequestParam(name = "patientId", required = false) String patientId,
            @RequestParam(name = "issuerOfPatientId", required = false) String issuerOfPatientId,
            @RequestParam(name = "modality", required = false) String modality,
            @RequestParam(name = "studyDateFrom", required = false) String studyDateFrom,
            @RequestParam(name = "studyDateTo", required = false) String studyDateTo
    ) {
        return viewerApplicationService.loadStudies(new StudyQueryCriteria(
                        patientId,
                        issuerOfPatientId,
                        null,
                        null,
                        modality,
                        studyDateFrom,
                        studyDateTo
                )).stream()
                .map(this::toStudy)
                .toList();
    }

    @GetMapping("/studies/{studyInstanceUid}/series")
    public List<Map<String, Object>> series(@PathVariable("studyInstanceUid") String studyInstanceUid) {
        return viewerApplicationService.loadSeries(new SeriesQueryCriteria(studyInstanceUid, null, null)).stream()
                .map(this::toSeries)
                .toList();
    }

    @GetMapping("/studies/{studyInstanceUid}/series/{seriesInstanceUid}/instances")
    public List<Map<String, Object>> instances(
            @PathVariable("studyInstanceUid") String studyInstanceUid,
            @PathVariable("seriesInstanceUid") String seriesInstanceUid
    ) {
        return viewerApplicationService.loadInstances(new InstanceQueryCriteria(studyInstanceUid, seriesInstanceUid, null)).stream()
                .map(instance -> toInstance(studyInstanceUid, seriesInstanceUid, instance))
                .toList();
    }

    private Map<String, Object> toStudy(PacsStudy study) {
        return Map.of(
                "studyInstanceUid", study.studyInstanceUid(),
                "accessionNo", study.accessionNo() == null ? "" : study.accessionNo(),
                "studyDate", study.studyDate() == null ? "" : study.studyDate(),
                "studyDescription", study.studyDescription() == null ? "" : study.studyDescription(),
                "modalitiesInStudy", study.modalitiesInStudy() == null ? "" : study.modalitiesInStudy(),
                "numSeries", study.numSeries(),
                "numInstances", study.numInstances()
        );
    }

    private Map<String, Object> toSeries(PacsSeries series) {
        return Map.of(
                "seriesInstanceUid", series.seriesInstanceUid(),
                "modality", series.modality() == null ? "" : series.modality(),
                "seriesDescription", series.seriesDescription() == null ? "" : series.seriesDescription(),
                "bodyPartExamined", series.bodyPartExamined() == null ? "" : series.bodyPartExamined(),
                "numInstances", series.numInstances()
        );
    }

    private Map<String, Object> toInstance(String studyUid, String seriesUid, PacsInstance instance) {
        int rows = parseIntTag(instance, "00280010");
        int columns = parseIntTag(instance, "00280011");
        int numberOfFrames = Math.max(1, parseIntTag(instance, "00280008"));
        int samplesPerPixel = Math.max(1, parseIntTag(instance, "00280002"));
        int bitsAllocated = parseIntTag(instance, "00280100");
        int bitsStored = parseIntTag(instance, "00280101");
        int highBit = parseIntTag(instance, "00280102");
        int planarConfiguration = parseIntTag(instance, "00280006");
        int pixelRepresentation = parseIntTag(instance, "00280103");
        double windowCenter = parseDoubleTag(instance, "00281050");
        double windowWidth = parseDoubleTag(instance, "00281051");
        String photometricInterpretation = parseStringTag(instance, "00280004");
        return Map.ofEntries(
                Map.entry("sopInstanceUid", instance.sopInstanceUid()),
                Map.entry("instanceNumber", instance.instanceNumber() == null ? 0 : instance.instanceNumber()),
                Map.entry("sopClassUid", instance.sopClassUid() == null ? "" : instance.sopClassUid()),
                Map.entry("transferSyntaxUid", instance.transferSyntaxUid() == null ? "" : instance.transferSyntaxUid()),
                Map.entry("fileSize", instance.fileSize() == null ? 0L : instance.fileSize()),
                Map.entry("rows", rows),
                Map.entry("columns", columns),
                Map.entry("numberOfFrames", numberOfFrames),
                Map.entry("samplesPerPixel", samplesPerPixel),
                Map.entry("bitsAllocated", bitsAllocated),
                Map.entry("bitsStored", bitsStored),
                Map.entry("highBit", highBit),
                Map.entry("planarConfiguration", planarConfiguration),
                Map.entry("pixelRepresentation", pixelRepresentation),
                Map.entry("photometricInterpretation", photometricInterpretation),
                Map.entry("windowCenter", windowCenter),
                Map.entry("windowWidth", windowWidth),
                Map.entry("renderable", rows > 0 && columns > 0),
                Map.entry("wadoUri", "/wado-rs/studies/" + studyUid + "/series/" + seriesUid + "/instances/" + instance.sopInstanceUid())
        );
    }

    private int parseIntTag(PacsInstance instance, String tag) {
        if (instance.extraTags() == null) {
            return 0;
        }
        String value = instance.extraTags().get(tag);
        if (value == null || value.isBlank()) {
            return 0;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private double parseDoubleTag(PacsInstance instance, String tag) {
        if (instance.extraTags() == null) {
            return 0;
        }
        String value = instance.extraTags().get(tag);
        if (value == null || value.isBlank()) {
            return 0;
        }
        String normalized = value.trim().split("\\\\")[0];
        try {
            return Double.parseDouble(normalized);
        } catch (NumberFormatException exception) {
            return 0;
        }
    }

    private String parseStringTag(PacsInstance instance, String tag) {
        if (instance.extraTags() == null) {
            return "";
        }
        String value = instance.extraTags().get(tag);
        return value == null ? "" : value.trim();
    }
}
