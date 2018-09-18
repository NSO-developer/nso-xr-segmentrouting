package com.tailf.pkg.idpool;

import java.io.Serializable;

/*
 * Range of Integer
 *
 * Comparable in order to use Range in the ordered TreeSet.
 * Compares on the start of the Range.
 */
public class Range implements Serializable, Comparable<Range> {

    private static final long serialVersionUID = 5090757995724461537L;
    private long start;
    private long end;

    public Range() {}

    public Range(long start, long end) {
        super();
        if (end >= start) {
            this.start = start;
            this.end = end;
        } else {
            this.end = start;
            this.start = end;
        }
    }

    public Range(Range that) {
        super();
        this.start = that.start;
        this.end = that.end;
    }

    public long getStart() {
        return start;
    }

    public long getEnd() {
        return end;
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    @Override
    public String toString() {
        return String.format("{\"start\":%s,\"end\":%s}",
                                 start, end);
    }

    /*
     * (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        Long result = 1L;
        result = prime * result + start;
        result = prime * result + end;
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

        Range other = (Range) obj;

        if (start != other.start) {
            return false;
        }

        if (end != other.end) {
            return false;
        }

        return true;
    }

    public boolean contains(long id) {
        return id >= start && id <= end;
    }

    @Override
    public int compareTo(Range other) {
        if(this.start > other.start) {
            return 1;
        } else if (this.start < other.start) {
            return -1;
        } else {
            return 0;
        }
    }

    /* Only works if start is < end. */
    public boolean isDisjoint(Range that) {
        return  this.getEnd() < that.getStart() ||
            this.getStart() > that.getEnd();
    }
}
