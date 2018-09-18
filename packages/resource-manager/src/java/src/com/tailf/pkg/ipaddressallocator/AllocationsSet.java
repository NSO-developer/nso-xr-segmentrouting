package com.tailf.pkg.ipaddressallocator;

import java.util.HashSet;
import java.util.List;

import org.apache.log4j.Logger;

import com.tailf.pkg.ipaddressallocator.namespaces.ipaddressAllocatorOper;
import com.tailf.pkg.ipam.Allocation;
import com.tailf.pkg.ipam.Subnet;
import com.tailf.cdb.CdbSession;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfIP;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfUInt8;


public class AllocationsSet extends HashSet<Allocation> {

    private static final long serialVersionUID = 0;
    private static final Logger LOGGER = Logger.getLogger(AllocationsSet.class);

    private CdbSession wsess;
    public String poolName;
    public String poolPath;

    private ConfPath allocPath;

    public AllocationsSet(CdbSession wsess, String poolName) {
        super();

        this.wsess = wsess;
        this.poolName = poolName;

        /* Populate from allocations stored in CDB oper data */

        try {
          this.allocPath = new ConfPath("/%s:%s/%s{%s}",
                  ipaddressAllocatorOper.prefix,
                  ipaddressAllocatorOper._ip_allocator_,
                  ipaddressAllocatorOper._pool_,
                  poolName);

          /* We have configured a pool but it isn't set up in oper data yet. */
          if (wsess.exists(this.allocPath) == false) {
              LOGGER.debug("Operational pool missing, creating.");
              wsess.create(this.allocPath);
          }

          this.allocPath.append(ipaddressAllocatorOper._allocation_);

          int n = wsess.getNumberOfInstances(this.allocPath);
          if (n > 0) {
              List<ConfObject[]> objs = wsess.getObjects(5, 0, n, this.allocPath);

              for (ConfObject[] obj : objs) {
                  String address = ((ConfIP)obj[0]).toString();
                  int mask = (int)((ConfUInt8)obj[1]).longValue();
                  String owner = ((ConfBuf)obj[2]).toString();
                  String username = ((ConfBuf)obj[3]).toString();
                  String requestId = ((ConfBuf)obj[4]).toString();
                  Subnet sub = new Subnet(address, mask);
                  super.add(new Allocation(sub, owner, username, requestId));
              }
          }

        } catch (Exception e) {
            LOGGER.error("Failed to setup up allocationsSet", e);
        }
    }

    public String getAllocationsPath() {
        return this.allocPath.toString();
    }

    public boolean add(Allocation e) {
        boolean res = super.add(e);

        if (res) {
            try {
                Subnet sub = e.getAllocated();
                String x = String.format("%s{%s %s}",
                        this.allocPath.toString(),
                        sub.getAddress().getHostAddress(),
                        Integer.toString(sub.getCIDRMask()));
                this.wsess.create(x);
                this.wsess.setElem(new ConfBuf(e.getOccupant()),
                              new ConfPath(x + "/" +
                                           ipaddressAllocatorOper._owner_));
                this.wsess.setElem(new ConfBuf(e.getUsername()),
                              new ConfPath(x + "/" +
                                           ipaddressAllocatorOper._username_));
                this.wsess.setElem(new ConfBuf(e.getRequestId()),
                              new ConfPath(x + "/" +
                                           ipaddressAllocatorOper._request_id_));
            } catch (Exception ex) {
                LOGGER.error("Error adding allocation to pool", ex);
            }
        }
        return res;
    }

    public boolean remove(Object o) {
        boolean res = super.remove(o);

        Allocation e = (Allocation) o;

        if (res) {
            try {
                Subnet sub = e.getAllocated();
                String x = String.format("%s{%s %s}",
                        this.allocPath.toString(),
                        sub.getAddress().getHostAddress(),
                        Integer.toString(sub.getCIDRMask()));
                this.wsess.delete(x);
            } catch (Exception ex ) {
                LOGGER.error("Error removing allocation from pool", ex);
            }

        }

        return res;
    }

    public void clear() {
        super.clear();

        try {
            if (this.wsess.exists(this.allocPath)) {
                this.wsess.delete(this.allocPath);
            }
        } catch (Exception ex ) {
            LOGGER.error("Error clearing pool", ex);
        }
    }
}
