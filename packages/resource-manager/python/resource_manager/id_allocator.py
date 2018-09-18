import ncs
import ncs.maapi as maapi
import ncs.maagic as maagic


def id_request(service, svc_xpath, username,
               pool_name, allocation_name, sync, requested_id=-1):
    """Create an allocation request.

    After calling this function, you have to call response_ready
    to check the availability of the allocated ID.

    Example:
    import resource_manager.id_allocator as id_allocator
    pool_name = "The Pool"
    allocation_name = "Unique allocation name"

    # This will try to allocate the value 20 from the pool named 'The Pool'
    # using allocation name: 'Unique allocation name'
    id_allocator.id_request(service,
                            "/services/vl:loop-python[name='%s']" % (service.name),
                            tctx.username,
                            pool_name,
                            allocation_name,
                            False,
                            20)


    id = id_allocator.id_read(tctx.username, root,
                              pool_name, allocation_name)

    if not id:
        self.log.info("Alloc not ready")
        return

    print ("id = %d" % (id))

    Arguments:
    service -- the requesting service node
    svc_xpath -- xpath to the requesting service
    username -- username to use when redeploying the requesting service
    pool_name -- name of pool to request from
    allocation_name -- unique allocation name
    sync -- sync allocations with this name across pools
    requested_id -- a specific ID to be requested
    """
    template = ncs.template.Template(service)
    vars = ncs.template.Variables()
    vars.add("POOL", pool_name)
    vars.add("ALLOCATIONID", allocation_name)
    vars.add("USERNAME", username)
    vars.add("SERVICE", svc_xpath)
    vars.add("SYNC", sync)
    vars.add("REQUESTEDID", requested_id)
    template.apply('resource-manager-id-allocation', vars)


def id_read(username, root, pool_name, allocation_name):
    """Returns the allocated ID or None

    Arguments:
    username -- the requesting service's transaction's user
    root -- a maagic root for the current transaction
    pool_name -- name of pool to request from
    allocation_name -- unique allocation name
    """

    # Look in the current transaction
    id_pool_l = root.ralloc__resource_pools.id_pool

    if pool_name not in id_pool_l:
        raise LookupError("ID pool %s does not exist" % (pool_name))

    id_pool = id_pool_l[pool_name]

    if allocation_name not in id_pool.allocation:
        raise LookupError("allocation %s does not exist in pool %s" %
                          (allocation_name, pool_name))

    # Now we switch from the current trans to actually see if
    # we have received the alloc
    with maapi.single_read_trans(username, "system",
                                 db=ncs.OPERATIONAL) as th:
        id_pool_l = maagic.get_root(th).ralloc__resource_pools.id_pool

        if pool_name not in id_pool_l:
            return None

        id_pool = id_pool_l[pool_name]

        if allocation_name not in id_pool.allocation:
            return None

        alloc = id_pool.allocation[allocation_name]

        if alloc.response.id:
            return alloc.response.id
        elif alloc.response.error:
            raise LookupError(alloc.response.error)
        else:
            return None
