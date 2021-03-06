package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.CONT_FORM_TYPE;
import static uk.gov.ons.census.casesvc.utility.FormTypeHelper.mapQuestionnaireTypeToFormType;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.cache.UacQidCache;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacCreatedDTO;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.entity.UacQidLinkMetadata;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.EventHelper;
import uk.gov.ons.census.casesvc.utility.Sha256Helper;

@Service
public class UacService {
  private static final String UAC_UPDATE_ROUTING_KEY = "event.uac.update";
  private static final int CCS_INTERVIEWER_HOUSEHOLD_QUESTIONNAIRE_FOR_ENGLAND_AND_WALES = 71;

  private final UacQidLinkRepository uacQidLinkRepository;
  private final RabbitTemplate rabbitTemplate;
  private final UacQidCache uacQidCache;
  private final EventLogger eventLogger;
  private final CaseService caseService;

  @Value("${queueconfig.case-event-exchange}")
  private String outboundExchange;

  public UacService(
      UacQidLinkRepository uacQidLinkRepository,
      RabbitTemplate rabbitTemplate,
      UacQidCache uacQidCache,
      EventLogger eventLogger,
      CaseService caseService) {
    this.rabbitTemplate = rabbitTemplate;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.uacQidCache = uacQidCache;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
  }

  public UacQidLink buildUacQidLink(
      Case caze, int questionnaireType, UUID batchId, EventDTO sourceEvent) {
    UacQidDTO uacQid = uacQidCache.getUacQidPair(questionnaireType);
    return buildUacQidLink(caze, batchId, sourceEvent, uacQid.getUac(), uacQid.getQid());
  }

  private UacQidLink buildUacQidLink(
      Case linkedCase, UUID batchId, EventDTO sourceEvent, String uac, String qid) {
    UacQidLinkMetadata metadata = new UacQidLinkMetadata();
    metadata.setChannel(sourceEvent.getChannel());
    metadata.setSource(sourceEvent.getSource());

    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac(uac);
    uacQidLink.setCaze(linkedCase);
    uacQidLink.setBatchId(batchId);
    uacQidLink.setActive(true);
    uacQidLink.setQid(qid);
    uacQidLink.setMetadata(metadata);

    return uacQidLink;
  }

  public UacQidLink createUacQidLinkedToCCSCase(Case caze, EventDTO sourceEvent) {
    UacQidLink uacQidLink =
        buildUacQidLink(
            caze, CCS_INTERVIEWER_HOUSEHOLD_QUESTIONNAIRE_FOR_ENGLAND_AND_WALES, null, sourceEvent);
    uacQidLink.setCcsCase(true);

    uacQidLinkRepository.saveAndFlush(uacQidLink);

    return uacQidLink;
  }

  public PayloadDTO saveAndEmitUacUpdatedEvent(UacQidLink uacQidLink) {
    uacQidLinkRepository.save(uacQidLink);

    EventDTO eventDTO = EventHelper.createEventDTO(EventTypeDTO.UAC_UPDATED);

    UacDTO uac = new UacDTO();
    uac.setQuestionnaireId(uacQidLink.getQid());
    uac.setUacHash(Sha256Helper.hash(uacQidLink.getUac()));
    uac.setUac(uacQidLink.getUac());
    uac.setActive(uacQidLink.isActive());
    // It's perfectly possible to derive the Form Type from the supplied data, so RM should not be
    // forced to incorporate CENSUS business logic. It's for the CENSUS team to put business logic
    // wherever it's needed, which quite clearly is NOT here. TODO: Put the business logic elsewhere
    String formType = mapQuestionnaireTypeToFormType(uacQidLink.getQid());
    if (CONT_FORM_TYPE.equals(formType)) {
      // We want to send out null form type rather than "Cont" for continuation questionnaires
      // since they are not a valid form type in EQ/RH
      formType = null;
    }
    uac.setFormType(formType);

    Case caze = uacQidLink.getCaze();
    if (caze != null) {
      uac.setCaseId(caze.getCaseId());
      uac.setCaseType(caze.getCaseType());
      uac.setCollectionExerciseId(caze.getCollectionExerciseId());
      uac.setRegion(caze.getRegion());
    }

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setUac(uac);
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    responseManagementEvent.setEvent(eventDTO);
    responseManagementEvent.setPayload(payloadDTO);

    rabbitTemplate.convertAndSend(
        outboundExchange, UAC_UPDATE_ROUTING_KEY, responseManagementEvent);

    return payloadDTO;
  }

  public void ingestUacCreatedEvent(
      ResponseManagementEvent responseManagementEvent,
      OffsetDateTime messageTimestamp,
      UacCreatedDTO uacCreated) {
    Case linkedCase = caseService.getCaseByCaseId(uacCreated.getCaseId());

    UacQidLink uacQidLink =
        buildUacQidLink(
            linkedCase,
            uacCreated.getBatchId(),
            responseManagementEvent.getEvent(),
            uacCreated.getUac(),
            uacCreated.getQid());

    saveAndEmitUacUpdatedEvent(uacQidLink);

    eventLogger.logUacQidEvent(
        uacQidLink,
        responseManagementEvent.getEvent().getDateTime(),
        "RM UAC QID pair created",
        EventType.RM_UAC_CREATED,
        responseManagementEvent.getEvent(),
        responseManagementEvent.getPayload(),
        messageTimestamp);
  }

  public UacQidLink findByQid(String questionnaireId) {
    Optional<UacQidLink> uacQidLinkOpt = uacQidLinkRepository.findByQid(questionnaireId);

    if (uacQidLinkOpt.isEmpty()) {
      throw new RuntimeException(
          String.format("Questionnaire Id '%s' not found!", questionnaireId));
    }

    return uacQidLinkOpt.get();
  }
}
