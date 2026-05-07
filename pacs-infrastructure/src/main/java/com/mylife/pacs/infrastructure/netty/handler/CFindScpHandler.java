package com.mylife.pacs.infrastructure.netty.handler;

import com.mylife.pacs.application.DicomApplicationService;
import com.mylife.pacs.domain.model.PacsPatient;
import com.mylife.pacs.domain.model.PacsSeries;
import com.mylife.pacs.domain.model.PacsStudy;
import com.mylife.pacs.domain.service.PatientQueryCriteria;
import com.mylife.pacs.domain.service.SeriesQueryCriteria;
import com.mylife.pacs.domain.service.StudyQueryCriteria;
import com.mylife.pacs.infrastructure.netty.message.DicomCommandType;
import com.mylife.pacs.infrastructure.netty.message.DicomMessage;
import com.mylife.pacs.infrastructure.netty.message.DicomMessages;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@ChannelHandler.Sharable
public class CFindScpHandler extends SimpleChannelInboundHandler<DicomMessage> {

    private static final String QUERY_RETRIEVE_LEVEL = "QueryRetrieveLevel";

    private final DicomApplicationService dicomApplicationService;

    public CFindScpHandler(DicomApplicationService dicomApplicationService) {
        this.dicomApplicationService = dicomApplicationService;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext context, DicomMessage message) {
        if (message.commandType() != DicomCommandType.C_FIND_REQUEST) {
            context.fireChannelRead(message);
            return;
        }
        String level = message.attributes().getOrDefault(QUERY_RETRIEVE_LEVEL, message.attributes().get("00080052"));
        if (level == null || level.isBlank()) {
            context.writeAndFlush(DicomMessages.error(message, 400, "Missing QueryRetrieveLevel"));
            return;
        }
        List<Map<String, String>> results = switch (level.toUpperCase()) {
            case "PATIENT" -> queryPatients(message.attributes());
            case "STUDY" -> queryStudies(message.attributes());
            case "SERIES" -> querySeries(message.attributes());
            default -> throw new IllegalArgumentException("Unsupported QueryRetrieveLevel: " + level);
        };
        context.writeAndFlush(DicomMessages.cFindResponse(message, results));
    }

    private List<Map<String, String>> queryPatients(Map<String, String> attributes) {
        return dicomApplicationService.findPatients(new PatientQueryCriteria(
                        findOptional(attributes, "patientId", "00100020"),
                        findOptional(attributes, "issuerOfPatientId", "00100021"),
                        findOptional(attributes, "patientName", "00100010")
                )).stream()
                .map(this::toPatientResult)
                .toList();
    }

    private List<Map<String, String>> queryStudies(Map<String, String> attributes) {
        return dicomApplicationService.findStudies(new StudyQueryCriteria(
                        findOptional(attributes, "patientId", "00100020"),
                        findOptional(attributes, "issuerOfPatientId", "00100021"),
                        findOptional(attributes, "studyInstanceUid", "0020000D"),
                        findOptional(attributes, "accessionNo", "00080050"),
                        findOptional(attributes, "modality", "00080060"),
                        findOptional(attributes, "studyDateFrom", "studyDateFrom"),
                        findOptional(attributes, "studyDateTo", "studyDateTo")
                )).stream()
                .map(this::toStudyResult)
                .toList();
    }

    private List<Map<String, String>> querySeries(Map<String, String> attributes) {
        return dicomApplicationService.findSeries(new SeriesQueryCriteria(
                        findOptional(attributes, "studyInstanceUid", "0020000D"),
                        findOptional(attributes, "seriesInstanceUid", "0020000E"),
                        findOptional(attributes, "modality", "00080060")
                )).stream()
                .map(this::toSeriesResult)
                .toList();
    }

    private Map<String, String> toPatientResult(PacsPatient patient) {
        return Map.of(
                "00100020", patient.patientId(),
                "00100021", patient.issuerOfPatientId() == null ? "" : patient.issuerOfPatientId(),
                "00100010", patient.patientName() == null ? "" : patient.patientName()
        );
    }

    private Map<String, String> toStudyResult(PacsStudy study) {
        return Map.of(
                "0020000D", study.studyInstanceUid(),
                "00080050", study.accessionNo() == null ? "" : study.accessionNo(),
                "00080020", study.studyDate() == null ? "" : study.studyDate(),
                "00081030", study.studyDescription() == null ? "" : study.studyDescription(),
                "00080061", study.modalitiesInStudy() == null ? "" : study.modalitiesInStudy()
        );
    }

    private Map<String, String> toSeriesResult(PacsSeries series) {
        return Map.of(
                "0020000E", series.seriesInstanceUid(),
                "00080060", series.modality() == null ? "" : series.modality(),
                "0008103E", series.seriesDescription() == null ? "" : series.seriesDescription(),
                "00180015", series.bodyPartExamined() == null ? "" : series.bodyPartExamined()
        );
    }

    private String findOptional(Map<String, String> attributes, String alias, String tag) {
        String value = attributes.get(alias);
        if (value == null || value.isBlank()) {
            value = attributes.get(tag);
        }
        return value;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
        context.writeAndFlush(DicomMessages.error(null, 400, cause.getMessage()));
    }
}
