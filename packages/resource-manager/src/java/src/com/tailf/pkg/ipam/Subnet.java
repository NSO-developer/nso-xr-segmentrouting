package com.tailf.pkg.ipam;

import java.math.BigInteger;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.util.Iterator;

import com.tailf.pkg.ipam.exceptions.InvalidNetmaskException;
import com.tailf.pkg.ipam.util.BitString;

/**
 * Used to represent an IP Subnet in the same spirit as the
 * java.net.InetAddress class.
 *
 * <p>
 * The class is "read only" and can therefore be referred to without worrying
 * about values being changed by another reference holder.
 * </p>
 *
 * <p>
 * Constructors are provided to easily instantiate the class using CIDR
 * notation or classic address/mask notation. Note : Subnets must always use
 * valid CIDR masks, whichever way they are instantiated.
 * </p>
 *
 * <p>
 * Refer to {@link InvalidNetmaskException} for a better definition of a CIDR
 * mask.
 * </p>
 */
public class Subnet implements java.io.Serializable {
    private static final long serialVersionUID = 2324848105420911407L;

    protected static final int
        SIZEOF_INET4  = 4,      // Size of an IPv4 address in bytes
        SIZEOF_INET6  = 16,     // Size of an IPv6 address in bytes
        SIZEOF_INT    = 4,      // Size of the int type in bytes
        BITS_PER_BYTE = 8;      // Size of a byte in bits

    protected static final int
        MAX_PREFIX4   = SIZEOF_INET4 * BITS_PER_BYTE,
        MAX_PREFIX6   = SIZEOF_INET6 * BITS_PER_BYTE;

    /**********************************************************************/
    private InetAddress           address;
    private int                   cidrmask;

    private transient InetAddress mask, broadcast;

    public static final Subnet ANY;
    /* Possible improvement: define "any" subnet for ipv6 */

    static {
        Subnet any = null;

        try {
            any = new Subnet(InetAddress.getByName("0.0.0.0"), 0);
        } catch (Exception e) {
            throw new Error(e); // Can't happen, 0.0.0.0/0 is a valid Subnet
        } finally {
            ANY = any;
        }
    }

    /**********************************************************************/

    /**
     * creates a new Subnet given a subnet expression, ie a String containing :
     * &lt;IP address&gt;'&thinsp;/&thinsp;'(&lt;netmask expressed in dotquad
     * notation&gt; | &lt;int mask length&gt;)
     *
     * @param expression string representation of a subnet
     *
     * @throws UnknownHostException if the &lt;IP address&gt; is not valid.
     * @throws InvalidNetmaskException if the expression trailing the
     *         '&thinsp;/&thinsp;' is not recognized as a valid netmask.
     */
    public Subnet(String expression)
        throws UnknownHostException, InvalidNetmaskException {
        this(expression.contains("/") ?
             expression.substring(0, expression.indexOf('/')) : expression,
             expression.contains("/") ?
             expression.substring(expression.indexOf('/') + 1) : "32");
    }

    /**
     * creates a new Subnet given an expression for the the subnet address and
     * its netmask. <br>
     * The mask argument may be specified either as a CIDR mask length, or as
     * a netmask in dotquad notation.
     *
     * @param address IP Address or host name
     * @param mask Dot-decimal notation OR integer mask length ( 1 to 31 )
     *
     * @throws UnknownHostException if the &lt;IP address&gt; is not valid or
     *         if the netmask.
     * @throws InvalidNetmaskException if the expression is not recognized
     *         as a valid netmask.
     */
    public Subnet(String address, String mask)
        throws UnknownHostException, InvalidNetmaskException {
        this(InetAddress.getByName(address), parseMask(mask));
    }

    public Subnet() {}

    /**
     * creates a subnet with the specified network address and mask.
     *
     * @param address network address
     * @param mask netmask
     *
     * @throws InvalidNetmaskException if the netmask is not valid for
     *         the given network address.
     */
    public Subnet(InetAddress address, InetAddress mask)
        throws InvalidNetmaskException {
        if (!mask.getClass().equals(address.getClass())) {
            throw new InvalidNetmaskException(
                     "Netmask and address must share " +
                     "the same address family");
        }
        this.cidrmask = mask2prefix(mask);
        this.address  = networkOf(address, cidrmask);
        this.mask     = mask;
    }

    /**
     * creates a subnet with the specified network address and mask.
     *
     * @param address network address
     * @param mask netmask in CIDR form
     *
     * @throws InvalidNetmaskException if the mask is not valid
     */
    public Subnet(InetAddress address, int mask)
        throws InvalidNetmaskException {
        if (address instanceof Inet4Address) {
            checkPrefix4(mask);
        } else if (address instanceof Inet6Address) {
            checkPrefix6(mask);
        } else {
            throw new Error("Unsupported IP version");
        }
        this.address      = networkOf(address, mask);
        this.cidrmask     = mask;
    }

    public Subnet(String address, int mask)
        throws UnknownHostException, InvalidNetmaskException {
        this(InetAddress.getByName(address), mask);
    }

    private static int parseMask(String smask)
        throws InvalidNetmaskException {
        try {
            int cidrmask;
            if (smask.indexOf('.') == -1) {
                /* Interpret the mask as an int */
                cidrmask = Integer.parseInt(smask);
            } else {
                /* Interpret the mask as an address */
                InetAddress mask = InetAddress.getByName(smask);
                cidrmask = mask2prefix(mask);
            }
            return cidrmask;
        } catch (NumberFormatException nfe) {
            String err = String.format("%s is not a valid netmask expression", smask);
            throw new InvalidNetmaskException(err);
        } catch (UnknownHostException uhe) {
            String err = String.format("%s is not a valid netmask expression", smask);
            throw new InvalidNetmaskException(err);
        }
    }

    private static void checkPrefix4(int prefix)
        throws InvalidNetmaskException {
        if ((prefix < 0) || (prefix > MAX_PREFIX4)) {
            throw new InvalidNetmaskException(
                   "IPv4 prefix must be within [0-" + MAX_PREFIX4 + "]");
        }
    }

    private static void checkPrefix6(int prefix)
        throws InvalidNetmaskException {
        if ((prefix < 0) || (prefix > MAX_PREFIX6)) {
            throw new InvalidNetmaskException(
                   "IPv6 netmask must be within [0-" + MAX_PREFIX6 + "]");
        }
    }

    public static int mask2prefix(InetAddress addr)
        throws InvalidNetmaskException {
        byte[] baddr = addr.getAddress();
        int prefix = 0;
        int curbyte = 0;
        while (true) {
            boolean nextByteMustBe0 = (baddr[curbyte] != -1);
            while (baddr[curbyte] != 0) {
                if (baddr[curbyte] > 0) {
                    /* High bit (ie negative value) must be set if the byte is non 0 */
                    String err =
                        String.format("%s is not a valid netmask", addr.getHostAddress());
                    throw new InvalidNetmaskException(err);
                }
                baddr[curbyte] <<= 1;
                prefix++;
            }
            curbyte++;
            if (curbyte >= baddr.length) {
                break;
            }
            if (nextByteMustBe0 && (baddr[curbyte] != 0)) {
                String err =
                    String.format("%s is not a valid netmask", addr.getHostAddress());
                throw new InvalidNetmaskException(err);
            }
        }
        return prefix;
    }

    public static Inet4Address prefix2mask4(int prefix)
        throws InvalidNetmaskException {
        checkPrefix4(prefix);
        ByteBuffer bb = ByteBuffer.allocate(SIZEOF_INET4);
        return (Inet4Address) prefix2mask(bb, prefix);
    }

    public static Inet6Address prefix2mask6(int prefix)
        throws InvalidNetmaskException {
        checkPrefix6(prefix);
        ByteBuffer bb = ByteBuffer.allocate(SIZEOF_INET6);
        return (Inet6Address) prefix2mask(bb, prefix);
    }

    private static InetAddress prefix2mask(ByteBuffer bb, int prefix) {
        try {
            int tmpPrefix = prefix;
            while (tmpPrefix > 0) {
                int shiftWidth = tmpPrefix > SIZEOF_INT * BITS_PER_BYTE ?
                    SIZEOF_INT * BITS_PER_BYTE :
                    tmpPrefix;
                tmpPrefix -= shiftWidth;
                bb.putInt(-1 << (SIZEOF_INT * BITS_PER_BYTE - shiftWidth));
            }
            while (bb.hasRemaining()) {
                bb.putInt(0);
            }
            return InetAddress.getByAddress(bb.array());
        } catch (UnknownHostException e) {
            throw new Error(e); // Can't happen, InetAddress is valid by construction
        }
    }

    /**
     * Normalizes a subnets address based on a CIDR mask length. <br>
     * The resulting InetAddress is the one passed in as a parameter where
     * only the network part is retained.
     *
     * @param a address
     * @param prefix prefix
     *
     * @return InetAddress a binary ANDed with a netmask of length prefix
     */
    public static InetAddress networkOf(InetAddress a, int prefix) {
        try {
            byte[] baddr = a.getAddress();
            int tmpPrefix = prefix;
            for (int i = 0; i < baddr.length; i++) {
                if (tmpPrefix >= BITS_PER_BYTE) {
                    tmpPrefix -= BITS_PER_BYTE;
                } else if (tmpPrefix > 0) {
                    int shiftWidth = tmpPrefix;
                    tmpPrefix = 0;
                    baddr[i] &= (-1 << (BITS_PER_BYTE - shiftWidth));
                } else {
                    baddr[i] = 0;
                }
            }
            return InetAddress.getByAddress(baddr);
        } catch (UnknownHostException e) {
            /* Can't happen since the built address is based on an already valid one */
            throw new Error(e);
        }
    }

    /**
     * Returns the network address of the Subnet.
     *
     * @return network address
     */
    public InetAddress getAddress() {
        return address;
    }

    /**
     * Returns the Subnet's netmask in dotquad notation.
     *
     * @return network netmask
     */
    public synchronized InetAddress getMask() {
        if (mask == null) {
            try {
                if (address instanceof Inet4Address) {
                    mask = prefix2mask4(cidrmask);
                } else if (address instanceof Inet6Address) {
                    mask = prefix2mask6(cidrmask);
                }
            } catch (InvalidNetmaskException e) {
                /* Can't happen since it is checked upon construction of the Subnet object */
                throw new Error(e);
            }
        }
        return mask;
    }

    /**
     * Returns the Subnet's netmask in CIDR notation, ie the prefix length.
     *
     * @return CIDR netmask
     */
    public int getCIDRMask() {
        return cidrmask;
    }

    /**
     * Returns the broadcast address of the subnet
     *
     * @return an InetAddress representing the network's broadcast address
     */
    public synchronized InetAddress getBroadcast() {
        if (broadcast == null) {
            try {
                /* Binary OR the address with the complement of the netmask */
                byte[] bmask = getMask().getAddress();
                byte[] baddr = address.getAddress();
                for (int i = 0; i < baddr.length; i++) {
                    baddr[i] |= (~ bmask[i]);
                }
                broadcast = InetAddress.getByAddress(baddr);
            } catch (UnknownHostException e) {
                throw new Error(e); // The broadcast address can never be invalid
            }
        }
        return broadcast;
    }

    /**
     * Does this subnet contain another?
     *
     * @param s another subnet
     *
     * @return true if the subnet passed in as a parmeter is contained in the
     *         one the method was called upon.
     */
    public boolean contains(Subnet other) {
        if (cidrmask > other.cidrmask) {
            return false;
        }
        return networkOf(other.getAddress(), cidrmask).equals(address);
    }

    /**
     * Does the subnet contain a specific address?
     *
     * @param a address
     *
     * @return true if the address is contained in the subnet.
     */
    public boolean contains(InetAddress a) {
        return networkOf(a, cidrmask).equals(address);
    }

    /**
     * Compare the width, i.e. which one has more addresses.
     * If this is narrower than the other (i.e. the cidrMask
     * is greater) then return -1, else if the width is equal
     * return 0, else return 1.
     *
     * @param other Another subnet
     * @return -1 if this is narrower, 0 if same, 1 if wider.
     */
    public int compareWidth(Subnet other) {
        return this.cidrmask > other.cidrmask ? -1 :
            this.cidrmask == other.cidrmask ? 0 :
            1;
    }

    /**
     * Does this subnet overlap another subnet?
     *
     * @param other Another subnet
     * @return true if the subnets are equal or if either subnet contains the other.
     */

    public boolean overlaps(Subnet other) {
        if (equals(other)) {
            return true;
        }

        if (contains(other) || other.contains(this)) {
            return true;
        }

        return false;
    }

    /**
     * retrieve an Iterator on the addresses contained in the set the "first"
     * and last addresses are discarded since they are broadcast addresses. <br>
     * The iteration will only contain non broadcast addresses, furthermore
     * the call will only behave correctly if called on a /30 or larger
     * network.
     *
     * @return an Iterator iterating the InetAddresses of the subnet
     */
    public Iterator<InetAddress> iterator() {
        return new SubnetIterator();
    }

    public Iterator<InetAddress> iterator(boolean reverse) {
        return new SubnetIterator(reverse);
    }

    /**
     * get an iterator positioned on the specified address a. The first call to
     * next on the iterator will return the next address following a that is
     * inside the subnet.
     *
     * @param a starting address
     *
     * @return Iterator starting with address a
     *
     * @throws IllegalArgumentException if address a is not part of this subnet
     */
    public Iterator<InetAddress> iterator(InetAddress a) {
        if (!contains(a)) {
            throw new IllegalArgumentException(
                    String.format("Address %s is not part of the %s subnet.",
                                  a.getHostAddress(), this));
        }

        return new SubnetIterator(a);
    }

    public int getSingleHostPrefix() {
        if (address instanceof Inet4Address) {
            return MAX_PREFIX4;
        }
        if (address instanceof Inet6Address) {
            return MAX_PREFIX6;
        }
        throw new Error("Unsupported IP version");
    }

    public boolean isSingleHost() {
        return ((address instanceof Inet4Address) && (cidrmask == MAX_PREFIX4))
            || ((address instanceof Inet6Address) && (cidrmask == MAX_PREFIX6));
    }

    /**
     * the number of non-broadcast addresses available in the subnet. the /32
     * subnets are a special case because they have a size of 1.
     * warning the method returns a long which should serve most purposes
     * but some IPv6 subnets can actually be bigger than that.
     *
     * @return The number of non-broadcast addresses, or Long.MAX_VALUE if
     *         more than Long.MAX_VALUE addresses are available.
     */
    public long size() {
        if (isSingleHost()) {
            return 1;
        } else {
            int shiftWidth = 0;
            if (address instanceof Inet4Address) {
                shiftWidth = MAX_PREFIX4 - cidrmask;
            } else if (address instanceof Inet6Address) {
                shiftWidth = MAX_PREFIX6 - cidrmask;
            }
            BigInteger res = BigInteger.ONE.shiftLeft(shiftWidth).
                subtract(BigInteger.ONE).
                subtract(BigInteger.ONE);
            BigInteger max = new BigInteger(Long.toString(Long.MAX_VALUE));
            if (res.compareTo(max) > 0) {
                return Long.MAX_VALUE; // Return plenty
            } else {
                return res.longValue();
            }
        }
    }

    /*
     * Correct behavior for Object.equals()
     */
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        try {
            Subnet other = (Subnet) o;

            return (cidrmask == other.cidrmask) &&
                address.equals(other.address);
        } catch (ClassCastException cce) {
            return false;
        } catch (NullPointerException npe) {
            return false;
        }
    }

    /*
     * Correct behavior when used in a HashTable
     */
    public int hashCode() {
        return address.hashCode() ^ cidrmask;
    }

    /**********************************************************************/
    public String toString() {
        StringBuffer sb = new StringBuffer();
        sb.append(getAddress().getHostAddress());
        sb.append("/");
        sb.append(cidrmask);

        return sb.toString();
    }

    /**
     * Iterator capable of iterating addresses in a subnet.
     * Can travel forwards and backwards, depending onboolean reverse.
     * However, only in one of these directions.
     */
    private class SubnetIterator implements Iterator<InetAddress> {
        private BigInteger i;
        private BigInteger max;
        private BigInteger min;
        boolean reverse = false;

        private SubnetIterator() {
            this(address, false);
        }

        private SubnetIterator(boolean reverse) {
            this(address, reverse);
        }

        private SubnetIterator(InetAddress a) {
            this(a, false);
        }

        private SubnetIterator(InetAddress a, boolean reverse) {
            this.reverse = reverse;
            min = new BigInteger(1, a.getAddress());
            max = new BigInteger(1, getBroadcast().getAddress());
            i = reverse ? max : min;

            /*
             * Special case, there is a single valid address
             * in the Subnet. In other cases we must make sure
             * to skip the first address in the subnet.
             */
            if ((! isSingleHost()) && a.equals(address)) {
                if (reverse) {
                    i = i.subtract(BigInteger.ONE);
                } else {
                    i = i.add(BigInteger.ONE);
                }
            }
        }

        public boolean hasNext() {
            if (isSingleHost()) {
                return i.equals(max);
            } else if (reverse) {
                return i.compareTo(min) > 0;
            } else {
                return i.compareTo(max) < 0;
            }
        }

        public InetAddress next() {
            byte[] naddr = null;
            int targetSize =
                (address instanceof Inet4Address) ? SIZEOF_INET4 :
                (address instanceof Inet6Address) ? SIZEOF_INET6 :
                0; // Can't happen
            byte[] baddr = i.toByteArray();
            if (baddr.length == targetSize) {
                naddr = baddr;
            } else if (baddr.length < targetSize) {
                /* Big int is smaller than what we need */
                naddr = new byte[targetSize];
                System.arraycopy(baddr, 0, naddr,
                                 targetSize - baddr.length,
                                 baddr.length);
            } else {
                /* First byte is 0 (sign bit) and should be ignored */
                assert((baddr[0] == 0) &&
                       (baddr.length == targetSize + 1));
                naddr = new byte[targetSize];
                System.arraycopy(baddr, 1, naddr, 0, naddr.length);
            }
            InetAddress nextAddress = null;
            try {
                nextAddress = InetAddress.getByAddress(naddr);
            } catch (UnknownHostException e) {
                throw new Error(e); // The next address can never be invalid
            }
            if (reverse) {
                i = i.subtract(BigInteger.ONE);
            } else {
                i = i.add(BigInteger.ONE);
            }
            return nextAddress;
        }

        public void remove() {
            throw new UnsupportedOperationException();
        }
    }

    public boolean isBroadcastFor(Subnet addr) {
        /*
         * The given address ANDed with the subnet's broadcast address
         * equal to the subnet's broadcast address
         */
        InetAddress broadcast = getBroadcast();

        byte[] bbcast = broadcast.getAddress();
        byte[] baddr  = addr.getAddress().getAddress();
        for (int i = 0; i < baddr.length; i++) {
            if (!(bbcast[i] == (baddr[i] & bbcast[i]))) {
                return false;
            }
        }
        return true;
    }

    /**
     * Special case code for IPAddressReservation.
     *
     * Normally a 4-address Subnet cannot be split into two "Subnets"
     * but has to be split into four single addresses.  When
     * attempting to reserve a two-address block, we create what
     * appears to be a "Subnet" with only two addresses.  When
     * breaking down a four-address subnet, we call this special case
     * to break it into 2.
     * @return
     * @throws InvalidNetmaskException
     * @throws UnknownHostException
     */
    public Subnet[] split4into2()
        throws InvalidNetmaskException {
        if (size() != 2) {
            throw new InvalidNetmaskException("Not a four-address subnet.");
        }

        Subnet[] result = new Subnet[2];
        InetAddress network = getAddress();
        try {
            result[0] = new Subnet(network, cidrmask + 1);
            BitString addr = new BitString(network.getAddress());
            addr.setBit(cidrmask, 1);
            result[1] = new Subnet(InetAddress.getByAddress(addr.getData()),
                                   cidrmask + 1);
        } catch (UnknownHostException e) {
            /* Flipping bits in an IP address can not cause UnknownHostException */
            throw new Error(e);
        }
        return result;
    }

    public Subnet[] split() throws InvalidNetmaskException {
        /*
         * MSL 3/23/2012: One-address "Subnet" is already treated as a
         * special case.  Treat a two-address "Subnet" as a special
         * case too: allow it to be split into two one-address
         * "Subnets".  There really aren't two-address Subnets, but
         * for IP Address Reservation we use the Subnet class to
         * represent an address range, and a range of two addresses is
         * reasonable.
         *
         * One-address "Subnet".size() == 1.  Two-address "Subnet".size() == 0.
         * Four-address Subnet (smallest REAL subnet).size() == 2.
         * Eight-address Subnet.size() == 6 and so on.
         */
        boolean isIpv6 = (address instanceof Inet6Address);
        try {
            if (size() > 1 || size() == 0) {
                if (size() > 2) {
                    Subnet[] result = new Subnet[2];
                    InetAddress network = getAddress();

                    /* First subnet has the same network address with a wider mask */
                    result[0] = new Subnet(network, cidrmask + 1);

                    /* Second subnet has the last bit of the wider mask set */
                    BitString addr = new BitString(network.getAddress());
                    addr.setBit(cidrmask, 1);
                    result[1] = new Subnet(InetAddress.getByAddress(addr.getData()),
                                           cidrmask + 1);
                    return result;
                } else if (size() == 0) {
                    int prefix = isIpv6 ? 128 : 32;
                    /* Split two-address "Subnet" into 2 /32 or /128 addresses. */
                    Subnet[] result = new Subnet[2];
                    InetAddress network = getAddress();
                    byte[] addr = network.getAddress();
                    result[0] = new Subnet(InetAddress.getByAddress(addr), prefix);
                    addr[addr.length-1]++;
                    result[1] = new Subnet(InetAddress.getByAddress(addr), prefix);
                    return result;
                }
                /* Must split the subnet into 4 /32 or /128 addresses at this point */
                int prefix = isIpv6 ? 128 : 32;
                Subnet[] result = new Subnet[4];
                InetAddress network = getAddress();
                byte[] addr = network.getAddress();
                for (int i = 0; i < result.length; i++) {
                    result[i] = new Subnet(InetAddress.getByAddress(addr), prefix);
                    addr[addr.length-1]++;
                }
                return result;
            }
        } catch (UnknownHostException e) {
            /* Splitting a subnet can not reasonably cause an UnknownHostException */
            throw new Error(e);
        }
        throw new InvalidNetmaskException("Can't split subnet further");
    }

    public static int compare(InetAddress adr1, InetAddress adr2) {
        byte[] ba1 = adr1.getAddress();
        byte[] ba2 = adr2.getAddress();

        /* General ordering: ipv4 before ipv6 */
        if (ba1.length < ba2.length) return -1;
        if (ba1.length > ba2.length) return 1;

        /* We have 2 IPs of the same type, so we have to compare each byte */
        for (int i = 0; i < ba1.length; i++) {
            int b1 = unsignedByteToInt(ba1[i]);
            int b2 = unsignedByteToInt(ba2[i]);
            if (b1 == b2) {
                continue;
            }
            if (b1 < b2) {
                return -1;
            } else {
                return 1;
            }
        }
        return 0;
    }

    private static int unsignedByteToInt(byte b) {
        return (int) b & 0xFF;
    }

}
