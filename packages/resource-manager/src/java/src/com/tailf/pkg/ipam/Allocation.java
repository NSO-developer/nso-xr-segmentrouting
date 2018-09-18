package com.tailf.pkg.ipam;

import java.io.Serializable;

public class Allocation implements Serializable {
    private static final long serialVersionUID = -7671139904147340643L;
    private static final String RESOURCE_TYPE =
        "entity.resourcepool.resource.allocation";

    private String uuid;
    private String etype;
    private Subnet allocated;
    private String occupant;
    private String username;
    private String requestId;

    public String getUuid() {
        return uuid;
    }

    public void setUuid(String uuid) {
        this.uuid = uuid;
    }

    public String getEtype() {
        return etype;
    }

    public void setEtype(String etype) {
        this.etype = etype;
    }

    public Allocation() {}

    public Allocation(Subnet allocated, String occupant, String username, String requestId) {
        this.allocated = allocated;
        this.occupant = occupant;
        this.username = username;
        this.requestId = requestId;
        this.etype = RESOURCE_TYPE;
    }

    public Allocation(Allocation that) {
        super();
        this.allocated = that.allocated;
        this.occupant = that.occupant;
        this.username = that.username;
        this.requestId = that.requestId;
        this.etype = RESOURCE_TYPE;
    }

    public Subnet getAllocated() {
        return allocated;
    }

    public void setAllocated(Subnet allocated) {
        this.allocated = allocated;
    }

    public String getOccupant() {
        return occupant;
    }

    public String getUsername() {
        return username;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setOccupant(String occupant) {
        this.occupant = occupant;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("{\"allocated\": %s, \"occupant\": %s," +
                             " \"username\": %s, \"requestId\": %s}",
                             allocated, occupant, username, requestId);
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + allocated.hashCode();
        result = prime * result + occupant.hashCode();
        result = prime * result + username.hashCode();
        return result;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }

        if (obj == null) {
            return false;
        }

        if (getClass() != obj.getClass()) {
            return false;
        }

        Allocation other = (Allocation) obj;
        if (allocated != other.allocated) {
            return false;
        }

        if (occupant != other.occupant) {
            return false;
        }

        if (username != other.username) {
            return false;
        }

        if (requestId != other.requestId) {
            return false;
        }

        return true;
    }
}
