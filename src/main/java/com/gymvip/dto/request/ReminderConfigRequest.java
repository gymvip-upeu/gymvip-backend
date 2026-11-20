package com.gymvip.dto.request;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.stream.IntStream;
public record ReminderConfigRequest(
    @NotNull Boolean enabled,
    @NotNull List<@Min(1) @Max(30) Integer> anticipationDays,
    @Min(1) @Max(5) int postExpiryDays,
    @NotEmpty List<@Pattern(regexp="WHATSAPP|APP") String> channels,
    String defaultChannel,
    @NotEmpty List<@Min(1) @Max(5) Integer> postExpiryDaysSelected
) {
    public ReminderConfigRequest(Boolean enabled, List<Integer> days, int post, List<String> channels) {
        this(enabled, days, post, channels, null, null);
    }
    public ReminderConfigRequest(Boolean enabled, List<Integer> days, int post, List<String> channels, String defaultChannel) {
        this(enabled, days, post, channels, defaultChannel, null);
    }
    public ReminderConfigRequest {
        // Legacy callers can still send one channel and a maximum post-expiry day.
        if (channels == null) channels = List.of(defaultChannel == null ? "WHATSAPP" : defaultChannel);
        channels = channels.stream().distinct().toList();
        if (postExpiryDaysSelected == null)
            postExpiryDaysSelected = IntStream.rangeClosed(1, Math.min(postExpiryDays, 5)).boxed().toList();
        else postExpiryDaysSelected = postExpiryDaysSelected.stream().distinct().sorted().toList();
    }
}
