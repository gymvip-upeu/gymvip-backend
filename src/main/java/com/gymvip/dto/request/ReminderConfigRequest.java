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

        List<String> channels,
        String defaultChannel
) {
    public ReminderConfigRequest(Boolean enabled, List<Integer> anticipationDays, int postExpiryDays, List<String> channels) {
        this(enabled, anticipationDays, postExpiryDays, channels, null);
    }

    public ReminderConfigRequest {
        if ((channels == null || channels.isEmpty()) && defaultChannel != null && !defaultChannel.isBlank()) {
            channels = List.of(defaultChannel.trim());
        }
        if (channels == null || channels.isEmpty()) {
            channels = List.of("WHATSAPP");
        }
    }
}
