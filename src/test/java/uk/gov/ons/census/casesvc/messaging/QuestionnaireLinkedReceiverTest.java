package uk.gov.ons.census.casesvc.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.getTestResponseManagementEvent;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.QuestionnaireLinkedService;

public class QuestionnaireLinkedReceiverTest {

  @Test
  public void testQuestionnaireLinking() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    QuestionnaireLinkedService questionnaireLinkedService = mock(QuestionnaireLinkedService.class);
    OffsetDateTime expectedEventDateTime = managementEvent.getEvent().getDateTime();

    QuestionnaireLinkedReceiver questionnaireLinkedReceiver =
        new QuestionnaireLinkedReceiver(questionnaireLinkedService);
    questionnaireLinkedReceiver.receiveMessage(managementEvent);

    ArgumentCaptor<ResponseManagementEvent> eventArgumentCaptor =
        ArgumentCaptor.forClass(ResponseManagementEvent.class);
    verify(questionnaireLinkedService).processQuestionnaireLinked(eventArgumentCaptor.capture());

    OffsetDateTime actualEventDateTime = eventArgumentCaptor.getValue().getEvent().getDateTime();
    assertThat(actualEventDateTime).isEqualTo(expectedEventDateTime);
  }
}
