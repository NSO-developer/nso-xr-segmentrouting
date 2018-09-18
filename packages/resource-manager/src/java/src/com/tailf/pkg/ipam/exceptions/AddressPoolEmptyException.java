package com.tailf.pkg.ipam.exceptions;

public class AddressPoolEmptyException extends AddressPoolException {
    private static final long serialVersionUID = 0;

    public AddressPoolEmptyException() {
        super("No addresses available");
    }
}
