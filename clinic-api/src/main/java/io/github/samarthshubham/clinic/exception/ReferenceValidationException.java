package io.github.samarthshubham.clinic.exception;

public class ReferenceValidationException extends RuntimeException {

  public ReferenceValidationException(String referencedType, String id) {
    super("Referenced " + referencedType + " with id '" + id + "' does not exist");
  }

  public ReferenceValidationException(String referencedType, String id, String patientId) {
    super(
        "Referenced "
            + referencedType
            + " with id '"
            + id
            + "' does not belong to Patient '"
            + patientId
            + "'");
  }
}
