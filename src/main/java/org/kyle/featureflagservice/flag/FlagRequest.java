package org.kyle.featureflagservice.flag;

import jakarta.validation.constraints.*;
import org.jspecify.annotations.Nullable;

import java.util.Set;

public record FlagRequest(
        @FlagKey String name,
        @Nullable @Size(max = 255) String description,
        @NotNull Boolean enabled,
        @NotNull Set<@NotBlank @FlagKey String> environments,
        @NotNull @Min(0) @Max(100) Integer rolloutPercentage) {}
