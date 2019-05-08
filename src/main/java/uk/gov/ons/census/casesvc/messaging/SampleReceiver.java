package uk.gov.ons.census.casesvc.messaging;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.UUID;

import com.godaddy.logging.Logger;
import com.godaddy.logging.LoggerFactory;
import ma.glasnost.orika.MapperFacade;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.Address;
import uk.gov.ons.census.casesvc.model.dto.CollectionCase;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.model.dto.Event;
import uk.gov.ons.census.casesvc.model.dto.EventType;
import uk.gov.ons.census.casesvc.model.dto.Payload;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.Uac;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.utility.IacDispenser;
import uk.gov.ons.census.casesvc.utility.QidCreator;
import uk.gov.ons.census.casesvc.utility.Sha256Helper;

@MessageEndpoint
public class SampleReceiver {
  private static final Logger log = LoggerFactory.getLogger(SampleReceiver.class);

  private static final String UNKNOWN_COUNTRY_ERROR = "Unknown Country";
  private static final String UNEXPECTED_CASE_TYPE_ERROR = "Unexpected Case Type";

  private static final String EVENT_SOURCE = "CASE_SERVICE";
  private static final String SURVEY = "CENSUS";
  private static final String EVENT_CHANNEL = "RM";
  private static final String CASE_CREATED_EVENT_DESCRIPTION = "Case created";
  private static final String UAC_QID_LINKED_EVENT_DESCRIPTION = "UAC QID linked";

  private final CaseRepository caseRepository;
  private final UacQidLinkRepository uacQidLinkRepository;
  private final EventRepository eventRepository;
  private final RabbitTemplate rabbitTemplate;
  private final IacDispenser iacDispenser;
  private final QidCreator qidCreator;
  private final MapperFacade mapperFacade;

  @Value("${queueconfig.emit-case-event-exchange}")
  private String emitCaseEventExchange;

  public SampleReceiver(
      CaseRepository caseRepository,
      UacQidLinkRepository uacQidLinkRepository,
      EventRepository eventRepository,
      RabbitTemplate rabbitTemplate,
      IacDispenser iacDispenser,
      QidCreator qidCreator,
      MapperFacade mapperFacade) {
    this.caseRepository = caseRepository;
    this.rabbitTemplate = rabbitTemplate;
    this.iacDispenser = iacDispenser;
    this.uacQidLinkRepository = uacQidLinkRepository;
    this.eventRepository = eventRepository;
    this.qidCreator = qidCreator;
    this.mapperFacade = mapperFacade;
  }

  @Transactional
  @ServiceActivator(inputChannel = "caseSampleInputChannel")
  public void receiveMessage(CreateCaseSample createCaseSample) {
    Case caze = saveCase(createCaseSample);
    int questionnaireType = calculateQuestionnaireType(caze.getTreatmentCode());
    UacQidLink uacQidLink = saveUacQidLink(caze, questionnaireType);
    emitUacUpdatedEvent(uacQidLink, caze);
    emitCaseCreatedEvent(caze);
    logEvent(uacQidLink, CASE_CREATED_EVENT_DESCRIPTION);
    logEvent(uacQidLink, UAC_QID_LINKED_EVENT_DESCRIPTION);
    if (isQuestionnaireWelsh(caze.getTreatmentCode())) {
      uacQidLink = saveUacQidLink(caze, 3);
      emitUacUpdatedEvent(uacQidLink, caze);
      logEvent(uacQidLink, UAC_QID_LINKED_EVENT_DESCRIPTION);
    }
  }

  private Case saveCase(CreateCaseSample createCaseSample) {
    Case caze = mapperFacade.map(createCaseSample, Case.class);
    caze.setCaseId(UUID.randomUUID());
    caze.setState(CaseState.ACTIONABLE);
    caze = caseRepository.saveAndFlush(caze);
    return caze;
  }

  private UacQidLink saveUacQidLink(Case caze, int questionnaireType) {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setId(UUID.randomUUID());
    uacQidLink.setUac(iacDispenser.getIacCode());
    uacQidLink.setCaze(caze);
    uacQidLink = uacQidLinkRepository.saveAndFlush(uacQidLink);

    // The unique number has been generated by the DB by this point, so we can use it
    String qid = qidCreator.createQid(questionnaireType, uacQidLink.getUniqueNumber());
    uacQidLink.setQid(qid);
    uacQidLinkRepository.save(uacQidLink);

    return uacQidLink;
  }

  private void logEvent(UacQidLink uacQidLink, String eventDescription) {
    uk.gov.ons.census.casesvc.model.entity.Event loggedEvent =
        new uk.gov.ons.census.casesvc.model.entity.Event();
    loggedEvent.setId(UUID.randomUUID());
    loggedEvent.setEventDate(new Date());
    loggedEvent.setEventDescription(eventDescription);
    loggedEvent.setUacQidLink(uacQidLink);
    eventRepository.save(loggedEvent);
  }

  private void emitUacUpdatedEvent(UacQidLink uacQidLink, Case caze) {
    Event event = createEvent(EventType.UAC_UPDATED);

    Uac uac = new Uac();
    uac.setActive(true);
    uac.setCaseId(caze.getCaseId().toString());
    uac.setCaseType(caze.getAddressType());
    uac.setCollectionExerciseId(caze.getCollectionExerciseId());
    uac.setQuestionnaireId(uacQidLink.getQid());
    uac.setUacHash(Sha256Helper.hash(uacQidLink.getUac()));
    uac.setUac(uacQidLink.getUac());

    Payload payload = new Payload();
    payload.setUac(uac);
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    responseManagementEvent.setEvent(event);
    responseManagementEvent.setPayload(payload);

    rabbitTemplate.convertAndSend(emitCaseEventExchange, "", responseManagementEvent);
  }

  private void emitCaseCreatedEvent(Case caze) {
    Event event = createEvent(EventType.CASE_CREATED);
    Address address = createAddress(caze);
    CollectionCase collectionCase = createCollectionCase(caze, address);
    Payload payload = new Payload();
    payload.setCollectionCase(collectionCase);
    ResponseManagementEvent responseManagementEvent = new ResponseManagementEvent();
    responseManagementEvent.setEvent(event);
    responseManagementEvent.setPayload(payload);

    rabbitTemplate.convertAndSend(emitCaseEventExchange, "", responseManagementEvent);
  }

  private Event createEvent(EventType eventType) {
    Event event = new Event();
    event.setChannel(EVENT_CHANNEL);
    event.setSource(EVENT_SOURCE);
    event.setDateTime(LocalDateTime.now().toString());
    event.setTransactionId(UUID.randomUUID().toString());
    event.setType(eventType);
    return event;
  }

  private Address createAddress(Case caze) {
    Address address = new Address();
    address.setAddressLine1(caze.getAddressLine1());
    address.setAddressLine2(caze.getAddressLine2());
    address.setAddressLine3(caze.getAddressLine3());
    address.setAddressType(caze.getAddressType());
    address.setArid(caze.getArid());
    address.setRegion(caze.getRgn().substring(0, 1));
    address.setEstabType(caze.getEstabType());
    address.setLatitude(caze.getLatitude());
    address.setLongitude(caze.getLongitude());
    address.setPostcode(caze.getPostcode());
    address.setTownName(caze.getTownName());
    return address;
  }

  private CollectionCase createCollectionCase(Case caze, Address address) {
    CollectionCase collectionCase = new CollectionCase();
    collectionCase.setActionableFrom(LocalDateTime.now().toString());
    collectionCase.setAddress(address);
    collectionCase.setCaseRef(Long.toString(caze.getCaseRef()));
    collectionCase.setCollectionExerciseId(caze.getCollectionExerciseId());
    collectionCase.setId(caze.getCaseId().toString());
    collectionCase.setState(caze.getState().toString());
    collectionCase.setSurvey(SURVEY);

    // Below this line is extra data potentially needed by Action Scheduler - can be ignored by RM
    collectionCase.setActionPlanId(caze.getActionPlanId());
    collectionCase.setTreatmentCode(caze.getTreatmentCode());
    collectionCase.setOa(caze.getOa());
    collectionCase.setLsoa(caze.getLsoa());
    collectionCase.setMsoa(caze.getMsoa());
    collectionCase.setLad(caze.getLad());
    collectionCase.setHtcWillingness(caze.getHtcWillingness());
    collectionCase.setHtcDigital(caze.getHtcDigital());

    return collectionCase;
  }

  private boolean isQuestionnaireWelsh(String treatmentCode) {
    return (treatmentCode.startsWith("HH_Q") && treatmentCode.endsWith("W"));
  }

  private int calculateQuestionnaireType(String treatmentCode) {
    String country = treatmentCode.substring(treatmentCode.length() - 1);
    if (!country.equals("E") && !country.equals("W") && !country.equals("N")) {
      log.with("treatment_code", treatmentCode).error(UNKNOWN_COUNTRY_ERROR);
      throw new IllegalArgumentException();
    }

    if (treatmentCode.startsWith("HH")) {
      switch (country) {
        case "E":
          return 1;
        case "W":
          return 2;
        case "N":
          return 4;
      }
    } else if (treatmentCode.startsWith("CI")) {
      switch (country) {
        case "E":
          return 21;
        case "W":
          return 22;
        case "N":
          return 24;
      }
    } else if (treatmentCode.startsWith("CE")) {
      switch (country) {
        case "E":
          return 31;
        case "W":
          return 32;
        case "N":
          return 34;
      }
    } else {
      log.with("treatment_code", treatmentCode).error(UNEXPECTED_CASE_TYPE_ERROR);
      throw new IllegalArgumentException();
    }

    throw new RuntimeException(); // This code should be unreachable
  }

}
