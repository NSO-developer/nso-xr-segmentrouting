package com.tailf.pkg.ipaddressallocator;

import com.tailf.pkg.ipaddressallocator.namespaces.ipaddressAllocator;
import com.tailf.pkg.ipam.Subnet;
import com.tailf.pkg.ipam.exceptions.InvalidNetmaskException;
import com.tailf.pkg.ipam.util.InetAddressRange;
import com.tailf.pkg.resourcemanager.namespaces.resourceAllocator;
import com.tailf.conf.*;
import com.tailf.ncs.annotations.*;
import com.tailf.maapi.*;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Hashtable;
import java.util.Vector;

import org.apache.log4j.Logger;

public class IPValidator {
    private static final Logger LOGGER = Logger.getLogger(IPValidator.class);

    private Hashtable<ConfKey, Vector<Entry>> subnetCache;
    private Hashtable<ConfKey, Vector<Entry>> excludeCache;
    private Hashtable<ConfKey, Vector<Entry>> rangeCache;

    private class Entry {
        Subnet subnet;
        ConfKey key;
    }

    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE,
              qualifier="reactive-fm-ipaddressallocator-validator-m")
    private Maapi maapi;

    @TransValidateCallback(callType=TransValidateCBType.INIT)
    public void init(DpTrans trans) throws DpCallbackException {
        /* Attach to the transaction */
        try {
            maapi.startUserSession("admin",
                                   maapi.getSocket().getInetAddress(),
                                   "system",
                                   new String[] {"admin"},
                                   MaapiUserSessionFlag.PROTO_TCP);

            int th = trans.getTransaction();
            maapi.attach(th, 0, trans.getUserInfo().getUserId());

            subnetCache = new Hashtable<ConfKey, Vector<Entry>>();
            excludeCache = new Hashtable<ConfKey, Vector<Entry>>();
            rangeCache = new Hashtable<ConfKey, Vector<Entry>>();
            LOGGER.info("init");
        } catch (Exception e) { // IOException, MaapiException
            throw new DpCallbackException("Failed to attach via maapi", e);
        }
    }

    @TransValidateCallback(callType = { TransValidateCBType.STOP })
    public void stop(DpTrans trans) {
        try {
            LOGGER.info("stop");
            maapi.detach(trans.getTransaction());
        } catch (Exception e) { // IOException, MaapiException
            ;
        }
    }

    void addSubnetsToCache(Hashtable<ConfKey,Vector<Entry>> cache,
                           ConfKey pool,
                           String path,
                           int th)
        throws IOException, ConfException, InvalidNetmaskException {
        MaapiCursor mc;
        ConfKey idx;
        Vector<Entry> subnet = new Vector<Entry>();
        mc = maapi.newCursor(th, path, pool);
        while ( (idx = maapi.getNext(mc)) != null) {
            String addr = idx.elementAt(0).toString();
            String mask = idx.elementAt(1).toString();
            Subnet sub = new Subnet(addr, mask);
            LOGGER.debug(String.format("Found %s subnet: %s", idx, sub));
            Entry e = new Entry();
            e.subnet = sub;
            e.key = idx;
            subnet.add(e);
        }
        cache.put(pool, subnet);
    }

    void addRangeToCache(Hashtable<ConfKey, Vector<Entry>> cache,
                         ConfKey pool,
                         String path,
                         int th)
        throws IOException, ConfException, InvalidNetmaskException, UnknownHostException {
        MaapiCursor mc;
        ConfKey idx;
        Vector<Entry> subnet = new Vector<Entry>();
        mc = maapi.newCursor(th, path, pool);
        while ( (idx = maapi.getNext(mc)) != null) {
            InetAddress addr1 = InetAddress.getByName(idx.elementAt(0).toString());
            InetAddress addr2 = InetAddress.getByName(idx.elementAt(1).toString());
            InetAddressRange range = new InetAddressRange(addr1, addr2);
            for (Subnet s : range.getSubnets()) {
                Entry e = new Entry();
                e.subnet = s;
                e.key = idx;
                subnet.add(e);
            }
        }
        cache.put(pool, subnet);
    }

    void buildCache(ConfKey pool, int th) {
        LOGGER.debug(String.format("buildCache for pool %s", pool));
        try {
            addSubnetsToCache(subnetCache, pool, "/resource-pools/ip-address-pool{%x}/subnet", th);
            addSubnetsToCache(excludeCache, pool, "/resource-pools/ip-address-pool{%x}/exclude", th);
            addRangeToCache(rangeCache, pool, "/resource-pools/ip-address-pool{%x}/range", th);
        } catch (Exception e) {
            LOGGER.error("Error building cache", e);
        }
    }

    @ValidateCallback(callPoint = "ipa_validate",
                      callType = { ValidateCBType.VALIDATE })
    public void validate(DpTrans trans, ConfObject[] kp,
                         ConfValue newval) throws DpCallbackException {
        ConfTag tag;
        ConfKey pool;
        String addr;
        String mask;
        int idx = kp.length - 1;

        LOGGER.debug(String.format("Validating %s", new ConfPath(kp).toString()));
        int th = trans.getTransaction();

        tag = (ConfTag) kp[idx--];
        if (tag.getTagHash() == resourceAllocator._resource_pools) {
            tag = (ConfTag) kp[idx--];
            if (tag.getTagHash() == ipaddressAllocator._ip_address_pool) {
                pool = (ConfKey) kp[idx--];
                tag = (ConfTag) kp[idx--];
                if (! subnetCache.containsKey(pool)) {
                    buildCache(pool, th);
                }
                Vector<Entry> subnetEntries = subnetCache.get(pool);
                Vector<Entry> excludeEntries = excludeCache.get(pool);
                Vector<Entry> rangeEntries = rangeCache.get(pool);
                Subnet overlap = null;

                switch (tag.getTagHash()) {
                    /*
                     * Subnets must not overlap each other,
                     * exclusions must not overlap each other,
                     * and exclusions must be subsets of existing subnets.
                     */
                case ipaddressAllocator._exclude:
                    ConfKey exclude = (ConfKey) kp[idx--];
                    addr = exclude.elementAt(0).toString();
                    mask = exclude.elementAt(1).toString();
                    boolean match = false;

                    try {
                        Subnet excl = new Subnet(addr, mask);

                        /* Make sure this exclusion is contained by some subnet */
                        for (Entry e : subnetEntries) {
                            if (e.subnet.contains(excl)) {
                                match = true;
                                break;
                            }
                        }
                        if (match == false) {
                            String s =
                                String.format("No subnet contains exclusion %s in ip pool", excl);
                            throw new DpCallbackException(s);
                        }

                        /* Make sure this exclusion does not overlap any other exclusion */
                        for (Entry e : excludeEntries) {
                            if (e.key.equals(exclude)) {
                                continue;
                            }
                            if (excl.overlaps(e.subnet)) {
                                String s =
                                    String.format("Exclusions %s and %s overlap", excl, e.subnet);
                                throw new DpCallbackException(s);
                            }
                        }

                    } catch (UnknownHostException e) {
                        /* Can't happen as we are only dealing with ip addresses */
                        LOGGER.error("", e);
                    } catch (InvalidNetmaskException e) {
                        /* Can't happen, already checked in yang */
                        LOGGER.error("", e);
                    }
                    return;
                case ipaddressAllocator._subnet:
                    ConfKey subn = (ConfKey) kp[idx--];
                    addr = subn.elementAt(0).toString();
                    mask = subn.elementAt(1).toString();
                    try {
                        Subnet subnet = new Subnet(addr, mask);

                        /* Make sure this subnet does not overlap any other subnet */
                        for (Entry e : subnetEntries) {
                            if (e.key.equals(subn)) {
                                continue;
                            }
                            if (subnet.overlaps(e.subnet)) {
                                String s =
                                    String.format("Subnets %s and %s overlap", subnet, e.subnet);
                                throw new DpCallbackException(s);
                            }
                        }

                        /* Make sure this subnet does not overlap any range */
                        for (Entry e : rangeEntries) {
                            if (subnet.overlaps(e.subnet)) {
                                String s = String.format("Subnet %s overlaps range %s",
                                                         subnet, e.key);
                                throw new DpCallbackException(s);
                            }
                        }

                    } catch (UnknownHostException e) {
                        /* Can't happen as we are only dealing with ip addresses */
                        LOGGER.error("", e);
                    } catch (InvalidNetmaskException e) {
                        /* Can't happen, already checked in yang */
                        LOGGER.error("", e);
                    }
                    return;
                case ipaddressAllocator._range:
                    ConfKey rangeKey = (ConfKey) kp[idx--];
                    try {
                        InetAddress addr1 = InetAddress.getByName(rangeKey.elementAt(0).toString());
                        InetAddress addr2 = InetAddress.getByName(rangeKey.elementAt(1).toString());
                        InetAddressRange range = new InetAddressRange(addr1, addr2);
                        for (Subnet subnet : range.getSubnets()) {
                            /* Make sure this part of the range does not overlap any subnet */
                            for (Entry e : subnetEntries) {
                                if (subnet.overlaps(e.subnet)) {
                                    String s = String.format("Range %s overlaps subnet %s",
                                                             rangeKey, e.subnet);
                                throw new DpCallbackException(s);
                                }
                            }
                            /* Make sure this part of the range does not overlap any other range */
                            for (Entry e : rangeEntries) {
                                if (e.key.equals(rangeKey)) {
                                    continue;
                                }
                                if (subnet.overlaps(e.subnet)) {
                                    String s = String.format("Range %s overlaps range %s",
                                                             rangeKey, e.key);
                                    throw new DpCallbackException(s);
                                }
                            }
                        }
                    } catch (UnknownHostException e) {
                        /* Can't happen as we are only dealing with ip addresses */
                        LOGGER.error("", e);
                    }
                    return;
                }
            }
        }
        LOGGER.error("Unhandled validation: " + new ConfPath(kp));
    }
}
