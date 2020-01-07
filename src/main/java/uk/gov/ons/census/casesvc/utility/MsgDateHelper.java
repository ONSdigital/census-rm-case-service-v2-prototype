package uk.gov.ons.census.casesvc.utility;

import org.springframework.messaging.Message;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneId;

public class MsgDateHelper {
  public static OffsetDateTime getMsgTimeStamp(Message<?> msg) {

    if( msg.getHeaders().getTimestamp() == null) {
      throw new RuntimeException("Message Headers missing Timestamp, is this important enough to reject message on?");
    }

    return OffsetDateTime.ofInstant(Instant.ofEpochMilli(msg.getHeaders().getTimestamp()), ZoneId.of("UTC"));
  }
}
