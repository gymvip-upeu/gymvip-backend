package com.gymvip.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record ReminderConfigRequest(
        @NotNull(message = "El estado de activación es obligatorio")
        Boolean enabled,

        @NotNull(message = "Debe especificar los días de anticipación")
        List<Integer> anticipationDays,

        @Min(value = 1, message = "Los días posteriores deben ser al menos 1")
        @Max(value = 5, message = "Los días posteriores no deben exceder 5")
        int postExpiryDays,

        @NotNull(message = "Debe especificar al menos un canal")
        List<String> channels
) {}
