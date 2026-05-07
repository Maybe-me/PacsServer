package com.mylife.pacs.test.integration;

import com.mylife.pacs.boot.PacsApplication;
import com.mylife.pacs.common.config.DicomPacsProperties;
import com.mylife.pacs.infrastructure.netty.DicomClientBootstrap;
import com.mylife.pacs.infrastructure.netty.DicomServerBootstrap;
import com.mylife.pacs.infrastructure.netty.message.DicomCommandType;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import com.mylife.pacs.test.support.PublicSampleLibrarySupport;
import com.mylife.pacs.test.support.TestDicomObjects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Path;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PacsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PublicSampleLibraryIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DicomClientBootstrap dicomClientBootstrap;

    @Autowired
    private DicomServerBootstrap dicomServerBootstrap;

    @Autowired
    private DicomPacsProperties properties;

    @Test
    void shouldIngestDownloadedSampleAndExposeItThroughQidoWadoAndViewerApis() throws Exception {
        Path samplePath = PublicSampleLibrarySupport.requireDownloadedSample(
                "pydicom-test-files",
                "pydicom-CT_small.dcm"
        );
        byte[] payload = PublicSampleLibrarySupport.readBytes(samplePath);
        Attributes sourceDataset = PublicSampleLibrarySupport.readDataset(samplePath);

        String studyUid = sourceDataset.getString(Tag.StudyInstanceUID);
        String seriesUid = sourceDataset.getString(Tag.SeriesInstanceUID);
        String sopUid = sourceDataset.getString(Tag.SOPInstanceUID);
        String patientId = sourceDataset.getString(Tag.PatientID, "");
        int rows = sourceDataset.getInt(Tag.Rows, 0);
        int columns = sourceDataset.getInt(Tag.Columns, 0);
        int numberOfFrames = Math.max(1, sourceDataset.getInt(Tag.NumberOfFrames, 1));
        int bitsStored = sourceDataset.getInt(Tag.BitsStored, 0);
        int highBit = sourceDataset.getInt(Tag.HighBit, 0);
        String photometricInterpretation = sourceDataset.getString(Tag.PhotometricInterpretation, "");
        String modality = firstNonBlank(
                sourceDataset.getString(Tag.ModalitiesInStudy),
                sourceDataset.getString(Tag.Modality),
                "CT"
        );

        DicomMessage storeResponse = dicomClientBootstrap.store(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "PUBSAMPLE",
                properties.getLocalAet(),
                Map.of(),
                payload
        );

        assertThat(storeResponse.commandType()).isEqualTo(DicomCommandType.C_STORE_RESPONSE);
        assertThat(storeResponse.status()).isZero();

        mockMvc.perform(get("/qido-rs/studies")
                        .param("StudyInstanceUID", studyUid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].0020000D.Value[0]").value(studyUid));

        mockMvc.perform(get("/wado-rs/studies/{study}/series/{series}/instances/{instance}", studyUid, seriesUid, sopUid))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/dicom"))
                .andExpect(result -> {
                    Attributes storedDataset = TestDicomObjects.readDataset(result.getResponse().getContentAsByteArray());
                    assertThat(storedDataset.getString(Tag.StudyInstanceUID)).isEqualTo(studyUid);
                    assertThat(storedDataset.getString(Tag.SeriesInstanceUID)).isEqualTo(seriesUid);
                    assertThat(storedDataset.getString(Tag.SOPInstanceUID)).isEqualTo(sopUid);
                });

        if (!patientId.isBlank()) {
            mockMvc.perform(get("/api/viewer/studies")
                            .param("patientId", patientId)
                            .param("modality", modality))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].studyInstanceUid", hasItem(studyUid)));
        }

        mockMvc.perform(get("/api/viewer/studies/{studyInstanceUid}/series", studyUid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].seriesInstanceUid", hasItem(seriesUid)));

        mockMvc.perform(get("/api/viewer/studies/{studyInstanceUid}/series/{seriesInstanceUid}/instances", studyUid, seriesUid))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].sopInstanceUid", hasItem(sopUid)))
                .andExpect(jsonPath("$[?(@.sopInstanceUid=='" + sopUid + "')].rows", hasItem(rows)))
                .andExpect(jsonPath("$[?(@.sopInstanceUid=='" + sopUid + "')].columns", hasItem(columns)))
                .andExpect(jsonPath("$[?(@.sopInstanceUid=='" + sopUid + "')].numberOfFrames", hasItem(numberOfFrames)))
                .andExpect(jsonPath("$[?(@.sopInstanceUid=='" + sopUid + "')].bitsStored", hasItem(bitsStored)))
                .andExpect(jsonPath("$[?(@.sopInstanceUid=='" + sopUid + "')].highBit", hasItem(highBit)))
                .andExpect(jsonPath("$[?(@.sopInstanceUid=='" + sopUid + "')].photometricInterpretation", hasItem(photometricInterpretation)))
                .andExpect(jsonPath("$[?(@.sopInstanceUid=='" + sopUid + "')].renderable", hasItem(true)));
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }
}
