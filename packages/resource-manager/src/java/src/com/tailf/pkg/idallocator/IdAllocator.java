package com.tailf.pkg.idallocator;

import java.net.UnknownHostException;

import org.apache.log4j.Logger;

import com.tailf.conf.Conf;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfInt32;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfObjectRef;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfUInt8;
import com.tailf.conf.ConfUInt32;
import com.tailf.conf.ConfValue;
import com.tailf.conf.DiffIterateFlags;
import com.tailf.conf.DiffIterateOperFlag;
import com.tailf.conf.DiffIterateResultFlag;
import com.tailf.dp.services.ServiceContext;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.ApplicationComponent;
import com.tailf.cdb.CdbException;
import com.tailf.cdb.CdbSubscription;
import com.tailf.cdb.CdbDiffIterate;
import com.tailf.cdb.CdbSubscriptionSyncType;
import com.tailf.cdb.CdbDBType;
import com.tailf.cdb.CdbLockType;
import com.tailf.cdb.Cdb;
import com.tailf.cdb.CdbSession;
import com.tailf.maapi.Maapi;
import com.tailf.maapi.MaapiUserSessionFlag;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;
import com.tailf.pkg.idallocator.namespaces.idAllocator;
import com.tailf.pkg.idallocator.namespaces.idAllocatorOper;
import com.tailf.pkg.idpool.Allocation;
import com.tailf.pkg.idpool.IDPool;
import com.tailf.pkg.idpool.Range;
import com.tailf.pkg.idpool.exceptions.AllocationException;
import com.tailf.pkg.nsoutil.NSOUtil;
import com.tailf.pkg.nsoutil.ToRedeploy;
import com.tailf.pkg.resourcemanager.ResourceErrorException;
import com.tailf.pkg.resourcemanager.ResourceException;
import com.tailf.pkg.resourcemanager.ResourceWaitException;
import com.tailf.pkg.resourcemanager.namespaces.resourceAllocator;

import java.io.IOException;
import java.net.SocketException;
import java.nio.channels.ClosedChannelException;
import java.util.EnumSet;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;

import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

/*
 * The ID Allocator is an implementation of a Resource Manager package,
 * (not to be confused with a Java interface implementation).
 *
 * It contains of two parts:
 *     1) A subscriber part that reacts when requests/changes
 *        of id:s are detected (dels with config data).
 *     2) The other part is the ID Pool implementation which
 *        stores the pools in CDB oper data. This, second part,
 *        could be extracted and reworked to use some other means of
 *        managing the pools.
 */

public class IdAllocator implements ApplicationComponent {
    private static Logger LOGGER = Logger.getLogger(IdAllocator.class);

    private CdbSubscription sub = null;
    private CdbSession wsess, isess;

    private Set<Pool> pools = new HashSet<Pool>();

    public IdAllocator() {}

    @Resource(type=ResourceType.CDB, scope=Scope.INSTANCE,
              qualifier="id-allocator-subscriber")
    private Cdb cdb;

    @Resource(type=ResourceType.CDB, scope=Scope.INSTANCE,
              qualifier="id-allocator-reactive-fm-loop")
    private Cdb wcdb;

    @Resource(type=ResourceType.CDB, scope=Scope.INSTANCE,
              qualifier="id-allocator-reactive-fm-loop-iter")
    private Cdb icdb;

    @Resource(type=ResourceType.MAAPI, scope=Scope.INSTANCE,
              qualifier="id-alloc-reactive-fm-idallocator-m")
    private Maapi maapi;

    private int tid;
    private int alloc_subid, range_subid,
        exclude_subid, pool_subid,
        alarms_enabled_subid, alarms_threshold_subid;

    private NavuList idpool;

    /* Used as a memory variable to find out when a node has become master. */
    private boolean isMaster = true;

    public void init() throws Exception {
        try {
            EnumSet<CdbLockType> flags =
                EnumSet.<CdbLockType>of(CdbLockType.LOCK_REQUEST,
                                        CdbLockType.LOCK_WAIT);
            wsess = wcdb.startSession(CdbDBType.CDB_OPERATIONAL, flags);
            /*
             * System session, either we must pick up the NB username through
             * the fastmap data, or we must have a dedicated user that is
             * allowed to do this. Authgroup and credentials are needed to
             * redeploy since that might touch the network.
             */
            maapi.startUserSession("",
                                   maapi.getSocket().getInetAddress(),
                                   "system",
                                   new String[] {},
                                   MaapiUserSessionFlag.PROTO_TCP);

            tid = maapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ);

            sub = cdb.newSubscription();

            alloc_subid = sub.subscribe(
                     4, new resourceAllocator(),
                     String.format("/%s:%s/%s/%s",
                     resourceAllocator.prefix,resourceAllocator._resource_pools_,
                     idAllocator._id_pool_,idAllocator._allocation_));

            range_subid = sub.subscribe(
                     3, new resourceAllocator(),
                     String.format("/%s:%s/%s/%s",
                     resourceAllocator.prefix,resourceAllocator._resource_pools_,
                     idAllocator._id_pool_, idAllocator._range_));

            exclude_subid = sub.subscribe(
                     3, new resourceAllocator(),
                     String.format("/%s:%s/%s/%s",
                     resourceAllocator.prefix,resourceAllocator._resource_pools_,
                     idAllocator._id_pool_, idAllocator._exclude_));

            alarms_enabled_subid = sub.subscribe(
                     2, new resourceAllocator(),
                     String.format("/%s:%s/%s/%s/%s",
                     resourceAllocator.prefix,resourceAllocator._resource_pools_,
                     idAllocator._id_pool_, idAllocator._alarms_, idAllocator._enabled_));

            alarms_threshold_subid = sub.subscribe(
                     2, new resourceAllocator(),
                     String.format("/%s:%s/%s/%s/%s",
                     resourceAllocator.prefix,resourceAllocator._resource_pools_,
                     idAllocator._id_pool_, idAllocator._alarms_,
                     idAllocator._low_threshold_alarm_));

            pool_subid = sub.subscribe(
                     1, new resourceAllocator(),
                     String.format("/%s:%s/%s",
                     resourceAllocator.prefix,resourceAllocator._resource_pools_,
                     idAllocator._id_pool_));

            sub.subscribeDone();

            /*
             * We check for pending allocations at this point
             * since the system may have crashed before processing the
             * allocation request
             */
            LOGGER.info("Setting up state");
            loadState();
        } catch (Exception e) {
            LOGGER.error("init error", e);
            throw(e);
        }
    }

    public void run() {
        LOGGER.info("Running...");
        while(true) {
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
                    ;
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

                /* Process each subscription point. */
                for(int i=0 ; i < points.length ; i++) {
                    Type subType = null;
                    if (points[i] == alloc_subid) {
                        subType = Type.ALLOC;
                    } else if (points[i] == pool_subid) {
                        subType = Type.POOL;
                    } else if (points[i] == range_subid) {
                        subType = Type.RANGE;
                    } else if (points[i] == exclude_subid) {
                        subType = Type.EXCLUDE;
                    } else if (points[i] == alarms_enabled_subid) {
                        subType = Type.ALARMS_ENABLED;
                    } else if (points[i] == alarms_threshold_subid) {
                        subType = Type.ALARMS_THRESHOLD;
                    }
                    try {
                        sub.diffIterate(points[i], new Iter(sub, subType), enumSet, reqs);
                    } catch (Exception e) {
                        reqs = new ArrayList<Request>();
                        /*
                         * We create an empty list, so as to not get an NPE
                         * in the for loop below.
                         */
                    }
                }

                isess.endSession();

                /*
                 * If you want to use something else than the IdPool:
                 * If we are calling an external allocator we should do
                 * the following call here and not after the for loop
                 * sub.sync(CdbSubscriptionSyncType.DONE_PRIORITY);
                 */

                Set<ToRedeploy> redeps = new HashSet<ToRedeploy>();
                ArrayList<Pool> modifiedPools = new ArrayList<Pool>();
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
                 */

                /*
                 * NOTE: if you are calling an external algorithm you must
                 * do the below call earlier
                 */
                LOGGER.debug("Syncing subscriptions");
                sub.sync(CdbSubscriptionSyncType.DONE_PRIORITY);
            } catch (Exception e) {
                LOGGER.error("", e);
            }
        }
    }

    private void loadState() throws NavuException,
                                    UnknownHostException,
                                    ConfException,
                                    IOException {
        pools = new HashSet<Pool>();

        /* Read existing config and create existing pools. */
        NavuContext context = new NavuContext(maapi, tid);
        NavuContainer base  = new NavuContainer(context);
        NavuContainer root  = base.container(resourceAllocator.hash);
        NavuContainer resources =
            root.container(resourceAllocator.prefix,
                           resourceAllocator._resource_pools_);
        idpool = resources.list(idAllocator.prefix, idAllocator._id_pool_);

        /* Create id pools. */
        for(NavuContainer pool : idpool.elements()) {
            createPool(pool);
        }

        Set<ToRedeploy> init_redeps = new HashSet<ToRedeploy>();

        try {
            LOGGER.debug(String.format("pool size = %d", pools.size()));
            for(Pool pool : pools) {
                NavuList allocList =
                    (NavuList) idpool.getNavuNode(new ConfPath(pool.path+"/"+
                                                  idAllocator._allocation_));
                NavuContainer idPool =
                    resources.list(idAllocator.prefix, idAllocator._id_pool_).
                    elem(pool.idPool.getName());

                for(NavuContainer alloc : allocList.elements()) {

                    ConfPath path = new ConfPath(alloc.getKeyPath());

                    try {
                        wsess.getCase(idAllocator._response_choice_,
                                      path+"/"+idAllocator._response_);
                    } catch (ConfException e) {
                        /* No case set, continue. */
                        Request req = new Request();
                        req.path = path;
                        req.key = alloc.getKey();
                        req.pool = idPool.getKey();
                        req.val = null;
                        req.type = Type.ALLOC;
                        try {
                            allocateId(pool, init_redeps, req);
                        } catch (Exception ex) {
                            LOGGER.error("Cannot allocate id", ex);
                        }
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.error("Could not load state", e);
        }

        /* Invoke redeploy. */
        for (ToRedeploy rep : init_redeps) {
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug(String.format("Redeploying service %s as user %s",
                                           rep.getAllocatingService(), rep.getUsername()));
            }
            NSOUtil.redeploy(rep.getAllocatingService(), rep.getUsername());
        }
    }

    private String getOwner(String path) throws Exception {
        if (maapi.exists(tid,
                         String.format("%s/%s", path, idAllocator._allocating_service_))) {
            ConfObjectRef v =
                (ConfObjectRef) maapi.
                getElem(tid,
                        String.format("%s/%s", path, idAllocator._allocating_service_));

            return new ConfPath(v.getElems()).toString();
        } else {
            return "";
        }
    }

    private String getUsername(String path) throws Exception {
        if (maapi.exists(tid,
                String.format("%s/%s", path, idAllocator._username_))) {
           ConfBuf v =
               (ConfBuf) maapi.
               getElem(tid,
                       String.format("%s/%s", path, idAllocator._username_));

           return v.toString();
        } else {
           return "";
        }
    }

    private long getAllocatedId(String path) throws Exception {
        /* Check if it has allocation. */
        try {
            String selectedCase =
                wsess.getCase(idAllocator._response_choice_,
                    String.format("%s/%s", path, idAllocator._response_)).toString();

            LOGGER.debug(String.format("Found selected case: %s", selectedCase));

            if ("idalloc:ok".equals(selectedCase)) {
                long allocatedId =
                    ((ConfUInt32) wsess.getElem(
                               String.format("%s/%s/%s",
                               path,
                               idAllocator._response_,
                               idAllocator._id_))).longValue();
                LOGGER.debug(String.format("Found allocated id: %d", allocatedId));
                return allocatedId;
            } else {
                return -1L;
            }
        } catch (ConfException e) {
            LOGGER.debug(String.format("No case selected for: %s", path));
            return -1L;
        }
    }

    private boolean getSync(String path) throws Exception {
        return ((ConfBool) maapi.getElem(
                    tid, String.format("%s/%s/%s",
                                       path,
                                       idAllocator._request_,
                                       idAllocator._sync_))).booleanValue();
    }

    private long getRequestId(String path) throws Exception {
        String requestId = String.format("%s/%s/%s",
                                         path,
                                         idAllocator._request_,
                                         idAllocator._id_);
        if (maapi.exists(tid, requestId)) {
            return ((ConfUInt32) maapi.getElem(tid, requestId)).longValue();
        } else {
            return -1L;
        }
    }


    private String getRequestMethod(String path) throws Exception {
        return (maapi.getCase(
                                  tid, idAllocator._method_,
                                  String.format("%s/%s/%s",
                                                path,
                                                idAllocator._request_,
                                                idAllocator._method_)).getTag());

    }

    private void cleanupResponse(String path) throws Exception {
        wsess.setCase(idAllocator._response_choice_,
                      null,
                      String.format("%s/%s",
                      path, idAllocator._response_));
        try {
            wsess.delete(String.format("%s/%s/%s",
                                       path,
                                       idAllocator._response_,
                                       idAllocator._id_));
        } catch (CdbException e) {
            /* Ignore because we might not have an _id_ set. */
            ;
        }

        try {
            wsess.delete(path+"/"+idAllocator._response_+"/"+
                         idAllocator._error_);
        } catch (CdbException e) {
            /* Ignore because we might not have an _error_ */
            ;
        }
    }

    private void reportSuccess(long id, String path, Set<ToRedeploy> redeps)
        throws Exception {
        LOGGER.debug(String.format("SET: %s/%s/%s -> %d",
                                   path, idAllocator._response_, idAllocator._id_, id));

        wsess.setElem(new ConfUInt32(id),
                      String.format("%s/%s/%s",
                                    path,
                                    idAllocator._response_,
                                    idAllocator._id_));
        /* We need to setCase after setElem due to a bug in NCS. */
        wsess.setCase(idAllocator._response_choice_,
                      idAllocator._ok_,
                      String.format("%s/%s", path, idAllocator._response_));
        String owner = getOwner(path);
        String username = getUsername(path);
        if (owner != "") {
            /*
             * Redeploy the service that consumes this
             * data, runs in separate thread
             */
            ToRedeploy t = new ToRedeploy(owner, username);
            if (!redeps.contains(owner)) {
                LOGGER.debug(String.format("Adding %s to redeploy list", owner));
                redeps.add(t);
            }
        }
    }

    private void reportError(String error, String path,
                             Set<ToRedeploy> redeps)
        throws Exception {
        LOGGER.debug(String.format("SET: %s/response/error -> %s", path, error));
        wsess.setElem(new ConfBuf(error),
                      String.format("%s/%s/%s" , path,
                                    idAllocator._response_,
                                    idAllocator._error_));
        /*  We need to setCase after setElem due to a bug in NCS. */
        wsess.setCase(idAllocator._response_choice_,
                      idAllocator._error_,
                      String.format("%s/%s", path, idAllocator._response_));

        String owner    = getOwner(path);
        String username = getUsername(path);

        if (owner != "") {
            /*
             * Redeploy the service that consumes this
             * data, runs in separate thread
             */
            ToRedeploy t = new ToRedeploy(owner, username);
            if (!redeps.contains(owner)) {
                LOGGER.debug(String.format("Adding %s to redeploy list", owner));
                redeps.add(t);
            }
        }
    }

    private void allocateOneId(Pool p, Set<ToRedeploy> redeps, Request req,
                               long requestedId, String requestMethod)
        throws Exception, NavuException {
        String basePath = String.format("/%s:%s/%s:%s%s/%s%s",
                                        resourceAllocator.prefix,
                                        resourceAllocator._resource_pools_,
                                        idAllocator.prefix,
                                        idAllocator._id_pool_,
                                        req.pool.toString(),
                                        idAllocator._allocation_,
                                        req.key.toString());
        try {
            Allocation a;
            LOGGER.debug(String.format("Trying to allocate %d", requestedId));
            String owner = getOwner(basePath);
            if (requestedId == -1) {
                a = p.idPool.allocate(owner, requestMethod);
            } else {
                a = p.idPool.allocate(owner, requestedId);
            }

            /* Write the result and redeploy */
            long id = a.getAllocated();
            reportSuccess(id, basePath, redeps);
        } catch (AllocationException ex) {
            reportError(ex.toString(), basePath, redeps);
        }
    }

    private void allocateId(Pool p, Set<ToRedeploy> redeps, Request req)
        throws Exception, NavuException {
        String reqPath = req.path.toString();
        String basePath = String.format("/%s:%s/%s:%s%s/%s%s",
                                        resourceAllocator.prefix,
                                        resourceAllocator._resource_pools_,
                                        idAllocator.prefix,
                                        idAllocator._id_pool_,
                                        req.pool.toString(),
                                        idAllocator._allocation_,
                                        req.key.toString());
        boolean sync = false;
        try {
            sync = getSync(basePath);
        } catch (Exception e) {
            //
        }
        if (!sync) {
            try {
                LOGGER.debug(String.format("Is this already processed? %s/%s",
                                           basePath, idAllocator._response_ ));
                wsess.getCase(
                    idAllocator._response_choice_,
                    String.format("%s/%s", basePath, idAllocator._response_));
                /* Already processed, return. */
                return;
            } catch (ConfException e) {
                /* No case set, continue */
                ;
            }
            long requestedId = getRequestId(basePath);
            String requestMethod = getRequestMethod(basePath);
            allocateOneId(p, redeps, req, requestedId, requestMethod);
        } else {

            /*
             * Check if we have already produced a response for this
             * allocation due to processing another element in the
             * sync group
             */

            try {
                LOGGER.debug(String.format("Is this already processed? %s/%s",
                                           basePath, idAllocator._response_ ));
                wsess.getCase(
                    idAllocator._response_choice_,
                    String.format("%s/%s", basePath, idAllocator._response_));
                /* Already processed, return. */
                return;
            } catch (ConfException e) {
                /* No case set, continue */
                ;
            }

            /*
             * We need to see if there already is an allocation with the
             * requested id, in which case we should requests the same id in
             * this pool
             */
            Set<SyncGroup> syncGroups = new HashSet<SyncGroup>();

            long allocatedId = -1L;
            long requestedId = getRequestId(basePath);

            /* We use the default behavior: */
            String requestMethod = idAllocator._firstfree_;

            String allocationId =
                maapi.getElem(tid, String.format("%s/%s", basePath,
                                                 idAllocator._id_)).toString();

            syncGroups.add(new SyncGroup(p, basePath));

            for(Pool pool : pools) {
                if (pool == p) {
                    continue;
                }

                String path = String.format("%s/%s{%s}", pool.path,
                                            idAllocator._allocation_,
                                            allocationId);
                try {
                    if (maapi.exists(tid, path) && getSync(path) == true) {
                        LOGGER.debug(String.format("Found sync pool node: %s",
                                                   pool.path));
                        syncGroups.add(new SyncGroup(pool, path));

                        if (allocatedId == -1) {
                            allocatedId = getAllocatedId(path);
                        }

                        if (requestedId == -1) {
                            requestedId = getRequestId(path);
                        } else if (getRequestId(path) != -1 &&
                                 getRequestId(path) != requestedId) {
                            /*
                             * We cannot request two different ids in the
                             * same group, throw an error
                             */
                            String err =
                                "Conflicting id requests: Requested ID" +
                                " does not match previous allocation";
                            throw new AllocationException(err);
                        }

                        /*
                         * Get the request method
                         * roundrobin or firstfree?
                         */
                        requestMethod = getRequestMethod(path);

                    }
                } catch (Exception e) {
                    LOGGER.info(String.format("No sync for path %s", path));
                }
            }

            /*
             * We have three special cases
             * 1. only one node in sync group - allocate as normal
             */
            if (syncGroups.size() == 1) {
                allocateOneId(p, redeps, req, requestedId, requestMethod);
            } else if (allocatedId != -1) {
            /*
             * 2. at least one node has allocation already - try to allocate
             *    same id for this entry
             */
                allocateOneId(p, redeps, req, allocatedId, requestMethod);
            } else if (requestedId != -1) {
            /*
             * 3. at least one node has requested a specific id - try to
             *    allocate same id for this entry
             */
                allocateOneId(p, redeps, req, requestedId, requestMethod);
            } else {
            /*
             * 4. no node has allocation - try finding one id that can be
             *    allocated in all pools
             *    The way we do this is by allocating an id from the pool
             *    and then trying to allocate the same id in all pools:
             *
             *     - if this succeeds we are done
             *     - if this fails, we save the allocated id in a Set and
             *       attempts to allocate a new id:
             *       - if this fails, we fail and release all allocations in Set
             *       - if this succeeds, we try to allocate same id in all pools
             *         - if this succeeds, we deallocate all failed allocations
             *           and keep the successful one
             *         - if it fails, we save the allocation in Set, and try a
             *           new allocation
             */

                Set<Allocation> failedCandidates = new HashSet<Allocation>();

                while(true) {
                    try {
                        Allocation a = p.idPool.allocate(
                            getOwner(basePath));
                        long id = a.getAllocated();

                        Set<PoolAlloc> poolAllocs = new HashSet<PoolAlloc>();
                        LOGGER.info(String.format("Checking if alloc-id %d"
                                                  + " exists elsewhere", id));
                        try {
                            for(SyncGroup sg : syncGroups) {
                                if (sg.pool == p) {
                                    continue;
                                }

                                Allocation alloc =
                                    sg.pool.idPool.allocate(getOwner(sg.path),
                                                            id);

                                poolAllocs.add(new PoolAlloc(sg.pool, alloc));
                            }

                            /* Success: release failed candidates. */
                            for(Allocation fa : failedCandidates) {
                                p.idPool.release(fa);
                            }

                            /* Report all successful allocations. */
                            for(SyncGroup sg : syncGroups) {
                                reportSuccess(id, sg.path, redeps);
                            }

                            break;

                        } catch (AllocationException ex) {
                            /* Failed, release all and try another. */
                            for(PoolAlloc pa : poolAllocs) {
                                pa.p.idPool.release(pa.a);
                            }

                            failedCandidates.add(a);
                        }

                    } catch (AllocationException ex) {
                        /*
                         * Failed, release all and report failure
                         * release failed candidates
                         */
                        for(Allocation fa : failedCandidates) {
                            p.idPool.release(fa);
                        }

                        /* Report all failed. */
                        for(SyncGroup sg : syncGroups) {
                            reportError("sync allocation failed", sg.path,
                                        redeps);
                        }

                        break;
                    }
                }
            }
        }
    }

    /*
     * Release all allocations that are in range and add them to
     * the reallocReqs list.
     */
    private void reallocateIds(Pool pool, ConfPath cdbRange,
                               ArrayList<Request> reallocReqs,
                               Range range, boolean exclude)
        throws Exception {
        /*
         * Loop over all allocations in the pool and reallocate all that
         * fall outside the new range
         */

        NavuContext context     = new NavuContext(maapi, tid);
        NavuContainer base      = new NavuContainer(context);
        NavuContainer root      = base.container(resourceAllocator.hash);
        NavuContainer resources = root.container(resourceAllocator.prefix,
                                                 resourceAllocator.
                                                 _resource_pools_);
        NavuContainer idPool =
            resources.list(idAllocator.prefix, idAllocator._id_pool_).
            elem(pool.idPool.getName());
        NavuList allocations = idPool.list(idAllocator._allocation_);

        for(NavuContainer alloc : allocations.elements()) {
            ConfPath cdbAlloc = new ConfPath(alloc.getKeyPath());
            cdbAlloc.append(idAllocator._response_);

            ConfKey allocationKey = alloc.getKey();
            ConfKey idPoolKey = idPool.getKey();
            boolean caseOk = false;
            try {
                ConfTag respCase = (ConfTag)this.wsess.getCase(
                                            idAllocator._response_choice_,
                                            cdbAlloc);
                caseOk = respCase.getTagHash() == idAllocator._ok;
            }
            catch (Exception e) {
                LOGGER.info("No response case set - strange");
            }

            if (caseOk) {
                ConfPath okId = cdbAlloc.copyAppend(idAllocator._id_);
                long id = ((ConfUInt32) this.wsess.getElem(okId)).longValue();
                LOGGER.debug(String.format("Checking if %d is in range %s", id, range));
                LOGGER.debug(String.format("Exclude is %s, range.contains: %s",
                                           exclude, range.contains(id)));
                if (exclude && range.contains(id)) {
                    LOGGER.debug(String.format("We need to reallocate %d", id));
                    /* Needs to be reallocated */
                    this.wsess.delete(okId);
                    pool.idPool.release(id);
                    Request r = new Request();
                    r.path = new ConfPath(alloc.getKeyPath());
                    r.pool = idPoolKey;
                    r.key = allocationKey;
                    reallocReqs.add(r);
                    cleanupResponse(r.path.toString());
                } else if (!exclude && !range.contains(id)) {
                    /* needs to be reallocated */
                    LOGGER.debug(String.format("We need to reallocate %d", id));
                    this.wsess.delete(okId);
                    pool.idPool.release(id);
                    Request r = new Request();
                    r.path = new ConfPath(alloc.getKeyPath());
                    r.pool = idPoolKey;
                    r.key = allocationKey;
                    reallocReqs.add(r);
                    cleanupResponse(r.path.toString());
                }
            }
        }
    }

    public void finish() {
        try {
            wsess.endSession();
        } catch (ClosedChannelException e) {
            /* Silence here, normal close (redeploy/reload package). */
            ;
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
            } catch (Throwable ignore) {
                ;
            }
        }
        catch (Exception e) {
            LOGGER.error("", e);
        }
        LOGGER.debug("Finish end");
    }

    private void createPool(NavuContainer navuPool)
        throws NavuException, UnknownHostException, ConfException, IOException {
        RangeSet excludes;
        RangeSet availables;
        AllocationsSet allocations;

        String pname = navuPool.leaf("name").value().toString();
        LOGGER.debug(String.format("Creating new pool %s", pname));
        excludes     = new RangeSet(wsess, pname, idAllocatorOper._exclude_);
        availables   = new RangeSet(wsess, pname, idAllocatorOper._available_);
        allocations  = new AllocationsSet(wsess, pname);

        LOGGER.debug(String.format("Creating IDPool: excludes %s, availables %s, allocation %s",
                                   excludes, availables, allocations));

        NavuContainer alarms = navuPool.container("alarms");
        boolean alarmsEnabled = alarms.leaf("enabled").exists();

        int threshold = (int) ((ConfUInt8) alarms.leaf("low-threshold-alarm").value()).longValue();

        LOGGER.debug(String.format("Got %s and %s",
                                   alarmsEnabled, threshold));

        IDPool pool  = new IDPool(pname, excludes,
                                  availables, allocations,
                                  alarmsEnabled, threshold);

        /* Configure overall range. */
        NavuContainer poolRange =
            navuPool.container(idAllocatorOper.prefix, idAllocatorOper._range_);

        long start = ((ConfUInt32) poolRange.leaf(idAllocatorOper._start_).
                          value()).longValue();
        long end   = ((ConfUInt32) poolRange.leaf(idAllocatorOper._end_).
                          value()).longValue();

        pool.setRange(new Range(start, end));

        /* Compare configured excludes to known excludes and add/remove. */
        ConfPath cdbExclude = availables.getPoolPath().copyAppend(
                                    idAllocatorOper._exclude_);

        NavuList poolExclude = navuPool.list(idAllocatorOper.prefix,
                                             idAllocatorOper._exclude_);

        /* First add those that are new. */
        for(NavuContainer exclude : poolExclude.elements()) {
            start = ((ConfUInt32) exclude.leaf(idAllocatorOper._start_).
                           value()).longValue();
            end = ((ConfUInt32) exclude.leaf(idAllocatorOper._end_).
                           value()).longValue();

            if (this.wsess.exists("%s{%s %s}", cdbExclude,
                                  Long.toString(start),
                                  Long.toString(end)) == false) {
                pool.addToExcludes(new Range(start, end));
            }
        }

        /* Then remove those that have been removed. */
        int n = this.wsess.getNumberOfInstances(cdbExclude);
        if (n > 0) {
            List<ConfObject[]> objs = this.wsess.getObjects(2, 0, n, cdbExclude);

            for (ConfObject[] obj : objs) {
                start = ((ConfUInt32) obj[0]).longValue();
                end = ((ConfUInt32) obj[1]).longValue();
                String[] strKey = new String[] {Long.toString(start),
                                                Long.toString(end)};
                if (poolExclude.elem(strKey) == null) {
                    try {
                        pool.removeFromExcludes(new Range(start, end));
                    } catch (Exception e) {
                        LOGGER.error("Pool already removed", e);
                    }
                }
            }
        }

        Pool po = new Pool();
        po.idPool = pool;
        po.excludes = excludes;
        po.availables = availables;
        po.allocations = allocations;
        po.path = navuPool.getKeyPath();
        LOGGER.debug(String.format("Ok adding %s", po));
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
        IDPool idPool;
        RangeSet excludes;
        RangeSet availables;
        AllocationsSet allocations;
        String path;
    }

    private class PoolAlloc {
        Pool p;
        Allocation a;

        public PoolAlloc(Pool p, Allocation a) {
            this.p = p;
            this.a = a;
        }
    }

    private class SyncGroup {
        Pool pool;
        String path;

        public SyncGroup(Pool p, String path) {
            this.pool = p;
            this.path = path;
        }
    }

    private enum Operation { CREATE, DELETE, SET };
    private enum Type { ALLOC, RANGE, EXCLUDE, POOL,
                        ALARMS_ENABLED, ALARMS_THRESHOLD};

    private class Request {
        ConfKey pool;
        ConfKey key;
        Operation op;
        Type type;
        ConfPath path;
        ConfValue val;
        long range_start;
        long range_end;
        int alarmThreshold;
    }

    private class Iter implements CdbDiffIterate {
        Type itype;

        Iter(CdbSubscription sub, Type itype) {
            this.itype = itype;
        }

        public DiffIterateResultFlag iterate(
            ConfObject[] kp,
            DiffIterateOperFlag op,
            ConfObject oldValue,
            ConfObject newValue, Object initstate) {
            LOGGER.debug(String.format("ITERATING %s", itype));
            /* Our initstate is always an ArrayList<Request> */
            @SuppressWarnings("unchecked")
            ArrayList<Request> reqs = (ArrayList<Request>) initstate;

            try {
                ConfPath p = new ConfPath(kp);
                LOGGER.debug(String.format("%s:ITER %s %s", itype, op, p));

                if (itype == Type.POOL && kp.length > 3) {
                    return DiffIterateResultFlag.ITER_RECURSE;
                }

                Request newRequest = new Request();

                newRequest.path = p;
                newRequest.pool = (ConfKey) kp[kp.length-3];
                newRequest.val  = null;

                LOGGER.debug(String.format("New request kp = %s", Arrays.toString(kp)));
                LOGGER.debug(String.format("New request kp.length = %d", kp.length));

                if (itype == Type.ALARMS_ENABLED) {
                    newRequest.key = (ConfKey) kp[kp.length-3];
                } else if (itype == Type.ALARMS_THRESHOLD) {
                    newRequest.key = (ConfKey) kp[kp.length-3];
                } else if (kp.length >= 5 && itype != Type.RANGE) {
                    newRequest.key = (ConfKey) kp[kp.length-5];
                } else {
                    newRequest.key = null;
                }

                if (op == DiffIterateOperFlag.MOP_CREATED) {
                    newRequest.op = Operation.CREATE;
                    newRequest.type = itype;
                    reqs.add(newRequest);
                } else if (op == DiffIterateOperFlag.MOP_DELETED) {
                    newRequest.op = Operation.DELETE;

                    if (kp.length >= 5) {
                        newRequest.type = itype;
                    } else {
                        newRequest.type = Type.POOL;
                    }

                    if (newRequest.type == Type.ALLOC) {
                        ConfValue v =
                            wsess.getElem(String.format("/%s:%s/%s:%s%s/%s%s/%s/%s",
                                          resourceAllocator.prefix, resourceAllocator._resource_pools_,
                                          idAllocator.prefix, idAllocator._id_pool_, newRequest.pool.toString(),
                                          idAllocator._allocation_, newRequest.key.toString(),
                                          idAllocator._response_,
                                          idAllocator._id_));
                        newRequest.val = v;
                    }

                    reqs.add(newRequest);
                } else if (op == DiffIterateOperFlag.MOP_VALUE_SET &&
                           itype == Type.RANGE) {
                    /* Range modified. */
                    LOGGER.debug(String.format("We have a modified range with %s",
                                               newRequest.path));
                    String rangeStart = String.format("%s/../%s",
                                                      newRequest.path,
                                                      idAllocator._start_);
                    String rangeEnd = String.format("%s/../%s",
                                                    newRequest.path,
                                                    idAllocator._end_);
                    newRequest.op = Operation.SET;
                    newRequest.type = itype;
                    newRequest.range_start =
                        ((ConfUInt32) isess.getElem(rangeStart)).longValue();
                    newRequest.range_end =
                        ((ConfUInt32) isess.getElem(rangeEnd)).longValue();

                    LOGGER.debug(String.format("range_start: %s\nrange_end: %s",
                                               newRequest.range_start, newRequest.range_end));

                    boolean foundRange=false;
                    for(Request req : reqs) {
                        if (req.type == Type.RANGE &&
                            req.pool == newRequest.pool) {
                            /*
                             * We only need one RANGE set since it
                             * will process both start and end changes
                             */
                            foundRange = true;
                        }
                    }

                    if (foundRange == false) {
                        reqs.add(newRequest);
                    }
                } else if (op == DiffIterateOperFlag.MOP_VALUE_SET &&
                           itype == Type.ALARMS_THRESHOLD) {
                    newRequest.type = Type.ALARMS_THRESHOLD;
                    newRequest.op = Operation.SET;
                    String low_th = String.format("/%s:%s/%s%s/%s/%s",
                                                  resourceAllocator.prefix,resourceAllocator._resource_pools_,
                                                  idAllocator._id_pool_, newRequest.key, idAllocator._alarms_,
                                                  idAllocator._low_threshold_alarm_);
                    newRequest.alarmThreshold =
                        (int) ((ConfUInt8) isess.getElem(low_th)).longValue();
                        reqs.add(newRequest);
                } else {
                    /* Ignore VALUE_SET etc */
                    ;
                }
            } catch (Exception e) {
                LOGGER.error("", e);
            }
            return DiffIterateResultFlag.ITER_RECURSE;
        }
    }

    /**
     * Create or update an id allocation request.
     *
     * @param service <code>NavuNode</code> referencing the requesting
     * service node.
     * @param poolName     name of pool to request from
     * @param username username to use when redeploying the requesting
     * service
     * @param id           unique allocation id
     * @param sync         sync allocations with this id across pools
     * @param requestedId  a specific id to be requested
     * @throws ResourceErrorException if the pool does not exist
     */

    public static void idRequest(NavuNode service,
                                 String poolName,
                                 String username,
                                 String id,
                                 boolean sync,
                                 long requestedId)
        throws ResourceErrorException
    {
        try {
            Template t = new Template(service.context(),
                                      "resource-manager-id-allocation");
            TemplateVariables v = getIdRequestTemplateVars(service, poolName,
                                                           id, username,
                                                           sync, requestedId);
            t.apply(new NavuContainer(service.context()), v);
        } catch (Exception e) {
            throw new ResourceErrorException("Unable to create allocation request", e);
        }
    }

    /**
     * Create or update an id allocation request.
     *
     * @param context      <code>ServiceContext</code> referencing the
     *                     requesting context that the service was invoked in.
     * @param service      <code>NavuNode</code> referencing the requesting
     *                     service node.
     * @param poolName     name of pool to request from
     * @param username     username to use when redeploying the requesting
     *                     service
     * @param id           unique allocation id
     * @param sync         sync allocations with this id across pools
     * @param requestedId  a specific id to be requested
     * @throws ResourceErrorException if the pool does not exist
     */
    public static void idRequest(ServiceContext context,
                                 NavuNode service,
                                 String poolName,
                                 String username,
                                 String id,
                                 boolean sync,
                                 long requestedId)
        throws ResourceErrorException
    {
        try {
            Template t = new Template(context, "resource-manager-id-allocation");
            TemplateVariables v = getIdRequestTemplateVars(service, poolName,
                                                           id, username,
                                                           sync, requestedId);
            t.apply(service, v);
        } catch (Exception e) {
            throw new ResourceErrorException("Unable to create allocation request", e);
        }
    }

    @Deprecated
    public static void idRequest(NavuNode service,
                                 String poolName,
                                 String username,
                                 String id,
                                 boolean sync)
        throws ConfException, ResourceErrorException
    {
        idRequest(service, poolName, username, id, sync, -1L);
    }

    public static void idRequest(ServiceContext context,
                                 NavuNode service,
                                 String poolName,
                                 String username,
                                 String id,
                                 boolean sync)
        throws ConfException, ResourceErrorException
    {
        idRequest(context, service, poolName, username, id, sync, -1L);
    }

    public static TemplateVariables getIdRequestTemplateVars(NavuNode service,
                                                             String poolName,
                                                             String id,
                                                             String username,
                                                             boolean sync,
                                                             long requestedId)
        throws ConfException {
        TemplateVariables v = new TemplateVariables();
        v.putQuoted("POOL", poolName);
        v.putQuoted("ALLOCATIONID", id);
        v.putQuoted("USERNAME", username);
        v.putQuoted("SERVICE",
                    new ConfObjectRef(new ConfPath(service.getKeyPath()))
                    .toString().replace("'", "\""));
        v.putQuoted("SYNC", Boolean.toString(sync));
        v.putQuoted("REQUESTEDID",
                    Long.toString(requestedId < 0 ? -1 : requestedId));
        return v;
    }

    /**
     * Check if response is ready
     *
     * @param context   a <code>NavuContext</code> for the current transaction
     * @param cdb       a <code>Cdb</code> resource
     * @param poolName  name of pool the request was created in
     * @param id        unique allocation id
     * @return          <code>true</code> if a response for the allocation is ready
     * @throws ResourceErrorException if the request does not exist or the pool does not exist
     */
    public static boolean responseReady(NavuContext context, Cdb cdb, String poolName, String id)
        throws ResourceException, ConfException, IOException {
        NavuContainer transRoot = new NavuContainer(context).container(resourceAllocator.hash);
        NavuContainer transPool = transRoot.container(resourceAllocator._resource_pools).
            list(idAllocator.prefix, idAllocator._id_pool_).
            elem(poolName);

        if (transPool == null) {
            throw new ResourceErrorException("Pool does not exist");
        }

        NavuContainer transAlloc = transPool.list(idAllocator._allocation).elem(id);
        if (transAlloc == null) {
            throw new ResourceErrorException("Allocation does not exist");
        }

        AllocStatus alloc = cdbAllocation(cdb, poolName, id);
        if (alloc == null) {
            return false;
        }

        if (alloc.id != null) {
            return true;
        } else if (alloc.error != null) {
            return true;
        } else {
            return false;
        }
    }

    /**
     * Read result of an id allocation request.
     *
     * @param cdb a Cdb resource
     * @param poolName name of pool the request was created in
     * @param id unique allocation id
     * @throws ResourceErrorException if the allocation has failed,
     *                                the request does not exist, or the pool does not exist
     * @throws ResourceWaitException if the allocation is not ready
     */
    public static ConfUInt32 idRead(Cdb cdb, String poolName, String id)
        throws ResourceException, IOException, ConfException {
        AllocStatus alloc = cdbAllocation(cdb, poolName, id);
        if (alloc == null) {
            throw new ResourceErrorException(String.format("No such allocation: %s", id));
        }

        if (alloc.id != null) {
            return (ConfUInt32) alloc.id;
        } else if (alloc.error != null) {
            throw new ResourceErrorException(alloc.error.toString());
        } else {
            throw new ResourceWaitException("Not ready");
        }
    }

    private static AllocStatus cdbAllocation(Cdb cdb, String poolName, String id)
        throws ConfException, IOException, ResourceErrorException
    {
        CdbSession session = null;
        try {
            session = cdb.startSession(CdbDBType.CDB_RUNNING,
                                         EnumSet.of(CdbLockType.LOCK_REQUEST,
                                                    CdbLockType.LOCK_WAIT));
            ConfPath poolPath = new ConfPath("/%s:%s/%s:%s{%s}",
                                             resourceAllocator.prefix, resourceAllocator._resource_pools_,
                                             idAllocator.prefix, idAllocator._id_pool_, poolName);

            if (session.exists(poolPath) == false) {
                LOGGER.info(String.format("Checking response ready for missing id pool '%s'", poolName));
                return null;
            }

            ConfPath allocPath = poolPath.copyAppend("/allocation{" + id + "}");

            if (session.exists(allocPath) == false) {
                return null;
            }

            session.endSession();
            session = cdb.startSession(CdbDBType.CDB_OPERATIONAL,
                                         EnumSet.of(CdbLockType.LOCK_REQUEST,
                                                    CdbLockType.LOCK_WAIT));

            ConfPath idPath = allocPath.copyAppend("/response/id");
            ConfPath errPath = allocPath.copyAppend("/response/error");

            AllocStatus res = new AllocStatus();

            if (session.exists(idPath)) {
                res.id = session.getElem(idPath);
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
        }
    }

    private static class AllocStatus {
        ConfValue id = null;
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
                Set<ToRedeploy> redeps = new HashSet<ToRedeploy>();
                ArrayList<Pool> modifiedPools = new ArrayList<Pool>();

                /*
                 * If you want to use something else than the IdPool:
                 * If we are calling an external allocator we should do
                 * the following call here and not after the for loop
                 * sub.sync(CdbSubscriptionSyncType.DONE_PRIORITY);
                 */
                for (Request req : reqs) {

                    /* Find proper pool. */
                    Pool pool = null;

                    for(Pool _pool : pools) {
                        if (_pool.idPool.getName().equals(req.pool.elementAt(0).
                                                          toString())) {
                            pool = _pool;
                            break;
                        }
                    }

                    if (pool == null &&
                        !(req.type == Type.POOL && req.op == Operation.CREATE)) {
                        LOGGER.error(String.format("No matching pool found: %s",
                                                   req.pool.elementAt(0).toString()));
                        continue;
                    }

                    if (req.type == Type.POOL) {
                        if (req.op == Operation.CREATE) {
                            /* A new pool has been added. */
                            try {
                                createPool(idpool.elem(req.pool));
                            } catch (Exception e) {
                                LOGGER.error("Failed to create pool", e);
                            }
                        } else {
                            /* An existing pool has been removed, cleanup. */
                        try {
                            LOGGER.debug("Removing ALARMS");
                            pool.idPool.clearAllAlarms();
                            pools.remove(pool);
                            if (wsess.exists(pool.availables.getPoolPath())) {
                                wsess.delete(pool.availables.getPoolPath());
                            }
                        } catch (Exception e) {
                            LOGGER.error("Failed to delete pool", e);
                        }
                    }
                } else if (req.type == Type.ALARMS_ENABLED) {
                    if (req.op == Operation.DELETE) {
                        LOGGER.debug("DISABLE ALARMS!");
                        pool.idPool.disableAlarms();
                    } else {
                        LOGGER.debug("ENABLE ALARMS!");
                        pool.idPool.enableAlarms();
                    }

                } else if (req.type == Type.ALARMS_THRESHOLD) {
                        LOGGER.debug("Update alarm threshold");
                        pool.idPool.setThreshold(req.alarmThreshold);
                } else if (req.type == Type.RANGE) {
                    LOGGER.debug("got range change");

                    modifiedPools.add(pool);

                        ConfPath cdbRange =
                            pool.availables.getPoolPath().copyAppend(
                                                       idAllocatorOper._range_);

                        long start = req.range_start;
                        long end = req.range_end;
                        Range range = new Range(start, end);

                        /*
                         * The range has been modified Remove all
                         * allocations that are outside the new range,
                         * allocate ids, and re-deploy all services.
                         */

                        ArrayList<Request> reallocReqs =
                            new ArrayList<Request>();

                        reallocateIds(pool, cdbRange, reallocReqs,
                                      range, false);

                        /*
                         * Now all ids should have been released
                         * and we can modify the range
                         */

                        LOGGER.debug(String.format("Setting new range: %s",
                                                   range));
                        pool.idPool.setRange(range);

                        for(Request reallocReq : reallocReqs) {
                            allocateId(pool, redeps, reallocReq);
                        }

                } else if (req.type == Type.EXCLUDE) {

                    modifiedPools.add(pool);

                        ConfPath cdbExclude =
                            pool.availables.getPoolPath().copyAppend(
                                                     idAllocatorOper._exclude_);
                        ConfPath cdbRange =
                            pool.availables.getPoolPath().copyAppend(
                                                       idAllocatorOper._range_);

                        long start =
                            ((ConfUInt32) req.key.elementAt(0)).longValue();
                        long end =
                            ((ConfUInt32) req.key.elementAt(1)).longValue();
                        String[] key = new String[] {
                            Long.toString(start), Long.toString(end)};
                        Range range = new Range(start, end);

                        if (req.op == Operation.DELETE) {
                            /* An exclusion has been removed from the pool. */
                            if (this.wsess.exists("%s{%s %s}", cdbExclude,
                                                  key[0], key[1])) {
                                pool.idPool.removeFromExcludes(range);
                            } else {
                                LOGGER.debug(
                                    String.format("Got DELETE, but already" +
                                                  " removed: %s",
                                                  Arrays.toString(key)));
                            }
                        } else {
                            /* A new exclusion has been added to the pool. */
                            if (this.wsess.exists("%s{%s %s}", cdbExclude,
                                                  key[0], key[1]) == false) {
                                LOGGER.debug("new exclusion");

                                /*
                                 * Remove all allocations that
                                 * belong to this range, allocate new
                                 * ids, and re-deploy all services.
                                 */
                                ArrayList<Request> reallocReqs =
                                    new ArrayList<Request>();

                                reallocateIds(pool, cdbRange, reallocReqs,
                                              range, true);

                                /*
                                 * Now all ids in the new excluded section
                                 * should have been released
                                 * and we can add the new range exclusion
                                 */

                                try {
                                    pool.idPool.addToExcludes(range);
                                } catch (Exception e) {
                                    LOGGER.error("Exclude range already exists",
                                                 e);
                                }

                                for(Request reallocReq : reallocReqs) {
                                    allocateId(pool, redeps, reallocReq);
                                }

                            } else {
                                LOGGER.debug(String.format("already" +
                                                 " removed: %s",
                                                 Arrays.toString(key)));
                            }
                        }
                } else if (req.type == Type.ALLOC) {
                    if (req.op == Operation.CREATE) {
                        allocateId(pool, redeps, req);
                    } else {
                        /* Delete: clean up oper data, and de-allocate. */
                        try {
                            if (req.val != null) {
                                    long id =
                                        ((ConfUInt32) req.val).longValue();
                                    pool.idPool.release(id);
                            }
                            String basePath =
                                String.format("/%s:%s/%s:%s%s/%s%s",
                                             resourceAllocator.prefix,
                                             resourceAllocator._resource_pools_,
                                             idAllocator.prefix,
                                             idAllocator._id_pool_,
                                             req.pool.toString(),
                                             idAllocator._allocation_,
                                             req.key.toString());

                            cleanupResponse(basePath);
                        }
                        catch (Exception e) {
                            LOGGER.error("Error deleting allocation", e);
                        }
                    }
                }
            }

            NavuContext context = new NavuContext(maapi, tid);
            NavuContainer base  = new NavuContainer(context);
            NavuContainer root  = base.container(resourceAllocator.hash);
                NavuContainer resources
                    = root.container(resourceAllocator.prefix,
                                     resourceAllocator._resource_pools_);

                for (Pool pool : modifiedPools) {
                    /*
                     * The pool definition was changed, see if some
                     * previously failed allocation should now be retried
                     */
                    NavuContainer idPool =
                        resources.list(idAllocator.prefix,
                                       idAllocator._id_pool_).elem(pool.idPool.getName());
                    NavuList allocations =
                        idPool.list(idAllocator._allocation_);

                    for(NavuContainer alloc : allocations.elements()) {
                        String cdbAllocPath =
                            new ConfPath(alloc.getKeyPath()).toString();
                        String responsePath =
                            cdbAllocPath+"/"+idAllocator._response_;

                        ConfTag selectedCase = null;

                        try {
                            selectedCase =
                                ((ConfTag) wsess.getCase(
                                               idAllocator._response_choice_,
                                               responsePath));
                        } catch (ConfException e) {
                            /*
                             * No case selected. Ignore for now since we
                             * will set this later on.
                             */
                            LOGGER.debug("NO RESPONSE CHOICE NOW");
                            continue;
                        }

                        if (selectedCase.getTag().equals(idAllocator._error_)) {
                            /* Previously failed allocation, retry. */
                            LOGGER.debug("ERROR RESPONSE RETRYING");
                            Request r = new Request();
                            r.path = new ConfPath(alloc.getKeyPath());
                            r.key = alloc.getKey();
                            r.pool = idPool.getKey();
                            LOGGER.debug(String.format("retrying on %s ",
                                                       r.path.toString()));
                            cleanupResponse(r.path.toString());
                            allocateId(pool, redeps, r);
                        }
                    }
                }

                LOGGER.debug("REDEPLOYING....");

                /* Invoke redeploy */
                NSOUtil.redeploy(redeps);

                } catch (ConfException e) {
            if (e.getCause() instanceof java.io.EOFException) {
                /* Silence here, normal close (redeploy/reload package). */
                LOGGER.error("", e);
            } else {
                LOGGER.error("", e);
            }
        } catch (SocketException e) {
            /* Silence here, normal close (redeploy/reload package). */
            LOGGER.error("", e);
        } catch (ClosedChannelException e) {
            /* Silence here, normal close (redeploy/reload package). */
            LOGGER.error("", e);
        } catch (Exception e) {
            LOGGER.error("", e);
            }
        }
    }

}
