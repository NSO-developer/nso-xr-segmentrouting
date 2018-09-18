package com.tailf.pkg.ipam.exceptions;

public class AddressNotAllocatedException extends AddressPoolException {
    private static final long serialVersionUID = 0;

    public AddressNotAllocatedException() {
        super("Address was not allocated from the pool");
    }

    public AddressNotAllocatedException(String message) {
        super(message);
    }
}
