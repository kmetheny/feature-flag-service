package org.kyle.featureflagservice.flag;

public class DuplicateFlagException extends RuntimeException {
    public DuplicateFlagException(String name) {
        super("Name already exists: " + name);
    }
}
