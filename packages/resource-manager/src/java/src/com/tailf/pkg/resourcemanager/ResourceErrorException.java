package com.tailf.pkg.resourcemanager;

public class ResourceErrorException extends ResourceException {

    private static final long serialVersionUID = -5799506847324229352L;

    /**
     * Failed to allocate resource
     */

    public ResourceErrorException(String msg) {
        super(msg);
    }

    public ResourceErrorException(String msg, Throwable cause) {
        super(msg, cause);
    }
}
