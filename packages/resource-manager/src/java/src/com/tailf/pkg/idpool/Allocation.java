package com.tailf.pkg.idpool;

import java.io.Serializable;

public class Allocation implements Serializable {

    private static final long serialVersionUID = 2068013029116522734L;
    private long allocated;

    public Allocation() {}

    public Allocation(long allocated) {
        this.allocated = allocated;
    }

    public Allocation(Allocation that) {
        super();
        this.allocated = that.allocated;
    }

    public long getAllocated() {
        return allocated;
    }

    public void setAllocated(long segment) {
        this.allocated = segment;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("{\"segment\":%s}", allocated);
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     * This function order the iterator returns ranges.
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        Long result = 1L;
        result = prime * result + allocated;
        return result.hashCode();
    }

    /*
     * (non-Javadoc)
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

        return true;
    }
}
