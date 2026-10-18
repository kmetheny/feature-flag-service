package org.kyle.featureflagservice.flag;

final class FlagEvaluator {
    private FlagEvaluator() {}

    static int bucket(String flagName, String userId) {
        return Math.floorMod((flagName + ":" + userId).hashCode(), 100);
    }

    static boolean isEnabledFor(Flag flag, String userId, String environment) {
        return flag.isEnabled()
                && flag.getEnvironments().contains(environment)
                && bucket(flag.getName(), userId) < flag.getRolloutPercentage();
    }
}
