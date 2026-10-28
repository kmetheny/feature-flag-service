package org.kyle.featureflagservice.flag;

public class InvalidSortException extends RuntimeException {
    public InvalidSortException(String invalidSort) {
        super("Invalid sort: " + invalidSort);
    }
}
