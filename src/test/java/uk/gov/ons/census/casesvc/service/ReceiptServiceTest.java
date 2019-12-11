package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.service.ReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;
import static uk.gov.ons.census.casesvc.utility.JsonHelper.convertObjectToJson;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.*;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class ReceiptServiceTest {
  private static final String TEST_CCS_QID_ID = "7134567890123456";
  private static final String TEST_QID_1 = "213456787654567";
  private static final String TEST_QID_2 = "213456787654568";
  private static final String TEST_UAC_1 = "123456789012233";
  private static final String TEST_UAC_2 = "123456789012237";

  @Mock private CaseService caseService;
  @Mock private UacService uacService;
  @Mock private EventLogger eventLogger;
  @Mock private FieldworkFollowupService fieldworkFollowupService;

  @InjectMocks ReceiptService underTest;

  @Test
  public void testReceiptForUacQidLinkedToUnreceiptedCase() {
    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);

    UacQidLink expectedUacQidLink =
        generateUacQidLinkedToCase(expectedCase, TEST_QID_1, TEST_UAC_1);

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();
    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());
    managementEvent.getPayload().getResponse().setUnreceipt(false);

    when(uacService.findByQid(anyString())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent);

    // then
    verify(uacService).findByQid(expectedReceipt.getQuestionnaireId());

    Case actualCase = checkCaseSavedAndEmitted(expectedCase);
    assertThat(actualCase.isReceiptReceived()).isTrue();

    UacQidLink actualUacQidLink = checkUacQidLinkSavedAndEmitted(expectedUacQidLink, false);
    assertThat(actualUacQidLink.isActive()).isFalse();

    assertThat(actualUacQidLink.isReceipted()).isTrue();

    verifiyEventLogged(expectedUacQidLink, managementEvent);
  }

  @Test
  public void testReceiptForUacQidLinkedToUnreceiptedCCSCase() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();
    expectedReceipt.setUnreceipt(false);

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);
    expectedCase.setCcsCase(true);
    UacQidLink expectedUacQidLink = generateRandomUacQidLinkedToCase(expectedCase);
    expectedUacQidLink.setQid(TEST_CCS_QID_ID);

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent);

    // then
    verify(uacService).findByQid(anyString());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveCase(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.getCaseId()).isEqualTo(expectedCase.getCaseId());
    assertThat(actualCase.isReceiptReceived()).isTrue();

    // Do we want to litter our code with this negative proofs
    verifyNoMoreInteractions(caseService);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveUacQidLink(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());
    assertThat(actualUacQidLink.isActive()).isFalse();

    verifiyEventLogged(expectedUacQidLink, managementEvent);
  }

  // Suggest that receipt testing for both CCS and normal cases call this function
  //  private void testUnreceiptedCaseIsReceiptedByUacQid(boolean isCCScase) {
  //
  //  }

  // scenario 1. PQRS receipt (QID A)processed and then Blank Q're (QID A). No other UAC/QID pair
  // against the case. HAPPY PATH
  @Test
  public void blankQuestionnaireHappyPath() {
    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(true);

    UacQidLink expectedUacQidLink =
        generateUacQidLinkedToCase(expectedCase, TEST_QID_1, TEST_UAC_1);
    when(uacService.findByQid(anyString())).thenReturn(expectedUacQidLink);

    ResponseManagementEvent managementEvent = createReceiptReceivedEvent();
    managementEvent.getPayload().getResponse().setUnreceipt(true);

    // when
    underTest.processReceipt(managementEvent);

    // then
    verify(uacService).findByQid(managementEvent.getPayload().getResponse().getQuestionnaireId());

    Case actualCase = checkCaseSavedAndEmitted(expectedCase);
    assertThat(actualCase.isReceiptReceived()).isFalse();

    UacQidLink actualUacQidLink = checkUacQidLinkSavedAndEmitted(expectedUacQidLink, true);
    assertThat(actualUacQidLink.isBlankQuestionnaireReceived()).isTrue();
    assertThat(actualUacQidLink.isActive()).isFalse();
    assertThat(actualUacQidLink.isReceipted()).isFalse();

    verifyCaseSentToFieldWorkFollowUpService(fieldworkFollowupService, expectedCase);

    verifiyEventLogged(expectedUacQidLink, managementEvent);
  }

  // Scenario 2 - QM Blank Q're (QID A) processed before PQRS receipt (QID A).  No other UAC/QID
  // pair against the case.
  @Test
  public void blankQuestionnaireReceivedQMBeforePQRS() {
    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);

    UacQidLink uacQidLinkSetToBlank =
        generateUacQidLinkedToCase(expectedCase, TEST_QID_1, TEST_UAC_1);
    uacQidLinkSetToBlank.setBlankQuestionnaireReceived(true);
    uacQidLinkSetToBlank.setReceipted(false);
    uacQidLinkSetToBlank.setActive(false);
    when(uacService.findByQid(anyString())).thenReturn(uacQidLinkSetToBlank);

    ResponseManagementEvent PQRSReceiptmanagementEvent = createReceiptReceivedEvent();
    PQRSReceiptmanagementEvent.getPayload().getResponse().setUnreceipt(false);

    // WHEN
    underTest.processReceipt(PQRSReceiptmanagementEvent);

    // Then
    verify(uacService).findByQid(TEST_QID_1);

    // This check is useful here as these services should receive no interactions after getting the
    // UacQidLink
    verifyNoMoreInteractions(uacService);
    verifyZeroInteractions(caseService, fieldworkFollowupService);

    verifiyEventLogged(uacQidLinkSetToBlank, PQRSReceiptmanagementEvent);
  }

  // Scenario 3 - QM Blank Q're (QID A) processed and then PQRS receipt (QID A).  Another QID B
  // exists which is inactive (has been receipted against)
  // We've split this into 2 tests

  // Scenario 3 a - Blank Qid received for Case. Case already successfully receipted by another
  // QidLink
  // Scenario 3 b is covered in scenario 2
  @Test
  public void blankQidLinkForCaseAlreadyReceiptedByAnotherUacQidLink() {
    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(true);

    UacQidLink existingReceiptedQidUacLink =
        generateUacQidLinkedToCase(expectedCase, TEST_QID_2, TEST_UAC_2);
    existingReceiptedQidUacLink.setReceipted(true);

    UacQidLink qidUacToReceiveBlankQuestionnaire =
        generateUacQidLinkedToCase(expectedCase, TEST_QID_1, TEST_UAC_1);
    qidUacToReceiveBlankQuestionnaire.setReceipted(false);
    when(uacService.findByQid(anyString())).thenReturn(qidUacToReceiveBlankQuestionnaire);

    ResponseManagementEvent unreceiptingQuestionnaireEvent = createReceiptReceivedEvent();
    unreceiptingQuestionnaireEvent.getPayload().getResponse().setUnreceipt(true);

    // when
    underTest.processReceipt(unreceiptingQuestionnaireEvent);

    // then
    verify(uacService).findByQid(TEST_QID_1);

    UacQidLink actualUacQidLink =
        checkUacQidLinkSavedAndEmitted(qidUacToReceiveBlankQuestionnaire, true);
    assertThat(actualUacQidLink.isBlankQuestionnaireReceived()).isTrue();
    assertThat(actualUacQidLink.isReceipted()).isFalse();

    // Case shouldn't be updated, nothing should be sent to fieldwork
    verifyZeroInteractions(caseService, fieldworkFollowupService);

    verifiyEventLogged(qidUacToReceiveBlankQuestionnaire, unreceiptingQuestionnaireEvent);
  }


  //  @Test
  //  public void handleUnlinkedBlankQuestionnaireStuff() {
  //    assertFalse(true);
  //  }

  //Scenario 4
  @Test
  public void blankQuestionnaireForAnotherUACQIDPair() {
    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);

    UacQidLink blankUacQidPairLink =
        generateUacQidLinkedToCase(expectedCase, TEST_QID_2, TEST_UAC_2);
    blankUacQidPairLink.setActive(false);
    blankUacQidPairLink.setBlankQuestionnaireReceived(true);
    blankUacQidPairLink.setReceipted(false);

    UacQidLink newPQRSLink = generateUacQidLinkedToCase(expectedCase, TEST_QID_1, TEST_UAC_1);

    ResponseManagementEvent newPQRSEvent = createReceiptReceivedEvent();
    ResponseDTO expectedReceipt = newPQRSEvent.getPayload().getResponse();

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(newPQRSLink);

    // when
    underTest.processReceipt(newPQRSEvent);

    // then
    verify(uacService).findByQid(anyString());

    Case actualCase = checkCaseSavedAndEmitted(expectedCase);
    assertThat(actualCase.isReceiptReceived()).isTrue();

    UacQidLink actualUacQidLink = checkUacQidLinkSavedAndEmitted(newPQRSLink, false);
    assertThat(actualUacQidLink.isReceipted()).isTrue();

    verifiyEventLogged(newPQRSLink, newPQRSEvent);

  }

  // scenario 5, A valid PQRS receipt A and then a Blank PQ B are received for the same case, but different QIDs
  @Test
  public void blankQuestionnaieBwhenAalreadyReceipedSuccessfullyMeansNoMoreActionCreateEvents() {
    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(true);

    UacQidLink existingReceiptedQidUacLink =  generateUacQidLinkedToCase(expectedCase, TEST_QID_2, TEST_UAC_2);
    existingReceiptedQidUacLink.setReceipted(true);

    UacQidLink qidUacToReceiveBlankQuestionnaire = generateUacQidLinkedToCase(expectedCase, TEST_QID_1, TEST_UAC_1);
    qidUacToReceiveBlankQuestionnaire.setReceipted(true);

    ResponseManagementEvent unreceiptingQuestionnaireEvent = createReceiptReceivedEvent();
    unreceiptingQuestionnaireEvent.getPayload().getResponse().setUnreceipt(true);

    when(uacService.findByQid(anyString())).thenReturn(qidUacToReceiveBlankQuestionnaire);

    // when
    underTest.processReceipt(unreceiptingQuestionnaireEvent);

    // then
    verify(uacService).findByQid(TEST_QID_1);

    UacQidLink actualUacQidLink = checkUacQidLinkSavedAndEmitted(qidUacToReceiveBlankQuestionnaire, true);
    assertThat(actualUacQidLink.getQid()).isEqualTo(qidUacToReceiveBlankQuestionnaire.getQid());
    assertThat(actualUacQidLink.isBlankQuestionnaireReceived()).isTrue();
    assertThat(actualUacQidLink.isReceipted()).isFalse();

    // Do not update the case or send anything to field
    verifyZeroInteractions(caseService, fieldworkFollowupService);
  }


  // scenario 6, 2 Blank QIDs are returned at the same time (after they have been receipted by PQRS)
  @Test
  public void twoPQRSReceiptsForCaseRecceivedThenBothReceiveQMBlanks() {
    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(true);

    UacQidLink existingBlankedQidUacLink = generateUacQidLinkedToCase(expectedCase, TEST_QID_2, TEST_UAC_2);
    existingBlankedQidUacLink.setReceipted(false);
    existingBlankedQidUacLink.setBlankQuestionnaireReceived(true);
    existingBlankedQidUacLink.setActive(false);

    UacQidLink qidUacToReceiveBlankQuestionnaire = generateUacQidLinkedToCase(expectedCase, TEST_QID_1, TEST_UAC_1);
    qidUacToReceiveBlankQuestionnaire.setReceipted(true);
    qidUacToReceiveBlankQuestionnaire.setBlankQuestionnaireReceived(false);
    qidUacToReceiveBlankQuestionnaire.setActive(false);
    when(uacService.findByQid(anyString())).thenReturn(qidUacToReceiveBlankQuestionnaire);

    ResponseManagementEvent blankResponseEventFor2ndUacQidLink = createReceiptReceivedEvent();
    blankResponseEventFor2ndUacQidLink.getPayload().getResponse().setUnreceipt(true);

    // When
    underTest.processReceipt(blankResponseEventFor2ndUacQidLink);

    // Then
    verify(uacService).findByQid(TEST_QID_1);

    Case actualCase = checkCaseSavedAndEmitted(expectedCase);
    assertThat(actualCase.isReceiptReceived()).isFalse();

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture(), eq(true));
    UacQidLink actualUacQidLink = checkUacQidLinkSavedAndEmitted(qidUacToReceiveBlankQuestionnaire, true);
    assertThat(actualUacQidLink.isBlankQuestionnaireReceived()).isTrue();
    assertThat(actualUacQidLink.isReceipted()).isFalse();
    assertThat(actualUacQidLink.isActive()).isFalse();

    verifyCaseSentToFieldWorkFollowUpService(fieldworkFollowupService, expectedCase);

    verifiyEventLogged(qidUacToReceiveBlankQuestionnaire, blankResponseEventFor2ndUacQidLink);
  }

  private UacQidLink generateUacQidLinkedToCase(Case expectedCase, String testQid, String testUac) {
    UacQidLink uacQidLink = new UacQidLink();
    uacQidLink.setQid(testQid);
    uacQidLink.setUac(testUac);
    uacQidLink.setCaze(expectedCase);
    uacQidLink.setEvents(null);
    uacQidLink.setBlankQuestionnaireReceived(false);
    uacQidLink.setReceipted(false);
    expectedCase.getUacQidLinks().add(uacQidLink);

    return uacQidLink;
  }

  private ResponseManagementEvent createReceiptReceivedEvent() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    event.setType(EventTypeDTO.RESPONSE_RECEIVED);
    event.setSource("RECEIPT_SERVICE");
    event.setChannel("DUMMY");
    event.setDateTime(OffsetDateTime.now());
    managementEvent.setEvent(event);

    ResponseDTO responseDTO = new ResponseDTO();
    responseDTO.setQuestionnaireId(TEST_QID_1);

    PayloadDTO payloadDTO = new PayloadDTO();
    payloadDTO.setResponse(responseDTO);
    managementEvent.setPayload(payloadDTO);

    managementEvent.getEvent().setTransactionId(UUID.randomUUID());

    return managementEvent;
  }

  private UacQidLink checkUacQidLinkSavedAndEmitted(
      UacQidLink expectedUacQidLink, boolean unreceiptedCheck) {
    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture(), eq(unreceiptedCheck));
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());

    return actualUacQidLink;
  }

  private Case checkCaseSavedAndEmitted(Case expectedCase) {
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveAndEmitCaseUpdatedEvent(caseArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();

    assertThat(actualCase.getCaseId()).isEqualTo(expectedCase.getCaseId());

    return actualCase;
  }

  private void verifyCaseSentToFieldWorkFollowUpService(
      FieldworkFollowupService fieldworkFollowupService, Case expectedCase) {
    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(fieldworkFollowupService).buildAndSendFieldWorkFollowUp(caseArgumentCaptor.capture());
    assertThat(caseArgumentCaptor.getValue().getCaseId()).isEqualTo(expectedCase.getCaseId());
  }

  private void verifiyEventLogged(
      UacQidLink expectedUacQidLink, ResponseManagementEvent managementEvent) {
    verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            eq(managementEvent.getEvent().getDateTime()),
            eq(QID_RECEIPTED),
            eq(EventType.RESPONSE_RECEIVED),
            eq(managementEvent.getEvent()),
            eq(convertObjectToJson(managementEvent.getPayload().getResponse())));
  }
}
