package com.sauti.demo;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class DemoRequestDtos {
    private DemoRequestDtos() { }

    public record CreateDemoRequest(
            @NotBlank @Size(max = 120) String businessName,
            @NotBlank @Size(max = 120) String contactName,
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(min = 2, max = 2) String countryCode,
            @Size(max = 40) String phone,
            @NotBlank @Size(max = 80) String industry,
            @NotBlank @Size(max = 40) String monthlyCallVolume,
            @NotEmpty @Size(max = 5) List<@NotBlank @Size(max = 30) String> channels,
            @NotBlank @Size(max = 500) String primaryUseCase,
            @Size(max = 1000) String notes,
            @Size(max = 120) String website
    ) { }

    public record DemoRequestResponse(String status, String message) { }
}
