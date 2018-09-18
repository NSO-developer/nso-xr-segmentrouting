import ncs
from ncs.application import Service
import resource_manager.id_allocator as id_allocator


class LoopService(Service):
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')
        pool_name = service.pool
        alloc_name = service.name
        id_allocator.id_request(service,
                                "/services/id-vl:id-loop-python[name='%s']" % (service.name),
                                tctx.username,
                                pool_name,
                                alloc_name,
                                False)

        id = id_allocator.id_read(tctx.username, root,
                                  pool_name, alloc_name)
        if not id:
            self.log.info("Alloc not ready")
            return

        self.log.info('id = %s' % (id))

        id_allocator.id_request(service,
                                "/services/id-vl:id-loop-python[name='%s']" % (service.name),
                                tctx.username,
                                pool_name,
                                "specific_id",
                                False,
                                20)

        id = id_allocator.id_read(tctx.username, root,
                                  pool_name, "specific_id")
        if not id:
            self.log.info("Alloc not ready")
            return
        self.log.info('id = %s' % (id))


class Loop(ncs.application.Application):
    def setup(self):
        self.log.info('Loop RUNNING')
        self.register_service('id-loopspnt-python', LoopService)

    def teardown(self):
        self.log.info('Loop FINISHED')
