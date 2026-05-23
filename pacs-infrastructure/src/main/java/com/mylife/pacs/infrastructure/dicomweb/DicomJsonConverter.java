package com.mylife.pacs.infrastructure.dicomweb;

import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.json.JSONWriter;
import org.springframework.stereotype.Component;

import jakarta.json.Json;
import jakarta.json.stream.JsonGenerator;
import java.io.File;
import java.io.StringWriter;

@Component
public class DicomJsonConverter {

    public String convertToJsonMetadata(File dicomFile, String wadoRsBaseUrl, String studyUid, String seriesUid, String sopUid) {
        try (DicomInputStream dis = new DicomInputStream(dicomFile)) {
            return convertToJsonMetadata(dis, wadoRsBaseUrl, studyUid, seriesUid, sopUid);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert DICOM to JSON metadata for: " + sopUid, e);
        }
    }

    public String convertToJsonMetadata(byte[] payload, String wadoRsBaseUrl, String studyUid, String seriesUid, String sopUid) {
        try (DicomInputStream dis = new DicomInputStream(new java.io.ByteArrayInputStream(payload))) {
            return convertToJsonMetadata(dis, wadoRsBaseUrl, studyUid, seriesUid, sopUid);
        } catch (Exception e) {
            throw new RuntimeException("Failed to convert DICOM to JSON metadata for: " + sopUid, e);
        }
    }

    private String convertToJsonMetadata(DicomInputStream dis, String wadoRsBaseUrl, String studyUid, String seriesUid, String sopUid) throws Exception {
        // Read all metadata, skip bulk pixel data entirely
        dis.setIncludeBulkData(DicomInputStream.IncludeBulkData.NO);
        Attributes attrs = dis.readDataset();

        // BulkDataURI pointing to our /frames endpoint
        String bulkDataUri = String.format("%s/studies/%s/series/%s/instances/%s/frames/1",
                wadoRsBaseUrl, studyUid, seriesUid, sopUid);

        // Mark pixel data as null OB so JSONWriter emits the empty VR tag
        if (attrs.contains(Tag.PixelData)) {
            attrs.setNull(Tag.PixelData, VR.OB);
        } else if (attrs.contains(Tag.Rows) && attrs.contains(Tag.Columns)) {
            // Since dis.setIncludeBulkData(NO) was used, PixelData was skipped and not present in attrs.
            // If the dataset contains Rows and Columns, it is a pixel image, so we inject a placeholder PixelData tag.
            attrs.setNull(Tag.PixelData, VR.OB);
        }

        StringWriter sw = new StringWriter();
        // dcm4che JSONWriter.write(Attributes) calls gen.writeStartObject()/writeEnd() internally
        // so we must NOT wrap with writeStartObject here - just let writer handle it
        try (JsonGenerator gen = Json.createGenerator(sw)) {
            JSONWriter writer = new JSONWriter(gen);
            writer.write(attrs);
            gen.flush();
        }

        String jsonStr = sw.toString();

        // Post-process: inject BulkDataURI for the null pixel data tag
        // dcm4che emits "7FE00010":{"vr":"OB"} when setNull is used
        jsonStr = jsonStr.replace(
            "\"7FE00010\":{\"vr\":\"OB\"}",
            "\"7FE00010\":{\"vr\":\"OB\",\"BulkDataURI\":\"" + bulkDataUri + "\"}"
        ).replace(
            "\"7FE00010\":{\"vr\":\"OW\"}",
            "\"7FE00010\":{\"vr\":\"OW\",\"BulkDataURI\":\"" + bulkDataUri + "\"}"
        ).replace(
            "\"7FE00010\":{\"vr\":\"UN\"}",
            "\"7FE00010\":{\"vr\":\"UN\",\"BulkDataURI\":\"" + bulkDataUri + "\"}"
        );

        return jsonStr;
    }
}
