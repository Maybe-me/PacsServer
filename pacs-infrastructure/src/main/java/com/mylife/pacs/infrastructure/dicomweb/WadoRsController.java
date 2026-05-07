package com.mylife.pacs.infrastructure.dicomweb;

import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.infrastructure.storage.ObjectStorageService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/wado-rs")
public class WadoRsController {

    private final DicomApplicationService dicomApplicationService;
    private final ObjectStorageService objectStorageService;

    public WadoRsController(DicomApplicationService dicomApplicationService, ObjectStorageService objectStorageService) {
        this.dicomApplicationService = dicomApplicationService;
        this.objectStorageService = objectStorageService;
    }

    @GetMapping("/studies/{studyInstanceUid}/series/{seriesInstanceUid}/instances/{sopInstanceUid}")
    public ResponseEntity<byte[]> getInstance(
            @PathVariable("studyInstanceUid") String studyInstanceUid,
            @PathVariable("seriesInstanceUid") String seriesInstanceUid,
            @PathVariable("sopInstanceUid") String sopInstanceUid
    ) {
        PacsInstance instance = dicomApplicationService.findInstances(
                        new InstanceQueryCriteria(studyInstanceUid, seriesInstanceUid, sopInstanceUid)
                ).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DICOM instance not found"));
        byte[] payload = objectStorageService.read(instance);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/dicom"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + sopInstanceUid + ".dcm\"")
                .body(payload);
    }
}
