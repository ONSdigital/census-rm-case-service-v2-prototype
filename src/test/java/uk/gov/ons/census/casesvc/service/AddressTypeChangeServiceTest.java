package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.jeasy.random.EasyRandom;
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
import uk.gov.ons.census.casesvc.utility.JsonHelper;

@RunWith(MockitoJUnitRunner.class)
public class AddressTypeChangeServiceTest {
  private static final EasyRandom easyRandom = new EasyRandom();
  @Mock private CaseService caseService;
  @Mock private EventLogger eventLogger;
  @Mock private InvalidAddressService invalidAddressService;
  @InjectMocks private AddressTypeChangeService underTest;

  @Test
  public void testProcessMessageHappyPath() {
    // Given
    ResponseManagementEvent rme = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    rme.setEvent(event);
    event.setDateTime(OffsetDateTime.now());

    OffsetDateTime messageTimestamp = OffsetDateTime.now();
    PayloadDTO payload = new PayloadDTO();
    rme.setPayload(payload);

    AddressTypeChange addressTypeChange = new AddressTypeChange();
    payload.setAddressTypeChange(addressTypeChange);

    AddressTypeChangeDetails addressTypeChangeDetails = new AddressTypeChangeDetails();
    addressTypeChange.setCollectionCase(addressTypeChangeDetails);
    addressTypeChangeDetails.setCeExpectedCapacity("20");
    addressTypeChangeDetails.setId(UUID.randomUUID());

    Address address = new Address();
    addressTypeChangeDetails.setAddress(address);
    address.setAddressType("SPG");

    Case oldCase = easyRandom.nextObject(Case.class);
    oldCase.setCaseType("HH");
    oldCase.setCaseId(UUID.randomUUID());
    when(caseService.getCaseByCaseId(any())).thenReturn(oldCase);
    when(caseService.saveNewCaseAndStampCaseRef(any())).thenAnswer(i -> i.getArguments()[0]);

    // When
    underTest.processMessage(rme, messageTimestamp);

    // Then
    verify(invalidAddressService)
        .invalidateCase(eq(rme), eq(messageTimestamp), eq(oldCase), eq(addressTypeChange));

    verify(eventLogger)
        .logCaseEvent(
            eq(oldCase),
            eq(rme.getEvent().getDateTime()),
            eq("Address type changed"),
            eq(EventType.ADDRESS_TYPE_CHANGED),
            eq(rme.getEvent()),
            eq(JsonHelper.convertObjectToJson(addressTypeChange)),
            eq(messageTimestamp));

    ArgumentCaptor<Case> newCaseArgCaptor = ArgumentCaptor.forClass(Case.class);
    verify(caseService).saveNewCaseAndStampCaseRef(newCaseArgCaptor.capture());

    Case newCase = newCaseArgCaptor.getValue();
    assertThat(newCase.isSkeleton()).isTrue();
    assertThat(newCase.getCaseId()).isEqualTo(addressTypeChange.getNewCaseId());
    assertThat(newCase.getCaseType()).isEqualTo("SPG");
    assertThat(newCase.getAddressType()).isEqualTo("SPG");
    assertThat(newCase.getAddressLevel()).isEqualTo("U");
    assertThat(newCase.getRegion()).isEqualTo(oldCase.getRegion());
    assertThat(newCase.getCollectionExerciseId()).isEqualTo(oldCase.getCollectionExerciseId());
    assertThat(newCase.getActionPlanId()).isEqualTo(oldCase.getActionPlanId());
    assertThat(newCase.getSurvey()).isEqualTo(oldCase.getSurvey());
    assertThat(newCase.getCeExpectedCapacity()).isEqualTo(20);
    assertThat(newCase.getUprn()).isEqualTo(oldCase.getUprn());
    assertThat(newCase.getOrganisationName()).isEqualTo(oldCase.getOrganisationName());
    assertThat(newCase.getAddressLine1()).isEqualTo(oldCase.getAddressLine1());
    assertThat(newCase.getAddressLine2()).isEqualTo(oldCase.getAddressLine2());
    assertThat(newCase.getAddressLine3()).isEqualTo(oldCase.getAddressLine3());
    assertThat(newCase.getTownName()).isEqualTo(oldCase.getTownName());
    assertThat(newCase.getPostcode()).isEqualTo(oldCase.getPostcode());
    assertThat(newCase.getLatitude()).isEqualTo(oldCase.getLatitude());
    assertThat(newCase.getLongitude()).isEqualTo(oldCase.getLongitude());
    assertThat(newCase.getOa()).isEqualTo(oldCase.getOa());
    assertThat(newCase.getLsoa()).isEqualTo(oldCase.getLsoa());
    assertThat(newCase.getMsoa()).isEqualTo(oldCase.getMsoa());
    assertThat(newCase.getLad()).isEqualTo(oldCase.getLad());
    assertThat(newCase.getHtcWillingness()).isEqualTo(oldCase.getHtcWillingness());
    assertThat(newCase.getHtcDigital()).isEqualTo(oldCase.getHtcDigital());

    verify(caseService).emitCaseCreatedEvent(eq(newCase));
    verify(eventLogger)
        .logCaseEvent(
            eq(newCase),
            eq(rme.getEvent().getDateTime()),
            eq("Address type changed"),
            eq(EventType.ADDRESS_TYPE_CHANGED),
            eq(rme.getEvent()),
            eq(JsonHelper.convertObjectToJson(addressTypeChange)),
            eq(messageTimestamp));
  }

  @Test(expected = RuntimeException.class)
  public void testOldCaseIdCannotEqualNewCaseId() {
    // Given
    UUID oneUuid = UUID.randomUUID();
    ResponseManagementEvent rme = new ResponseManagementEvent();

    PayloadDTO payload = new PayloadDTO();
    rme.setPayload(payload);

    AddressTypeChange addressTypeChange = new AddressTypeChange();
    payload.setAddressTypeChange(addressTypeChange);
    addressTypeChange.setNewCaseId(oneUuid);

    AddressTypeChangeDetails addressTypeChangeDetails = new AddressTypeChangeDetails();
    addressTypeChange.setCollectionCase(addressTypeChangeDetails);
    addressTypeChangeDetails.setId(oneUuid);

    // When
    underTest.processMessage(rme, null);

    // Then

  }

  @Test(expected = RuntimeException.class)
  public void testCannotChangeHiCases() {
    // Given
    ResponseManagementEvent rme = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    rme.setEvent(event);

    PayloadDTO payload = new PayloadDTO();
    rme.setPayload(payload);

    AddressTypeChange addressTypeChange = new AddressTypeChange();
    payload.setAddressTypeChange(addressTypeChange);

    AddressTypeChangeDetails addressTypeChangeDetails = new AddressTypeChangeDetails();
    addressTypeChange.setCollectionCase(addressTypeChangeDetails);
    addressTypeChangeDetails.setId(UUID.randomUUID());

    Case oldCase = easyRandom.nextObject(Case.class);
    oldCase.setCaseType("HI");
    oldCase.setCaseId(UUID.randomUUID());
    when(caseService.getCaseByCaseId(any())).thenReturn(oldCase);

    // When
    underTest.processMessage(rme, null);
  }

  @Test(expected = RuntimeException.class)
  public void testCannotChangeHhToHhCases() {
    // Given
    ResponseManagementEvent rme = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    rme.setEvent(event);

    PayloadDTO payload = new PayloadDTO();
    rme.setPayload(payload);

    AddressTypeChange addressTypeChange = new AddressTypeChange();
    payload.setAddressTypeChange(addressTypeChange);

    AddressTypeChangeDetails addressTypeChangeDetails = new AddressTypeChangeDetails();
    addressTypeChange.setCollectionCase(addressTypeChangeDetails);
    addressTypeChangeDetails.setId(UUID.randomUUID());

    Address address = new Address();
    addressTypeChangeDetails.setAddress(address);
    address.setAddressType("HH");

    Case oldCase = easyRandom.nextObject(Case.class);
    oldCase.setCaseType("HH");
    oldCase.setCaseId(UUID.randomUUID());
    when(caseService.getCaseByCaseId(any())).thenReturn(oldCase);

    // When
    underTest.processMessage(rme, null);
  }

  @Test(expected = RuntimeException.class)
  public void testCannotChangeSpgToSpgCases() {
    // Given
    ResponseManagementEvent rme = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    rme.setEvent(event);

    PayloadDTO payload = new PayloadDTO();
    rme.setPayload(payload);

    AddressTypeChange addressTypeChange = new AddressTypeChange();
    payload.setAddressTypeChange(addressTypeChange);

    AddressTypeChangeDetails addressTypeChangeDetails = new AddressTypeChangeDetails();
    addressTypeChange.setCollectionCase(addressTypeChangeDetails);
    addressTypeChangeDetails.setId(UUID.randomUUID());

    Address address = new Address();
    addressTypeChangeDetails.setAddress(address);
    address.setAddressType("SPG");

    Case oldCase = easyRandom.nextObject(Case.class);
    oldCase.setCaseType("SPG");
    oldCase.setCaseId(UUID.randomUUID());
    when(caseService.getCaseByCaseId(any())).thenReturn(oldCase);

    // When
    underTest.processMessage(rme, null);
  }

  @Test(expected = RuntimeException.class)
  public void testCannotChangeCeToCeCases() {
    // Given
    ResponseManagementEvent rme = new ResponseManagementEvent();

    EventDTO event = new EventDTO();
    rme.setEvent(event);

    PayloadDTO payload = new PayloadDTO();
    rme.setPayload(payload);

    AddressTypeChange addressTypeChange = new AddressTypeChange();
    payload.setAddressTypeChange(addressTypeChange);

    AddressTypeChangeDetails addressTypeChangeDetails = new AddressTypeChangeDetails();
    addressTypeChange.setCollectionCase(addressTypeChangeDetails);
    addressTypeChangeDetails.setId(UUID.randomUUID());

    Address address = new Address();
    addressTypeChangeDetails.setAddress(address);
    address.setAddressType("CE");

    Case oldCase = easyRandom.nextObject(Case.class);
    oldCase.setCaseType("CE");
    oldCase.setCaseId(UUID.randomUUID());
    when(caseService.getCaseByCaseId(any())).thenReturn(oldCase);

    // When
    underTest.processMessage(rme, null);
  }
}
