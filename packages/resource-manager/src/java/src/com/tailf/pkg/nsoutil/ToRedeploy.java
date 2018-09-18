package com.tailf.pkg.nsoutil;

/**
 * When re-deploying a service, we also want to
 * know which user to re-deploy with. This class serves
 * as a Pair class to keep track of this relation.
 *
 * @author krisallb
 *
 */
public class ToRedeploy {
    private final String allocatingService;
    private final String username;

    public ToRedeploy(String allocatingService, String username) {
        this.allocatingService = allocatingService;
        this.username = username;
    }

    public String getAllocatingService() {
        return allocatingService;
    }

    public String getUsername() {
        return username;
    }

    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + allocatingService.hashCode();
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

        ToRedeploy other = (ToRedeploy) obj;

        if (!allocatingService.equals(other.allocatingService)) {
            return false;
        }

        if (!username.equals(other.username)) {
            return false;
        }

        return true;
    }
}
