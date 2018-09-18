package com.tailf.pkg.ipam.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.SortedSet;
import java.util.TreeSet;

import com.tailf.pkg.ipam.Subnet;
import com.tailf.pkg.ipam.exceptions.InvalidNetmaskException;

/**
 * A class representing a set of InetAddress Ranges.
 * This can be constructed either from a collection of InetAddress ranges
 * or from a collection of Subnets.
 *
 * The Subnets are assumed to be "inclusive", i.e. no broadcast or gateway
 * addresses are reserved.  That is, the Subnets are used to represent
 * IP Address ranges.
 *
 * This is stored as a TreeSet of InetAddressRanges.  InetAddressRanges are
 * comparable in the following way:  One with a lower starting address is
 * "less than" one with a higher starting address.  If both have the same
 * starting address, the one with the lower ending address is "less than"
 * the other.  If both have the same ending address they are equal.
 *
 * InetAddressRanges are kept normalized in this set in the following way:
 * The address ranges are kept in order.  When two adjacent ranges overlap,
 * they are replaced by the union of the two.
 *
 * @author mlutton
 *
 */
public class InetAddressRangeSet {

    private SortedSet<InetAddressRange> itsInnerSet =
        new TreeSet<InetAddressRange>();

    /**
     * Default constructor:  Empty set.
     */
    public InetAddressRangeSet() {}

    /**
     * Constructor takes a set of InetAddressRanges or a set of Subnets.
     *
     * @param inputRanges
     */
    public InetAddressRangeSet(Collection<?> inputSet) {
        // Elements can be subnets or input ranges.
        for (Object eachObject : inputSet) {
            if (eachObject instanceof InetAddressRange) {
                InetAddressRange range = (InetAddressRange)eachObject;
                itsInnerSet.add(range);
            } else {
                Subnet aSubnet = (Subnet) eachObject;
                InetAddressRange range =
                    new InetAddressRange(aSubnet.getAddress(),
                                         aSubnet.getBroadcast());
                itsInnerSet.add(range);
            }
        }
        normalize();
    }

    /**
     * Return the set as a range of Inet Addresses.
     */
    public SortedSet<InetAddressRange> asInetAddressRangeSet() {
        return this.itsInnerSet;
    }

    /**
     * Return the set of ranges as a set of subnets.
     * @return set of subnets.
     * @throws InvalidNetmaskException if the logic is messed up.
     */
    public Collection<Subnet> asSubnetSet()
        throws InvalidNetmaskException {
        Collection<InclusiveSubnet> someSubnets =
            new ArrayList<InclusiveSubnet>();

        // Copy this InetAddressRangeSet.  We are going to remove
        // address ranges from it until it is empty.
        InetAddressRangeSet workSet =
            new InetAddressRangeSet(this.asInetAddressRangeSet());

        while (!workSet.isEmpty()) {
            // Get the lowest range in the set.
            InetAddressRange lowestRange = workSet.lowestRange();

            // Calculate the largest subnet that starts at that range.
            int cidr = lowestRange.cidrForStartAddr();
            InclusiveSubnet largestSubnet =
                new InclusiveSubnet(lowestRange.getStart(), cidr);

            // If that subnet fits, remove it.  Otherwise split the
            // subnet and try with both parts.
            removeSubnetFromWorkset(someSubnets, workSet, largestSubnet);
        }

        // Return ordinary subnets, not inclusive subnets.
        Collection<Subnet> result = new ArrayList<Subnet>();
        for (InclusiveSubnet eachSubnet: someSubnets) {
            // If it's a two-address InclusiveSubnet, split into two.
            if (eachSubnet.size() == 2) {
                Subnet[] singles = eachSubnet.split();
                result.add(new Subnet(singles[0].getAddress(),
                                      singles[0].getMask()));
                result.add(new Subnet(singles[1].getAddress(),
                                      singles[1].getMask()));
            } else {
                result.add(new Subnet(eachSubnet.getAddress(),
                                      eachSubnet.getMask()));
            }
        }
        return result;

    }

    /**
     * Returns the lowest AddressRange in the set.
     * @return
     * @throws NoSuchElementException if empty.
     */
    public InetAddressRange lowestRange() {
        return itsInnerSet.first();
    }

    /**
     * Test if set is empty.
     *
     * @return true if there are no addresses in the set.
     */
    public boolean isEmpty() {
        return itsInnerSet.isEmpty();
    }

    /**
     * Add the range to the set.  Maintain the set as normalized.
     *
     * @param newRange   New range to add.
     */
    public void add(InetAddressRange newRange) {
        itsInnerSet.add(newRange);
        normalize();
    }

    /**
     * Remove a range from the set.
     *
     * @param newRange
     */
    public void remove(InetAddressRange theRange) {
        // Copy all the InetAddressRanges.  For each one,
        // remove theRange. Then normalize.
        SortedSet<InetAddressRange> newSet = new TreeSet<InetAddressRange>();

        for (InetAddressRange eachRange : itsInnerSet) {
            InetAddressRange[] result = eachRange.difference(theRange);
            for (int i = 0; i < result.length; i++) {
                newSet.add(result[i]);
            }
        }

        itsInnerSet = newSet;
        normalize();
    }

    /**
     * This operation maintains the inner set in a "normalized" form,
     * which simply means that there are no overlapping address ranges.
     */
    protected void normalize() {
        // Quickly see if normalized.
        boolean normalized = true;
        InetAddressRange prev = null;

        // SortedSet iterator returns elements in order.
        Iterator<InetAddressRange> iterator = this.itsInnerSet.iterator();

        while (iterator.hasNext()) {
            InetAddressRange next = iterator.next();
            if (prev != null) {
                if (prev.overlaps(next) || prev.isAdjacentTo(next)) {
                    normalized = false;
                    break;
                }
            }
            prev = next;
        }

        if (normalized) {
            return;
        }

        // InetAddressRanges are immutable so the set can't be
        // normalized in place.  Instead, copy to a new set.
        // If an adjacent pair of ranges overlaps, replace with a new
        // range which is the union.
        SortedSet<InetAddressRange> newSet = new TreeSet<InetAddressRange>();
        prev = null;
        iterator = this.itsInnerSet.iterator();

        while (iterator.hasNext()) {
            InetAddressRange next = iterator.next();
            // If next does not overlap with prev, then add prev to set and
            // replace prev with next.
            if (prev == null) {
                prev = next;
            } else {
                // If next DOES overlap with prev, consolidate the two.
                // We know that prev.getStart() <= next.getStart() by definition
                // of our comparator.
                if (prev.overlaps(next) || prev.isAdjacentTo(next)) {
                    prev = prev.combine(next);
                } else {
                    // Does not overlap.
                    newSet.add(prev);
                    prev = next;
                }
            }
        }

        if (prev != null) {
            newSet.add(prev);
        }
        this.itsInnerSet = newSet;
    }

    private static void removeSubnetFromWorkset(
                                       Collection<InclusiveSubnet> newSubnets,
                                       InetAddressRangeSet workSet,
                                       InclusiveSubnet subnet) {
        if (workSet.isEmpty()) {
            return;
        }

        // If the subnet fits, remove it.
        // Otherwise split the subnet and remove both.
        // Stop when we get a subnet that can't be split:
        // i.e. invalidNetmaskException.
        InetAddressRange rangeToRemove =
            new InetAddressRange(subnet);
        InetAddressRange lowestRange = workSet.lowestRange();
        // No overlap?  Can't remove.
        if (!(rangeToRemove.overlaps(lowestRange))) {
            return;
        }

        if (lowestRange.containsAll(rangeToRemove)) {
            // If it fits entirely, add to collection
            // and remove from workSet.
            newSubnets.add(subnet);
            workSet.remove(rangeToRemove);
        } else {
            // Split subnet into 2 and remove both.
            try {
                InclusiveSubnet[] subnets = subnet.split();
                for (int i = 0; i < subnets.length; i++) {
                    removeSubnetFromWorkset(newSubnets, workSet, subnets[i]);
                }
            } catch (InvalidNetmaskException e) {
                // Can't split further; done.
            }
        }
    }

    /**
     * If this InetAddressRangeSet already contains any address
     * in the given InetAddressRange, return true, else false.
     *
     * @param theRange
     * @return
     */
    public boolean alreadyContainsAny(InetAddressRange theRange) {
        for (InetAddressRange eachRange : this.itsInnerSet) {
            if (theRange.overlaps(eachRange)) {
                return true;
            }
        }
        return false;
    }


    @Override
    public String toString() {
        if (itsInnerSet.isEmpty()) {
            return ("<empty>");
        }
        StringBuilder sb = new StringBuilder();
        String comma = "";
        for (InetAddressRange eachRange : itsInnerSet) {
            sb.append(comma);
            comma = ",";
            sb.append(eachRange.toString());
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object otherObject) {
        if (this == otherObject) {
            return true;
        }
        if (otherObject == null) {
            return false;
        }
        if (this.getClass() != otherObject.getClass()) {
            return false;
        }
        InetAddressRangeSet other = (InetAddressRangeSet) otherObject;
        return this.itsInnerSet.equals(other.itsInnerSet);
    }

    @Override
    public int hashCode() {
        return this.itsInnerSet.hashCode();
    }

}
