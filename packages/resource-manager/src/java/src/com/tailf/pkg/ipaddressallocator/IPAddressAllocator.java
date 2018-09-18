package com.tailf.pkg.ipaddressallocator;

import java.io.IOException;
import java.net.UnknownHostException;
import org.apache.log4j.Logger;

import com.tailf.pkg.resourcemanager.ResourceErrorException;
import com.tailf.pkg.resourcemanager.ResourceException;
import com.tailf.pkg.resourcemanager.ResourceWaitException;
import com.tailf.pkg.resourcemanager.namespaces.resourceAllocator;

import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.*;
import com.tailf.ncs.template.*;
import com.tailf.cdb.*;
import com.tailf.maapi.*;
import com.tailf.ncs.annotations.*;
import com.tailf.pkg.ipaddressallocator.namespaces.ipaddressAllocator;
import com.tailf.pkg.ipam.*;
import com.tailf.pkg.ipam.util.InetAddressRange;
import com.tailf.pkg.ipam.exceptions.*;
import com.tailf.pkg.nsoutil.NSOUtil;
import com.tailf.pkg.nsoutil.ToRedeploy;
import com.tailf.dp.services.ServiceContext;

import java.net.InetAddress;
import java.net.Inet6Address;
import java.net.Socket;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.tailf.navu.*;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

public class IPAddressAllocator implements ApplicationComponent {
    private static final Logger LOGGER = Logger.getLogger(IPAddressAllocator.class);

    private CdbSubscription sub = null;
    private CdbSession wsess, isess;

    private Set<Pool> pools = new HashSet<Pool>();

    public IPAddressAllocator() {}

    @Resource(type=ResourceType.CDB, scope=Scope.INSTANCE,
              qualifier="ip-address-allocator-subscriber")
    private Cdb cdb;

    @Resource(type=ResourceType.CDB, scope=Scope.INSTANCE,
              qualifier="ip-address-allocator-reactive-fm-loop")
    private Cdb wcdb;

    @Resource(type=ResourceType.CDB, scope=Scope.INSTANCE,
              qualifier="ip-address-allocator-reactive-fm-loop-iter")
    private Cdb icdb;

    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE,
              qualifier="reactive-fm-ipaddressallocator-m")
    private Maapi maapi;

    private int tid;
    private int subIdAlloc, subIdSubnet, subIdRange, subIdPool,
        subIdExclude, alarms_enabled_subid, alarms_threshold_subid;;

    /* Used as a memory variable to find out when a node has become master. */
    private boolean isMaster = true;

    public void init() {
        try {
            EnumSet<CdbLockType> flags =
                EnumSet.<CdbLockType>of(CdbLockType.LOCK_REQUEST,
                                        CdbLockType.LOCK_WAIT);
            wsess = wcdb.startSession(CdbDBType.CDB_OPERATIONAL, flags);
            /*
             * System session, either we must pick up the NB username
             * through the fastmap data, or we must have a dedicated
             * user that is allowed to do this. Authgroup and
             * credentials are needed to to redeploy since that might
             * touch the network.
            */
            maapi.startUserSession("admin",
                                   maapi.getSocket().getInetAddress(),
                                   "system",
                                   new String[] {"admin"},
                                   MaapiUserSessionFlag.PROTO_TCP);

            tid = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);

            sub = cdb.newSubscription();

            /* Create subscriptions */
            subIdAlloc = sub.subscribe(
                     4, new resourceAllocator(),
                     "/"+
                     resourceAllocator.prefix + ":" +
                     resourceAllocator._resource_pools_ + "/" +
                     ipaddressAllocator.prefix + ":" +
                     ipaddressAllocator._ip_address_pool_ + "/" +
                     ipaddressAllocator._allocation_);

            subIdSubnet = sub.subscribe(
                     3, new resourceAllocator(),
                     "/"+
                     resourceAllocator.prefix + ":" +
                     resourceAllocator._resource_pools_ + "/" +
                     ipaddressAllocator.prefix + ":" +
                     ipaddressAllocator._ip_address_pool_ + "/" +
                     ipaddressAllocator._subnet_);

            subIdRange = sub.subscribe(
                     3, new resourceAllocator(),
                     "/"+
                     resourceAllocator.prefix + ":" +
                     resourceAllocator._resource_pools_ + "/" +
                     ipaddressAllocator.prefix + ":" +
                     ipaddressAllocator._ip_address_pool_ + "/" +
                     ipaddressAllocator._range_);

            subIdExclude = sub.subscribe(
                     3, new resourceAllocator(),
                     "/"+
                     resourceAllocator.prefix + ":" +
                     resourceAllocator._resource_pools_ + "/" +
                     ipaddressAllocator.prefix + ":" +
                     ipaddressAllocator._ip_address_pool_ + "/" +
                     ipaddressAllocator._exclude_);

            alarms_enabled_subid = sub.subscribe(
                     2, new resourceAllocator(),
                     "/"+
                     resourceAllocator.prefix + ":" +
                     resourceAllocator._resource_pools_ + "/" +
                     ipaddressAllocator.prefix + ":" +
                     ipaddressAllocator._ip_address_pool_ + "/" +
                     ipaddressAllocator._alarms_ + "/" +
                     ipaddressAllocator._enabled_);

            alarms_threshold_subid = sub.subscribe(
                     2, new resourceAllocator(),
                     "/"+
                     resourceAllocator.prefix + ":" +
                     resourceAllocator._resource_pools_ + "/" +
                     ipaddressAllocator.prefix + ":" +
                     ipaddressAllocator._ip_address_pool_ + "/" +
                     ipaddressAllocator._alarms_ + "/" +
                     ipaddressAllocator._low_threshold_alarm_);

            subIdPool = sub.subscribe(
                     1, new resourceAllocator(),
                     "/"+
                     resourceAllocator.prefix + ":" +
                     resourceAllocator._resource_pools_ + "/" +
                     ipaddressAllocator.prefix + ":" +
                     ipaddressAllocator._ip_address_pool_);

            /* Tell CDB we are ready for notifications */
            sub.subscribeDone();

            loadState();

        }
        catch (Throwable e) {
            LOGGER.error("", e);
        }
    }

    private void loadState() throws NavuException, ConfException,
                                    IOException,
                                    UnknownHostException,
                                    AddressPoolException,
                                    InvalidNetmaskException {

        pools = new HashSet<Pool>();

        /* Read existing config and create existing pools */

        NavuContext context = new NavuContext(maapi, tid);
        NavuContainer base = new NavuContainer(context);
        NavuContainer root = base.container(resourceAllocator.hash);
        NavuContainer resources =
            root.container(resourceAllocator.prefix, resourceAllocator._resource_pools_);
        NavuList ipaddressPool =
            resources.list(ipaddressAllocator.prefix, ipaddressAllocator._ip_address_pool_);

        /* Create IP address pools */
        for (NavuContainer pool : ipaddressPool.elements()) {
            createPool(pool);
        }
    }

    public void run() {
        LOGGER.info("Running...");
        while (true) {
            int[] points;
            try {
                points = sub.read();
            } catch (Exception e) {
                if (e.getCause() instanceof java.io.EOFException) {
                    /*
                     * The read exited with an exception and the cause is
                     * what we expect when we do a normal close
                     * (e.g. redeploy/reload package). Therefore we do
                     * nothing and exit since we expect to be restarted.
                     */
                } else {
                    /*
                     * Either IOException or ConfException
                     * Both are actual errors.
                     */
                    LOGGER.error("Subscription read error", e);
                }
                return;
            }

            try {
                if (NSOUtil.isHaEnabled(maapi, tid)) {
                    /* Needed to determine if this node just became master. */
                    boolean updatedIsMaster = NSOUtil.isMaster(maapi, tid);
                    if (updatedIsMaster && !isMaster) {
                        /* This node just became master, re-read our state. */
                        loadState();
                    }
                    /* Remember new isMaster. */
                    isMaster = updatedIsMaster;
                    if (!updatedIsMaster) {
                        /* This node is not the master node, it should sync. */
                        sub.sync(CdbSubscriptionSyncType.DONE_PRIORITY);
                        continue;
                    }
                }

            } catch (Exception e) {
                LOGGER.error("Failed reading HA mode:", e);
            }
            ExecutorService executor =
                Executors.newSingleThreadExecutor();

            try {
                isess = icdb.startSession(CdbDBType.CDB_RUNNING,
                                          EnumSet.of(CdbLockType.LOCK_SESSION,
                                                     CdbLockType.LOCK_WAIT));

                ArrayList<Request> reqs = new ArrayList<Request>();
                EnumSet<DiffIterateFlags> enumSet =
                    EnumSet.<DiffIterateFlags>of(
                                    DiffIterateFlags.ITER_WANT_PREV,
                                    DiffIterateFlags.ITER_WANT_SCHEMA_ORDER);

                LOGGER.debug("Subscription triggered");

                /* process each subscription point */
                for (int i=0; i < points.length; i++) {
                    Type subType = null;
                    if (points[i] == subIdAlloc) {
                        subType = Type.ALLOC;
                    } else if (points[i] == subIdPool) {
                        subType = Type.POOL;
                    } else if (points[i] == subIdSubnet) {
                        subType = Type.SUBNET;
                    } else if (points[i] == subIdRange) {
                        subType = Type.RANGE;
                    } else if (points[i] == subIdExclude) {
                        subType = Type.EXCLUDE;
                    }  else if (points[i] == alarms_enabled_subid) {
                        subType = Type.ALARMS_ENABLED;
                    } else if (points[i] == alarms_threshold_subid) {
                        subType = Type.ALARMS_THRESHOLD;
                    } else {
                        continue;
                    }

                    try {
                        sub.diffIterate(points[i], new Iter(subType), enumSet,
                                        reqs);
                    } catch (Exception e) {
                        LOGGER.error("", e);
                        reqs = null;
                    }
                }

                isess.endSession();
                RequestThread IterWorkItems = new RequestThread(reqs, wsess);

                executor.execute(IterWorkItems);
            } catch (Exception e) {
                LOGGER.error("", e);
            } catch (Throwable e) {
                LOGGER.error("", e);
            }

            try {
                executor.shutdown();
                /*
                 * We need to wait for a while, otherwise the sync
                 * lock might be released before we have updated the
                 * resource pool status.
                */
                if (!executor.awaitTermination(300000, TimeUnit.MILLISECONDS)) {
                    LOGGER.warn("Timeout waiting for ip address pool" +
                                " update!");
                    executor.shutdownNow();
                }

                /*
                 * It's important that we return as early as possible here,
                 * This is a common technique, gather the work to do, tell
                 * CDB that we're done and then do the work.
                 * It could be that the allocator needs to reach out (RPC)
                 * and that can be slow
                 *
                 * If we are calling an external allocator we should do
                 * the following call here and not after the for loop
                 * sub.sync(CdbSubscriptionSyncType.DONE_PRIORITY);
                 */
                /*
                 * NOTE: if you are calling an external algorithm you must
                 * do the below call earlier
                 */
                sub.sync(CdbSubscriptionSyncType.DONE_PRIORITY);
            } catch (Exception e) {
                LOGGER.error("", e);
            }
        }
    }

    private void allocateAddress(HashSet<ToRedeploy> redeps,
                                 Request req,
                                 boolean reAlloc)
        throws IOException, ConfException, NavuException {

        String owner = "";
        String requestId = "";
        String username = "";

        String allocSPath = req.path + "/" + ipaddressAllocator._allocating_service_;
        String usernamePath = req.path + "/" + ipaddressAllocator._username_;
        String idPath = req.path + "/" + ipaddressAllocator._id_;

        if (maapi.exists(tid, allocSPath)) {
            ConfObjectRef v = (ConfObjectRef) maapi.getElem(tid, allocSPath);
            owner = new ConfPath(v.getElems()).toString();
        }

        username = maapi.getElem(tid, usernamePath).toString();
        requestId = maapi.getElem(tid, idPath).toString();


        int subnetSize = (int) ((ConfUInt8)
                maapi.
                getElem(tid, req.path+"/"+
                    ipaddressAllocator.
                    _request_+
                    "/"+
                    ipaddressAllocator.
                    _subnet_size_)).
            longValue();
        boolean invertSubnetSize = maapi.exists(
                tid,
                req.path + "/" +
                ipaddressAllocator._request_ + "/" +
                ipaddressAllocator._invert_subnet_size_);

        int cidr4, cidr6;
        if (invertSubnetSize) {
            cidr4 = (32 - subnetSize) < 0 ? 0 : (32 - subnetSize);
            cidr6 = (128 - subnetSize) < 0 ? 0 : (128 - subnetSize);
        } else {
            cidr4 = cidr6 = subnetSize;
        }

        Allocation a = null;
        try {
            a = req.pool.ipPool.allocate(cidr4, cidr6, owner, username, requestId);

            /* Write the result and redeploy */
            Subnet net = a.getAllocated();

            Subnet fromNet = null;

            for (Subnet sub : req.pool.subnets) {
                if (sub.contains(net)) {
                    fromNet = sub;
                    break;
                }
            }

            if (a.getAllocated().getAddress() instanceof Inet6Address) {
                wsess.setElem(new ConfIPv6Prefix(net.toString()),
                              req.path+"/"+
                              ipaddressAllocator._response_+"/"+
                              ipaddressAllocator._subnet_);
            } else {
                wsess.setElem(new ConfIPv4Prefix(net.toString()),
                              req.path+"/"+
                              ipaddressAllocator._response_+"/"+
                              ipaddressAllocator._subnet_);
            }

            if (fromNet != null &&
                fromNet.getAddress() instanceof Inet6Address) {
                wsess.setElem(new ConfIPv6Prefix(fromNet.toString()),
                              req.path+"/"+
                              ipaddressAllocator._response_+"/"+
                              ipaddressAllocator._from_);
            } else if (fromNet != null) {
                wsess.setElem(new ConfIPv4Prefix(fromNet.toString()),
                              req.path+"/"+
                              ipaddressAllocator._response_+"/"+
                              ipaddressAllocator._from_);
            }
            /* We need to setCase after setElem due to a bug in NCS */
            wsess.setCase(ipaddressAllocator.
                          _response_choice_,
                          ipaddressAllocator._ok_,
                          req.path+"/"+
                          ipaddressAllocator._response_);

        } catch (AddressPoolException ex) {
            wsess.setElem(new ConfBuf(ex.toString()),
                          req.path + "/"+
                          ipaddressAllocator._response_+"/"+
                          ipaddressAllocator._error_);
            /* We need to setCase after setElem due to a bug in NCS */
            wsess.setCase(ipaddressAllocator.
                          _response_choice_,
                          ipaddressAllocator._error_,
                          req.path+"/"+
                          ipaddressAllocator._response_);
        }

        if (owner != "") {
            /*
             * Redeploy the service that consumes this
             * data, runs in separate thread
             */
            ToRedeploy t = new ToRedeploy(owner, username);
            boolean autoReDeploy = getAutoReDeploy(req.pool.path);

            if (!redeps.contains(t)) {
                if (!reAlloc || (reAlloc && autoReDeploy)) {
                    redeps.add(t);
                }
            }
        }
    }

    /**
     * Loop over all allocations in the pool and reallocate all that
     * belong to the <class>Subnet</class> sub.
     *
     * @param pool
     * @param reallocReqs
     * @param subnet
     * @throws ConfException
     * @throws UnknownHostException
     * @throws AddressPoolException
     * @throws InvalidNetmaskException
     * @throws IOException
     */
    private void reallocateSubnets(Pool pool,
                                   ArrayList<Request> reallocReqs,
                                   Subnet subnet)
        throws ConfException, UnknownHostException, AddressPoolException,
               InvalidNetmaskException, IOException {

        LOGGER.info(String.format("reallocSubnets: %s", subnet));

        NavuContext context = new NavuContext(maapi, tid);
        NavuContainer base = new NavuContainer(context);
        NavuContainer root = base.container(resourceAllocator.hash);
        NavuContainer resources =
            root.container(resourceAllocator.prefix, resourceAllocator._resource_pools_);
        NavuContainer ipaddressPool =
            resources.list(ipaddressAllocator.prefix, ipaddressAllocator._ip_address_pool_).
            elem(pool.ipPool.getName());
        NavuList allocations =
            ipaddressPool.list(ipaddressAllocator._allocation_);

        for (NavuContainer alloc : allocations.elements()) {
            String cdbAllocPath = new ConfPath(alloc.getKeyPath()).toString();
            String responsePath = cdbAllocPath + "/" +
                                  ipaddressAllocator._response_;

            if (((ConfTag) wsess.getCase(ipaddressAllocator.
                                         _response_choice_,responsePath)).
                getTag().equals(ipaddressAllocator._ok_) &&
                wsess.getElem(responsePath + "/" + ipaddressAllocator._subnet_)
                              != null) {
                Subnet allocatedSubnet =
                    new Subnet(wsess.getElem(responsePath + "/" +
                                             ipaddressAllocator._subnet_)
                               .toString());
                if (subnet.contains(allocatedSubnet) ||
                    //allocatedSubnet.contains(allocatedSubnet) || //{ // This seems weird...
                    allocatedSubnet.contains(subnet)) {

                    /* Needs to be reallocated */
                    wsess.delete(responsePath + "/" + ipaddressAllocator._subnet_);
                    pool.ipPool.release(allocatedSubnet.getAddress());
                    Request r = new Request();
                    r.path = new ConfPath(alloc.getKeyPath());
                    r.pool = pool;
                    reallocReqs.add(r);
                }
            }
        }
    }

    public void finish() {
        try {
            wsess.endSession();
        } catch (ClosedChannelException e) {
            /* Silence here, normal close (redeploy/reload package) */
        } catch (Exception e) {
            LOGGER.error("", e);
        }

        try {
            safeclose(cdb);
            safeclose(wcdb);
            try {
                maapi.finishTrans(tid);
            } catch (Throwable ignore) {
                ;
            }

            try {
                maapi.getSocket().close();
            }
            catch (Throwable ignore) {
                ;
            }
        } catch (Exception e) {
            LOGGER.error("", e);
        }
    }

    private boolean getAutoReDeploy(String path) throws IOException, ConfException {
        return ((ConfBool) maapi.getElem(
                    tid, path+"/auto-redeploy")).booleanValue();
    }

    private void createPool(NavuContainer pool)
        throws ConfException, UnknownHostException, AddressPoolException,
               InvalidNetmaskException, IOException {
        AllocationsSet allocations;
        AvailablesSet availables;
        Set<Subnet> subnets = new HashSet<Subnet>();
        Set<Subnet> excludes = new HashSet<Subnet>();

        String pname = pool.leaf(ipaddressAllocator._name).value().toString();

        availables = new AvailablesSet(wsess, pname, new SubnetComparator());
        allocations = new AllocationsSet(wsess, pname);

        /* Compare configured subnets to known subnets and add/remove */
        String cdbSubnetPath = availables.getAvailablesPath()
                               + "/../" + ipaddressAllocator._subnet_;

        NavuList poolSubnet = pool.list(ipaddressAllocator.prefix,
                                        ipaddressAllocator._subnet_);

            /* First add those that are new */
            for (NavuContainer subnet : poolSubnet.elements()) {
                String address = subnet.leaf(ipaddressAllocator._address_).
                    value().toString();
                String mask = subnet.leaf(ipaddressAllocator._cidrmask_).
                    value().toString();

                int imask =
                    (int) ((ConfUInt8) subnet.
                           leaf(ipaddressAllocator._cidrmask_).
                           value()).longValue();
                Subnet snet = new Subnet(address, imask);
                subnets.add(snet);
            }

            IPAddressPool ipPool = new IPAddressPool(pname, availables,
                                                     allocations, subnets);

            for (NavuContainer subnet : poolSubnet.elements()) {
                String address = subnet.leaf(ipaddressAllocator._address_).
                    value().toString();
                String mask = subnet.leaf(ipaddressAllocator._cidrmask_).
                    value().toString();

                int imask =
                    (int) ((ConfUInt8) subnet.
                           leaf(ipaddressAllocator._cidrmask_).
                           value()).longValue();
                Subnet snet = new Subnet(address, imask);

                String subnetPath = cdbSubnetPath+"{"+address+" "+mask+"}";

                if (!wsess.exists(subnetPath)) {
                    ipPool.addToAvailable(snet);
                    wsess.create(subnetPath);
                }
            }

            /* Then remove those that have been removed */
            Set<String> toDelete = new HashSet<String>();

            for (NavuContainer subnet : poolSubnet.elements()) {
                String address = subnet.leaf(ipaddressAllocator._address_).
                        value().toString();
                String mask = subnet.leaf(ipaddressAllocator._cidrmask_).
                        value().toString();

                if (poolSubnet.elem(new String[] {address,mask}) == null) {
                    int imask =
                            (int) ((ConfUInt8) subnet.
                                   leaf(ipaddressAllocator._cidrmask_).
                                   value()).longValue();
                    try {
                        ipPool.removeFromAvailable(new Subnet(address, imask));
                    } catch (Exception e) {
                        LOGGER.error("", e);
                    }
                    toDelete.add(cdbSubnetPath+"{"+address+" "+mask+"}");
                }
            }

            for (String subnetPath : toDelete) {
                wsess.delete(subnetPath);
            }

            /* Compare configured excludess to known excludes and add/remove */
            String cdbExcludePath = availables.getAvailablesPath()
                                    + "/../" + ipaddressAllocator._exclude_;

            NavuList poolExclude = pool.list(ipaddressAllocator.prefix,
                                             ipaddressAllocator._exclude_);

            /* First add those that are new */
            for (NavuContainer exclude : poolExclude.elements()) {
                String address = exclude.leaf(ipaddressAllocator._address_).
                    value().toString();
                String mask = exclude.leaf(ipaddressAllocator._cidrmask_).
                    value().toString();

                int imask =
                    (int) ((ConfUInt8) exclude.
                           leaf(ipaddressAllocator._cidrmask_).
                           value()).longValue();
                Subnet excludedSubnet = new Subnet(address, imask);
                excludes.add(excludedSubnet);

                String exPath = cdbExcludePath+"{"+address+" "+mask+"}";
                if (!wsess.exists(exPath)) {
                    for (Subnet subnet : subnets) {
                        if (subnet.contains(excludedSubnet)) {
                            try {
                                ipPool.removeFromAvailable(excludedSubnet);
                            } catch (Exception e) {
                                LOGGER.error("", e);
                            }
                        }
                    }
                    wsess.create(exPath);
                }
            }

            toDelete = new HashSet<String>();

            /* Then remove those that have been removed */
            for (NavuContainer exclude : poolExclude.elements()) {
                String address = exclude.leaf(ipaddressAllocator._address_).
                    value().toString();
                String mask = exclude.leaf(ipaddressAllocator._cidrmask_).
                    value().toString();

                if (poolExclude.elem(new String[] {address,mask}) == null) {
                    int imask =
                        (int) ((ConfUInt8) exclude.leaf(ipaddressAllocator._cidrmask_)
                                                  .value()).longValue();

                    Subnet excludeSnet = new Subnet(address, imask);
                    for (Subnet osub : subnets) {
                        if (osub.contains(excludeSnet)) {
                            try {
                                 ipPool.addToAvailable(excludeSnet);
                            } catch (Exception e) {
                                LOGGER.error("", e);
                            }
                        }
                    }
                    toDelete.add(cdbExcludePath+"{"+address+" "+mask+"}");
                }
            }

            for (String subnetPath : toDelete) {
              wsess.delete(subnetPath);
            }

        Pool po = new Pool();
        po.ipPool = ipPool;
        po.availables = availables;
        po.subnets = subnets;
        po.excludes = excludes;
        po.path = pool.getKeyPath();

        pools.add(po);
    }

    private void safeclose(Cdb s) {
        try {
            s.close();
        } catch (Exception ignore) {
            ;
        }
    }

    private class Pool {
        IPAddressPool ipPool;
        AvailablesSet availables;
        Set<Subnet> subnets;
        Set<Subnet> excludes;
        String path;
    }

    private enum Operation { CREATE, DELETE };
    private enum Type { ALLOC, SUBNET, RANGE, EXCLUDE, POOL,
                        ALARMS_ENABLED, ALARMS_THRESHOLD};

    private class Request {
        Pool pool;
        ConfKey poolKey;
        ConfKey subnetKey;
        Operation op;
        Type type;
        ConfPath path;
        ConfValue response;
        int alarmThreshold;

        public String getAddress() {
            return subnetKey.elementAt(0).toString();
        }

        /* If Type is SUBNET or EXCLUDE */
        public int getMaskLength() {
            return (int) ((ConfUInt8) subnetKey.elementAt(1)).longValue();
        }

        /* If Type is RANGE */
        public String getAddress2() {
            return subnetKey.elementAt(1).toString();
        }

        /*
         * Return all subnets covered by this request.
         * Can only be more than one if the request type is RANGE
         */
        public List<Subnet> getSubnets() throws UnknownHostException, InvalidNetmaskException {
            List<Subnet> subnets;
            if (type == Type.SUBNET) {
                Subnet sub = new Subnet(getAddress(), getMaskLength());
                subnets = new ArrayList<Subnet>();
                subnets.add(sub);
            } else {
                InetAddress addr1 = InetAddress.getByName(getAddress());
                InetAddress addr2 = InetAddress.getByName(getAddress2());
                InetAddressRange range = new InetAddressRange(addr1, addr2);
                subnets = range.getSubnets();
            }
            return subnets;
        }
    }

    private class Iter implements CdbDiffIterate {
        Type itype;

        Iter(Type itype) {
            this.itype = itype;
        }

        public DiffIterateResultFlag iterate(
            ConfObject[] kp,
            DiffIterateOperFlag op,
            ConfObject oldValue,
            ConfObject newValue, Object initstate) {
            LOGGER.debug(String.format("ITERATING %s", itype));
            @SuppressWarnings("unchecked")
            ArrayList<Request> reqs = (ArrayList<Request>) initstate;

            try {
                ConfPath p = new ConfPath(kp);

                if (itype == Type.POOL && kp.length > 3) {
                    return DiffIterateResultFlag.ITER_CONTINUE;
                }

                Request r = new Request();

                r.path = p;
                r.poolKey = (ConfKey) kp[kp.length-3];
                r.response = null;

                LOGGER.debug(String.format("New request kp = %s", Arrays.toString(kp)));
                LOGGER.debug(String.format("New request kp.length = %d", kp.length));

                if (itype == Type.ALARMS_ENABLED) {
                    r.subnetKey = null;
                } else if (itype == Type.ALARMS_THRESHOLD) {
                    r.subnetKey = null;
                } else if (kp.length >= 5) {
                    r.subnetKey = (ConfKey) kp[kp.length-5];
                } else {
                    r.subnetKey = null;
                }

                if (op == DiffIterateOperFlag.MOP_CREATED &&
                    itype != Type.ALLOC) {
                    LOGGER.debug(String.format("Got MOP_CREATED " + kp));
                    r.op = Operation.CREATE;
                    r.type = itype;
                    if (itype == Type.ALARMS_ENABLED) {
                        r.poolKey = (ConfKey) kp[kp.length-3];
                    } else if (itype == Type.ALARMS_THRESHOLD) {
                        String low_th =
                            String.format("/%s:%s/%s%s/%s/%s",
                                          resourceAllocator.prefix,
                                          resourceAllocator._resource_pools_,
                                          ipaddressAllocator._ip_address_pool_,
                                          r.poolKey,
                                          ipaddressAllocator._alarms_,
                                      ipaddressAllocator._low_threshold_alarm_);
                        r.alarmThreshold =
                            (int) ((ConfUInt8) isess.getElem(low_th)).longValue();
                        r.poolKey = (ConfKey) kp[kp.length-3];
                    }
                    reqs.add(r);
                } else if (op == DiffIterateOperFlag.MOP_DELETED) {
                    LOGGER.debug("got delete: "+kp);
                    r.op = Operation.DELETE;
                    r.type = itype;

                    if (r.type == Type.ALLOC && kp.length <= 5) {
                        LOGGER.debug("ALLOC");
                        ConfValue v =
                            wsess.getElem(r.path+"/"+
                                          ipaddressAllocator._response_ + "/"
                                          +ipaddressAllocator._subnet_);
                        r.response = v;
                        LOGGER.debug("ALLOC:"+v);
                    }
                    reqs.add(r);
                } else if (op == DiffIterateOperFlag.MOP_VALUE_SET &&
                           itype == Type.ALLOC &&
                           kp.length == 7) {
                    ConfPath requestp = new ConfPath(Arrays.copyOfRange(kp,2,kp.length));
                    if (wsess.exists(requestp+"/"+ipaddressAllocator._response_+ "/"
                                     +ipaddressAllocator._subnet_)) {
                        LOGGER.debug("An allocation already exists, removing old");
                        ConfValue v =
                            wsess.getElem(requestp+"/"+
                                          ipaddressAllocator._response_ + "/"
                                          +ipaddressAllocator._subnet_);
                        r.response = v;
                        r.op = Operation.DELETE;
                        r.type = Type.ALLOC;
                        r.path = requestp;
                        reqs.add(r);
                    }

                    LOGGER.debug("Creating new allocation");
                    Request rcreate = new Request();
                    rcreate.path = requestp;
                    rcreate.poolKey = (ConfKey) kp[kp.length-3];
                    rcreate.response = null;
                    rcreate.subnetKey = (ConfKey) kp[kp.length-5];
                    rcreate.op = Operation.CREATE;
                    rcreate.type = Type.ALLOC;
                    reqs.add(rcreate);

                } else if (op == DiffIterateOperFlag.MOP_VALUE_SET &&
                           itype == Type.ALARMS_THRESHOLD) {
                    String low_th =
                        String.format("/%s:%s/%s%s/%s/%s",
                                      resourceAllocator.prefix,
                                      resourceAllocator._resource_pools_,
                                      ipaddressAllocator._ip_address_pool_,
                                      r.poolKey,
                                      ipaddressAllocator._alarms_,
                                      ipaddressAllocator._low_threshold_alarm_);
                    r.alarmThreshold =
                        (int) ((ConfUInt8) isess.getElem(low_th)).longValue();
                    r.poolKey = (ConfKey) kp[kp.length-3];
                    r.op = Operation.CREATE;
                    r.type = itype;
                    reqs.add(r);
                } else {
                    LOGGER.debug("Ignoring : "+ op + " " + itype);
                }
            } catch (Exception e) {
                LOGGER.error("", e);
            }
            return DiffIterateResultFlag.ITER_RECURSE;
        }
    }

    private static class SubnetComparator implements Comparator<Subnet> {
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

    public static void subnetRequest(NavuNode service,
                                     String poolName,
                                     String username,
                                     int cidrmask,
                                     String id)
        throws ResourceErrorException {
        subnetRequest(service,
                      poolName,
                      username,
                      cidrmask,
                      id,
                      false);
    }
    /**
     * Create an IP subnet allocation request. Make sure that the
     * <code>NavuNode</code> service is the same node you get in your
     * service create. This ensures that the backpointers are updated
     * correctly and that the Reactive Fastmap algorithm works as
     * intended.
     *
     * @param service   <code>NavuNode</code> referencing the requesting
     *                  service node. Make sure that this is the node you get
     *                  in your service create!
     * @param poolName  name of pool to request from
     * @param username  username to use when redeploying the requesting service
     * @param cidrmask  CIDR mask length of requested subnet
     * @param id        unique allocation id
     * @throws ResourceErrorException if the pool does not exist
     */
    public static void subnetRequest(NavuNode service,
                                     String poolName,
                                     String username,
                                     int cidrmask,
                                     String id,
                                     boolean invertCidr)
        throws ResourceErrorException {
        try {
            Template t = new Template(service.context(),
                                 "resource-manager-ipaddress-allocation");
            TemplateVariables v = new TemplateVariables();
            v.putQuoted("POOL", poolName);
            v.putQuoted("ALLOCATIONID", id);
            v.putQuoted("USERNAME", username);
            v.putQuoted("SERVICE",
                        new ConfObjectRef(new ConfPath(service.getKeyPath()))
                        .toString().replace("'", "\""));
            v.putQuoted("SUBNET_SIZE", Integer.toString(cidrmask));
            v.putQuoted("INVERT", "");
            t.apply(new NavuContainer(service.context()), v);
        } catch (Exception e) {
            throw new ResourceErrorException("Unable to create allocation" +
                                             " request", e);
        }
    }

    public static void subnetRequest(ServiceContext context,
                                     NavuNode service,
                                     String poolName,
                                     String username,
                                     int cidrmask,
                                     String id)
        throws ResourceErrorException {
        subnetRequest(context,
                      service,
                      poolName,
                      username,
                      cidrmask,
                      id,
                      false);
    }
    /**
     * Create an IP subnet allocation request. Make sure you use the
     * service context you get in the service create callback. This
     * method takes any <code>NavuNode</code>, should you need it.
     *
     * @param context   <code>ServiceContext</code> referencing the requesting
     *                  context that the service was invoked in.
     * @param service   <code>NavuNode</code> referencing the requesting
     *                  service node
     * @param poolName  name of pool to request from
     * @param username  username to use when redeploying the requesting service
     * @param cidrmask  CIDR mask length of requested subnet
     * @param id        unique allocation id
     * @throws ResourceErrorException if the pool does not exist
     */
    public static void subnetRequest(ServiceContext context,
                                     NavuNode service,
                                     String poolName,
                                     String username,
                                     int cidrmask,
                                     String id,
                                     boolean invertCidr)
        throws ResourceErrorException {
        try {
            Template t = new Template(context,
                                      "resource-manager-ipaddress-allocation");
            TemplateVariables v = new TemplateVariables();
            v.putQuoted("POOL", poolName);
            v.putQuoted("ALLOCATIONID", id);
            v.putQuoted("USERNAME", username);
            v.putQuoted("SERVICE",
                        new ConfObjectRef(new ConfPath(service.getKeyPath()))
                        .toString().replace("'", "\""));
            v.putQuoted("SUBNET_SIZE", Integer.toString(cidrmask));
            v.putQuoted("INVERT", "");
            t.apply(service, v);
        } catch (Exception e) {
            throw new ResourceErrorException(
                          "Unable to create allocation request", e);
        }
    }

    /**
     * Check if response is ready
     *
     * @param context   a <code>NavuContext</code> for the current transaction
     * @param cdb       a <code>Cdb</code> resource
     * @param poolName  name of pool the request was created in
     * @param id        unique allocation id
     * @return          <code>true</code> if a response for the allocation is
     *                  ready
     * @throws ResourceErrorException if the request does not exist or
     * the pool does not exist
     */
    public static boolean responseReady(NavuContext context, Cdb cdb,
                                        String poolName, String id)
        throws ResourceErrorException,
               NavuException,
               ConfException,
               IOException {

        /* Setup Navu context */
        NavuContainer transRoot =
            new NavuContainer(context).container(resourceAllocator.hash);
        NavuContainer transPool =
            transRoot.container(resourceAllocator._resource_pools).
            list(ipaddressAllocator.prefix,
                 ipaddressAllocator._ip_address_pool_).
            elem(poolName);

        if (transPool == null) {
            throw new ResourceErrorException("Pool " + poolName +
                                             " does not exist");
        }

        NavuContainer transAlloc =
            transPool.list(ipaddressAllocator._allocation).elem(id);
        if (transAlloc == null) {
            throw new ResourceErrorException("Allocation " + id +
                                             " does not exist in Pool " +
                                             poolName);
        }

        NavuContainer transRequest =
            transAlloc.container(ipaddressAllocator._request);
        NavuLeaf transSize = transRequest.leaf(ipaddressAllocator._subnet_size);
        /* Get the requested prefix length from the current transaction */
        int requestedSize = (int) ((ConfUInt32)transSize.value()).longValue();

        /* Get allocation response oper data */
        AllocStatus alloc = cdbAllocation(cdb, poolName, id);

        if (alloc == null) {
            return false;
        } else if (alloc.subnet != null) {
            int allocatedSize = ((ConfIPPrefix) alloc.subnet).getMaskLength();
            LOGGER.debug(String.format("requested size: %s, allocated size: %s",
                                       requestedSize, allocatedSize));
            /*
             * If the allocated size is not equal to the requested size
             * we are looking at an old response and must return false.
             */
            return (allocatedSize == requestedSize);
        } else if (alloc.error != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Read result of an ip subnet allocation request.
     *
     * @param cdb      a <code>Cdb</code> resource
     * @param poolName name of pool the request was created in
     * @param id       unique allocation id
     * @return         the allocated subnet
     * @throws ResourceErrorException if the allocation has failed,
     *                                the request does not exist, or
     *                                the pool does not exist
     * @throws ResourceWaitException if the allocation is not ready
     */
    public static ConfIPPrefix subnetRead(Cdb cdb, String poolName, String id)
        throws ResourceException, NavuException, ConfException, IOException {

        AllocStatus alloc = cdbAllocation(cdb, poolName, id);

        if (alloc == null) {
            throw new ResourceErrorException(String.format("No such" +
                                                 " allocation: %s", id));
        } else if (alloc.subnet != null) {
            return (ConfIPPrefix)alloc.subnet;
        } else if (alloc.error != null) {
            throw new ResourceErrorException(alloc.error.toString());
        } else {
            throw new ResourceWaitException("Not ready");
        }
    }

    /**
     * Read from which subnet an allocation was allocated
     *
     * @param cdb      a <code>Cdb</code> resource
     * @param poolName name of pool the request was created in
     * @param id       unique allocation id
     * @return         the subnet from which the allocation was made
     * @throws ResourceErrorException if the allocation has failed,
     *                                the request does not exist, or
     *                                the pool does not exist
     * @throws ResourceWaitException if the allocation is not ready
     */
    public static ConfIPPrefix fromRead(Cdb cdb, String poolName, String id)
        throws ResourceException, NavuException, ConfException, IOException {

        AllocStatus alloc = cdbAllocation(cdb, poolName, id);

        if (alloc == null) {
            throw new ResourceErrorException(
                                             String.format("No such" +
                                                 " allocation: %s", id));
        } else if (alloc.from != null) {
            return (ConfIPPrefix)alloc.from;
        } else if (alloc.error != null) {
            throw new ResourceErrorException(alloc.error.toString());
        } else {
            throw new ResourceWaitException("Not ready");
        }
    }

    private static AllocStatus cdbAllocation(Cdb cdb, String poolName,
                                             String id)
        throws NavuException, ResourceErrorException, ConfException, IOException
    {
        Cdb myCdb = null;
        CdbSession session = null;
        try {
            /* Create a new Cdb instance */
            Socket cdbSock = cdb.getSocket();
            InetAddress ia = cdbSock.getInetAddress();
            int port = cdbSock.getPort();
            myCdb = new Cdb("cbdAllocation", new Socket(ia, port));

            session = myCdb.startSession(CdbDBType.CDB_RUNNING,
                                         EnumSet.of(CdbLockType.LOCK_REQUEST,
                                                    CdbLockType.LOCK_WAIT));

            ConfPath poolPath = new ConfPath("/%s:%s/%s:%s{%s}",
                    resourceAllocator.prefix,
                        resourceAllocator._resource_pools_,
                    ipaddressAllocator.prefix,
                        ipaddressAllocator._ip_address_pool_,
                    poolName);

            if (session.exists(poolPath) == false) {
                LOGGER.info(String.format("Checking response ready for missing ip address pool '%s'", poolName));
                return null;
            }

            ConfPath allocPath = poolPath.copyAppend("/allocation{" + id + "}");

            if (session.exists(allocPath) == false) {
                return null;
            }

            session.endSession();
            session = myCdb.startSession(CdbDBType.CDB_OPERATIONAL,
                                         EnumSet.of(CdbLockType.LOCK_REQUEST,
                                                    CdbLockType.LOCK_WAIT));

            ConfPath fromPath = allocPath.copyAppend("/response/from");
            ConfPath subnetPath = allocPath.copyAppend("/response/subnet");
            ConfPath errPath = allocPath.copyAppend("/response/error");

            AllocStatus res = new AllocStatus();

            if (session.exists(fromPath)) {
                res.from = session.getElem(fromPath);
            }

            if (session.exists(subnetPath)) {
                res.subnet = session.getElem(subnetPath);
            }

            if (session.exists(errPath)) {
                res.error = session.getElem(errPath);
            }

            return res;
        }
        finally {
            if (session != null) {
                session.endSession();
            }
            if (myCdb != null) {
                myCdb.close();
            }
        }
    }

    private static class AllocStatus {
        ConfValue from = null;
        ConfValue subnet = null;
        ConfValue error = null;
    }

    private class RequestThread implements Runnable {

        private ArrayList<Request> reqs;
        private CdbSession wsess;

        public RequestThread(ArrayList<Request> reqs, CdbSession wsess){
            this.reqs = reqs;
            this.wsess = wsess;
        }

        @Override
        public void run() {
            try {
                HashSet<ToRedeploy> redeps = new HashSet<ToRedeploy>();
                ArrayList<Pool> modifiedPools = new ArrayList<Pool>();
                ArrayList<Request> reallocReqs = new ArrayList<Request>();

                /*
                 * Order to do process operations
                 *
                 * 1. process all subnet/range delete events
                 * 2. process all subnetrange create events
                 * 3. process all other pool modification events
                 * 4. process all allocation release events
                 * 5. retry previously failed allocations if there are any
                 * 6. process all new allocations
                 */

                for (Request req : reqs) {
                    /*
                     * Processing subnet/range DELETE first
                     */
                    if (req.op != Operation.DELETE) {
                        continue;
                    }

                    if (req.type != Type.SUBNET && req.type != Type.RANGE) {
                        continue;
                    }
                    LOGGER.debug(String.format("Processing a %s %s", req.type,
                                               req.op));
                    Pool pool = null;
                    for (Pool p : pools) {
                        if (p.ipPool.getName().equals(req.poolKey.elementAt(0).toString())) {
                            pool = p;
                            break;
                        }
                    }

                    if (pool == null) {
                        LOGGER.error(String.format("No matching pool found: %s",
                                         req.poolKey.elementAt(0).toString()));
                        continue;
                    }

                    String cdbSubnetPath =
                        pool.availables.getAvailablesPath() + "/../" +
                        ipaddressAllocator._subnet_;

                    List<Subnet> subnets = req.getSubnets();

                    for (Subnet sub : subnets) {
                        String subnetPath = String.format("%s{%s %d}",
                                              cdbSubnetPath,
                                              sub.getAddress().getHostAddress(),
                                              sub.getCIDRMask());

                        /* A subnet has been removed from the pool */
                        if (wsess.exists(subnetPath)) {

                            modifiedPools.add(pool);
                            /*
                             * Remove all allocations that
                             * belong to this subnet, allocate new
                             * addresses, and re-deploy all services.
                             */
                            reallocateSubnets(pool, reallocReqs, sub);

                            /* First restore any excludes */
                            for (Subnet esub : pool.excludes) {
                                if (sub.contains(esub)) {
                                    pool.ipPool.addToAvailable(esub);
                                } else if (esub.contains(sub)) {
                                    pool.ipPool.addToAvailable(sub);
                                }
                            }

                            /*
                             * Now all subnets should have been released
                             * and we can remove the subnet
                             */
                            wsess.delete(subnetPath);
                            pool.subnets.remove(sub);
                            try {
                                pool.ipPool.removeFromAvailable(sub);
                            } catch (Exception e) {
                                LOGGER.warn("Out of sync with cdb", e);
                            }
                        } else {
                            LOGGER.debug(String.format(
                                            "Already removed subnet: %s", sub));
                        }
                    }
                }

                for (Request req : reqs) {
                    /*
                     * All other pool modification events
                     */

                    if ((req.type == Type.SUBNET || req.type == Type.RANGE)
                        && req.op == Operation.DELETE) {
                        /* Already taken care of */
                        continue;
                    }
                    LOGGER.debug(String.format("Processing a %s %s",
                                               req.type, req.op));
                    Pool pool = null;

                    for (Pool p : pools) {
                        if (p.ipPool.getName().equals(req.poolKey.elementAt(0).toString())) {
                            pool = p;
                            break;
                        }
                    }

                    if (pool == null &&
                        !(req.type == Type.POOL &&
                          req.op == Operation.CREATE)) {
                        LOGGER.error(String.format(
                                         "No matching pool found: %s %s %s",
                                         req.poolKey.elementAt(0).toString(),
                                         req.type, req.op));
                        continue;
                    }
                    if (req.type == Type.POOL) {
                        if (req.op == Operation.CREATE) {
                            try {
                                NavuContext context =
                                    new NavuContext(maapi, tid);
                                NavuContainer base =
                                    new NavuContainer(context);
                                NavuContainer root =
                                    base.container(resourceAllocator.hash);
                                NavuContainer resources =
                                    root.container(resourceAllocator.prefix,
                                        resourceAllocator._resource_pools_);
                                NavuList ipaddressPool =
                                    resources.list(ipaddressAllocator.prefix,
                                        ipaddressAllocator._ip_address_pool_);
                                createPool(ipaddressPool.elem(req.poolKey));
                            } catch (Exception e) {
                                LOGGER.error("Failed to create pool", e);
                            }
                        } else {
                            /* An existing pool has been removed, cleanup */
                            try {
                                pool.ipPool.clearAllAlarms();
                                pools.remove(pool);
                                /* Delete CDB oper structures for pool */
                                wsess.delete(pool.availables.getAvailablesPath()
                                             + "/..");
                            } catch (Exception e) {
                                LOGGER.error("Failed to delete pool", e);
                            }
                        }
                    } else if ((req.type == Type.SUBNET ||
                                req.type == Type.RANGE)
                               && (req.op == Operation.CREATE)) {
                        /* A new subnet/range has been added */
                        String cdbSubnetPath =
                            pool.availables.getAvailablesPath() + "/../" +
                            ipaddressAllocator._subnet_;

                        List<Subnet> subnets = req.getSubnets();
                        for (Subnet sub : subnets) {
                            String subnetPath =
                                String.format("%s{%s %d}", cdbSubnetPath,
                                              sub.getAddress().getHostAddress(),
                                              sub.getCIDRMask());

                            /*
                             * A subnet has been added to the pool
                             * Check if it was already created by the
                             * pool create.
                             */
                            modifiedPools.add(pool);

                            if (!wsess.exists(subnetPath)) {
                                pool.ipPool.addToAvailable(sub);
                                wsess.create(subnetPath);
                                pool.subnets.add(sub);

                                /* Check all excludes */
                                for (Subnet esub : pool.excludes) {
                                    if (sub.contains(esub)) {
                                        pool.ipPool.removeFromAvailable(esub);
                                    }
                                }
                            }
                        }
                    } else if (req.type == Type.EXCLUDE) {
                        String cdbExcludePath =
                            pool.availables.getAvailablesPath() + "/../" +
                            ipaddressAllocator._exclude_;

                        String address = req.getAddress();
                        int imask = req.getMaskLength();
                        Subnet sub = new Subnet(address, imask);
                        String excludePath =
                            String.format("%s{%s %d}", cdbExcludePath, address,
                                          imask);

                        if (req.op == Operation.DELETE) {
                            /* An exclude has been deleted from the pool */
                            modifiedPools.add(pool);

                            if (wsess.exists(excludePath)) {
                                for (Subnet osub : pool.subnets) {
                                    if (osub.contains(sub)) {
                                        pool.ipPool.addToAvailable(sub);
                                    } else if (sub.contains(osub)) {
                                        pool.ipPool.addToAvailable(osub);
                                    }
                                }
                                wsess.delete(excludePath);
                                pool.excludes.remove(sub);
                            } else {
                                LOGGER.error(String.format("Already removed" +
                                                           " exclude: %s",
                                                           sub));
                            }
                        } else {
                            /*
                             * An exclude has been added Check if it
                             * was already created by the pool create.
                             */
                            LOGGER.debug(String.format(
                                             "Adding in exclusion %s ",
                                             excludePath));
                            if (!wsess.exists(excludePath)) {
                                LOGGER.debug(String.format("This pool needs" +
                                                           " to be modified %s",
                                                           pool));

                                modifiedPools.add(pool);
                                /*
                                 * Remove all allocations that
                                 * belong to this subnet, allocate new
                                 * addresses, and re-deploy all services.
                                 */
                                reallocateSubnets(pool, reallocReqs, sub);

                                /*
                                 * Now all subnets should have been released
                                 * and we can remove the subnet
                                 */
                                wsess.create(excludePath);
                                pool.excludes.add(sub);
                                for (Subnet osub : pool.subnets) {
                                    if (osub.contains(sub)) {
                                        try {
                                           pool.ipPool.removeFromAvailable(sub);
                                        } catch (Exception e) {
                                            // ignore for now
                                            LOGGER.error("", e);
                                        }
                                    } else if (sub.contains(osub)) {
                                        try {
                                            LOGGER.debug(String.format("osub " +
                                               "%s Removing %s from availables",
                                                                       osub,
                                                                       sub));
                                            pool.ipPool.removeFromAvailable(sub);
                                        } catch (Exception e) {
                                            // ignore for now
                                            LOGGER.error("", e);
                                        }
                                    }
                                }
                            } else {
                                /*
                                 * Created together with the subnet
                                 */
                                LOGGER.debug(String.format("The exclusion %s" +
                                                           " already exists!",
                                                           excludePath));
                            }
                        }
                    } else if (req.type == Type.ALLOC) {
                        if (req.op == Operation.CREATE) {
                            continue;
                        } else {
                            LOGGER.debug(String.format("Found deallocation: %s",
                                                       req.path));

                            /*
                             * Delete:
                             * Clean up oper data, and de-allocate
                             */
                            try {
                                try {
                                    wsess.setCase(
                                        ipaddressAllocator._response_choice_,
                                        null, req.path+"/"+ipaddressAllocator.
                                        _response_);
                                    wsess.delete(
                                        req.path + "/"+ipaddressAllocator.
                                        _response_+"/"+ipaddressAllocator.
                                        _subnet_);
                                } catch (CdbException e) {
                                    /* Nothing to delete */
                                    LOGGER.debug(String.format("No OK"+
                                                               " response to" +
                                                               " delete at %s",
                                                               req.path));
                                }

                                try {
                                    wsess.delete(
                                        req.path + "/"+ipaddressAllocator.
                                        _response_+"/"+ipaddressAllocator.
                                        _error_);
                                } catch (CdbException e) {
                                    /* Nothing to delete */
                                    LOGGER.debug(String.format("No ERROR " +
                                        "response to delete at %s", req.path));
                                }
                                LOGGER.debug(String.format("Checking if we " +
                                    "need to release %s",req));
                                if (req.response != null) {
                                    /* This was a successful
                                     * allocation, release it */
                                    LOGGER.debug(String.format("Releasing %s",
                                                               req.response));
                                    Subnet sub =
                                        new Subnet(req.response.toString());
                                    pool.ipPool.release(sub.getAddress());
                                    modifiedPools.add(pool);
                                }

                                // Could not delete allocation
                            } catch (Exception e) {
                                LOGGER.error("", e);
                            }
                        }
                    } else if (req.type == Type.ALARMS_ENABLED) {
                        if (req.op == Operation.DELETE) {
                            LOGGER.debug("DISABLE ALARMS!");
                            pool.ipPool.disableAlarms();
                        } else {
                            LOGGER.debug("ENABLE ALARMS!");
                            pool.ipPool.enableAlarms();
                        }
                    } else if (req.type == Type.ALARMS_THRESHOLD) {
                        LOGGER.debug("Update alarm threshold");
                        pool.ipPool.setThreshold(req.alarmThreshold);
                    }
                }

                for (Request reallocReq : reallocReqs) {
                    allocateAddress(redeps, reallocReq, false);
                }

                reallocReqs = new ArrayList<Request>();

                /* Finally do all new allocations */

                for (Request req : reqs) {

                    if (req.type == Type.ALLOC && req.op == Operation.CREATE) {
                        Pool pool = null;

                        for (Pool p : pools) {
                            String requestPoolKey =
                                req.poolKey.elementAt(0).toString();
                            if (p.ipPool.getName().equals(requestPoolKey)) {
                                pool = p;
                                break;
                            }
                        }

                        if (pool == null)  {
                            continue;
                        }

                        req.pool = pool;
                        allocateAddress(redeps, req, false);
                    } else {
                        continue;
                    }
                }

                LOGGER.debug("Subscription processing done");

                /*
                 * Check if some pool definition has been changed, in that
                 * case we should look for failed allocations and re-try them
                 */

                    NavuContext context = new NavuContext(maapi, tid);
                    NavuContainer base = new NavuContainer(context);
                    NavuContainer root = base.container(resourceAllocator.hash);
                    NavuContainer resources = root.container(
                                    resourceAllocator.prefix,resourceAllocator.
                                    _resource_pools_);

                    for (Pool p : modifiedPools) {
                        /*
                         * The pool definition was changed, see if some
                         * previously failed allocation should now be retried
                         */
                        NavuContainer ipaddressPool =
                            resources.list(ipaddressAllocator.prefix,
                                ipaddressAllocator._ip_address_pool_).
                            elem(p.ipPool.getName());
                        NavuList allocations =
                            ipaddressPool.list(ipaddressAllocator._allocation_);

                        for (NavuContainer alloc : allocations.elements()) {
                            String cdbAllocPath =
                                new ConfPath(alloc.getKeyPath()).toString();
                            String responsePath =
                                cdbAllocPath + "/" +
                                ipaddressAllocator._response_;

                            ConfTag selectedCase = null;

                            try {
                                selectedCase =
                                    ((ConfTag) wsess.getCase(
                                                   ipaddressAllocator.
                                                   _response_choice_,responsePath));
                            } catch (ConfException e) {
                                /* no case selected, ignore */
                            }

                            if (selectedCase != null &&
                                selectedCase.getTag().equals(
                                    ipaddressAllocator._error_)) {
                                /* Previously failed allocation, retry */
                                Request r = new Request();
                                r.path = new ConfPath(alloc.getKeyPath());
                                r.pool = p;
                                allocateAddress(redeps, r, true);
                            }
                        }
                    }

                    LOGGER.debug("REDEPLOYING....");

                    /* invoke redeploy */
                    NSOUtil.redeploy(redeps);

            }
            catch (ConfException e) {
                if (e.getCause() instanceof java.io.EOFException) {
                    /* Silence here, normal close (redeploy/reload package) */
                    LOGGER.error("", e);
                } else {
                    LOGGER.error("", e);
                }
            } catch (SocketException e) {
                /* Silence here, normal close (redeploy/reload package) */
                LOGGER.error("", e);
            } catch (ClosedChannelException e) {
                /* Silence here, normal close (redeploy/reload package) */
                LOGGER.error("", e);
            } catch (Exception e) {
                LOGGER.error("", e);
            } catch (Throwable e) {
                LOGGER.error("", e);
            }
        }
    }
}
