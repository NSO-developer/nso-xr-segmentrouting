package com.cisco.sr;

import com.cisco.sr.namespaces.*;

import com.tailf.pkg.idallocator.IdAllocator;
import java.util.Properties;
import org.apache.log4j.Logger;

import com.tailf.cdb.Cdb;
import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;
import com.tailf.ncs.PlanComponent;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;

public class srRFS {
    private static final Logger LOGGER = Logger.getLogger(srRFS.class);

    @Resource(type = ResourceType.CDB, scope = Scope.CONTEXT,
        qualifier = "reactive")
    public Cdb cdb;

    /**
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
     * @throws ConfException
     */

    @ServiceCallback(servicePoint="sr-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
    throws ConfException {

        LOGGER.info("Create service: " + service.getKeyPath());
        final String serviceName = service.leaf(sr._name).valueAsString();

        NavuList srgbPools = ncsRoot.getParent()
            .container(infrastructure.uri)
            .container(infrastructure._sr_infrastructure)
            .list(infrastructure._sr_global_block_pools);

        if (srgbPools.size() == 0) {
          throw new DpCallbackException("No SRGB pools defined");
        }

        final String poolName = srgbPools.iterator().next()
          .leaf(infrastructure._name).valueAsString();

        Template srTemplate = new Template(context, "sr-template");
        TemplateVariables templateVars = new TemplateVariables();

        for(NavuNode router : service.list(sr._router)){
            String deviceName = router.leaf(sr._device_name).valueAsString();
            PlanComponent routerPlan =
                new PlanComponent(service, deviceName, "ncs:self");
            routerPlan.appendState("ncs:init").appendState("ncs:ready");
            routerPlan.setReached("ncs:init");

            NavuLeaf prefixLeaf = router.container(sr._prefix_preference)
                .leaf(sr._assign_prefix_sid);

            long requestedPrefix = -1L;

            if (prefixLeaf.exists()) {
                requestedPrefix =
                    ((ConfUInt32)prefixLeaf.value()).longValue();
            }

            String allocationId =
                String.format("%s-%s", serviceName, deviceName);
            String user = ((ServiceContextImpl)context)
                .getCurrentDpTrans().getUserInfo().getUserName();

            try {
                IdAllocator.idRequest(service, poolName, user,
                    allocationId, false, requestedPrefix);

                if (IdAllocator.responseReady(service.context(), cdb,
                    poolName, allocationId)) {

                    String prefix = IdAllocator
                        .idRead(cdb, poolName, allocationId).toString();
                    LOGGER.info(String.format(
                        "Allocated prefix %s for router %s",
                        prefix, deviceName));

                    templateVars.putQuoted("PREFIX-SID", prefix);
                    srTemplate.apply(router, templateVars);
                    routerPlan.setReached("ncs:ready");
                    LOGGER.info(String.format("Router %s ready", deviceName));
                } else {
                    LOGGER.info(String.format(
                        "Prefix for router %s not ready", deviceName));
                }
            } catch (Exception e) {
                throw new DpCallbackException(e.getMessage(), e);
            }

        }
        return opaque;
    }
}
