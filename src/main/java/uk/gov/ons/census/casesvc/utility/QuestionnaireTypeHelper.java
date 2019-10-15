package uk.gov.ons.census.casesvc.utility;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public class QuestionnaireTypeHelper {
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND = "21";
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_WALES_ENGLISH = "22";
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_WALES_WELSH = "23";
  private static final String HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_NORTHERN_IRELAND = "24";
  private static final Set<String> individualQuestionnaireTypes =
      new HashSet<>(
          Arrays.asList(
              HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_ENGLAND,
              HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_WALES_ENGLISH,
              HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_WALES_WELSH,
              HOUSEHOLD_INDIVIDUAL_QUESTIONNAIRE_REQUEST_NORTHERN_IRELAND));

  public static int calculateQuestionnaireType(String treatmentCode) {
    String country = treatmentCode.substring(treatmentCode.length() - 1);
    if (!country.equals("E") && !country.equals("W") && !country.equals("N")) {
      throw new IllegalArgumentException(
          String.format("Unknown Country for treatment code %s", treatmentCode));
    }

    if (treatmentCode.startsWith("HH")) {
      switch (country) {
        case "E":
          return 1;
        case "W":
          return 2;
        case "N":
          return 4;
      }
    } else if (treatmentCode.startsWith("CI")) {
      switch (country) {
        case "E":
          return 21;
        case "W":
          return 22;
        case "N":
          return 24;
      }
    } else if (treatmentCode.startsWith("CE")) {
      switch (country) {
        case "E":
          return 31;
        case "W":
          return 32;
        case "N":
          return 34;
      }
    } else {
      throw new IllegalArgumentException(
          String.format("Unexpected Case Type for treatment code '%s'", treatmentCode));
    }

    throw new RuntimeException(String.format("Unprocessable treatment code '%s'", treatmentCode));
  }

  public static boolean isQuestionnaireWelsh(String treatmentCode) {
    return (treatmentCode.startsWith("HH_Q") && treatmentCode.endsWith("W"));
  }

  public static boolean isIndividualQuestionnaireType(String questionnaireId) {
    String questionnaireType = questionnaireId.substring(0, 2);

    return individualQuestionnaireTypes.contains(questionnaireType);
  }
}
