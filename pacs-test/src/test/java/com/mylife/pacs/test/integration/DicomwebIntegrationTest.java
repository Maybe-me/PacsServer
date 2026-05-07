package com.mylife.pacs.test.integration;

import com.mylife.pacs.boot.PacsApplication;
import com.mylife.pacs.infrastructure.netty.DicomClientBootstrap;
import com.mylife.pacs.infrastructure.netty.DicomServerBootstrap;
import com.mylife.pacs.test.support.TestDicomObjects;
import org.dcm4che3.data.Attributes;
import org.dcm4che3.data.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = PacsApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DicomwebIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DicomClientBootstrap dicomClientBootstrap;

    @Autowired
    private DicomServerBootstrap dicomServerBootstrap;

    @Test
    void shouldQueryStudiesThroughQidoRs() throws Exception {
        String patientId = "WEB-" + System.nanoTime();
        String studyUid = "1.2.826.0.4." + System.nanoTime();
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";

        seedStudy(patientId, studyUid, seriesUid, sopUid, "CT", "Web Query Study", "web-query-payload");

        mockMvc.perform(get("/qido-rs/studies")
                        .param("PatientID", patientId)
                        .param("StudyDate", "20260401-20260430")
                        .param("ModalitiesInStudy", "CT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].0020000D.Value[0]").value(studyUid))
                .andExpect(jsonPath("$[0].00081030.Value[0]").value("Web Query Study"));
    }

    @Test
    void shouldReturnDicomPayloadThroughWadoRs() throws Exception {
        String patientId = "WADO-" + System.nanoTime();
        String studyUid = "1.2.826.0.5." + System.nanoTime();
        String seriesUid = studyUid + ".1";
        String sopUid = seriesUid + ".1";
        String payload = "dicomweb-payload-" + patientId;

        seedStudy(patientId, studyUid, seriesUid, sopUid, "MR", "Wado Study", payload);

        mockMvc.perform(get("/wado-rs/studies/{study}/series/{series}/instances/{instance}", studyUid, seriesUid, sopUid))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/dicom"))
                .andExpect(result -> {
                    Attributes dataset = TestDicomObjects.readDataset(result.getResponse().getContentAsByteArray());
                    org.assertj.core.api.Assertions.assertThat(dataset.getString(Tag.StudyInstanceUID)).isEqualTo(studyUid);
                    org.assertj.core.api.Assertions.assertThat(dataset.getString(Tag.SeriesInstanceUID)).isEqualTo(seriesUid);
                    org.assertj.core.api.Assertions.assertThat(dataset.getString(Tag.SOPInstanceUID)).isEqualTo(sopUid);
                    org.assertj.core.api.Assertions.assertThat(new String(dataset.getBytes(Tag.PixelData), java.nio.charset.StandardCharsets.UTF_8).replace("\u0000", ""))
                            .isEqualTo(payload);
                });
    }

    private void seedStudy(
            String patientId,
            String studyUid,
            String seriesUid,
            String sopUid,
            String modality,
            String studyDescription,
            String payload
    ) {
        dicomClientBootstrap.store(
                "127.0.0.1",
                dicomServerBootstrap.boundPort(),
                "REMOTE_WEB_SEED",
                "MY_PACS",
                Map.ofEntries(
                        Map.entry("00100020", patientId),
                        Map.entry("00100021", "LOCAL"),
                        Map.entry("00100010", "Web^Patient"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("00080050", "ACC-" + patientId),
                        Map.entry("00080020", "20260428"),
                        Map.entry("00081030", studyDescription),
                        Map.entry("00080061", modality),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080060", modality),
                        Map.entry("0008103E", studyDescription + " Series"),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.2"),
                        Map.entry("00020010", "1.2.840.10008.1.2.1")
                ),
                TestDicomObjects.createDicomBytes(Map.ofEntries(
                        Map.entry("00100020", patientId),
                        Map.entry("00100021", "LOCAL"),
                        Map.entry("00100010", "Web^Patient"),
                        Map.entry("0020000D", studyUid),
                        Map.entry("00080050", "ACC-" + patientId),
                        Map.entry("00080020", "20260428"),
                        Map.entry("00081030", studyDescription),
                        Map.entry("00080061", modality),
                        Map.entry("0020000E", seriesUid),
                        Map.entry("00080060", modality),
                        Map.entry("0008103E", studyDescription + " Series"),
                        Map.entry("00080018", sopUid),
                        Map.entry("00080016", "1.2.840.10008.5.1.4.1.1.2"),
                        Map.entry("00020010", "1.2.840.10008.1.2.1")
                ), payload)
        );
    }
}
