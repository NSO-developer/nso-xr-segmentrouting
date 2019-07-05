# -*- mode: python; python-indent: 4 -*-
from resource_manager import id_allocator
import ncs
from ncs.application import Service
from ncs.application import PlanComponent


# ------------------------
# SERVICE CALLBACK EXAMPLE
# ------------------------
class ServiceCallbacks(Service):

    # The create() callback is invoked inside NCS FASTMAP and
    # must always exist.
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        srgb_pools = root.cfinfra__sr_infrastructure.sr_global_block_pools
        if not srgb_pools:
            raise Exception('No SRGB pools defined')

        pool_name = next(iter(srgb_pools)).name

        for router in service.router:
            router_plan = PlanComponent(service, router.device_name, 'ncs:self')
            router_plan.append_state('ncs:init')
            router_plan.append_state('ncs:ready')
            router_plan.set_reached('ncs:init')

            requested_prefix_sid = -1
            if router.prefix_preference == 'assign-prefix-sid':
                requested_prefix_sid = router.prefix_preference.\
                                       assign_prefix_sid

            allocation_name = '%s-%s' % (service.name, router.device_name)

            id_allocator.id_request(
                service, "/ncs:services/sr:sr[sr:name='%s']" % service.name,
                tctx.username, pool_name, allocation_name, False,
                requested_prefix_sid
            )

            prefix_sid = id_allocator.id_read(tctx.username, root, pool_name,
                                              allocation_name)
            if prefix_sid:
                self.log.info('Allocated prefix-sid %d for router %s'
                              % (prefix_sid, router.device_name))

                template = ncs.template.Template(router)
                template_vars = ncs.template.Variables()
                template_vars.add('PREFIX-SID', prefix_sid)
                template.apply('sr-template', template_vars)

                router_plan.set_reached('ncs:ready')
                self.log.info('Router %s ready' % router.device_name)
            else:
                self.log.info('Prefix for router %s not ready'
                              % router.device_name)


# ---------------------------------------------
# COMPONENT THREAD THAT WILL BE STARTED BY NCS.
# ---------------------------------------------
class Main(ncs.application.Application):
    def setup(self):
        # The application class sets up logging for us. It is accessible
        # through 'self.log' and is a ncs.log.Log instance.
        self.log.info('Main RUNNING')

        # Service callbacks require a registration for a 'service point',
        # as specified in the corresponding data model.
        #
        self.register_service('sr-servicepoint', ServiceCallbacks)

        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NCS went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('Main FINISHED')
