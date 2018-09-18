package com.tailf.pkg.ipaddressallocator;

import java.util.Comparator;
import java.util.List;
import java.util.TreeSet;

import org.apache.log4j.Logger;

import com.tailf.pkg.ipaddressallocator.namespaces.ipaddressAllocatorOper;
import com.tailf.pkg.ipam.Subnet;
import com.tailf.cdb.CdbSession;
import com.tailf.conf.ConfIP;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfUInt8;


public class AvailablesSet extends TreeSet<Subnet> {

    private static final long serialVersionUID = 0;
    private static Logger LOGGER = Logger.getLogger(AvailablesSet.class);

    private CdbSession wsess;
    public String poolName;
    public String poolPath;

    private ConfPath availPath;

    public AvailablesSet(CdbSession wsess,
                         String poolName,
                         Comparator<Subnet> comp) {
        super(comp);
        this.wsess = wsess;
        this.poolName = poolName;

        try {
          this.availPath = new ConfPath("/%s:%s/%s{%s}",
                  ipaddressAllocatorOper.prefix,
                  ipaddressAllocatorOper._ip_allocator_,
                  ipaddressAllocatorOper._pool_,
                  poolName);

          if (wsess.exists(this.availPath) == false) {
              LOGGER.debug("Operational pool missing, creating.");
              wsess.create(this.availPath);
          }

          this.availPath.append(ipaddressAllocatorOper._available_);

          int n = wsess.getNumberOfInstances(this.availPath);
          if (n > 0) {
              List<ConfObject[]> objs = wsess.getObjects(2, 0, n, this.availPath);

              for (ConfObject[] obj : objs) {
                  String address = ((ConfIP)obj[0]).toString();
                  int mask = (int)((ConfUInt8)obj[1]).longValue();
                  Subnet sub = new Subnet(address, mask);
                  super.add(sub);
              }
          }
        } catch (Exception e) {
            LOGGER.error("Failed to setup availablesSet", e);
        }
    }

    public String getAvailablesPath() {
        return this.availPath.toString();
    }

    public boolean add(Subnet sub) {
        boolean res = super.add(sub);

        if (res) {
            try {
              String address = sub.getAddress().getHostAddress();
              String mask = Integer.toString(sub.getCIDRMask());
              String x = String.format("%s{%s %s}",
                      this.availPath.toString(),
                      address,
                      mask);
              this.wsess.create(x);
            } catch (Exception ex) {
                LOGGER.error("Error adding to AvailablesSet", ex);
            }
        }

        return res;
    }

    public boolean remove(Object o) {
        boolean res = super.remove(o);
        Subnet sub = (Subnet) o;

        if (res) {
            try {
              String address = sub.getAddress().getHostAddress();
              String mask = Integer.toString(sub.getCIDRMask());
              String x = String.format("%s{%s %s}",
                      this.availPath.toString(),
                      address,
                      mask);
              this.wsess.delete(x);
            } catch (Exception ex ) {
                LOGGER.error("Error removing from AvailablesSet", ex);
            }
        }

        return res;
    }

    public void clear() {
        super.clear();

        try {
            if (this.wsess.exists(this.availPath)) {
                this.wsess.delete(this.availPath);
            }
        } catch (Exception ex ) {
            LOGGER.error("Error clearing AvailablesSet", ex);
        }
    }
}
