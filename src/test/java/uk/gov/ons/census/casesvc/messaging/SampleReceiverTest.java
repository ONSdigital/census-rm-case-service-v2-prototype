package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.Mockito.verify;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.model.dto.CreateCaseSample;
import uk.gov.ons.census.casesvc.service.EventProcessor;

@RunWith(MockitoJUnitRunner.class)
public class SampleReceiverTest {

  @InjectMocks private SampleReceiver underTest;

  @Mock private EventProcessor eventProcessor;

  @Test
  public void testHappyPath() {
    // Given
    CreateCaseSample createCaseSample = new CreateCaseSample();
    //    createCaseSample.setAddressLine1("123 Fake Street");
    //    createCaseSample.setRgn("E999");
    //    createCaseSample.setTreatmentCode("HH_LF3R2E");

    // When
    underTest.receiveMessage(createCaseSample);

    // Then
    verify(eventProcessor).processSampleReceivedMessage(createCaseSample);
  }

  //    when(uacQidLinkRepository.saveAndFlush(any()))
  //        .then(
  //            obj -> {
  //              UacQidLink uacQidLink = obj.getArgument(0);
  //              uacQidLink.setUniqueNumber(12345L);
  //              return uacQidLink;
  //            });
  //
  //    when(caseRepository.saveAndFlush(any())).then(obj -> obj.getArgument(0));
  //
  //    when(caseRepository.saveAndFlush(any()))
  //        .then(
  //            obj -> {
  //              Case caze = obj.getArgument(0);
  //              caze.setCaseRef(123456789L);
  //              return caze;
  //            });
  //
  //    ReflectionTestUtils.setField(underTest, "emitCaseEventExchange", "myExchange");
  //
  //    String uac = "abcd-1234-xyza-4321";
  //    when(iacDispenser.getIacCode()).thenReturn(uac);
  //
  //    String qid = "1234567891011125";
  //    when(qidCreator.createQid(eq(1), anyLong())).thenReturn(qid);
  //
  //    // When
  //    underTest.receiveMessage(createCaseSample);
  //
  //    // Then
  //    // Check the emitted event
  //    ArgumentCaptor<ResponseManagementEvent> emittedMessageArgCaptor =
  //        ArgumentCaptor.forClass(ResponseManagementEvent.class);
  //    verify(rabbitTemplate, times(2))
  //        .convertAndSend(eq("myExchange"), eq(""), emittedMessageArgCaptor.capture());
  //    List<ResponseManagementEvent> responseManagementEvents =
  // emittedMessageArgCaptor.getAllValues();
  //    assertEquals(2, responseManagementEvents.size());
  //
  //    ResponseManagementEvent caseCreatedEvent = null;
  //    ResponseManagementEvent uacUpdatedEvent = null;
  //    if (responseManagementEvents.get(0).getEvent().getType() == EventType.CASE_CREATED) {
  //      caseCreatedEvent = responseManagementEvents.get(0);
  //      uacUpdatedEvent = responseManagementEvents.get(1);
  //    } else {
  //      caseCreatedEvent = responseManagementEvents.get(1);
  //      uacUpdatedEvent = responseManagementEvents.get(0);
  //    }
  //
  //    assertEquals("123456789", caseCreatedEvent.getPayload().getCollectionCase().getCaseRef());
  //    assertEquals(
  //        "123 Fake Street",
  //        caseCreatedEvent.getPayload().getCollectionCase().getAddress().getAddressLine1());
  //    assertEquals("E",
  // caseCreatedEvent.getPayload().getCollectionCase().getAddress().getRegion());
  //    assertEquals("ACTIONABLE", caseCreatedEvent.getPayload().getCollectionCase().getState());
  //    assertEquals("CENSUS", caseCreatedEvent.getPayload().getCollectionCase().getSurvey());
  //    assertEquals("RM", caseCreatedEvent.getEvent().getChannel());
  //    assertEquals(EventType.CASE_CREATED, caseCreatedEvent.getEvent().getType());
  //    String now = LocalDateTime.now().toString();
  //    assertEquals(now.substring(0, 16), caseCreatedEvent.getEvent().getDateTime().substring(0,
  // 16));
  //
  //    assertEquals(uac, uacUpdatedEvent.getPayload().getUac().getUac());
  //    assertEquals(qid, uacUpdatedEvent.getPayload().getUac().getQuestionnaireId());
  //
  //    // Check IAC is retrieved
  //    verify(iacDispenser).getIacCode();
  //
  //    // Check IAC and QID are linked correctly
  //    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor =
  // ArgumentCaptor.forClass(UacQidLink.class);
  //    verify(uacQidLinkRepository).save(uacQidLinkArgumentCaptor.capture());
  //    UacQidLink uacQidLink = uacQidLinkArgumentCaptor.getValue();
  //    assertEquals(uac, uacQidLink.getUac());
  //    assertEquals(qid, uacQidLink.getQid());
  //
  //    // Check case event is stored
  //    ArgumentCaptor<Event> eventArgumentCaptor = ArgumentCaptor.forClass(Event.class);
  //    verify(eventRepository, times(2)).save(eventArgumentCaptor.capture());
  //    List<Event> events = eventArgumentCaptor.getAllValues();
  //    assertThat(
  //        events,
  //        containsInAnyOrder(
  //            hasProperty("eventDescription", is("Case created")),
  //            hasProperty("eventDescription", is("UAC QID linked"))));
  //
  //    // Check case is stored in the database
  //    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
  //    verify(caseRepository).saveAndFlush(caseArgumentCaptor.capture());
  //    Case caze = caseArgumentCaptor.getValue();
  //    assertEquals("123 Fake Street", caze.getAddressLine1());
  //    assertEquals(ACTIONABLE, caze.getState());
  //
  //    // Check sample gets mapped to a case
  //    verify(mapperFacade).map(any(CreateCaseSample.class), eq(Case.class));
  //  }
  //
  //  @Test(expected = RuntimeException.class)
  //  public void testDatabaseBlowsUp() {
  //    // Given
  //    CreateCaseSample createCaseSample = new CreateCaseSample();
  //    createCaseSample.setAddressLine1("123 Fake Street");
  //    createCaseSample.setRgn("E999");
  //    createCaseSample.setTreatmentCode("HH_LF3R2E");
  //    when(uacQidLinkRepository.saveAndFlush(any())).thenThrow(new RuntimeException());
  //    when(caseRepository.saveAndFlush(any())).then(obj -> obj.getArgument(0));
  //
  //    // When
  //    underTest.receiveMessage(createCaseSample);
  //
  //    // Then
  //    // Expected Exception is raised
  //
  //  }
  //
  //  @Test(expected = RuntimeException.class)
  //  public void testRabbitBlowsUp() {
  //    // Given
  //    CreateCaseSample createCaseSample = new CreateCaseSample();
  //    createCaseSample.setAddressLine1("123 Fake Street");
  //    createCaseSample.setRgn("E999");
  //    createCaseSample.setTreatmentCode("HH_LF3R2E");
  //    when(uacQidLinkRepository.saveAndFlush(any()))
  //        .then(
  //            obj -> {
  //              UacQidLink uacQidLink = obj.getArgument(0);
  //              uacQidLink.setUniqueNumber(12345L);
  //              return uacQidLink;
  //            });
  //
  //    when(caseRepository.saveAndFlush(any())).then(obj -> obj.getArgument(0));
  //
  //    when(caseRepository.saveAndFlush(any()))
  //        .then(
  //            obj -> {
  //              Case caze = obj.getArgument(0);
  //              caze.setCaseRef(123456789L);
  //              return caze;
  //            });
  //
  //    ReflectionTestUtils.setField(underTest, "emitCaseEventExchange", "myExchange");
  //
  //    String uac = "abcd-1234-xyza-4321";
  //    when(iacDispenser.getIacCode()).thenReturn(uac);
  //
  //    String qid = "1234567891011125";
  //    when(qidCreator.createQid(eq(1), anyLong())).thenReturn(qid);
  //
  //    doThrow(new RuntimeException())
  //        .when(rabbitTemplate)
  //        .convertAndSend(anyString(), anyString(), any(ResponseManagementEvent.class));
  //
  //    // When
  //    underTest.receiveMessage(createCaseSample);
  //
  //    // Then
  //    // Expected Exception is raised
  //  }
}
