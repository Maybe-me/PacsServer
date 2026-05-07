package com.mylife.pacs.test.integration;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.boot.PacsApplication;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.model.AetRole;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.service.StoreDicomRequest;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PacsApplication.class)
@ActiveProfiles("test")
class PacsPersistenceIntegrationTest {

    @Autowired
    private DicomApplicationService dicomApplicationService;

    @Autowired
    private AetApplicationService aetApplicationService;

    @Test
    void shouldStoreAndQueryStudyHierarchy() {
        dicomApplicationService.store(new StoreDicomRequest(
                "P001",
                "",
                "Demo Patient",
                "M",
                "19800101",
                Map.of("00101040", "38"),
                "1.2.840.10008.1",
                "ACC-001",
                "20260428",
                "100000",
                "Chest CT",
                "CT",
                "Dr.Test",
                Map.of("00080080", "General Hospital"),
                "1.2.840.10008.1.1",
                "CT",
                "Thin Slice",
                "CHEST",
                1,
                Map.of(),
                "1.2.840.10008.1.1.1",
                "1.2.840.10008.5.1.4.1.1.2",
                "1.2.840.10008.1.2.1",
                1,
                "P001\\1.2.840.10008.1\\1.2.840.10008.1.1\\1.2.840.10008.1.1.1.dcm",
                2048L,
                "0123456789abcdef0123456789abcdef",
                "local",
                null,
                "P001\\1.2.840.10008.1\\1.2.840.10008.1.1\\1.2.840.10008.1.1.1.dcm",
                Map.of("00200013", "1")
        ));

        List<PacsStudy> studies = dicomApplicationService.findStudies(
                new StudyQueryCriteria("P001", "", null, null, "CT", "20260401", "20260430")
        );

        assertThat(studies).hasSize(1);
        assertThat(studies.getFirst().numSeries()).isEqualTo(1);
        assertThat(studies.getFirst().numInstances()).isEqualTo(1);
    }

    @Test
    void shouldManageAetNodes() {
        AetNode node = aetApplicationService.register(new AetNode(
                null,
                "REMOTE_A",
                "127.0.0.1",
                11112,
                AetRole.REMOTE,
                "Remote PACS",
                "Integration test node",
                true,
                null,
                null,
                null
        ));

        assertThat(aetApplicationService.findByAet("REMOTE_A").id()).isEqualTo(node.id());
    }
}
