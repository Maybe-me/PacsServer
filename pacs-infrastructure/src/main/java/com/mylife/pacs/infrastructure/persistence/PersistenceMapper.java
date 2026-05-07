package com.mylife.pacs.infrastructure.persistence;

import com.mylife.pacs.common.util.JsonUtil;
import com.mylife.pacs.domain.model.AetNode;
import com.mylife.pacs.domain.model.PacsInstance;
import com.mylife.pacs.domain.model.PacsPatient;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.model.SyncJobConfig;
import com.mylife.pacs.infrastructure.persistence.entity.AetNodeEntity;
import com.mylife.pacs.infrastructure.persistence.entity.InstanceEntity;
import com.mylife.pacs.infrastructure.persistence.entity.PatientEntity;
import com.mylife.pacs.infrastructure.persistence.entity.SeriesEntity;
import com.mylife.pacs.infrastructure.persistence.entity.StudyEntity;
import com.mylife.pacs.infrastructure.persistence.entity.SyncJobConfigEntity;

final class PersistenceMapper {

    private PersistenceMapper() {
    }

    static PacsPatient toDomain(PatientEntity entity) {
        return new PacsPatient(
                entity.getId(),
                entity.getPatientId(),
                entity.getIssuerOfPatientId(),
                entity.getPatientName(),
                entity.getPatientSex(),
                entity.getPatientBirthDate(),
                JsonUtil.readMap(entity.getExtraTagsJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static PacsStudy toDomain(StudyEntity entity) {
        return new PacsStudy(
                entity.getId(),
                entity.getPatient().getId(),
                entity.getStudyInstanceUid(),
                entity.getAccessionNo(),
                entity.getStudyDate(),
                entity.getStudyTime(),
                entity.getStudyDescription(),
                entity.getModalitiesInStudy(),
                entity.getReferringDoctor(),
                entity.getNumSeries(),
                entity.getNumInstances(),
                JsonUtil.readMap(entity.getExtraTagsJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static PacsSeries toDomain(SeriesEntity entity) {
        return new PacsSeries(
                entity.getId(),
                entity.getStudy().getId(),
                entity.getSeriesInstanceUid(),
                entity.getModality(),
                entity.getSeriesDescription(),
                entity.getBodyPartExamined(),
                entity.getSeriesNumber(),
                entity.getNumInstances(),
                JsonUtil.readMap(entity.getExtraTagsJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static PacsInstance toDomain(InstanceEntity entity) {
        return new PacsInstance(
                entity.getId(),
                entity.getSeries().getId(),
                entity.getSopInstanceUid(),
                entity.getSopClassUid(),
                entity.getTransferSyntaxUid(),
                entity.getInstanceNumber(),
                entity.getFilePath(),
                entity.getFileSize(),
                entity.getFileMd5(),
                entity.getStorageType(),
                entity.getStorageBucket(),
                entity.getStorageKey(),
                JsonUtil.readMap(entity.getExtraTagsJson()),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static AetNode toDomain(AetNodeEntity entity) {
        return new AetNode(
                entity.getId(),
                entity.getAet(),
                entity.getHost(),
                entity.getPort(),
                entity.getRole(),
                entity.getNodeName(),
                entity.getDescription(),
                entity.isEnabled(),
                entity.getLastVerifiedAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }

    static SyncJobConfig toDomain(SyncJobConfigEntity entity) {
        return new SyncJobConfig(
                entity.getId(),
                entity.getJobName(),
                entity.getJobType(),
                entity.getTargetAet(),
                entity.getDestinationAet(),
                entity.getPatientId(),
                entity.getModality(),
                entity.getStudyDateLookbackDays(),
                entity.isPreventLoopToSource(),
                entity.isSkipRemoteDuplicates(),
                entity.getMaxStudiesPerRun(),
                entity.getMaxInstancesPerRun(),
                entity.getThrottleDelayMs(),
                JsonUtil.readList(entity.getSourceAetAllowList()),
                JsonUtil.readList(entity.getSourceAetBlockList()),
                entity.getMaxRetryCount(),
                entity.getFailureThreshold(),
                entity.isPaused(),
                entity.getConsecutiveFailureCount(),
                entity.getLastErrorCategory(),
                entity.getLastErrorMessage(),
                entity.getLastSuccessAt(),
                entity.getLastFailureAt(),
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
