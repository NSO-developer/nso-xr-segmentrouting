# -*- mode: python; python-indent: 4 -*-
import ncs
from ncs.application import Service

class ServiceCallbacks(Service):

    # The create() callback is invoked inside NCS FASTMAP and
    # must always exist.
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('POOL_SERVICE 1: Service create(service=', service._path, ')')

        self.log.info("service = {}".format(dir(service)))
        self.log.info("pool-service name == {}".format(service.name))
        self.log.info("service.subnet = {}".format(dir(service.subnet)))

        poolname = service.name

        ippool = root.ralloc__resource_pools.ipalloc__ip_address_pool
        ippool.create(poolname)

        mypool = ippool[poolname]
        subnet = mypool.subnet
        for s in service.subnet:
            subnet.create(s.address, s.cidrmask)

        self.log.info('POOL_SERVICE: Finished.')

class Service(ncs.application.Application):
    def setup(self):
        # The application class sets up logging for us. It is accessible
        # through 'self.log' and is a ncs.log.Log instance.
        self.log.info('Service RUNNING')

        # Service callbacks require a registration for a 'service point',
        # as specified in the corresponding data model.
        #
        self.register_service('pool-service-servicepoint', ServiceCallbacks)

        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NCS went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('Service FINISHED')
