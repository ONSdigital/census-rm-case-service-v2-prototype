package uk.gov.ons.census.casesvc.messaging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static uk.gov.ons.census.casesvc.model.entity.EventType.FULFILMENT_REQUESTED;
import static uk.gov.ons.census.casesvc.testutil.MessageConstructor.constructMessageWithValidTimeStamp;

import org.junit.Test;
import org.mockito.Mock;
import org.springframework.messaging.Message;
import uk.gov.ons.census.casesvc.logging.EventLogger;
import uk.gov.ons.census.casesvc.model.dto.ResponseManagementEvent;
import uk.gov.ons.census.casesvc.service.InvalidAddressService;
import uk.gov.ons.census.casesvc.utility.MsgDateHelper;

import java.time.OffsetDateTime;

public class InvalidAddressReceiverTest {

  @Mock
  private EventLogger eventLogger;


  @Test
  public void testInvalidAddress() {
    ResponseManagementEvent managementEvent = new ResponseManagementEvent();
    InvalidAddressService invalidAddressService = mock(InvalidAddressService.class);

    Message<ResponseManagementEvent> message = constructMessageWithValidTimeStamp(managementEvent);
    OffsetDateTime expectedDate = MsgDateHelper.getMsgTimeStamp(message);

    InvalidAddressReceiver invalidAddressReceiver =
        new InvalidAddressReceiver(invalidAddressService);
    invalidAddressReceiver.receiveMessage(message);

    verify(invalidAddressService).processMessage(eq(managementEvent), eq(expectedDate));
  }
}
