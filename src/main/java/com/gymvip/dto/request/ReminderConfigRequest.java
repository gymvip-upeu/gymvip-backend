package com.gymvip.dto.request;

import jakarta.validation.constraints.*;
import java.util.List;
import java.util.stream.IntStream;

public record ReminderConfigRequest(
    @NotNull(message = "El estado de activación es obligatorio") Boolean enabled,
    @NotNull(message = "Debe especificar los días de anticipación") List<@Min(1) @Max(30) Integer> anticipationDays,
    @Min(value = 1, message = "Los días posteriores deben ser al menos 1") @Max(value = 5, message = "Los días posteriores no deben exceder 5") int postExpiryDays,
    List<@Pattern(regexp="WHATSAPP|EMAIL|APP") String> channels,
    String defaultChannel,
    List<@Min(1) @Max(5) Integer> postExpiryDaysSelected
) {
    public ReminderConfigRequest(Boolean enabled, List<Integer> days, int post, List<String> channels) {
        this(enabled, days, post, channels, null, null);
    }

    public ReminderConfigRequest(Boolean enabled, List<Integer> days, int post, List<String> channels, String defaultChannel) {
        this(enabled, days, post, channels, defaultChannel, null);
    }

    public ReminderConfigRequest {
        if ((channels == null || channels.isEmpty()) && defaultChannel != null && !defaultChannel.isBlank()) {
            channels = List.of(defaultChannel.trim());
        }
        if (channels == null || channels.isEmpty()) {
            channels = List.of("WHATSAPP");
        }
        channels = channels.stream().distinct().toList();
        if (postExpiryDaysSelected == null || postExpiryDaysSelected.isEmpty()) {
            postExpiryDaysSelected = IntStream.rangeClosed(1, Math.min(Math.max(postExpiryDays, 1), 5)).boxed().toList();
        } else {
            postExpiryDaysSelected = postExpiryDaysSelected.stream().distinct().sorted().toList();
        }
    }
}
