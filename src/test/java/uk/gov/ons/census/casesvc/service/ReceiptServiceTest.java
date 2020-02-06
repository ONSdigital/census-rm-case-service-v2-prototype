package uk.gov.ons.census.casesvc.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.service.ReceiptService.QID_RECEIPTED;
import static uk.gov.ons.census.casesvc.testutil.DataUtils.*;

import java.time.OffsetDateTime;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseDTO;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.model.entity.Case;
import uk.gov.ons.census.casesvc.model.entity.EventType;
import uk.gov.ons.census.casesvc.model.entity.UacQidLink;

@RunWith(MockitoJUnitRunner.class)
public class ReceiptServiceTest {

  private final String TEST_NON_CCS_QID_ID = "0134567890123456";
  private final String TEST_CONTINUATION_QID = "113456789023";

  @Mock private CaseReceipter caseReceipter;

  @Mock private UacService uacService;

  @Mock private EventLogger eventLogger;

  @InjectMocks ReceiptService underTest;

  @Test
  public void testReceiptForCase() {
    ResponseManagementEvent managementEvent = getTestResponseManagementEvent();
    ResponseDTO expectedReceipt = managementEvent.getPayload().getResponse();

    // Given
    Case expectedCase = getRandomCase();
    expectedCase.setReceiptReceived(false);
    expectedCase.setSurvey("CENSUS");
    UacQidLink expectedUacQidLink = generateRandomUacQidLinkedToCase(expectedCase);
    expectedUacQidLink.setQid(TEST_NON_CCS_QID_ID);
    OffsetDateTime messageTimestamp = OffsetDateTime.now();

    managementEvent.getPayload().getResponse().setResponseDateTime(OffsetDateTime.now());

    when(uacService.findByQid(expectedReceipt.getQuestionnaireId())).thenReturn(expectedUacQidLink);

    // when
    underTest.processReceipt(managementEvent, messageTimestamp);

    // then
    verify(uacService).findByQid(anyString());

    ArgumentCaptor<Case> caseArgumentCaptor = ArgumentCaptor.forClass(Case.class);
    ArgumentCaptor<UacQidLink> uacQidLinkArgumentCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(caseReceipter)
        .handleReceipting(caseArgumentCaptor.capture(), uacQidLinkArgumentCaptor.capture());
    Case actualCase = caseArgumentCaptor.getValue();
    assertThat(actualCase.isReceiptReceived()).isFalse();
    assertThat(actualCase.getSurvey()).isEqualTo("CENSUS");
    assertThat(uacQidLinkArgumentCaptor.getValue().getQid()).isEqualTo(TEST_NON_CCS_QID_ID);

    verifyNoMoreInteractions(caseReceipter);

    ArgumentCaptor<UacQidLink> uacQidLinkCaptor = ArgumentCaptor.forClass(UacQidLink.class);
    verify(uacService).saveAndEmitUacUpdatedEvent(uacQidLinkCaptor.capture());
    UacQidLink actualUacQidLink = uacQidLinkCaptor.getValue();
    assertThat(actualUacQidLink.getQid()).isEqualTo(expectedUacQidLink.getQid());
    assertThat(actualUacQidLink.getUac()).isEqualTo(expectedUacQidLink.getUac());

    verify(eventLogger)
        .logUacQidEvent(
            eq(expectedUacQidLink),
            any(OffsetDateTime.class),
            eq(QID_RECEIPTED),
            eq(EventType.RESPONSE_RECEIVED),
            eq(managementEvent.getEvent()),
            anyString(),
            eq(messageTimestamp));
    verifyNoMoreInteractions(eventLogger);
  }
}
