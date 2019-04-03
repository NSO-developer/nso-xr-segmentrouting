# -*- mode: python; python-indent: 4 -*-
import ncs
from ncs.application import Service
from ncs.application import PlanComponent
from resource_manager import id_allocator

# ------------------------
# SERVICE CALLBACK EXAMPLE
# ------------------------
class ServiceCallbacks(Service):

    # The create() callback is invoked inside NCS FASTMAP and
    # must always exist.
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        self_plan = PlanComponent(service, 'self', 'ncs:self')
        self_plan.append_state('ncs:init')
        self_plan.append_state('ncs:ready')
        self_plan.set_reached('ncs:init')
        ready_count = 0

        for router in service.router:

            device_plan = PlanComponent(service, router.device_name,
                'sr-demo:sr-migration-router')
            device_plan.append_state('ncs:init')
            device_plan.append_state('sr-demo:segment-routing-enabled')
            device_plan.append_state('sr-demo:ti-lfa-enabled')
            if router.is_mapping_server:
                device_plan.append_state('sr-demo:mapping-server-enabled')
            device_plan.append_state('sr-demo:ldp-disabled')
            device_plan.append_state('ncs:ready')
            device_plan.set_reached('ncs:init')

            template = ncs.template.Template(router)
            template.apply('sr-plan-kicker')

            if service.enable_segment_routing:
                template.apply('enable-segment-routing')
                ready = False

                with ncs.maapi.single_read_trans(tctx.username, 'python',
                    db=ncs.OPERATIONAL) as th:
                    op_root = ncs.maagic.get_root(th)

                    if service.name in op_root.ncs__services.sr__sr:
                        sr_service = op_root.ncs__services.sr__sr[service.name]
                        if router.device_name in sr_service.plan.component:
                            sr_plan = sr_service.plan.\
                                component[router.device_name]
                            if sr_plan.state['ready'].status == 'reached':
                                ready = True

                if not ready:
                    self.log.info('SR service router %s not ready' %
                        router.device_name)
                    continue

                device_plan.set_reached('sr-demo:segment-routing-enabled')

                if service.enable_ti_lfa:
                    template.apply('enable-ti-lfa')
                    device_plan.set_reached('sr-demo:ti-lfa-enabled')

                    if service.enable_mapping_servers:
                        if router.is_mapping_server:
                            template.apply('enable-mapping-server')
                            device_plan.set_reached(
                                'sr-demo:mapping-server-enabled')

                        if service.disable_ldp:
                            template.apply('disable-ldp')
                            device_plan.set_reached('sr-demo:ldp-disabled')
                            device_plan.set_reached('ncs:ready')
                            ready_count = ready_count + 1


        if ready_count == len(service.router.keys()):
            self_plan.set_reached('ncs:ready')
            self.log.info('Service %s ready' % service.name)

    # The pre_modification() and post_modification() callbacks are optional,
    # and are invoked outside FASTMAP. pre_modification() is invoked before
    # create, update, or delete of the service, as indicated by the enum
    # ncs_service_operation op parameter. Conversely
    # post_modification() is invoked after create, update, or delete
    # of the service. These functions can be useful e.g. for
    # allocations that should be stored and existing also when the
    # service instance is removed.

    # @Service.pre_lock_create
    # def cb_pre_lock_create(self, tctx, root, service, proplist):
    #     self.log.info('Service plcreate(service=', service._path, ')')

    # @Service.pre_modification
    # def cb_pre_modification(self, tctx, op, kp, root, proplist):
    #     self.log.info('Service premod(service=', kp, ')')

    # @Service.post_modification
    # def cb_post_modification(self, tctx, op, kp, root, proplist):
    #     self.log.info('Service premod(service=', kp, ')')


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
        self.register_service('sr-demo-servicepoint', ServiceCallbacks)

        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NCS went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('Main FINISHED')
