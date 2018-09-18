package com.tailf.pkg.ipam.util;

import java.util.Formatter;

public class BitString {
    protected byte[] data = null;

    public BitString() {}

    public BitString(int size) {
        int bytecnt = (size + 7) / 8;
        data = new byte[bytecnt];
    }

    public BitString(byte[] data) {
        this.data = new byte[data.length];
        System.arraycopy(data, 0, this.data, 0, data.length);
    }

    public byte[] getData() {
        return data;
    }

    protected void resize(int size) {
        byte[] newData = new byte[size];
        if (data != null) {
            System.arraycopy(data, 0, newData, 0, data.length);
        }
        data = newData;
    }

    protected int getPosByte(int pos) {
        int posByte = pos/8;
        if (data == null || posByte >= data.length) {
            resize(posByte + 1);
        }
        return posByte;
    }

    protected int getPosBit(int pos) {
        return 7-pos%8;
    }

    protected int getMask(int pos) {
        return 0xFF >> 7 - pos;
    }

    public void setBit(int pos, int val) {
        int posByte = getPosByte(pos);
        int posBit = getPosBit(pos);
        byte oldByte = data[posByte];
        oldByte = (byte) (((0xFF7F>>(7-posBit)) & oldByte) & 0x00FF);
        byte newByte = (byte) ((val<<posBit) | oldByte);
        data[posByte] = newByte;
    }

    public int getBit(int pos) {
        int posByte = getPosByte(pos);
        int posBit = getPosBit(pos);
        byte valByte = data[posByte];
        int valInt = valByte>>posBit & 0x0001;
        return valInt;
    }

    public int nextSetBit(int fromIndex) {
        if (fromIndex <  0) {
            throw new IndexOutOfBoundsException("fromIndex <  0: " + fromIndex);
        }
        int posByte = getPosByte(fromIndex);
        int posBit = getPosBit(fromIndex);
        byte val = (byte)(data[posByte] & getMask(posBit));
        while (true) {
            if (val != 0) {
                return posByte * Byte.SIZE +
                    (Long.numberOfLeadingZeros(val & 0xFF) - Long.SIZE +
                     Byte.SIZE);
            } else if (++posByte >= data.length) {
                return -1;
            }
            val = data[posByte];
        }
    }

    public int nextClearBit(int fromIndex) {
        // Neither spec nor implementation handle bitsets of maximal length.
        // See 4816253.
        if (fromIndex < 0) {
            throw new IndexOutOfBoundsException("fromIndex < 0: " + fromIndex);
        }

        int posByte = getPosByte(fromIndex);
        int posBit = getPosBit(fromIndex);

        byte val = (byte)(~data[posByte] & getMask(posBit));

        while (true) {
            if (val != 0) {
                return posByte * Byte.SIZE +
                    (Long.numberOfLeadingZeros(val & 0xFF) - Long.SIZE +
                     Byte.SIZE);
            } else if (++posByte >= data.length) {
                return -1;
            }
            val = (byte)~data[posByte];
        }
    }

    public int length() { return data.length * Byte.SIZE; }

    public boolean allBitsSet() {
        for (int i = 0; i < data.length; i++) {
            if (data[i] != -1) {
                return false;
            }
        }
        return true;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        Formatter formatter = new Formatter(result);
        result.append("{");
        for (int i = 0; i < data.length; i++) {
            if (i > 0) {
                result.append(",");
            }
            formatter.format("0x%02X", data[i]);
        }
        result.append("}");
        formatter.close();
        return result.toString();
    }

}
