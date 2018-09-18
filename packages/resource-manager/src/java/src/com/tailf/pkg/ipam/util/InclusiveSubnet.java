package com.tailf.pkg.ipam.util;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Iterator;

import com.tailf.pkg.ipam.Subnet;
import com.tailf.pkg.ipam.exceptions.InvalidNetmaskException;

/**
 * InclusiveSubnet is a class that behaves similarly to Subnet except
 * that it is considered to be a block of IP Addresses with no broadcast
 * or gateway address.  As such:
 *
 * 1. A value like 192.168.0.0/31 makes sense.  That can't be a real Subnet,
 *    but for us that is a block containing the two addresses 192.168.0.0
 *    and 192.168.0.1.
 *
 * 2. The size will generally be 2 greater than the size of the equivalent
 *    subnet.  For instance the size of 192.168.0.0/30 will be 4.
 *
 * 3. An InclusiveSubnet of size 4 can be split into two InclusiveSubnets
 *    of size 2; an InclusiveSubnet of size 2 can be split into two
 *    InclusiveSubnets of size 1.
 *
 * @author mlutton
 *
 */
public class InclusiveSubnet extends Subnet {

    private static final long serialVersionUID = -8157913671904004137L;

    /**
     * creates a new InclusiveSubnet given a subnet expression, ie a
     * String containing : &lt;IP
     * address&gt;'&thinsp;/&thinsp;'(&lt;netmask expressed in dotquad
     * notation&gt; | &lt;int mask length&gt;)
     *
     * @param expression DOCUMENT ME!
     *
     * @throws UnknownHostException if the &lt;IP address&gt; is not valid or
     *         if the netmask.
     * @throws InvalidNetmaskException if the expression trailing the
     *         '&thinsp;/&thinsp;' is not     recognized as a valid netmask.
     */
    public InclusiveSubnet(String expression)
        throws UnknownHostException, InvalidNetmaskException {
        super(expression);
    }

    /**
     * creates a new InclusiveSubnet given an expression for the the
     * subnet address and its netmask. <br> The mask argument may be
     * specified either as a CIDR mask length, or as a netmask in
     * dotquad notation.
     *
     * @param address IP Address or host name
     * @param mask Dot-decimal notation OR integer mask length ( 1 to 31 )
     *
     * @throws UnknownHostException if the &lt;IP address&gt; is not valid or
     *         if the netmask.
     * @throws InvalidNetmaskException if the expression is not     recognized
     *         as a valid netmask.
     */
    public InclusiveSubnet(String address, String mask)
        throws UnknownHostException, InvalidNetmaskException {
        super(address, mask);
    }


    /**
     * creates a subnet with the specified network address and mask.
     *
     * @param address DOCUMENT ME!
     * @param mask DOCUMENT ME!
     *
     * @throws InvalidNetmaskException if the expression is not     recognized
     *         as a valid netmask.
     */
    public InclusiveSubnet(InetAddress address, InetAddress mask)
        throws InvalidNetmaskException {
        super(address, mask);
    }

    /**
     * creates a subnet with the specified network address and mask.
     *
     * @param address DOCUMENT ME!
     * @param mask DOCUMENT ME!
     *
     * @throws InvalidNetmaskException if the mask is not valid,     i.e. ! ( 0
     *         &lt;= mask &lt;= 32)
     */
    public InclusiveSubnet(InetAddress address, int mask)
        throws InvalidNetmaskException {
        super(address, mask);
    }

    public InclusiveSubnet(String address, int mask)
        throws UnknownHostException, InvalidNetmaskException {
        super(address, mask);
    }

    public InclusiveSubnet(Subnet subnet)
        throws InvalidNetmaskException {
        super(subnet.getAddress(), subnet.getCIDRMask());
    }

    /**
     * Retrieve an Iterator on the addresses contained in the set.
     * The first and last address are NOT discarded, because this
     * isn't really a Subnet at all but a block of addresses.
     *
     * @return an InclusiveSubnet iterator.
     */
    @Override
    public Iterator<InetAddress> iterator() {
        return new InclusiveSubnetIterator();
    }

    @Override
    public Iterator<InetAddress> iterator(boolean reverse) {
        return new InclusiveSubnetIterator(reverse);
    }

    /**
     * get an iterator positioned on the specified address a. The first call to
     * next on the iterator will return the next address following a that is
     * inside the subnet.
     *
     * @param a DOCUMENT ME!
     *
     * @return Iterator starting at a.
     *
     * @throws IllegalArgumentException
     */
    @Override
    public Iterator<InetAddress> iterator(InetAddress a) {
        if (!contains(a)) {
            String err = String.format("Address %s is not part of the %s subnet",
                                       a.getHostAddress(), this);
            throw new IllegalArgumentException(err);
        }

        return new InclusiveSubnetIterator(a);
    }

    /**
     * the number of addresses available in the subnet, including
     * broadcast addresses.  warning the method returns a long which
     * should serve most purposes but some IPv6 subnets can actually
     * be bigger than that.
     *
     * @return Actual number of addresses in the subnet block, or
     *         Long.MAX_VALUE if more than Long.MAX_VALUE addresses
     *         are available.
     */
    @Override
    public long size() {
        if (isSingleHost()) {
            return 1;
        } else {
            int shiftWidth = 0;
            if (getAddress() instanceof Inet4Address) {
                shiftWidth = MAX_PREFIX4 - getCIDRMask();
            } else if (getAddress() instanceof Inet6Address) {
                shiftWidth = MAX_PREFIX6 - getCIDRMask();
            } else {
                assert(false);
            }
            BigInteger res = BigInteger.ONE.shiftLeft(shiftWidth);
            BigInteger max = new BigInteger(Long.toString(Long.MAX_VALUE));
            if (res.compareTo(max) > 0) {
                // return plenty
                return Long.MAX_VALUE;
            } else {
                return res.longValue();
            }
        }
    }

    private class InclusiveSubnetIterator implements Iterator<InetAddress> {
        BigInteger i;
        BigInteger max;
        BigInteger min;
        boolean reverse = false;

        InclusiveSubnetIterator() {
            this(getAddress());
        }

        InclusiveSubnetIterator(boolean reverse) {
            this(getAddress());
            this.reverse = reverse;
        }

        InclusiveSubnetIterator(InetAddress a) {
            min = new BigInteger(1, a.getAddress());
            max = new BigInteger(1, getBroadcast().getAddress());

            // For Inclusive, we include the "Broadcast Address".
            max = max.add(BigInteger.ONE);
            i = reverse ? max : min;
        }

        public boolean hasNext() {
            return
                reverse ?
                i.compareTo(min) > 0 :
                i.compareTo(max) < 0;
        }

        public InetAddress next() {
            try {
                byte[] naddr = null;
                int targetSize =
                    (getAddress() instanceof Inet4Address) ? SIZEOF_INET4 :
                    (getAddress() instanceof Inet6Address) ? SIZEOF_INET6 :
                    0; // can't happen
                byte[] baddr = i.toByteArray();
                if (baddr.length == targetSize) {
                    naddr = baddr;
                } else if (baddr.length < targetSize) {
                    // big int is smaller than what we need
                    naddr = new byte[targetSize];
                    System.arraycopy(baddr, 0, naddr,
                                     targetSize - baddr.length,
                                     baddr.length);
                } else {
                    // first byte is 0 (sign bit) and should be ignored
                    assert((baddr[0] == 0) &&
                           (baddr.length == targetSize + 1));
                    naddr = new byte[targetSize];
                    System.arraycopy(baddr, 1, naddr, 0, naddr.length);
                }
                InetAddress nextAddress = InetAddress.getByAddress(naddr);
                i = reverse ? i.subtract(BigInteger.ONE) :
                    i.add(BigInteger.ONE);
                return nextAddress;
            } catch (UnknownHostException uhe) {
                assert(false);
                return null;
            }
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public InclusiveSubnet[] split()
        throws InvalidNetmaskException {
        if (size() > 1) {
            InclusiveSubnet[] result = new InclusiveSubnet[2];
            InetAddress network = getAddress();
            // First subnet has the same network address with a wider mask
            result[0] = new InclusiveSubnet(network, getCIDRMask() + 1);

            // Second subnet has the last bit of the wider mask set
            BitString addr = new BitString(network.getAddress());
            addr.setBit(getCIDRMask(), 1);
            try {
                result[1] = new InclusiveSubnet(InetAddress.getByAddress(addr.getData()),
                                                getCIDRMask() + 1);
            } catch (UnknownHostException e) {
                throw new Error(e);
            }
            return result;
        }
        throw new InvalidNetmaskException("Can't split subnet further");
    }

    public InetAddressRange toInetAddressRange()
    {
        return new InetAddressRange(this.getAddress(),
                                    this.getBroadcast());
    }

    public static InclusiveSubnet createInclusiveSubnet(String expression)
        throws UnknownHostException, InvalidNetmaskException {
        return new InclusiveSubnet(expression);
    }

    public String jsonValue() {
        return this.toString();
    }
}
