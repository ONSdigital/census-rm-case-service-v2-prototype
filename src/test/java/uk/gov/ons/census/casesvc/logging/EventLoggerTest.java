package uk.gov.ons.census.casesvc.logging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.PayloadDTO;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.Event;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;
import uk.gov.ons.census.casesvc.model.repository.EventRepository;

@RunWith(MockitoJUnitRunner.class)
public class EventLoggerTest {

  @Mock EventRepository eventRepository;

  @InjectMocks EventLogger underTest;

  @Test
  public void testLogEventWithoutEventMetaDataDateTime() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    Case caze = new Case();
    UUID caseUuid = UUID.randomUUID();
    caze.setCaseId(caseUuid);
    uacQidLink.setCaze(caze);

    // When
    underTest.logEvent(uacQidLink, "TEST_LOGGED_EVENT", EventType.UAC_UPDATED, new PayloadDTO());

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertEquals("TEST_LOGGED_EVENT", eventArgumentCaptor.getValue().getEventDescription());
    assertEquals(EventType.UAC_UPDATED, eventArgumentCaptor.getValue().getEventType());
  }

  @Test
  public void testLogEventWithEventMetaDataDateTime() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    OffsetDateTime now = OffsetDateTime.now();
    Case caze = new Case();
    UUID caseUuid = UUID.randomUUID();
    caze.setCaseId(caseUuid);
    uacQidLink.setCaze(caze);
    Map<String, String> headers = createTestDefaultHeaders();

    // When
    underTest.logEvent(
        uacQidLink,
        "TEST_LOGGED_EVENT",
        EventType.UAC_UPDATED,
        new PayloadDTO(),
        headers,
        any(OffsetDateTime.class));

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertEquals("TEST_LOGGED_EVENT", eventArgumentCaptor.getValue().getEventDescription());
    assertEquals(EventType.UAC_UPDATED, eventArgumentCaptor.getValue().getEventType());
    assertEquals(
        now.toString().substring(0, 17),
        eventArgumentCaptor.getValue().getEventDate().toString().substring(0, 17));
  }

  @Test
  public void testLogEventWithDefaultHeaders() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    Map<String, String> headers = createTestDefaultHeaders();

    // When
    underTest.logEvent(
        uacQidLink,
        "TEST_LOGGED_EVENT",
        EventType.UAC_UPDATED,
        new PayloadDTO(),
        headers,
        any(OffsetDateTime.class));

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertThat(eventArgumentCaptor.getValue().getEventSource()).isEqualTo("any default source");
    assertThat(eventArgumentCaptor.getValue().getEventChannel()).isEqualTo("any default channel");
  }

  @Test
  public void testLogEventWithNonDefaultHeaders() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    Map<String, String> headers = createTestNonDefaultHeaders();

    // When
    underTest.logEvent(
        uacQidLink,
        "TEST_LOGGED_EVENT",
        EventType.UAC_UPDATED,
        new PayloadDTO(),
        headers,
        any(OffsetDateTime.class));

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertThat(eventArgumentCaptor.getValue().getEventSource()).isEqualTo("any non-default source");
    assertThat(eventArgumentCaptor.getValue().getEventChannel())
        .isEqualTo("any non-default channel");
  }

  @Test(expected = RuntimeException.class)
  public void testLogEventWithInvalidHeaders() {

    // When
    underTest.logEvent(
        null, "TEST_LOGGED_EVENT", EventType.UAC_UPDATED, null, createTestInvalidHeaders(), null);
  }

  private Map<String, String> createTestDefaultHeaders() {
    Map<String, String> headers = new HashMap<>();

    headers.put("channel", "any default channel");
    headers.put("source", "any default source");

    return headers;
  }

  @Test
  public void testLogEventAddressed() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    OffsetDateTime now = OffsetDateTime.now();
    Case caze = new Case();
    UUID caseUuid = UUID.randomUUID();
    caze.setCaseId(caseUuid);
    uacQidLink.setCaze(caze);

    Map<String, String> headers = createTestNonDefaultHeaders();

    // When
    underTest.logEvent(
        uacQidLink,
        "TEST_LOGGED_EVENT",
        EventType.UAC_UPDATED,
        new PayloadDTO(),
        headers,
        OffsetDateTime.now());

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertThat(eventArgumentCaptor.getValue().getCaseId()).isEqualTo(caseUuid);
  }

  @Test
  public void testLogEventUnaddressed() {
    // Given
    UacQidLink uacQidLink = new UacQidLink();
    Map<String, String> headers = createTestNonDefaultHeaders();

    // When
    underTest.logEvent(
        uacQidLink,
        "TEST_LOGGED_EVENT",
        EventType.UAC_UPDATED,
        new PayloadDTO(),
        headers,
        OffsetDateTime.now());

    // Then
    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
    verify(eventRepository).save(eventArgumentCaptor.capture());
    assertThat(eventArgumentCaptor.getValue().getCaseId()).isNull();
  }

  private Map<String, String> createTestNonDefaultHeaders() {
    Map<String, String> headers = new HashMap<>();

    headers.put("channel", "any non-default channel");
    headers.put("source", "any non-default source");

    return headers;
  }

  private Map<String, String> createTestInvalidHeaders() {
    Map<String, String> headers = new HashMap<>();

    headers.put("not expected key", "anything");

    return headers;
  }
}
