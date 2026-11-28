package io.github.samarthshubham.clinic.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.List;

public record PriorAuthRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9.-]{1,64}") String orderId,
    @Valid BillingCode service,
    @NotBlank @Pattern(regexp = "[0-9]{2}") String placeOfService,
    @NotBlank @Pattern(regexp = "[A-Z0-9]{1,2}") String serviceCategory,
    List<@NotNull @Valid BillingCode> diagnoses) {

  public record BillingCode(@NotBlank String system, @NotBlank String code) {}
}
