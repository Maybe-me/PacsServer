package com.mylife.pacs.infrastructure.dicomweb;

import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.service.InstanceQueryCriteria;
import com.mylife.pacs.infrastructure.storage.ObjectStorageService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.io.File;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/wado-rs")
public class WadoRsController {

    private final DicomApplicationService dicomApplicationService;
    private final ObjectStorageService objectStorageService;
    private final DicomJsonConverter dicomJsonConverter;

    @Value("${server.port:8080}")
    private int serverPort;

    public WadoRsController(DicomApplicationService dicomApplicationService, ObjectStorageService objectStorageService, DicomJsonConverter dicomJsonConverter) {
        this.dicomApplicationService = dicomApplicationService;
        this.objectStorageService = objectStorageService;
        this.dicomJsonConverter = dicomJsonConverter;
    }

    @GetMapping("/log")
    public ResponseEntity<Void> logMessage(@org.springframework.web.bind.annotation.RequestParam("msg") String msg) {
        System.out.println("[FRONTEND LOG] " + msg);
        return ResponseEntity.ok().build();
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

    @GetMapping(value = "/studies/{studyInstanceUid}/series/{seriesInstanceUid}/metadata", produces = "application/dicom+json")
    public ResponseEntity<String> getSeriesMetadata(
            @PathVariable("studyInstanceUid") String studyInstanceUid,
            @PathVariable("seriesInstanceUid") String seriesInstanceUid
    ) {
        List<PacsInstance> instances = dicomApplicationService.findInstances(
                new InstanceQueryCriteria(studyInstanceUid, seriesInstanceUid, null)
        );

        String baseUrl = "http://localhost:" + serverPort + "/wado-rs";

        String jsonArrayInner = instances.stream().map(instance -> {
            byte[] payload = objectStorageService.read(instance);
            return dicomJsonConverter.convertToJsonMetadata(payload, baseUrl, studyInstanceUid, seriesInstanceUid, instance.sopInstanceUid());
        }).collect(Collectors.joining(","));

        return ResponseEntity.ok("[" + jsonArrayInner + "]");
    }

    @GetMapping(value = "/studies/{studyInstanceUid}/series/{seriesInstanceUid}/instances/{sopInstanceUid}/frames/{frame}")
    public ResponseEntity<byte[]> getInstanceFrame(
            @PathVariable("studyInstanceUid") String studyInstanceUid,
            @PathVariable("seriesInstanceUid") String seriesInstanceUid,
            @PathVariable("sopInstanceUid") String sopInstanceUid,
            @PathVariable("frame") int frame
    ) {
        PacsInstance instance = dicomApplicationService.findInstances(
                        new InstanceQueryCriteria(studyInstanceUid, seriesInstanceUid, sopInstanceUid)
                ).stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "DICOM instance not found"));

        byte[] payload = objectStorageService.read(instance);

        // Construct multipart/related response containing the full DICOM file bytes as a single frame part.
        // @cornerstonejs/dicom-image-loader's wadors loader can parse a DICOM file from this chunk.
        String boundary = "myboundary";
        String contentType = "multipart/related; type=\"application/octet-stream\"; boundary=" + boundary;

        byte[] headerBytes = ("\r\n--" + boundary + "\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes();
        byte[] footerBytes = ("\r\n--" + boundary + "--\r\n").getBytes();

        byte[] fullMultipart = new byte[headerBytes.length + payload.length + footerBytes.length];
        System.arraycopy(headerBytes, 0, fullMultipart, 0, headerBytes.length);
        System.arraycopy(payload, 0, fullMultipart, headerBytes.length, payload.length);
        System.arraycopy(footerBytes, 0, fullMultipart, headerBytes.length + payload.length, footerBytes.length);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(fullMultipart);
    }
}
