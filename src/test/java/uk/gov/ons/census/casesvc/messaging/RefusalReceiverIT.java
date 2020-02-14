package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.convertJsonToObject;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementRefusalEvent;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.ons.census.casesvc.model.dto.ActionInstructionType;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.EventTypeDTO;
import uk.gov.ons.census.casesvc.model.dto.RefusalDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;
import uk.gov.ons.census.casesvc.testutil.RabbitQueueHelper;

@ContextConfiguration
@ActiveProfiles("test")
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@RunWith(SpringJUnit4ClassRunner.class)
public class RefusalReceiverIT {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Value("${queueconfig.refusal-response-inbound-queue}")
  private String inboundQueue;

  @Value("${queueconfig.action-scheduler-queue}")
  private String actionQueue;

  @Value("${queueconfig.rh-case-queue}")
  private String rhCaseQueue;

  @Autowired private RabbitQueueHelper rabbitQueueHelper;
  @Autowired private CaseRepository caseRepository;
  @Autowired private EventRepository eventRepository;
  @Autowired private UacQidLinkRepository uacQidLinkRepository;

  @Before
  @Transactional
  public void setUp() {
    rabbitQueueHelper.purgeQueue(inboundQueue);
    rabbitQueueHelper.purgeQueue(actionQueue);
    rabbitQueueHelper.purgeQueue(rhCaseQueue);
    eventRepository.deleteAllInBatch();
    uacQidLinkRepository.deleteAllInBatch();
    caseRepository.deleteAllInBatch();
  }

  @Test
  public void testRefusalEmitsMessageAndLogsEventForCase()
      throws InterruptedException, IOException {
    // GIVEN
    BlockingQueue<String> outboundQueue = rabbitQueueHelper.listen(rhCaseQueue);

    Case caze = getRandomCase();
    caze.setCaseId(TEST_CASE_ID);
    caze.setRefusalReceived(false);
    caze.setSurvey("CENSUS");
    caze.setUacQidLinks(null);
    caze.setEvents(null);
    caze.setAddressLevel("U");
    caseRepository.saveAndFlush(caze);

    OffsetDateTime cazeCreatedTime =
        caseRepository.findByCaseId(TEST_CASE_ID).get().getCreatedDateTime();

    ResponseManagementEvent managementEvent = getTestResponseManagementRefusalEvent();
    managementEvent.getEvent().setTransactionId(UUID.randomUUID());
    RefusalDTO expectedRefusal = managementEvent.getPayload().getRefusal();
    expectedRefusal.getCollectionCase().setId(TEST_CASE_ID.toString());

    String json = convertObjectToJson(managementEvent);
    Message message =
        MessageBuilder.withBody(json.getBytes())
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .build();

    // WHEN
    rabbitQueueHelper.sendMessage(inboundQueue, message);

    // THEN
    ResponseManagementEvent responseManagementEvent =
        rabbitQueueHelper.checkExpectedMessageReceived(outboundQueue);

    EventDTO eventDTO = responseManagementEvent.getEvent();
    assertThat(eventDTO.getType()).isEqualTo(EventTypeDTO.CASE_UPDATED);
    assertThat(eventDTO.getSource()).isEqualTo("CASE_SERVICE");
    assertThat(eventDTO.getChannel()).isEqualTo("RM");

    Case actualCase = caseRepository.findByCaseId(TEST_CASE_ID).get();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    assertThat(actualCase.isRefusalReceived()).isTrue();
    assertThat(actualCase.getLastUpdated()).isNotEqualTo(cazeCreatedTime);

    // check the metadata is included with field close decision
    assertThat(responseManagementEvent.getPayload().getMetadata().getFieldDecision())
        .isEqualTo(ActionInstructionType.CLOSE);
    assertThat(responseManagementEvent.getPayload().getMetadata().getCauseEventType())
        .isEqualTo(EventTypeDTO.REFUSAL_RECEIVED);

    List<Event> events = eventRepository.findAll();
    assertThat(events.size()).isEqualTo(1);

    RefusalDTO actualRefusal =
        convertJsonToObject(events.get(0).getEventPayload(), RefusalDTO.class);
    assertThat(actualRefusal.getType()).isEqualTo(expectedRefusal.getType());
    assertThat(actualRefusal.getReport()).isEqualTo(expectedRefusal.getReport());
    assertThat(actualRefusal.getAgentId()).isEqualTo(expectedRefusal.getAgentId());
    assertThat(actualRefusal.getCollectionCase().getId())
        .isEqualTo(expectedRefusal.getCollectionCase().getId());
  }
}
