package org.kyle.featureflagservice.flag;

import java.util.List;

public class UnknownEnvironmentException extends RuntimeException {
    public UnknownEnvironmentException(List<String> names) {
        super("Unknown environments: " + names);
    }
}
