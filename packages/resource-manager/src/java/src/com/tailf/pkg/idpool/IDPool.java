package com.tailf.pkg.idpool;

import java.util.HashSet;
import java.util.Set;

import com.tailf.conf.ConfIdentityRef;
import com.tailf.pkg.idallocator.IdAllocator;
import com.tailf.pkg.idallocator.namespaces.idAllocator;
import com.tailf.pkg.nsoutil.Pool;
import com.tailf.pkg.idpool.exceptions.AllocationException;
import com.tailf.pkg.idpool.exceptions.PoolExhaustedException;

import org.apache.log4j.Logger;

public class IDPool extends Pool {

    private static Logger LOGGER = Logger.getLogger(IdAllocator.class);

    private String name;

    private Set<Range> excludes;
    private Set<Range> availables;
    private Set<Allocation> allocations;
    private Long lastAllocation;
    private Long max;
    private Long min;

    private Range poolRange = new Range(0,1);

    public IDPool(String name,
                  Set<Range> excludes,
                  Set<Range> availables,
                  Set<Allocation> allocations,
                  boolean alarmsEnabled,
                  int threshold) {

        super(name, new ConfIdentityRef(idAllocator.hash,
                                    idAllocator._id_pool_exhausted),
              new ConfIdentityRef(idAllocator.hash,
                                  idAllocator._id_pool_low_threshold_reached),
              alarmsEnabled, threshold);
        this.name = name;
        this.excludes = excludes;
        this.availables = availables;
        this.allocations = allocations;
        setupMinMaxAllocs();
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getName() {
        return this.name;
    }

    public Set<Range> getExcludes() {
        return excludes;
    }

    public synchronized void addToExcludes(Range range) {
        excludes.add(range);
        recalculateRanges();
    }

    public synchronized void removeFromExcludes(Range range) {
        excludes.remove(range);
        recalculateRanges();
    }

    public Set<Range> getAvailables() {
        return availables;
    }

    public Set<Allocation> getAllocations() {
        return allocations;
    }

    public synchronized void addAllocation(Allocation a) {
        this.allocations.add(a);
    }

    public synchronized void clearAllocation() {
        this.allocations.clear();
    }

    public Range getPoolRange() {
        return poolRange;
    }

    public synchronized Allocation allocate(String occupant)
        throws AllocationException {
        LOGGER.debug(String.format("Trying to allocate from availables %s", availables));
        if (availables.isEmpty()) {
            throw new PoolExhaustedException(
                          String.format("ID pool %s exhausted", name));
        }

        Range range = availables.iterator().next();
        long result = range.getStart();
        availables.remove(range);

        if (range.getStart() != range.getEnd()) {
            /* Otherwise it's a range with one element, which is now
             * allocated. */
            range = new Range(result+1, range.getEnd());
            if (range.getStart() <= range.getEnd()) {
                /* Range is not exhausted, add it to availables. */
                availables.add(range);
            }
        }

        reviewAlarms();

        Allocation allocation = new Allocation(result);
        allocations.add(allocation);
        return allocation;
    }

    public synchronized Allocation allocate(String occupant, String requestMethod)
        throws AllocationException {
        if (requestMethod.equals(idAllocator._firstfree_)) {
            return allocate(occupant);
        } else if(requestMethod.equals(idAllocator._roundrobin_)) {
            if (availables.isEmpty()) {
                throw new PoolExhaustedException(
                              String.format("ID pool %s exhausted", name));
            }

            Long requested;
            if (this.lastAllocation == null) {
                requested = this.min;
            } else {
                requested = this.lastAllocation + 1;
            }
            Allocation returnAlloc = null;
            while (requested != this.lastAllocation) {
                if (requested > this.max) { // Try from start again
                    requested = this.min;
                }
                try {
                    returnAlloc = allocate(occupant, requested);
                    break;
                }
                catch (AllocationException e) {
                    this.lastAllocation = requested;
                    requested += 1;
                }
            }
            reviewAlarms();
            return returnAlloc;
        }
        throw new AllocationException("Unknown request method");
    }

    public synchronized Allocation allocate(String occupant, long requested)
        throws AllocationException {
        if (availables.isEmpty()) {
            throw new PoolExhaustedException(
                          String.format("ID pool %s exhausted", name));
        }
        for(Range range : availables) {
            if (range.getStart() <= requested && range.getEnd() >= requested) {
                availables.remove(range);

                if (range.getStart() <= requested - 1) {
                    Range before = new Range(range.getStart(), requested-1);
                    availables.add(before);
                }

                if (requested+1 <= range.getEnd()) {
                    Range after = new Range(requested+1, range.getEnd());
                    availables.add(after);
                }

                Allocation allocation = new Allocation(requested);
                allocations.add(allocation);
                this.lastAllocation = allocation.getAllocated();
                reviewAlarms();
                return allocation;
            }
        }
        String err = String.format("Requested id (%d) not available in pool %s",
                                   requested, name);

        throw new PoolExhaustedException(err);
    }

    public synchronized void setRange(Range range) {
        long start = range.getStart();
        long end = range.getEnd();
        poolRange = new Range(start,end);

        recalculateRanges();

        reviewAlarms();
    }

    private void recalculateRanges() {
        Set<Range> tmp = new HashSet<Range>();

        tmp.add(poolRange);
        excludeRanges(tmp);
        for (Allocation id : allocations) {
            long idInt = id.getAllocated();
            Set<Range> oldAvailables = new HashSet<Range>(tmp);
            for (Range availableRange : oldAvailables) {
                if (!availableRange.contains(idInt)) {
                    continue;
                }

                tmp.remove(availableRange);
                if (availableRange.getStart() == availableRange.getEnd()) {
                } else if (availableRange.getEnd() == idInt) {
                    tmp.add(new Range(availableRange.getStart(), idInt - 1));
                } else if (availableRange.getStart() == idInt) {
                    tmp.add(new Range(idInt + 1, availableRange.getEnd()));
                } else if (availableRange.getStart() < idInt) {
                    tmp.add(new Range(availableRange.getStart(), idInt - 1));
                    tmp.add(new Range(idInt + 1, availableRange.getEnd()));
                }
                break;
            }
        }

        /* First remove all that are the same from tmp, and removed
         * from availables. */
        Set<Range> toRemove = new HashSet<Range>();
        for (Range r: availables) {
            if (tmp.contains(r)) {
                tmp.remove(r);
            } else {
                toRemove.add(r);
            }
        }

        for (Range r: toRemove) {
            availables.remove(r);
        }

        /* Then add all new. */
        for (Range r: tmp) {
            availables.add(r);
        }
        setupMinMaxAllocs();
    }

    /**
     * Excludes reserved id ranges from the list of available ranges
     */
    private void excludeRanges(Set<Range> poolRange) {
        for (Range reserved : excludes) {
            Set<Range> oldAvailables = new HashSet<Range>(poolRange);
            for (Range availableRange : oldAvailables) {
                if (reserved.isDisjoint(availableRange)) {
                    continue;
                }

                /*
                 * Now we *know* they overlap. Remove the available range.
                 * If the exclude and availableRange is exactly the same,
                 * nothing is added later.
                 */
                poolRange.remove(availableRange);

                if (availableRange.getStart() < reserved.getStart()) {
                    poolRange.add(new Range(availableRange.getStart(),
                                            reserved.getStart() - 1));
                }

                if (availableRange.getEnd() > reserved.getEnd()) {
                    poolRange.add(new Range(reserved.getEnd() + 1,
                                            availableRange.getEnd()));
                }
                break;
            }
        }
    }

    private void compact(Range range) {

        Set<Range> oldAvailables = new HashSet<Range>(availables);
        for (Range arange: oldAvailables) {
            if (arange.equals(range)) {
                /* Skip myself. */
                continue;
            }

            if (arange.getStart() == range.getEnd()+1) {
                availables.remove(range);
                availables.remove(arange);
                Range merged = new Range(range.getStart(), arange.getEnd());
                availables.add(merged);
                compact(merged);
                return;
            } else if (arange.getEnd() == range.getStart()-1) {
                availables.remove(range);
                availables.remove(arange);
                Range merged = new Range(arange.getStart(), range.getEnd());
                availables.add(merged);
                return;
            }
        }
    }

    public synchronized void release(long id) throws AllocationException {
        for(Allocation alloc: allocations) {
            if (alloc.getAllocated() == id) {
                release(alloc);
                return;
            }
        }
    }

    public synchronized void release(Allocation allocation)
        throws AllocationException {
        if (!allocations.contains(allocation)) {
            String err = String.format("allocation %s is not allocated" +
                                       " from the pool %s", allocation, name);
            throw new AllocationException(err);
        }

        allocations.remove(allocation);
        long id = allocation.getAllocated();

        Set<Range> oldAvailables = new HashSet<Range>(availables);
        for (Range range : oldAvailables) {
            if (range.getStart() == id+1) {
                availables.remove(range);
                Range newr = new Range(id, range.getEnd());
                availables.add(newr);
                compact(newr);
                return;
            } else if (range.getEnd() == id-1) {
                availables.remove(range);
                Range newr = new Range(range.getStart(),id);
                availables.add(newr);
                compact(newr);
                return;
            }
        }
        availables.add(new Range(id, id));
    }

    public boolean isAvailable(long id) {
        for (Range range : availables) {
            if (id >= range.getStart() && id <= range.getEnd()) {
                return true;
            }
        }
        return false;
    }

    private void setupMinMaxAllocs() {
        /* For rr scheme, find the highest allocated id, min and max */
        Long min = null, max = null, current = null, lastAlloc = null;
        for(Range range: excludes) {
            current = range.getStart();
            if (min == null || current <= min) {
                min = current;
            }
            current = range.getEnd();
            if (max == null || current >= max) {
                max = current;
            }
        }
        for(Range range: availables) {
            current = range.getStart();
            if (min == null || current <= min) {
                min = current;
            }
            current = range.getEnd();
            if (max == null || current >= max) {
                max = current;
            }
        }
        for(Allocation alloc : allocations) {
            current = alloc.getAllocated();
            if (lastAlloc == null || current >= lastAlloc) {
                lastAlloc = current;
            }
        }
        this.lastAllocation = lastAlloc;
        if (max != null) {
            this.max = max;
        }
        if (min != null) {
            this.min = min;
        }
    }

    public boolean isEmpty() {
        return availables.isEmpty();
    }

    public long getNumberOfAvailables() {
        long numberOfAvailables = 0;
        for (Range range : availables) {
            numberOfAvailables += range.getEnd() - range.getStart() + 1;
        }
        return numberOfAvailables;
    }

    public long getTotalSize() {
        return poolRange.getEnd() - poolRange.getStart();
    }
}
