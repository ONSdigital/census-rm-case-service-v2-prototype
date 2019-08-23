package uk.gov.ons.census.casesvc.service;

import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import uk.gov.ons.census.casesvc.client.UacQidServiceClient;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacDTO;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.EventHelper;
import uk.gov.ons.census.casesvc.utility.Sha256Helper;

@Service
public class UacService {
  private static final String UAC_UPDATE_ROUTING_KEY = "event.uac.update";

  private final UacQidLinkRepository uacQidLinkRepository;
  private final RabbitTemplate rabbitTemplate;
  private final UacQidServiceClient uacQidServiceClient;
  private final EventLogger eventLogger;
  private final CaseService caseService;

  @Value("${queueconfig.case-event-exchange}")
  private String outboundExchange;

  public UacService(
      UacQidLinkRepository uacQidLinkRepository,
      RabbitTemplate rabbitTemplate,
      UacQidServiceClient uacQidServiceClient,
      EventLogger eventLogger,
      CaseService caseService) {
    this.rabbitTemplate = rabbitTemplate;
    this.uacQidServiceClient = uacQidServiceClient;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.eventLogger = eventLogger;
    this.caseService = caseService;
  }

  public UacQidLink generateAndSaveUacQidLink(Case caze, int questionnaireType) {
    return generateAndSaveUacQidLink(caze, questionnaireType, null);
  }

  public UacQidLink generateAndSaveUacQidLink(Case caze, int questionnaireType, UUID batchId) {
    UacQidDTO uacQid = uacQidServiceClient.generateUacQid(questionnaireType);
    return createAndSaveUacQidLink(caze, batchId, uacQid.getUac(), uacQid.getQid());
  }

  private UacQidLink createAndSaveUacQidLink(
      Case linkedCase, UUID batchId, String uac, String qid) {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac(uac);
    uacQidLink.setCaze(linkedCase);
    uacQidLink.setBatchId(batchId);
    uacQidLink.setActive(true);
    uacQidLink.setQid(qid);

    uacQidLinkRepository.save(uacQidLink);
    return uacQidLink;
  }

  public PayloadDTO emitUacUpdatedEvent(UacQidLink uacQidLink, Case caze) {
    return emitUacUpdatedEvent(uacQidLink, caze, true);
  }

  public PayloadDTO emitUacUpdatedEvent(UacQidLink uacQidLink, Case caze, boolean active) {
    EventDTO eventDTO = EventHelper.createEventDTO(EventTypeDTO.UAC_UPDATED);

    UacDTO uac = new UacDTO();
    uac.setQuestionnaireId(uacQidLink.getQid());
    uac.setUacHash(Sha256Helper.hash(uacQidLink.getUac()));
    uac.setUac(uacQidLink.getUac());
    uac.setActive(active);

    if (caze != null) {
      uac.setCaseId(caze.getCaseId().toString());
      uac.setCaseType(caze.getAddressType());
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

  public void ingestUacCreatedEvent(ResponseManagementEvent uacCreatedEvent) {
    Case linkedCase =
        caseService.getCaseByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId());

    UacQidLink uacQidLink =
        createAndSaveUacQidLink(
            linkedCase,
            null,
            uacCreatedEvent.getPayload().getUacQidCreated().getUac(),
            uacCreatedEvent.getPayload().getUacQidCreated().getQid());

    emitUacUpdatedEvent(uacQidLink, linkedCase);

    eventLogger.logUacQidEvent(
        uacQidLink,
        uacCreatedEvent.getEvent().getDateTime(),
        OffsetDateTime.now(),
        "RM UAC QID pair created",
        EventType.RM_UAC_CREATED,
        uacCreatedEvent.getEvent(),
        convertObjectToJson(uacCreatedEvent.getPayload()));
  }
}