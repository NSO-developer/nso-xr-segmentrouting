package com.tailf.pkg.ipam.util;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

import com.tailf.pkg.ipam.Subnet;

/**
 * A SubnetGroup is a Set of Subnets. This class also includes methods
 * to determine containment of Subnets and other SubnetGroups on this
 * SubnetGroup.
 *
 */
public class SubnetGroup implements Serializable {
    private static final long serialVersionUID = -7205764413758813781L;

    private Set<Subnet> subnets = new HashSet<Subnet>();

    public SubnetGroup() {
        super();
    }

    public SubnetGroup(Collection<Subnet> subnets) {
        this.subnets = new HashSet<Subnet>(subnets);
    }

    public void addSubnet(Subnet subnet) {
        if (subnet != null) {
            subnets.add(subnet);
        }
    }

    public void addSubnets(SubnetGroup subnetGroup) {
        if (subnetGroup != null) {
            subnets.addAll(subnetGroup.getSubnets());
        }
    }

    public void removeSubnet(Subnet subnet) {
        if (subnet != null) {
            subnets.remove(subnet);
        }
    }

    public Collection<Subnet> getSubnets() {
        return subnets;
    }

    /**
     * Returns true if this SubnetGroup contains no Subnets.
     *
     * @return true if this SubnetGroup contains no Subnets
     */
    public boolean isEmpty() {
        return subnets.isEmpty();
    }

    /**
     * Returns an iterator for the Subnets in the SubnetGroup.
     * @return an iterator for the Subnets in the SubnetGroup
     */
    public Iterator<Subnet> iterator() {
        return subnets.iterator();
    }

    /**
     * Checks to see if the subnet passed in is equal to or is contained within
     * a subnet in this SubnetGroup.
     *
     * @param subnet Subnet whose inclusion in this SubnetGroup is to be tested
     * @return true if the subnet is equal to or contained in the SubnetGroup
     */
    public boolean contains(Subnet subnet) {
        if (subnets.isEmpty()) {
            return false;
        }

        for (Subnet iSubnet : subnets) {
            if (iSubnet.contains(subnet)) { // this will return true for
                                           // equality as well
                return true;
            }
        }
        return false;
    }


    /**
     * Returns true if any Subnet in the passed in SubnetGroup is equal to
     * or is contained within this SubnetGroup.
     *
     * @param subnetGroup Subnets to test against those in this SubnetGroup
     * @return true if any Subnet in the passed in SubnetGroup is equal
     *         to or is contained within this SubnetGroup.
     *
     */
    public boolean containsAny(SubnetGroup subnetGroup) {
        if (subnetGroup.isEmpty()) {
            return false;
        }

        for (Subnet iSubnet : subnetGroup.getSubnets()) {
            if (contains(iSubnet)) {
                return true;
            }
        }
        return false;
    }


    /**
     * Checks to see that all Subnets in the SubnetGroup passed in are
     * contained within this SubnetGroup.
     *
     * @param subnetGroup
     * @return true if the all Subnets in the passed in SubnetGroup are
     *         contained in this SubnetGroup.
     *
     */
    public boolean containsAll(SubnetGroup subnetGroup) {
        if (subnetGroup.isEmpty()) {
            return false;
        }

        for (Subnet iSubnet : subnetGroup.getSubnets()) {
            if (iSubnet != null && !contains(iSubnet)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Checks to see if the subnet passed in is equal to a subnet in
     * this SubnetGroup.
     *
     * @param subnet Subnet whose inclusion in this SubnetGroup is to be tested
     * @return true if the subnet is equal to or contained in the SubnetGroup
     */
    public boolean containsExact(Subnet subnet) {
        if (subnets.isEmpty()) {
            return false;
        }

        for (Subnet iSubnet : subnets) {
            if (iSubnet.equals(subnet)) { // this will return true for
                                          // equality as well
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        } else if (object == null || getClass() != object.getClass()) {
            return false;
        }

        SubnetGroup other = (SubnetGroup) object;
        if (other.isEmpty() && isEmpty()) {
            return true;
        }

        return other.getSubnets().containsAll(getSubnets()) &&
            getSubnets().containsAll(other.getSubnets());
    }

    @Override
    public int hashCode() {
        return getSubnets().hashCode();
    }

    public String toString() {
        StringBuffer buff = new StringBuffer();
        for (Iterator<Subnet> i = iterator(); i.hasNext(); ) {
            Subnet subnet = i.next();
            buff.append(subnet.toString());
            if (i.hasNext()) {
                buff.append(", ");
            }
        }
        return buff.toString();
    }

}
