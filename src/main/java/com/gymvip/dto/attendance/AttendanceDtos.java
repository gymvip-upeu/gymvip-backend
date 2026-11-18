package com.gymvip.dto.attendance;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public class AttendanceDtos {

    public record TerminalDto(
            UUID id,
            String name,
            UUID venueId,
            String status,
            String currentOperator,
            Instant lastActiveAtUtc
    ) {}

    public record TerminalCreateRequest(
            @NotBlank(message = "El nombre de la terminal es obligatorio")
            String name,
            UUID venueId
    ) {}

    public record ChallengeResponse(
            String challengeToken,
            UUID terminalId,
            String terminalName,
            Instant issuedAtUtc,
            Instant expiresAtUtc,
            int refreshIntervalSeconds
    ) {}

    public record MemberSelfCheckInRequest(
            @NotBlank(message = "El desafío QR es obligatorio")
            String challenge,
            UUID requestId
    ) {}

    public record MemberCheckInResponse(
            String status,
            boolean recorded,
            String message,
            LocalDate attendanceDate,
            Instant checkedAtUtc,
            String memberName,
            String memberCode
    ) {
        public static MemberCheckInResponse recorded(LocalDate date, Instant instant, String name, String code) {
            return new MemberCheckInResponse(
                    "ATTENDANCE_RECORDED",
                    true,
                    "¡Ingreso registrado con éxito! Bienvenido(a) a GYM VIP.",
                    date,
                    instant,
                    name,
                    code
            );
        }

        public static MemberCheckInResponse alreadyRegistered(LocalDate date, Instant instant, String name, String code) {
            return new MemberCheckInResponse(
                    "ALREADY_REGISTERED",
                    false,
                    "Su asistencia ya fue registrada el día de hoy.",
                    date,
                    instant,
                    name,
                    code
            );
        }

        public static MemberCheckInResponse rejected(String status, String message, String name, String code) {
            return new MemberCheckInResponse(
                    status,
                    false,
                    message,
                    null,
                    null,
                    name,
                    code
            );
        }
    }

    public record AgendaItemDto(
            UUID id,
            UUID clientId,
            String memberCode,
            String memberName,
            String documentNumber,
            LocalDate attendanceDate,
            Instant checkedAtUtc,
            String checkInMethod,
            String recordedBy,
            UUID terminalId,
            String terminalName,
            String status,
            String details
    ) {}

    public record AgendaStats(
            long totalIngresosHoy,
            long totalRechazosHoy,
            long totalIngresosMes
    ) {}

    public record AgendaPageResponse(
            List<AgendaItemDto> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            AgendaStats stats
    ) {}
}
