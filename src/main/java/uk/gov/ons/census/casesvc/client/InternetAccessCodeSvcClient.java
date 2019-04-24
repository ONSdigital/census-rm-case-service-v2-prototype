package uk.gov.ons.census.casesvc.client;

import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponents;
import org.springframework.web.util.UriComponentsBuilder;
import uk.gov.ons.census.casesvc.model.dto.CreateInternetAccessCodeDTO;

@Component
public class InternetAccessCodeSvcClient {
  private static final String CREATED_BY = "SYSTEM";

  @Value("${iacservice.generate-iacs-path}")
  private String generateIacsPath;

  @Value("${iacservice.connection.scheme}")
  private String scheme;

  @Value("${iacservice.connection.host}")
  private String host;

  @Value("${iacservice.connection.port}")
  private String port;

  @Value("${iacservice.connection.username}")
  private String username;

  @Value("${iacservice.connection.password}")
  private String password;

  public List<String> generateIACs(int count) {

    RestTemplate restTemplate = new RestTemplate();
    UriComponents uriComponents = createUriComponents(generateIacsPath);

    CreateInternetAccessCodeDTO createCodesDTO = new CreateInternetAccessCodeDTO(count, CREATED_BY);
    HttpEntity<CreateInternetAccessCodeDTO> httpEntity = createHttpEntity(createCodesDTO);

    ResponseEntity<String[]> responseEntity =
        restTemplate.exchange(uriComponents.toUri(), HttpMethod.POST, httpEntity, String[].class);
    return Arrays.asList(responseEntity.getBody());
  }

  private UriComponents createUriComponents(String path) {
    return UriComponentsBuilder.newInstance()
        .scheme(scheme)
        .host(host)
        .port(port)
        .path(path)
        .build()
        .encode();
  }

  private <H> HttpEntity<H> createHttpEntity(H entity) {
    return new HttpEntity(entity, getHttpHeaders());
  }

  private HttpHeaders getHttpHeaders() {
    HttpHeaders headers = new HttpHeaders();
    headers.set("Accept", "application/json");
    headers.set("Content-Type", "application/json");
    String auth = username + ":" + password;
    byte[] encodedAuth = Base64.getEncoder().encode(auth.getBytes(Charset.forName("US-ASCII")));
    String authHeader = "Basic " + new String(encodedAuth);
    headers.set("Authorization", authHeader);

    return headers;
  }
}
