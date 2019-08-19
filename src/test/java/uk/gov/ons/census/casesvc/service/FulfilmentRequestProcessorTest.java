package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;
import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;
import static uk.gov.ons.census.casesvc.service.FulfilmentRequestProcessor.HOUSEHOLD_INDIVIDUAL_RESPONSE;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getRandomCase;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.EventDTO;
import uk.gov.ons.census.casesvc.model.dto.FulfilmentRequestDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.CaseState;
import uk.gov.ons.census.casesvc.model.repository.CaseRepository;

@RunWith(MockitoJUnitRunner.class)
public class FulfilmentRequestProcessorTest {

  @Mock private CaseRepository caseRepository;

  @Mock private EventLogger eventLogger;

  @Mock private CaseProcessor caseProcessor;

  @InjectMocks FulfilmentRequestProcessor underTest;

  @Test
  public void testGoodFulfilmentRequest() {
    // Given
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();

    Case expectedCase = getRandomCase();
    expectedFulfilmentRequest.setCaseId(expectedCase.getCaseId().toString());

    when(caseRepository.findByCaseId(UUID.fromString(expectedFulfilmentRequest.getCaseId())))
        .thenReturn(Optional.of(expectedCase));

    // when
    underTest.processFulfilmentRequest(managementEvent);

    // then
    verify(eventLogger, times(1))
        .logFulfilmentRequestedEvent(
            expectedCase,
            expectedCase.getCaseId(),
            managementEvent.getEvent().getDateTime(),
            "Fulfilment Request Received",
            FULFILMENT_REQUESTED,
            expectedFulfilmentRequest,
            managementEvent.getEvent());

    verify(caseRepository, never()).save(any(Case.class));
    verifyNoMoreInteractions(eventLogger);
    verifyZeroInteractions(caseProcessor);
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT1() {
    testIndividualResponseCode("UACIT1");
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT2() {
    testIndividualResponseCode("UACIT2");
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT2W() {
    testIndividualResponseCode("UACIT2W");
  }

  @Test
  public void testGoodIndividualResponseFulfilmentRequestForUACIT4() {
    testIndividualResponseCode("UACIT4");
  }

  @Test(expected = RuntimeException.class)
  public void testEmptyFulfilmentCodeThrowsException() {
    // Given
    Case parentCase = getRandomCase();

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();
    expectedFulfilmentRequest.setCaseId(parentCase.getCaseId().toString());
    expectedFulfilmentRequest.setFulfilmentCode(null);

    when(caseRepository.findByCaseId(UUID.fromString(expectedFulfilmentRequest.getCaseId())))
        .thenReturn(Optional.of(parentCase));

    String expectedErrorMessage =
        String.format(
            "Fulfilment code '%s' not found from event for Case ID '%s",
            parentCase.getCaseId(), expectedFulfilmentRequest.getFulfilmentCode());

    try {
      // WHEN
      underTest.processFulfilmentRequest(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testCaseIdNotFound() {
    // GIVEN
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    managementEvent.getPayload().getFulfilmentRequest().setCaseId(UUID.randomUUID().toString());
    UUID expectedCaseIdNotFound =
        UUID.fromString(managementEvent.getPayload().getFulfilmentRequest().getCaseId());
    String expectedErrorMessage = String.format("Case ID '%s' not found!", expectedCaseIdNotFound);

    try {
      // WHEN
      underTest.processFulfilmentRequest(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }

  @Test(expected = RuntimeException.class)
  public void testNullDateTime() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();
    EventDTO event = managementEvent.getEvent();
    event.setDateTime(null);

    // Given
    Case expectedCase = getRandomCase();
    expectedFulfilmentRequest.setCaseId(expectedCase.getCaseId().toString());
    UUID caseId = expectedCase.getCaseId();

    when(caseRepository.findByCaseId(caseId)).thenReturn(Optional.of(expectedCase));

    String expectedErrorMessage =
        String.format("Date time not found in fulfilment request event for Case ID '%s", caseId);

    try {
      // WHEN
      underTest.processFulfilmentRequest(managementEvent);
    } catch (RuntimeException re) {
      // THEN
      assertThat(re.getMessage()).isEqualTo(expectedErrorMessage);
      throw re;
    }
  }

  private void testIndividualResponseCode(String individualResponseCode) {
    // Given
    Case parentCase = getRandomCase();
    parentCase.setUacQidLinks(new ArrayList<>());
    parentCase.setEvents(new ArrayList<>());
    parentCase.setCreatedDateTime(OffsetDateTime.now().minusDays(1));
    parentCase.setState(null);
    parentCase.setReceiptReceived(true);
    parentCase.setRefusalReceived(true);
    parentCase.setAddressType("HH");

    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    FulfilmentRequestDTO expectedFulfilmentRequest =
        managementEvent.getPayload().getFulfilmentRequest();
    expectedFulfilmentRequest.setCaseId(parentCase.getCaseId().toString());
    expectedFulfilmentRequest.setFulfilmentCode(individualResponseCode);

    when(caseRepository.findByCaseId(UUID.fromString(expectedFulfilmentRequest.getCaseId())))
        .thenReturn(Optional.of(parentCase));

    // when
    underTest.processFulfilmentRequest(managementEvent);

    // then
    verify(eventLogger, times(1))
        .logFulfilmentRequestedEvent(
            parentCase,
            parentCase.getCaseId(),
            managementEvent.getEvent().getDateTime(),
            "Fulfilment Request Received",
            FULFILMENT_REQUESTED,
            expectedFulfilmentRequest,
            managementEvent.getEvent());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseRepository).save(caseArgumentCaptor.capture());
    Case actualChildCase = caseArgumentCaptor.getValue();

    testIndivdualFulfilmentRequestCase(parentCase, actualChildCase);
    verify(caseProcessor).emitCaseCreatedEvent(actualChildCase);
    verify(caseProcessor, times(1)).getUniqueCaseRef();
  }

  private void testIndivdualFulfilmentRequestCase(Case parentCase, Case actualChildCase) {
    assertThat(actualChildCase.getCaseRef()).isNotEqualTo(parentCase.getCaseRef());
    assertThat(UUID.fromString(actualChildCase.getCaseId().toString()))
        .isNotEqualTo(parentCase.getCaseId());
    assertThat(actualChildCase.getUacQidLinks()).isNull();
    assertThat(actualChildCase.getEvents()).isNull();
    assertThat(actualChildCase.getCreatedDateTime())
        .isBetween(OffsetDateTime.now().minusSeconds(10), OffsetDateTime.now());
    assertThat(actualChildCase.getCollectionExerciseId())
        .isEqualTo(parentCase.getCollectionExerciseId());
    assertThat(actualChildCase.getActionPlanId()).isEqualTo(parentCase.getActionPlanId());
    assertThat(actualChildCase.getState()).isEqualTo(CaseState.ACTIONABLE);
    assertThat(actualChildCase.isReceiptReceived()).isFalse();
    assertThat(actualChildCase.isRefusalReceived()).isFalse();
    assertThat(actualChildCase.getArid()).isEqualTo(parentCase.getArid());
    assertThat(actualChildCase.getEstabArid()).isEqualTo(parentCase.getEstabArid());
    assertThat(actualChildCase.getUprn()).isEqualTo(parentCase.getUprn());
    assertThat(actualChildCase.getAddressType()).isEqualTo(HOUSEHOLD_INDIVIDUAL_RESPONSE);
    assertThat(actualChildCase.getEstabType()).isEqualTo(parentCase.getEstabType());
    assertThat(actualChildCase.getAddressLevel()).isNull();
    assertThat(actualChildCase.getAbpCode()).isEqualTo(parentCase.getAbpCode());
    assertThat(actualChildCase.getOrganisationName()).isEqualTo(parentCase.getOrganisationName());
    assertThat(actualChildCase.getAddressLine1()).isEqualTo(parentCase.getAddressLine1());
    assertThat(actualChildCase.getAddressLine2()).isEqualTo(parentCase.getAddressLine2());
    assertThat(actualChildCase.getAddressLine3()).isEqualTo(parentCase.getAddressLine3());
    assertThat(actualChildCase.getTownName()).isEqualTo(parentCase.getTownName());
    assertThat(actualChildCase.getPostcode()).isEqualTo(parentCase.getPostcode());
    assertThat(actualChildCase.getLatitude()).isEqualTo(parentCase.getLatitude());
    assertThat(actualChildCase.getLongitude()).isEqualTo(parentCase.getLongitude());
    assertThat(actualChildCase.getOa()).isEqualTo(parentCase.getOa());
    assertThat(actualChildCase.getLsoa()).isEqualTo(parentCase.getLsoa());
    assertThat(actualChildCase.getMsoa()).isEqualTo(parentCase.getMsoa());
    assertThat(actualChildCase.getLad()).isEqualTo(parentCase.getLad());
    assertThat(actualChildCase.getRegion()).isEqualTo(parentCase.getRegion());
    assertThat(actualChildCase.getHtcWillingness()).isNull();
    assertThat(actualChildCase.getHtcDigital()).isNull();
    assertThat(actualChildCase.getFieldCoordinatorId()).isNull();
    assertThat(actualChildCase.getFieldOfficerId()).isNull();
    assertThat(actualChildCase.getTreatmentCode()).isNull();
    assertThat(actualChildCase.getCeExpectedCapacity()).isNull();
  }
}
