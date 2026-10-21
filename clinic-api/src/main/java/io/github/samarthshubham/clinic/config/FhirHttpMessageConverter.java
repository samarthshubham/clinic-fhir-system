package io.github.samarthshubham.clinic.config;

import ca.uhn.fhir.context.FhirContext;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.springframework.http.HttpInputMessage;
import org.springframework.http.HttpOutputMessage;
import org.springframework.http.MediaType;
import org.springframework.http.converter.AbstractHttpMessageConverter;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.converter.HttpMessageNotWritableException;

public class FhirHttpMessageConverter extends AbstractHttpMessageConverter<IBaseResource> {

  static final MediaType FHIR_JSON = MediaType.valueOf("application/fhir+json");

  private final FhirContext fhirContext;

  public FhirHttpMessageConverter(FhirContext fhirContext) {
    super(FHIR_JSON, MediaType.APPLICATION_JSON);
    this.fhirContext = fhirContext;
  }

  @Override
  protected boolean supports(Class<?> clazz) {
    return IBaseResource.class.isAssignableFrom(clazz);
  }

  @Override
  protected IBaseResource readInternal(
      Class<? extends IBaseResource> clazz, HttpInputMessage inputMessage)
      throws IOException, HttpMessageNotReadableException {
    return fhirContext.newJsonParser().parseResource(clazz, inputMessage.getBody());
  }

  @Override
  protected void writeInternal(IBaseResource resource, HttpOutputMessage outputMessage)
      throws IOException, HttpMessageNotWritableException {
    outputMessage.getHeaders().setContentType(FHIR_JSON);
    try (Writer writer = new OutputStreamWriter(outputMessage.getBody(), StandardCharsets.UTF_8)) {
      fhirContext.newJsonParser().setPrettyPrint(true).encodeResourceToWriter(resource, writer);
    }
  }
}
