package com.tailf.pkg.ipam.exceptions;

public class AddressRequestNotAvailableException extends AddressPoolException {
    private static final long serialVersionUID = 0;

    public AddressRequestNotAvailableException() {
        super("Requested address is not available from the pool");
    }

    public AddressRequestNotAvailableException(String message) {
        super(message);
    }

}
