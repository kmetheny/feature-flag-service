package org.kyle.featureflagservice.flag;

import jakarta.persistence.*;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

@Entity
public class Flag {
    @Nullable
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @FlagKey
    @Column(unique = true, nullable = false)
    private String name = "";

    @Nullable
    @Size(max = 255)
    private String description;

    @ElementCollection
    @CollectionTable(
            name = "flag_environments",
            joinColumns = @JoinColumn(name = "flag_id")
    )
    private Set<String> environments = new HashSet<>();

    private boolean enabled;

    @Min(0)
    @Max(100)
    private int rolloutPercentage;

    @Nullable
    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Nullable
    public String getDescription() {
        return description;
    }

    public void setDescription(@Nullable String description) {
        this.description = description;
    }

    public Set<String> getEnvironments() {
        return environments;
    }

    public void setEnvironments(Set<String> environments) {
        this.environments = environments;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getRolloutPercentage() {
        return rolloutPercentage;
    }

    public void setRolloutPercentage(int rolloutPercentage) {
        this.rolloutPercentage = rolloutPercentage;
    }
}
