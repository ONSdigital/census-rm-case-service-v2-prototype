package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.generateUacCreatedEvent;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.test.util.ReflectionTestUtils;
import uk.gov.ons.census.casesvc.cache.UacQidCache;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.dto.UacQidDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.UacQidLinkRepository;

@RunWith(MockitoJUnitRunner.class)
public class UacServiceTest {

  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock UacQidLinkRepository uacQidLinkRepository;

  @Mock CaseService caseService;

  @Mock RabbitTemplate rabbitTemplate;

  @Mock UacQidCache uacCache;

  @Mock EventLogger eventLogger;

  @InjectMocks UacService underTest;

  @Test
  public void testSaveUacQidLinkEnglandHousehold() {
    // Given
    Case caze = new Case();

    UacQidDTO uacQidDTO = new UacQidDTO();
    uacQidDTO.setUac("testuac");
    uacQidDTO.setQid("01testqid");
    when(uacCache.getUacQidPair(anyInt())).thenReturn(uacQidDTO);

    EventDTO dummyEvent = new EventDTO();
    dummyEvent.setSource("DUMMY");
    dummyEvent.setChannel("DUMMY");

    // When
    UacQidLink result;
    result = underTest.buildUacQidLink(caze, 1, null, dummyEvent);

    // Then
    assertEquals("01", result.getQid().substring(0, 2));
    verify(uacCache).getUacQidPair(eq(1));
  }

  @Test
  public void testEmitUacUpdatedEvent() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setUac("12345");
    uacQidLink.setQid("01234567890");
    Case caze = new Case();
    UUID caseUuid = UUID.randomUUID();
    caze.setCaseId(caseUuid);
    ReflectionTestUtils.setField(underTest, "outboundExchange", "TEST_EXCHANGE");

    // When
    underTest.saveAndEmitUacUpdatedEvent(uacQidLink);

    // Then
    ArgumentCaptor<ResponseManagementEvent> responseManagementEventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(rabbitTemplate)
        .convertAndSend(
            eq("TEST_EXCHANGE"),
            eq("event.uac.update"),
            responseManagementEventArgumentCaptor.capture());
    assertEquals(
        "12345", responseManagementEventArgumentCaptor.getValue().getPayload().getUac().getUac());
    assertEquals(
        "H", responseManagementEventArgumentCaptor.getValue().getPayload().getUac().getFormType());
  }

  @Test
  public void testIngestUacCreatedEventSavesUacQidLink() {
    // Given
    Case linkedCase = getRandomCase();
    ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(linkedCase);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId()))
        .thenReturn(linkedCase);
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);

    // When
    underTest.ingestUacCreatedEvent(
        uacCreatedEvent, messageTimestamp, uacCreatedEvent.getPayload().getUacQidCreated());

    // Then
    verify(uacQidLinkRepository).save(uacQidLinkArgumentCaptor.capture());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getQid(),
        uacQidLinkArgumentCaptor.getValue().getQid());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getUac(),
        uacQidLinkArgumentCaptor.getValue().getUac());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getCaseId(),
        uacQidLinkArgumentCaptor.getValue().getCaze().getCaseId());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getBatchId(),
        uacQidLinkArgumentCaptor.getValue().getBatchId());
  }

  @Test
  public void testIngestUacCreatedEventEmitsUacUpdatedEvent() {
    // Given
    Case linkedCase = getRandomCase();
    ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(linkedCase);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId()))
        .thenReturn(linkedCase);

    ArgumentCaptor<ResponseManagementEvent> responseManagementEventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    ReflectionTestUtils.setField(underTest, "outboundExchange", "TEST_EXCHANGE");

    // When
    underTest.ingestUacCreatedEvent(
        uacCreatedEvent, messageTimestamp, uacCreatedEvent.getPayload().getUacQidCreated());

    // Then
    verify(rabbitTemplate)
        .convertAndSend(
            eq("TEST_EXCHANGE"),
            eq("event.uac.update"),
            responseManagementEventArgumentCaptor.capture());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getQid(),
        responseManagementEventArgumentCaptor
            .getValue()
            .getPayload()
            .getUac()
            .getQuestionnaireId());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getUac(),
        responseManagementEventArgumentCaptor.getValue().getPayload().getUac().getUac());
    assertEquals(
        uacCreatedEvent.getPayload().getUacQidCreated().getCaseId(),
        responseManagementEventArgumentCaptor.getValue().getPayload().getUac().getCaseId());
  }

  @Test
  public void testIngestUacCreatedEventLogsRmUacCreatedEvent() {
    // Given
    Case linkedCase = getRandomCase();
    ResponseManagementEvent uacCreatedEvent = generateUacCreatedEvent(linkedCase);
    when(caseService.getCaseByCaseId(uacCreatedEvent.getPayload().getUacQidCreated().getCaseId()))
        .thenReturn(linkedCase);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    // When
    underTest.ingestUacCreatedEvent(
        uacCreatedEvent, messageTimestamp, uacCreatedEvent.getPayload().getUacQidCreated());

    // Then
    verify(eventLogger)
        .logUacQidEvent(
            any(UacQidLink.class),
            any(OffsetDateTime.class),
            eq("RM UAC QID pair created"),
            eq(EventType.RM_UAC_CREATED),
            eq(uacCreatedEvent.getEvent()),
            any(),
            eq(messageTimestamp));
  }

  @Test
  public void testCreateUacQidLinkedToCCSCase() {
    // Given
    Case expectedCase = new Case();
    expectedCase.setCaseId(TEST_CASE_ID);
    expectedCase.setSurvey("CCS");

    EventDTO dummyEvent = new EventDTO();
    dummyEvent.setSource("DUMMY");
    dummyEvent.setChannel("DUMMY");

    UacQidDTO expectedUacQidDTO = new UacQidDTO();
    when(uacCache.getUacQidPair(71)).thenReturn(expectedUacQidDTO);

    // When
    UacQidLink actualUacQidLink = underTest.createUacQidLinkedToCCSCase(expectedCase, dummyEvent);

    // Then
    assertThat(actualUacQidLink.getCaze().getSurvey()).isEqualTo("CCS");
    assertThat(actualUacQidLink.getCaze()).isNotNull();
    assertThat(actualUacQidLink.isCcsCase()).isTrue();
    assertThat(actualUacQidLink.getMetadata().getChannel()).isEqualTo("DUMMY");
    assertThat(actualUacQidLink.getMetadata().getSource()).isEqualTo("DUMMY");

    Case actualCase = actualUacQidLink.getCaze();
    assertThat(actualCase.getCaseId()).isEqualTo(TEST_CASE_ID);
    assertThat(actualCase.getSurvey()).isEqualTo("CCS");
  }

  @Test
  public void testFindUacLinkExists() {
    // Given
    UacQidLink expectedUacQidLink = new UacQidLink();
    expectedUacQidLink.setId(UUID.randomUUID());

    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.of(expectedUacQidLink));

    // When
    UacQidLink actualUacQidLink = underTest.findByQid("Test qid");

    // Then
    assertThat(actualUacQidLink.getId()).isEqualTo(expectedUacQidLink.getId());
  }

  @Test(expected = RuntimeException.class)
  public void testCantFindUacLink() {
    when(uacQidLinkRepository.findByQid(anyString())).thenReturn(Optional.empty());
    underTest.findByQid("Test qid");
  }
}
