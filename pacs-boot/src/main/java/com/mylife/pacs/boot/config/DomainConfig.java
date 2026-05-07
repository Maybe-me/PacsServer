package com.mylife.pacs.boot.config;

import com.mylife.pacs.application.AetApplicationService;
import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.application.RemoteDicomApplicationService;
import com.mylife.pacs.application.RemoteDicomGateway;
import com.mylife.pacs.application.SyncJobConfigApplicationService;
import com.mylife.pacs.application.ViewerApplicationService;
import com.mylife.pacs.domain.repository.AetNodeRepository;
import com.mylife.pacs.domain.repository.PacsInstanceRepository;
import com.mylife.pacs.domain.repository.PacsPatientRepository;
import com.mylife.pacs.domain.repository.PacsSeriesRepository;
import com.mylife.pacs.domain.repository.PacsStudyRepository;
import com.mylife.pacs.domain.repository.SyncJobConfigRepository;
import com.mylife.pacs.domain.service.AetManager;
import com.mylife.pacs.domain.service.DefaultAetManager;
import com.mylife.pacs.domain.service.DefaultDicomQueryDomainService;
import com.mylife.pacs.domain.service.DefaultDicomRetrieveDomainService;
import com.mylife.pacs.domain.service.DefaultDicomStoreDomainService;
import com.mylife.pacs.domain.service.DicomQueryDomainService;
import com.mylife.pacs.domain.service.DicomRetrieveDomainService;
import com.mylife.pacs.domain.service.DicomStoreDomainService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class DomainConfig {

    @Bean
    DicomStoreDomainService dicomStoreDomainService(
            PacsPatientRepository patientRepository,
            PacsStudyRepository studyRepository,
            PacsSeriesRepository seriesRepository,
            PacsInstanceRepository instanceRepository
    ) {
        return new DefaultDicomStoreDomainService(patientRepository, studyRepository, seriesRepository, instanceRepository);
    }

    @Bean
    DicomQueryDomainService dicomQueryDomainService(
            PacsPatientRepository patientRepository,
            PacsStudyRepository studyRepository,
            PacsSeriesRepository seriesRepository
    ) {
        return new DefaultDicomQueryDomainService(patientRepository, studyRepository, seriesRepository);
    }

    @Bean
    DicomRetrieveDomainService dicomRetrieveDomainService(PacsInstanceRepository instanceRepository) {
        return new DefaultDicomRetrieveDomainService(instanceRepository);
    }

    @Bean
    AetManager aetManager(AetNodeRepository repository) {
        return new DefaultAetManager(repository);
    }

    @Bean
    DicomApplicationService dicomApplicationService(
            DicomStoreDomainService storeDomainService,
            DicomQueryDomainService queryDomainService,
            DicomRetrieveDomainService retrieveDomainService
    ) {
        return new DicomApplicationService(storeDomainService, queryDomainService, retrieveDomainService);
    }

    @Bean
    AetApplicationService aetApplicationService(AetManager aetManager) {
        return new AetApplicationService(aetManager);
    }

    @Bean
    SyncJobConfigApplicationService syncJobConfigApplicationService(SyncJobConfigRepository repository) {
        return new SyncJobConfigApplicationService(repository);
    }

    @Bean
    RemoteDicomApplicationService remoteDicomApplicationService(
            AetApplicationService aetApplicationService,
            RemoteDicomGateway remoteDicomGateway
    ) {
        return new RemoteDicomApplicationService(aetApplicationService, remoteDicomGateway);
    }

    @Bean
    ViewerApplicationService viewerApplicationService(DicomApplicationService dicomApplicationService) {
        return new ViewerApplicationService(dicomApplicationService);
    }
}
