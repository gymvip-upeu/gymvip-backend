package com.gymvip.controller;

import com.gymvip.service.MemberSignupService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/public/member-signups")
public class MemberSignupController {
    private final MemberSignupService service;

    public MemberSignupController(MemberSignupService service) {
        this.service = service;
    }

    public record Registration(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Pattern(regexp = "[0-9]{8}") String dni,
            @NotNull LocalDate birthDate,
            @NotBlank @Email @Size(max = 150) String email,
            @NotBlank @Pattern(regexp = "[+]519[0-9]{8}") String phone,
            boolean acceptedTerms) {
        @Override public String toString() { return "Registration[datos privados omitidos]"; }
    }

    public record Verification(
            @NotBlank @Pattern(regexp = "[0-9]{6}") String emailCode,
            @NotBlank @Pattern(regexp = "[0-9]{6}") String whatsappCode) {
        @Override public String toString() { return "Verification[códigos omitidos]"; }
    }

    @ExceptionHandler(MemberSignupService.Unavailable.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> unavailable(MemberSignupService.Unavailable error) {
        return Map.of("code", "SIGNUP_UNAVAILABLE", "message", error.getMessage());
    }

    @GetMapping("/availability")
    public Map<String, Boolean> availability() {
        return Map.of("enabled", service.isEnabled());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MemberSignupService.Started start(@Valid @RequestBody Registration request, HttpServletRequest http) {
        return service.start(request, http.getRemoteAddr());
    }

    @PostMapping("/{id}/verify")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public Map<String, String> verify(@PathVariable UUID id, @Valid @RequestBody Verification request) {
        service.verify(id, request);
        return Map.of("message", "Contactos verificados. Revisa tu correo para conocer los pasos de acceso.");
    }
}
