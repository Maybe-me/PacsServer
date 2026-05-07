package com.mylife.pacs.test.support;

import com.mylife.pacs.infrastructure.dimse.Dcm4cheAttributesBridge;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.dcm4che3.data.UID;
import org.dcm4che3.data.VR;
import org.dcm4che3.io.DicomInputStream;
import org.dcm4che3.io.DicomOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class TestDicomObjects {

    private TestDicomObjects() {
    }

    public static byte[] createDicomBytes(Map<String, String> attributes, String pixelPayload) {
        try {
            Attributes dataset = Dcm4cheAttributesBridge.toDataset(attributes);
            dataset.setInt(Tag.Rows, VR.US, 1);
            dataset.setInt(Tag.Columns, VR.US, Math.max(1, pixelPayload.length()));
            dataset.setInt(Tag.SamplesPerPixel, VR.US, 1);
            dataset.setString(Tag.PhotometricInterpretation, VR.CS, "MONOCHROME2");
            dataset.setInt(Tag.BitsAllocated, VR.US, 8);
            dataset.setInt(Tag.BitsStored, VR.US, 8);
            dataset.setInt(Tag.HighBit, VR.US, 7);
            dataset.setInt(Tag.PixelRepresentation, VR.US, 0);
            dataset.setBytes(Tag.PixelData, VR.OB, pixelPayload.getBytes(StandardCharsets.UTF_8));

            String transferSyntaxUid = attributes.getOrDefault("00020010", UID.ExplicitVRLittleEndian);
            Attributes fileMetaInformation = dataset.createFileMetaInformation(transferSyntaxUid);

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            try (DicomOutputStream dicomOutputStream = new DicomOutputStream(outputStream, UID.ExplicitVRLittleEndian)) {
                dicomOutputStream.writeDataset(fileMetaInformation, dataset);
            }
            return outputStream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to build test DICOM object", exception);
        }
    }

    public static Attributes readDataset(byte[] payload) {
        try (DicomInputStream inputStream = new DicomInputStream(new ByteArrayInputStream(payload))) {
            inputStream.readFileMetaInformation();
            return inputStream.readDataset(-1, -1);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to parse test DICOM payload", exception);
        }
    }
}
