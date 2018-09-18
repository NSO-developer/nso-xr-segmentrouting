package com.tailf.pkg.resourcemanager;

public class ResourceWaitException extends ResourceException {

    private static final long serialVersionUID = -5573791052156594213L;

    /**
     * Resource not yet ready
     */

    public ResourceWaitException(String msg) {
        super(msg);
    }

}
