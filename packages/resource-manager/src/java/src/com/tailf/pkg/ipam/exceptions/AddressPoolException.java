package com.tailf.pkg.ipam.exceptions;

public class AddressPoolException extends Exception {
    private static final long serialVersionUID = 0;

    public AddressPoolException(Throwable cause) {
        super(cause);
    }

    public AddressPoolException(String message) {
        super(message);
    }

    public AddressPoolException(String message, Throwable cause) {
        super(message, cause);
    }
}
