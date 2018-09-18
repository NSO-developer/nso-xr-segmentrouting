package com.tailf.pkg.ipam;

import java.io.Serializable;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.util.Collection;
import java.util.Set;

import com.tailf.conf.ConfIdentityRef;
import com.tailf.pkg.ipam.exceptions.AddressNotAllocatedException;
import com.tailf.pkg.ipam.exceptions.AddressPoolEmptyException;
import com.tailf.pkg.ipam.exceptions.AddressPoolException;
import com.tailf.pkg.ipam.exceptions.AddressPoolMaskInvalidException;
import com.tailf.pkg.ipam.exceptions.AddressRequestNotAvailableException;
import com.tailf.pkg.ipam.exceptions.InvalidNetmaskException;
import com.tailf.pkg.ipam.util.InetAddressRangeSet;

import com.tailf.pkg.ipaddressallocator.namespaces.ipaddressAllocator;
import com.tailf.pkg.nsoutil.Pool;

import org.apache.log4j.Logger;

public class IPAddressPool extends Pool implements Serializable {

    private static Logger LOGGER = Logger.getLogger(IPAddressPool.class);

    private static final long serialVersionUID = 0;
    private Set<Subnet> subnets; /*
                                  * Original Subnets, avoid handing out
                                  * /32 and /128 network and broadcast
                                  *  addresses from these networks
                                  */
    private Set<Subnet> availables;
    private Set<Allocation> allocations;

    private String name;

    public IPAddressPool(String name,
                         Set<Subnet> availables,
                         Set<Allocation> allocations,
                         Set<Subnet> subnets) {
        super(name, new ConfIdentityRef(ipaddressAllocator.hash,
                                    ipaddressAllocator._ip_address_pool_exhausted),
              new ConfIdentityRef(ipaddressAllocator.hash,
                      ipaddressAllocator._ip_address_pool_low_threshold_reached),
              false, 10);

        this.name = name;
        this.availables = availables;
        this.allocations = allocations;
        this.subnets = subnets;
    }

    public String getName() {
        return name;
    }

    public synchronized Allocation allocate(int cidr,
                                            String owner,
                                            String username,
                                            String requestId)
        throws AddressPoolException {
        return this.allocate(cidr,
                             cidr,
                             owner,
                             username,
                             requestId);
    }

    public synchronized Allocation allocate(int cidr4,
                                            int cidr6,
                                            String owner,
                                            String username,
                                            String requestId)
        throws AddressPoolException {
        /*
         * Iterate through available subnets. The set is ordered from
         * narrowest to widest so we will choose the narrowest subnet
         * that fits the requested size.
         */
        for (Subnet availableSubnet : availables) {
            int cidr;

            InetAddress address = availableSubnet.getAddress();
            if (address instanceof Inet4Address) {
                cidr = cidr4;
            } else if (address instanceof Inet6Address) {
                cidr = cidr6;
            } else {
                throw new Error("Unsupported IP version");
            }

            if (availableSubnet.getCIDRMask() == cidr &&
                notNetworkBroadcast(availableSubnet, cidr)) {
                availables.remove(availableSubnet);
                allocations.add(new Allocation(availableSubnet, owner, username, requestId));
                reviewAlarms();
                return new Allocation(availableSubnet, owner, username, requestId);
            } else if (availableSubnet.getCIDRMask() < cidr) {
                return allocateFrom(availableSubnet, cidr, owner, username, requestId);
            }
        }

        /* If we get here, then there is no room in the pool for the requested subnet */

        StringBuffer availMasks = new StringBuffer();
        for (Subnet availSubnet : availables) {
            int msk = availSubnet.getCIDRMask();
            if ((msk != 30) && (msk != 31) && (msk != 32)) {
                availMasks.append(msk + " ");
            }
        }
        if (availMasks.length() == 0) { // Empty pool
            LOGGER.debug("Availables is empty!");
            reviewAlarms();
            throw new AddressPoolEmptyException();
        } else {
            String err = String.format("Requested subnet is too big. Available prefix lengths: %s",
                                       availMasks.toString());
            throw new AddressPoolMaskInvalidException(err);
        }
    }

    private boolean notNetworkBroadcast(Subnet net, int cidr) {
        InetAddress a = net.getAddress();
        if (((a instanceof Inet4Address) && cidr != 32) ||
            ((a instanceof Inet6Address) && cidr != 128)) {
            return true;
        }

        for(Subnet sub : subnets) {
            InetAddress na = sub.getAddress();
            InetAddress ba = sub.getBroadcast();
            if (na instanceof Inet6Address) {
                if (sub.getCIDRMask() > 126) {
                    /* Don't worry about broadcast for such small networks */
                    continue;
                }
                if (na.equals(a) || ba.equals(a)) {
                    return false;
                }
            } else {
                if (sub.getCIDRMask() > 30) {
                    /* Don't worry about broadcast for such small networks */
                    continue;
                }
                if (na.equals(a) || ba.equals(a)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Allocation allocateFrom(Subnet source, int request,
                                    String owner, String username, String requestId) {

        assert(source.getCIDRMask() <= request);
        /* In any case, source subnet will no longer be available */
        availables.remove(source);
        if (source.getCIDRMask() == request) {
            Allocation a = new Allocation(source, owner, username, requestId);
            allocations.add(a);
            reviewAlarms();
            return a;
        }
        /* Split source, make the two halves available, and recurse on the first half. */
        try {
            Subnet[] subs = source.split();

            /* Special case here to handle subnet of just two addresses, ie /31. */
            boolean isIpv6 = source.getAddress() instanceof Inet6Address;

            if ((!isIpv6 && subs.length == 4 && request == 31 ) ||
                ( isIpv6 && subs.length == 4 && request == 127)) {
                /*
                 * Split the four into two twos instead
                 * Add only single addresses to available.
                 * add 3rd and 4th to available.
                 */
                availables.add(subs[2]);
                availables.add(subs[3]);
                /* Allocate as a 2-address "Subnet". */
                Allocation a = new Allocation(new Subnet(subs[0].getAddress(), request),
                                              owner, username, requestId);
                allocations.add(a);
                reviewAlarms();
                return a;
            } else {
                for (int i = 0; i < subs.length; i++) {
                    availables.add(subs[i]);
                }

                int Sub0CIDR = subs[0].getCIDRMask();

                if ((!isIpv6 && request == 32 &&  Sub0CIDR == 32) ||
                    ( isIpv6 && request == 128 && Sub0CIDR == 128)) {

                    if (notNetworkBroadcast(subs[0], request)) {
                        return allocateFrom(subs[0], request, owner, username, requestId);
                    } else {
                        return allocateFrom(subs[1], request, owner, username, requestId);
                    }
                } else {
                    return allocateFrom(subs[0], request, owner, username, requestId);
                }
            }
        } catch (InvalidNetmaskException e) {
            throw new Error("Internal error, allocation failed", e);
        }
    }

    public synchronized void addToAvailable(Subnet subnet) {

        // If subnet is null, then do not add to available.
        if (subnet == null) {
            return;
        }

        availables.add(subnet);

        /*
         * With IP Address Reservation we now have the situation where
         * the user may allocate subnets and then free portions of the
         * subnets.  The original code (commented out below) did not
         * handle this situation correctly.  For example if the
         * available subnets were 10.1.0.0/32, 10.1.0.1/32,
         * 10.1.0.2/31, 10.1.0.4/30, 10.1.0.8/29, 10.1.0.16/28,
         * 10.1.0.32/27, 10.1.0.64/26 and 10.1.0.128/25, and subnet
         * 10.1.0.0/25 was added to the available pool, it would merge
         * 10.1.0.18/25 and 10.1.0.0/25 into 10.1.0.0/24, without
         * removing the others, so many subnets would be in the
         * available list twice.  Then when for instance 10.1.0.16/28
         * was reserved and removed, 10.1.0.0/24 would still be there,
         * and 10.1.0.0/24 includes 10.1.0.16/28
         */

        /* Put all available addresses into a RangeSet. */
        InetAddressRangeSet rangeSet = new InetAddressRangeSet(availables);

        /* Now copy them into available as subnets. */
        availables.clear();

        try {
            for (Subnet eachSubnet : rangeSet.asSubnetSet()) {
                assert(eachSubnet instanceof Subnet);
                availables.add(eachSubnet);
            }
        } catch (InvalidNetmaskException e1) {
            throw new Error(e1); // Should not happen
        }
    }

    public synchronized void removeFromAvailable(Subnet subnet)
        throws AddressPoolException {

        /* If subnet is null, then do not remove from available. */
        if (subnet == null) {
            return;
        }


        /*
         * If the subnet to remove is a two-address subnet we need to split it and
         * remove each address separately (as that is how they are stored in the
         * availables set).
         */
        if (subnet.size() == 0) {
            try {
                Subnet[] subs = subnet.split();
                removeFromAvailable(subs[0]);
                removeFromAvailable(subs[1]);
                return;
            } catch (InvalidNetmaskException e) {
                throw new Error(e); // Can not happen when subnet.size() is 0
            }
        }

        /* Must exactly match an available subnet or be contained in another subnet. */
        if (availables.contains(subnet)) {
            availables.remove(subnet);
        } else {
            /*
             * We did not find an exact match, look for subnet
             * that contains the desired subnet, split and call
             * recursively
             */
            for (Subnet source : availables) {
                if (source.contains(subnet)) {
                    /* Split subnet and remove the part we are looking for */
                    availables.remove(source);
                    assert(source.getCIDRMask() < subnet.getCIDRMask());
                    /*
                     * Split source and put the two halves on the available list
                     * recurse on the half that contains the subnet
                     */
                    try {
                        Subnet[] subs = source.split();

                        if (subs.length == 4 && subnet.size() == 0) {
                            /* Split the four into two twos instead. */
                            Subnet[] newSubs = source.split4into2();
                            if (newSubs[0].contains(subnet)) {
                                /* Add 3rd and 4th to available */
                                availables.add(subs[2]);
                                availables.add(subs[3]);
                            } else {
                                /* Add 1st and 2nd to availables */
                                availables.add(subs[0]);
                                availables.add(subs[1]);
                            }

                            return;
                        } else {
                            for (Subnet s : subs) {
                                availables.add(s);
                            }
                            removeFromAvailable(subnet);
                            return;
                        }
                    } catch (InvalidNetmaskException e) {
                        String err = String
                            .format("Address %s is not an available subnet defined by the pool",
                                    subnet);
                        throw new AddressRequestNotAvailableException(err);
                    }
                }
            }

            /* No subnet found, throw error */
            String err =
                String.format("Address %s is not an available subnet defined by the pool", subnet);
            throw new AddressRequestNotAvailableException(err);
        }
    }

    public synchronized void release(Allocation allocation) throws AddressPoolException {
        if (!allocations.contains(allocation)) {
            String err = String.format("Allocation %s was not allocated from the pool", allocation);
            throw new AddressNotAllocatedException(err);
        }
        allocations.remove(allocation);
        addToAvailable(allocation.getAllocated());
        reviewAlarms();
    }

    public synchronized void release(InetAddress addr) throws AddressPoolException {
        /* Need to find allocated with this network address. */
        for (Allocation allocated : allocations) {
            if (allocated.getAllocated().getAddress().equals(addr)) {
                release(allocated);
                return;
            }
        }
        /* If we make it here, then the address wasn't found */
        String err = String.format("Address %s was not allocated from the pool", addr);
        throw new AddressNotAllocatedException(err);
    }

    public synchronized void releaseAll() {
        for (Allocation a : allocations) {
            addToAvailable(a.getAllocated());
        }
        allocations.clear();
        reviewAlarms();
    }

    public Collection<Subnet> getAvailables() {
        return availables;
    }

    public Collection<Allocation> getAllocations() {
        return allocations;
    }

    public synchronized void addAllocation(Allocation a) {
        this.allocations.add(a);
    }

    public synchronized void clearAllocations() {
        this.allocations.clear();
    }

    public boolean isEmpty() {
        return availables.isEmpty();
    }

    public long getNumberOfAvailables () {
        long numberOfAvailables = 0;
        for (Subnet subnet : availables) {
            numberOfAvailables += subnet.size();
        }
        return numberOfAvailables;
    }

    public long getTotalSize() {
        long sum = 0;
        for (Subnet subnet : subnets) {
            sum += subnet.size();
        }
        return sum;
    }
}
