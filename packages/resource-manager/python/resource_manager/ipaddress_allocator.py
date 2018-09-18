import ncs
import ncs.maapi as maapi
import ncs.maagic as maagic


def net_request(service, svc_xpath, username,
                pool_name, allocation_name, cidrmask, invert_cidr=False):
    """Create an allocation request.

    After calling this function, you have to call net_ready
    to check the availability of the allocated network.

    NOTE: Don't forget to add IP allocator as a required package
          in your package-meta-data.xml:
          <required-package>
             <name>ipaddress-allocator</name>
          </required-package>

    Example:

    import resource_manager.ipaddress_allocator as ip_allocator
    pool_name = "The Pool"
    allocation_name = "Unique allocation name"

    # This will try to allocate the network of size 24 from the pool named 'The Pool'
    # using allocation name: 'Unique allocation name'
    ip_allocator.net_request(service,
                             "/services/vl:loop-python[name='%s']" % (service.name),
                             tctx.username,
                             pool_name,
                             allocation_name,
                             24)


    net = ip_allocator.net_read(tctx.username, root,
                               pool_name, allocation_name)

    if not net:
        self.log.info("Alloc not ready")
        return

    print ("net = %s" % (net))

    Arguments:
    service -- the requesting service node
    svc_xpath -- xpath to the requesting service
    username -- username to use when redeploying the requesting service
    pool_name -- name of pool to request from
    allocation_name -- unique allocation name
    cidrmask -- the size of the network
    invert_cidr -- invert the cidrmask
    """
    template = ncs.template.Template(service)
    vars = ncs.template.Variables()
    vars.add("POOL", pool_name)
    vars.add("ALLOCATIONID", allocation_name)
    vars.add("USERNAME", username)
    vars.add("SERVICE", svc_xpath)
    vars.add("SUBNET_SIZE", cidrmask)
    vars.add("INVERT", "true" if invert_cidr else "")
    template.apply('resource-manager-ipaddress-allocation', vars)


def net_read(username, root, pool_name, allocation_name):
    """Returns the allocated network or None

    Arguments:
    username -- the requesting service's transaction's user
    root -- a maagic root for the current transaction
    pool_name -- name of pool to request from
    allocation_name -- unique allocation name
    """
    # Look in the current transaction
    ip_pool_l = root.ralloc__resource_pools.ip_address_pool

    if pool_name not in ip_pool_l:
        raise LookupError("IP pool %s does not exist" % (pool_name))

    ip_pool = ip_pool_l[pool_name]

    if allocation_name not in ip_pool.allocation:
        raise LookupError("allocation %s does not exist in pool %s" %
                          (allocation_name, pool_name))

    # Now we switch from the current trans to actually see if
    # we have received the alloc
    with maapi.single_read_trans(username, "system",
                                 db=ncs.OPERATIONAL) as th:
        ip_pool_l = maagic.get_root(th).ralloc__resource_pools.ip_address_pool

        if pool_name not in ip_pool_l:
            return None

        ip_pool = ip_pool_l[pool_name]

        if allocation_name not in ip_pool.allocation:
            return None

        alloc = ip_pool.allocation[allocation_name]

        if alloc.response.subnet:
            return alloc.response.subnet
        elif alloc.response.error:
            raise LookupError(alloc.response.error)
        else:
            return None
