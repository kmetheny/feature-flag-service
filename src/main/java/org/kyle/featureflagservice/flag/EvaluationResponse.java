package org.kyle.featureflagservice.flag;

public record EvaluationResponse(String flag, String userId, String environment, boolean enabled) {}
