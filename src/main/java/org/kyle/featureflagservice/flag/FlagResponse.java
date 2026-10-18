package org.kyle.featureflagservice.flag;

import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.Set;

public record FlagResponse(
        Long id,
        String name,
        @Nullable String description,
        Set<String> environments,
        boolean enabled,
        int rolloutPercentage
) {

    static FlagResponse from(Flag flag) {
        return new FlagResponse(
                Objects.requireNonNull(flag.getId()),
                flag.getName(),
                flag.getDescription(),
                Set.copyOf(flag.getEnvironments()),
                flag.isEnabled(),
                flag.getRolloutPercentage());
    }
}
