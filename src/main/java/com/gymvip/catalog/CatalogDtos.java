package com.gymvip.catalog;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

public final class CatalogDtos {
    private CatalogDtos() {}
    public record SupplementInput(
        @NotBlank @Size(max=120) String name, @NotBlank @Size(max=120) String brand,
        @NotBlank @Pattern(regexp="PROTEINA|CREATINA|PREENTRENO|AMINOACIDOS|VITAMINAS|OTROS") String category,
        @NotBlank @Size(max=120) String presentation, @NotBlank @Size(max=100) String netContent,
        @Size(max=100) String flavor, @NotBlank @Size(max=4000) String description,
        @Size(max=2000) String ingredients,
        @NotNull @Positive @Digits(integer=10,fraction=2) BigDecimal pricePen,
        @NotBlank @Pattern(regexp="DISPONIBLE|AGOTADO") String availability,
        boolean published, boolean reviewed, Long version, @Size(max=5) List<UUID> keepImageIds, String primaryImageKey) {}
    public record PromotionCancellation(@NotNull Long version) {}
    public record Image(UUID id,String filename,String mediaType) {}
    public record Supplement(UUID id,String name,String brand,String category,String presentation,String netContent,
        String flavor,String description,String ingredients,BigDecimal pricePen,String availability,boolean published,
        long version,LocalDateTime updatedAtUtc,List<Image> images) {}
    public record StateInput(@NotNull Long version,
        @NotBlank @Pattern(regexp="DISPONIBLE|AGOTADO") String availability, boolean published) {}
    public record PromotionInput(@NotNull UUID supplementId,@NotBlank @Size(max=160) String name,
        @NotBlank @Size(max=2000) String description,@NotBlank @Size(max=2000) String conditions,
        @NotNull @Positive @Digits(integer=10,fraction=2) BigDecimal offerPricePen,
        @NotNull LocalDate startDate,@NotNull LocalDate endDate,boolean enabled,boolean reviewed,Long version,@NotNull Long supplementVersion) {}
    public record Promotion(UUID id,Supplement supplement,String name,String description,String conditions,
        BigDecimal referencePricePen,BigDecimal offerPricePen,BigDecimal savingsPen,
        LocalDate startDate,LocalDate endDate,String state,boolean enabled,long version,LocalDateTime updatedAtUtc) {}
    public record Page<T>(List<T> content,long totalElements,int page,int totalPages) {}
    public record Change(long version,String actor,LocalDateTime changedAtUtc,String snapshot) {}
    public record BinaryImage(byte[] bytes,String mediaType,String sha256) {}
}
