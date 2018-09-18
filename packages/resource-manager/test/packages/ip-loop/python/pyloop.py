import ncs
from ncs.application import Service
import resource_manager.ipaddress_allocator as ip_allocator


class LoopService(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')
        pool_name = service.pool
        alloc_name = service.name
        self.log.info('CIDR length = ', service.cidr_length)
        ip_allocator.net_request(service,
                                 "/services/ip-vl:ip-loop-python[name='%s']" % (service.name),
                                 tctx.username,
                                 pool_name,
                                 alloc_name,
                                 service.cidr_length)

        net = ip_allocator.net_read(tctx.username, root,
                                    pool_name, alloc_name)
        if not net:
            self.log.info("Alloc not ready")
            return
        self.log.info('net = %s' % (net))


class Loop(ncs.application.Application):
    def setup(self):
        self.log.info('Loop RUNNING')
        self.register_service('ip-loopspnt-python', LoopService)

    def teardown(self):
        self.log.info('Loop FINISHED')
