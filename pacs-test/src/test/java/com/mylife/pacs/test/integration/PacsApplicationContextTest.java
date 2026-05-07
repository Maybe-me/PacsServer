package com.mylife.pacs.test.integration;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.application.ViewerApplicationService;
import com.mylife.pacs.boot.PacsApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = PacsApplication.class)
class PacsApplicationContextTest {

    @Autowired
    private DicomApplicationService dicomApplicationService;

    @Autowired
    private AetApplicationService aetApplicationService;

    @Autowired
    private ViewerApplicationService viewerApplicationService;

    @Test
    void shouldLoadCoreApplicationServices() {
        assertThat(dicomApplicationService).isNotNull();
        assertThat(aetApplicationService).isNotNull();
        assertThat(viewerApplicationService).isNotNull();
    }
}
