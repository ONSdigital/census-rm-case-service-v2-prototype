package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.entity.RefusalType.EXTRAORDINARY_REFUSAL;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementRefusalEvent;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.RefusalType;

@RunWith(MockitoJUnitRunner.class)
public class RefusalServiceTest {

  private static final String REFUSAL_RECEIVED = "Refusal Received";
  private static final UUID TEST_CASE_ID = UUID.randomUUID();

  @Mock private CaseService caseService;

  @Mock private EventLogger eventLogger;

  @InjectMocks RefusalService underTest;

  @Test
  public void testExtraordinaryRefusalForCase() {
    // GIVEN
    ResponseManagementEvent managementEvent =
        getTestResponseManagementRefusalEvent(RefusalTypeDTO.EXTRAORDINARY_REFUSAL);
    CollectionCase collectionCase = managementEvent.getPayload().getRefusal().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID);
    collectionCase.setRefusalReceived(null);
    Case testCase = getRandomCase();
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processRefusal(managementEvent, messageTimestamp);

    // THEN

    InOrder inOrder = inOrder(caseService, eventLogger);

    inOrder.verify(caseService).getCaseByCaseId(any(UUID.class));

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    inOrder
        .verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    Metadata metadata = metadataArgumentCaptor.getValue();
    verifyNoMoreInteractions(caseService);

    assertThat(actualCase.getRefusalReceived()).isEqualTo(RefusalType.EXTRAORDINARY_REFUSAL);
    assertThat(metadata.getCauseEventType()).isEqualTo(EventTypeDTO.REFUSAL_RECEIVED);
    assertThat(metadata.getFieldDecision()).isEqualTo(ActionInstructionType.CANCEL);
    inOrder
        .verify(eventLogger, times(1))
        .logCaseEvent(
            eq(testCase),
            any(OffsetDateTime.class),
            eq(REFUSAL_RECEIVED),
            eq(EventType.REFUSAL_RECEIVED),
            eq(managementEvent.getEvent()),
            any(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testHardRefusalCase() {
    // GIVEN
    ResponseManagementEvent managementEvent =
        getTestResponseManagementRefusalEvent(RefusalTypeDTO.HARD_REFUSAL);
    CollectionCase collectionCase = managementEvent.getPayload().getRefusal().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID);
    collectionCase.setRefusalReceived(RefusalTypeDTO.HARD_REFUSAL);
    Case testCase = getRandomCase();
    testCase.setRefusalReceived(null);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processRefusal(managementEvent, messageTimestamp);

    // THEN

    InOrder inOrder = inOrder(caseService, eventLogger);

    inOrder.verify(caseService).getCaseByCaseId(any(UUID.class));

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    inOrder
        .verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    Metadata metadata = metadataArgumentCaptor.getValue();
    verifyNoMoreInteractions(caseService);

    assertThat(actualCase.getRefusalReceived()).isEqualTo(RefusalType.HARD_REFUSAL);
    assertThat(metadata.getCauseEventType()).isEqualTo(EventTypeDTO.REFUSAL_RECEIVED);
    assertThat(metadata.getFieldDecision()).isEqualTo(ActionInstructionType.CANCEL);
    inOrder
        .verify(eventLogger, times(1))
        .logCaseEvent(
            eq(testCase),
            any(OffsetDateTime.class),
            eq(REFUSAL_RECEIVED),
            eq(EventType.REFUSAL_RECEIVED),
            eq(managementEvent.getEvent()),
            any(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testRefusalForCaseFromField() {
    // GIVEN
    ResponseManagementEvent managementEvent =
        getTestResponseManagementRefusalEvent(RefusalTypeDTO.HARD_REFUSAL);
    managementEvent.getEvent().setChannel("FIELD");
    CollectionCase collectionCase = managementEvent.getPayload().getRefusal().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID);
    collectionCase.setRefusalReceived(RefusalTypeDTO.HARD_REFUSAL);
    Case testCase = getRandomCase();
    testCase.setRefusalReceived(null);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processRefusal(managementEvent, messageTimestamp);

    // THEN
    InOrder inOrder = inOrder(caseService, eventLogger);

    inOrder.verify(caseService).getCaseByCaseId(any(UUID.class));

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<Metadata> metadataArgumentCaptor = ArgumentCaptor.forClass(Metadata.class);
    inOrder
        .verify(caseService)
        .saveCaseAndEmitCaseUpdatedEvent(
            caseArgumentCaptor.capture(), metadataArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    Metadata metadata = metadataArgumentCaptor.getValue();
    verifyNoMoreInteractions(caseService);

    assertThat(actualCase.getRefusalReceived()).isEqualTo(RefusalType.HARD_REFUSAL);
    assertThat(metadata.getCauseEventType()).isEqualTo(EventTypeDTO.REFUSAL_RECEIVED);
    assertThat(metadata.getFieldDecision()).isNull();
    inOrder
        .verify(eventLogger, times(1))
        .logCaseEvent(
            eq(testCase),
            any(OffsetDateTime.class),
            eq(REFUSAL_RECEIVED),
            eq(EventType.REFUSAL_RECEIVED),
            eq(managementEvent.getEvent()),
            any(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }

  @Test
  public void testHardRefusalAgainstAlreadyExtraordinaryRefusedCaseJustRecordsEvent() {
    // GIVEN
    ResponseManagementEvent managementEvent =
        getTestResponseManagementRefusalEvent(RefusalTypeDTO.HARD_REFUSAL);
    CollectionCase collectionCase = managementEvent.getPayload().getRefusal().getCollectionCase();
    collectionCase.setId(TEST_CASE_ID);
    collectionCase.setRefusalReceived(RefusalTypeDTO.HARD_REFUSAL);
    Case testCase = getRandomCase();
    testCase.setRefusalReceived(EXTRAORDINARY_REFUSAL);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    when(caseService.getCaseByCaseId(TEST_CASE_ID)).thenReturn(testCase);

    // WHEN
    underTest.processRefusal(managementEvent, messageTimestamp);

    // THEN

    verify(caseService).getCaseByCaseId(TEST_CASE_ID);
    verifyNoMoreInteractions(caseService);

    verify(eventLogger, times(1))
        .logCaseEvent(
            eq(testCase),
            any(OffsetDateTime.class),
            eq("Hard Refusal Received for case already marked Extraordinary refused"),
            eq(EventType.REFUSAL_RECEIVED),
            eq(managementEvent.getEvent()),
            any(),
            eq(messageTimestamp));
  }
}
