package org.kyle.featureflagservice.flag;

final class FlagEvaluator {
    private FlagEvaluator() {
    }

    static int bucket(String flagName, String userId) {
        return Math.floorMod((flagName + ":" + userId).hashCode(), 100);
    }

    static boolean isEnabledFor(FlagResponse flag, String userId, String environment) {
        return flag.enabled()
                && flag.environments().contains(environment)
                && bucket(flag.name(), userId) < flag.rolloutPercentage();
    }
}
