package org.kyle.featureflagservice.flag;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

class FlagEvaluatorTest {
    private static FlagResponse flagResponse(boolean enabled, int rollout, String env) {
        return new FlagResponse(0L, "test-flag", null, Set.of(env), enabled, rollout);
    }

    @Test
    void disabledFlagAlwaysOff() {
        String environment = "dev";
        assertThat(FlagEvaluator.isEnabledFor(flagResponse(false, 100, environment), "1234", environment)).isFalse();
    }

    @Test
    void zeroPercentRolloutAlwaysOff() {
        String environment = "dev";
        assertThat(FlagEvaluator.isEnabledFor(flagResponse(true, 0, environment), "1234", environment)).isFalse();
    }

    @Test
    void untargetedEnvironmentAlwaysOff() {
        assertThat(FlagEvaluator.isEnabledFor(flagResponse(true, 100, "dev"), "1234", "test")).isFalse();
    }

    @Test
    void zeroAppliesToEveryone() {
        String env = "dev";
        for (int i = 0; i < 1000; i++) {
            FlagResponse flag = flagResponse(true, 0, env);
            assertThat(FlagEvaluator.isEnabledFor(flag, "user-" + i, env)).isFalse();
        }
    }

    @Test
    void oneHundredAppliesToEveryone() {
        String env = "dev";
        for (int i = 0; i < 1000; i++) {
            FlagResponse flag = flagResponse(true, 100, env);
            assertThat(FlagEvaluator.isEnabledFor(flag, "user-" + i, env)).isTrue();
        }
    }

    @Test
    void rolloutBoundaryIsExclusive() {
        int b = FlagEvaluator.bucket("test-flag", "user-1");
        assertThat(FlagEvaluator.isEnabledFor(flagResponse(true, b, "dev"), "user-1", "dev")).isFalse();
        assertThat(FlagEvaluator.isEnabledFor(flagResponse(true, b + 1, "dev"), "user-1", "dev")).isTrue();
    }

    @Test
    void rolloutIsRoughlyProportional() {
        FlagResponse f = flagResponse(true, 30, "dev");
        long on = IntStream.range(0, 10_000)
                .filter(i -> FlagEvaluator.isEnabledFor(f, "user-" + i, "dev")).count();
        assertThat(on).isBetween(2_700L, 3_300L);
    }

    @Test
    void sameUserAlwaysGetsSameResult() {
        FlagResponse f = flagResponse(true, 50, "dev");
        boolean first = FlagEvaluator.isEnabledFor(f, "user-1", "dev");
        for (int i = 0; i < 100; i++) {
            assertThat(FlagEvaluator.isEnabledFor(f, "user-1", "dev")).isEqualTo(first);
        }
    }
}
