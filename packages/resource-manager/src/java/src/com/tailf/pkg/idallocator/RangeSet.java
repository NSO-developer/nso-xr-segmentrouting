package com.tailf.pkg.idallocator;

import java.util.TreeSet;
import java.util.List;

import org.apache.log4j.Logger;

import com.tailf.pkg.idallocator.namespaces.idAllocatorOper;
import com.tailf.pkg.idpool.Range;
import com.tailf.cdb.CdbSession;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;

/**
 * RangeSet
 *
 */
public class RangeSet extends TreeSet<Range> {

    private static final long serialVersionUID = 6778525701268471907L;
    private static Logger LOGGER = Logger.getLogger(RangeSet.class);

    private ConfPath poolPath;
    private ConfPath locationPath;
    private CdbSession wsess;

    public RangeSet(CdbSession wsess, String poolName, String location) {
        super();

        this.wsess = wsess;

        LOGGER.debug("Creating RangeSet");

        try {
            poolPath = new ConfPath("/%s:%s/%s{%s}",
                    idAllocatorOper.prefix, idAllocatorOper._id_allocator_,
                    idAllocatorOper._pool_, poolName);

            /* We have configured a pool but it isn't set up in oper data yet. */
            if (wsess.exists(poolPath) == false) {
                LOGGER.debug(String.format(
                            "Operational pool %s missing, creating.",
                            poolName));
                wsess.create(poolPath);
            }

            locationPath = poolPath.copyAppend(location);

            LOGGER.debug("Adding existing range");

            int n = wsess.getNumberOfInstances(locationPath);
            if (n > 0) {
                List<ConfObject[]> objs = wsess.getObjects(2, 0, n, locationPath);

                for (ConfObject[] obj : objs) {
                    long start = ((ConfUInt32) obj[0]).longValue();
                    long end = ((ConfUInt32) obj[1]).longValue();
                    Range res = new Range(start, end);
                    LOGGER.debug(String.format("Adding Range (%s)", res));
                    super.add(res);
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to setup RangeSet", e);
        }
    }

    public ConfPath getPoolPath() {
        return this.poolPath;
    }

    public boolean add(Range ren) {
        boolean res = super.add(ren);

        try {
            String p = String.format("%s{%s %s}",
                    this.locationPath,
                    Long.toString(ren.getStart()),
                    Long.toString(ren.getEnd()));

            if (this.wsess.exists(p) == false) {
                this.wsess.create(p);
            }
        } catch (Exception e) {
            if(res) {
                /* Only an error if the range really was added to the set */
                LOGGER.error(String.format("Could not add range %s", ren), e);
            }
        }

        return res;
    }

    public boolean remove(Object o) {
        boolean res = super.remove(o);
        Range ren = (Range) o;

        try {
            String p = String.format("%s{%s %s}",
                    this.locationPath,
                    Long.toString(ren.getStart()),
                    Long.toString(ren.getEnd()));

            if (this.wsess.exists(p)) {
                this.wsess.delete(p);
            }
        } catch (Exception e) {
            if (res) {
                /* Only an error if the range really was removed from the set */
                LOGGER.error("Could not remove range", e);
            }
        }
        return res;
    }

    public void clear() {
        super.clear();

        try {
            if (this.wsess.exists(this.locationPath)) {
                this.wsess.delete(this.locationPath);
            }
        } catch (Exception ex ) {
            LOGGER.error("Failed to clear", ex);
        }
    }
}
