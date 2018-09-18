package com.tailf.pkg.idallocator;

import java.util.HashSet;
import java.util.List;

import org.apache.log4j.Logger;

import com.tailf.pkg.idallocator.namespaces.idAllocatorOper;
import com.tailf.pkg.idpool.Allocation;
import com.tailf.cdb.CdbSession;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfUInt32;

/**
 * AllocationsSet
 *
 */
public class AllocationsSet extends HashSet<Allocation> {

    private static final long serialVersionUID = -7899235036264236762L;
    private static Logger LOGGER = Logger.getLogger(AllocationsSet.class);

    private ConfPath allocPath;
    private CdbSession wsess;

    public AllocationsSet(CdbSession wsess, String poolName) {
        super();

        this.wsess = wsess;

        LOGGER.debug("Creating AllocationsSet");

        /* Populate from allocations stored in CDB. */
        try {
            allocPath = new ConfPath("/%s:%s/%s{%s}",
                    idAllocatorOper.prefix, idAllocatorOper._id_allocator_,
                    idAllocatorOper._pool_, poolName);

            /* We have configured a pool but it isn't set up in oper data yet. */
            if (wsess.exists(allocPath) == false) {
                LOGGER.debug(String.format(
                            "Operational pool %s missing, creating.",
                            poolName));
                wsess.create(allocPath);
            }

            allocPath.append(idAllocatorOper._allocation_);

            LOGGER.debug("Adding existing allocations");

            int n = wsess.getNumberOfInstances(allocPath);
            if (n > 0) {
                List<ConfObject[]> objs = wsess.getObjects(1, 0, n, allocPath);

                for (ConfObject[] obj : objs) {
                    long id = ((ConfUInt32) obj[0]).longValue();
                    LOGGER.debug(String.format("Adding Allocation(%d)", id));
                    super.add(new Allocation(id));
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to setup up AllocationsSet", e);
        }
    }

    public boolean add(Allocation e) {
        boolean res = super.add(e);

        if (res) {
            try {
                long id = e.getAllocated();
                String p = String.format("%s{%s}",
                                this.allocPath.toString(),
                                Long.toString(id));
                this.wsess.create(p);
            } catch (Exception ex) {
                LOGGER.error("Could not add allocation", ex);
            }
        }

        return res;
    }

    public boolean remove(Object o) {
        boolean res = super.remove(o);
        Allocation e = (Allocation) o;

        if (res) {
            try {
                long id = e.getAllocated();
                String p = String.format("%s{%s}",
                                this.allocPath.toString(),
                                Long.toString(id));
                this.wsess.delete(p);
            } catch (Exception ex ) {
                LOGGER.error("Could not remove allocation", ex);
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
            LOGGER.error("Failed to clear allocations", ex);
        }
    }
}
