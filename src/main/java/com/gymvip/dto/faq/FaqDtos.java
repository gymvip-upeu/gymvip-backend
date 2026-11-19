package com.gymvip.dto.faq;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.UUID;

public final class FaqDtos {
    private FaqDtos() {}

    public record Content(
            @NotBlank @Size(max = 300) String question,
            @NotBlank @Size(max = 10000) String answer,
            @NotBlank @Size(max = 100) String category
    ) {}

    public record Record(UUID id, Content content, Content proposal, boolean active, int revision) {}

    public record Update(@NotNull @Positive Integer revision, @NotNull @Valid Content content) {}

    public record Transition(@NotNull @Positive Integer revision) {}
}
