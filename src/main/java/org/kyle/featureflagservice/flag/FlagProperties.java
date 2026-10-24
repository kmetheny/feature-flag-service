package org.kyle.featureflagservice.flag;

import jakarta.validation.constraints.NotEmpty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.Set;

@Validated
@ConfigurationProperties(prefix = "flags")
public record FlagProperties(@NotEmpty Set<@FlagKey String> environments) {
}
