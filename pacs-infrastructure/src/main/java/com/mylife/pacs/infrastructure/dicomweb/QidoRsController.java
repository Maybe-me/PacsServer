package com.mylife.pacs.infrastructure.dicomweb;

import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.service.SeriesQueryCriteria;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/qido-rs")
public class QidoRsController {

    private final DicomApplicationService dicomApplicationService;

    public QidoRsController(DicomApplicationService dicomApplicationService) {
        this.dicomApplicationService = dicomApplicationService;
    }

    @GetMapping("/studies")
    public List<Map<String, Object>> queryStudies(
            @RequestParam(name = "PatientID", required = false) String patientId,
            @RequestParam(name = "IssuerOfPatientID", required = false) String issuerOfPatientId,
            @RequestParam(name = "StudyInstanceUID", required = false) String studyInstanceUid,
            @RequestParam(name = "AccessionNumber", required = false) String accessionNo,
            @RequestParam(name = "ModalitiesInStudy", required = false) String modality,
            @RequestParam(name = "StudyDate", required = false) String studyDate
    ) {
        DateRange studyDateRange = parseDateRange(studyDate);
        return dicomApplicationService.findStudies(new StudyQueryCriteria(
                        patientId,
                        issuerOfPatientId,
                        studyInstanceUid,
                        accessionNo,
                        modality,
                        studyDateRange.from(),
                        studyDateRange.to()
                )).stream()
                .map(this::toStudyDataset)
                .toList();
    }

    @GetMapping("/studies/{studyInstanceUid}/series")
    public List<Map<String, Object>> querySeries(
            @PathVariable("studyInstanceUid") String studyInstanceUid,
            @RequestParam(name = "SeriesInstanceUID", required = false) String seriesInstanceUid,
            @RequestParam(name = "Modality", required = false) String modality
    ) {
        return dicomApplicationService.findSeries(new SeriesQueryCriteria(studyInstanceUid, seriesInstanceUid, modality)).stream()
                .map(this::toSeriesDataset)
                .toList();
    }

    private Map<String, Object> toStudyDataset(PacsStudy study) {
        LinkedHashMap<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("0020000D", dicomValue("UI", study.studyInstanceUid()));
        dataset.put("00080050", dicomValue("SH", study.accessionNo()));
        dataset.put("00080020", dicomValue("DA", study.studyDate()));
        dataset.put("00081030", dicomValue("LO", study.studyDescription()));
        dataset.put("00080061", dicomValue("CS", study.modalitiesInStudy()));
        return dataset;
    }

    private Map<String, Object> toSeriesDataset(PacsSeries series) {
        LinkedHashMap<String, Object> dataset = new LinkedHashMap<>();
        dataset.put("0020000E", dicomValue("UI", series.seriesInstanceUid()));
        dataset.put("00080060", dicomValue("CS", series.modality()));
        dataset.put("0008103E", dicomValue("LO", series.seriesDescription()));
        dataset.put("00180015", dicomValue("CS", series.bodyPartExamined()));
        return dataset;
    }

    private Map<String, Object> dicomValue(String vr, String value) {
        return Map.of(
                "vr", vr,
                "Value", value == null || value.isBlank() ? List.of() : List.of(value)
        );
    }

    private DateRange parseDateRange(String studyDate) {
        if (studyDate == null || studyDate.isBlank()) {
            return new DateRange(null, null);
        }
        String[] parts = studyDate.split("-", -1);
        if (parts.length == 1) {
            return new DateRange(parts[0], parts[0]);
        }
        return new DateRange(parts[0].isBlank() ? null : parts[0], parts[1].isBlank() ? null : parts[1]);
    }

    private record DateRange(String from, String to) {
    }
}
