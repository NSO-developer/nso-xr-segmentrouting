package com.tailf.pkg.ipam.util;

import java.math.BigInteger;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Comparator;

/**
 * Utility class containing a comparator comparing two InetAddresses.
 *
 */

public class InetAddressComparator implements Comparator<InetAddress> {

    /**
     * For our purposes we define IP Address comparison as follows:
     *
     * First, make sure both are IPv4 or IPv6.  If not, convert the
     * IPv4 address to IPv6.
     *
     * Now getAddress() for each returns an array of byte (4 bytes for IPv4,
     * 16 bytes for IPv16).
     *
     * If all bytes are equal, the two are equal.
     * Concatenating the bytes into a bit string representing an unsigned
     * binary number, the greater number is the greater address.
     *
     */
    public int compare(InetAddress addr1, InetAddress addr2) {
        // Turn the addresses into byte arrays.
        byte[] bytes1 = addr1.getAddress();
        byte[] bytes2 = addr2.getAddress();

        // They must both be IPv4 or IPv6.  If they do not match,
        // convert the shorter to IPv6.

        if (bytes1.length < bytes2.length){
            bytes1 = ipv4ToIpv6(addr1).getAddress();
        } else if (bytes2.length < bytes1.length) {
            bytes2 = ipv4ToIpv6(addr2).getAddress();
        }
        // Now compare.
        return (unsignedBytesToBigInteger(bytes1).compareTo(
                              unsignedBytesToBigInteger(bytes2)));
    }

    /**
     * Interpret an array of Bytes as an unsigned binary integer
     * and return its value.
     * @param inputBytes
     * @return
     */
    public BigInteger unsignedBytesToBigInteger(byte [] inputBytes) {
        // In order to ensure that the leftmost bit is zero,
        // append a zero byte on the left.
        byte [] bytesToConvert = new byte[1 + inputBytes.length];
        bytesToConvert[0] = 0;
        for (int index = 0; index < inputBytes.length; index++) {
            bytesToConvert[index+1] = inputBytes[index];
        }
        return new BigInteger(bytesToConvert);
    }


    /**
     * Convert an IPv4 address to an IPv6 address.
     * @param v4Addr
     * @return
     */
    public InetAddress ipv4ToIpv6(InetAddress v4Addr) {
        String [] splitAddr = v4Addr.toString().split("/");
        String literalPart = splitAddr[splitAddr.length-1];
        try {
            return InetAddress.getByName("::" + literalPart);
        } catch (UnknownHostException e) {
            // Will not happen, as we do not need to do a lookup.
            return null;
        }
    }

    /**
     * Given an InetAddress, return an InetAddress which is "one greater"
     *
     * @param input
     * @return
     */
    public InetAddress addOne(InetAddress input) {
        try {
            return InetAddress.getByAddress((this.addOne(input.getAddress())));
        } catch (UnknownHostException e) {
            // Should never happen because the length of the byte array
            // will be correct.
            return null;
        }
    }

    /**
     * Given an InetAddress, return an InetAddress which is "one less"
     *
     * @param input
     * @return
     */
    public InetAddress subtractOne(InetAddress input) {
        try {
            return InetAddress.getByAddress((this.
                                             subtractOne(input.getAddress())));
        } catch (UnknownHostException e) {
            // Should never happen because the length of the byte array
            // will be correct.
            return null;
        }
    }

    /**
     * Treating an array of bytes as a big unsigned binary number,
     * add one to it and return the result.
     * The length of the byte array stays the same, which would
     * not necessarily happen if we converted to BigInteger.
     *
     * @param byteArray   Array of bytes.
     * @return            Array of bytes representing one greater.
     */
    public byte[] addOne(byte [] byteArray) {
        byte [] result = Arrays.copyOf(byteArray, byteArray.length);

        // Working from the right, add one and carry.
        boolean carry = true;
        for (int index = result.length - 1;
             index >= 0 && carry;
             index--) {
            if (result[index] == -1) {
                result[index] = 0;
                carry = true;
            } else {
                result[index]++;
                carry = false;
            }
        }
        return result;
    }

    /**
     * Treating an array of bytes as a big unsigned binary number,
     * subtract one from it and return the result.
     * The length of the byte array stays the same, which would
     * not necessarily happen if we converted to BigInteger.
     *
     * @param byteArray   Array of bytes.
     * @return            Array of bytes representing one less.
     */
    public byte[] subtractOne(byte [] byteArray) {
        byte [] result = Arrays.copyOf(byteArray, byteArray.length);

        // Working from the right, subtract one and barrow.
        boolean borrow = true;
        for (int index = result.length - 1;
             index >= 0 && borrow;
             index--) {
            if (result[index] == 0) {
                result[index] = -1;
                borrow = true;
            } else {
                result[index]--;
                borrow = false;
            }
        }
        return result;
    }
}
