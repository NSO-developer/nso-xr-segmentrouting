package com.tailf.pkg.ipaddressallocatortest;

import java.util.Properties;
import java.io.IOException;
import org.apache.log4j.Logger;
import com.tailf.pkg.ipaddressallocator.IPAddressAllocator;
import com.tailf.pkg.resourcemanager.ResourceException;
import com.tailf.pkg.resourcemanager.ResourceErrorException;
import com.tailf.cdb.*;
import com.tailf.conf.*;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;
import com.tailf.navu.*;
import com.tailf.ncs.annotations.*;
import com.tailf.ncs.ns.Ncs;


public class IPAddressAllocatorTest {
    private static Logger LOGGER = Logger.getLogger(IPAddressAllocatorTest.class);

    @Resource(type=ResourceType.CDB, scope=Scope.INSTANCE,
              qualifier="ip-address-allocator-test-rfm-loop")
    private Cdb cdb;

    /**
     * Create callback method.
     * This method is called when a service instance committed due to a create
     * or update event.
     *
     * This method returns a opaque as a Properties object that can be null.
     * If not null it is stored persistently by Ncs.
     * This object is then delivered as argument to new calls of the create
     * method for this service (fastmap algorithm).
     * This way the user can store and later modify persistent data outside
     * the service model that might be needed.
     *
     * @param context - The current ServiceContext object
     * @param service - The NavuNode references the service node.
     * @param ncsRoot - This NavuNode references the ncs root.
     * @param opaque  - Parameter contains a Properties object.
     *                  This object may be used to transfer
     *                  additional information between consecutive
     *                  calls to the create callback.  It is always
     *                  null in the first call. I.e. when the service
     *                  is first created.
     * @return Properties the returning opaque instance
     * @throws DpCallbackException
     */

    @ServiceCallback(servicePoint="ipaddress-allocator-test-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
                             throws DpCallbackException {

        LOGGER.info("Servicepoint is triggered");

        try {
            String servicePath = service.getKeyPath();
            CdbSession sess = cdb.startSession(CdbDBType.CDB_OPERATIONAL);
            try {
                String dPath = servicePath + "/deploys";
                int deploys = 1;

                if (sess.exists(dPath)) {
                    deploys = (int)((ConfUInt32)sess.getElem(dPath)).longValue();
                }
                if (sess.exists(servicePath)) { // Will not exist the first time
                    sess.setElem(new ConfUInt32(deploys + 1), dPath);
                }

                NavuLeaf size = service.leaf("subnet-size");
                if (!size.exists()) {
                    return opaque;
                }
                int subnetSize = (int)((ConfUInt8)service.leaf("subnet-size").value()).longValue();
                IPAddressAllocator.subnetRequest(service, "mypool",
                                                 "admin", subnetSize, "test");
                boolean error = false;

                boolean allocated = sess.exists(servicePath + "/allocated");
                boolean ready =
                    IPAddressAllocator.responseReady(service.context(), cdb, "mypool", "test");

                if (ready) {
                    try {
                        IPAddressAllocator.fromRead(cdb, "mypool", "test");
                    } catch (ResourceErrorException e) {
                        LOGGER.info("The allocation has failed");
                        error = true;
                    }
                }
                if (ready && !error) {
                    if (!allocated) {
                        sess.create(servicePath + "/allocated");
                    }
                } else {
                    if (allocated) {
                        sess.delete(servicePath + "/allocated");
                    }
                }
            } finally {
                sess.endSession();
            }
        } catch (Exception e) {
            throw new DpCallbackException("Cannot create service", e);
        }
        return opaque;
    }
}
