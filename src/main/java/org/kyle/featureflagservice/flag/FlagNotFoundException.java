package org.kyle.featureflagservice.flag;

public class FlagNotFoundException extends RuntimeException {
    public FlagNotFoundException(Long id) {
        super("Flag not found: " + id);
    }

    public FlagNotFoundException(String name) {
        super("Flag not found: " + name);
    }
}
