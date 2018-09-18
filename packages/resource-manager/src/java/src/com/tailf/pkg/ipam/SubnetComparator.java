package com.tailf.pkg.ipam;

import java.util.Comparator;

public class SubnetComparator implements Comparator<Subnet> {
    public int compare(Subnet o1, Subnet o2) {
        /* Order first by mask */
        if (o1.getCIDRMask() == o2.getCIDRMask()) {
            /* Order by address next */
            byte[] bo1 = o1.getAddress().getAddress();
            byte[] bo2 = o2.getAddress().getAddress();
            for (int i = 0; i < bo1.length; i++) {
                if (bo1[i] != bo2[i]) {
                    return (bo1[i] & 0xff) - (bo2[i] & 0xff);
                }
            }
        }
        /* Order subnets from narrowest to widest */
        return o2.getCIDRMask() - o1.getCIDRMask();
    }
}
