# -*- mode: python; python-indent: 4 -*-
from ipaddress import IPv4Interface
import ncs
from ncs.application import Service

def interface_to_ip_network(interface):
    addr = interface.ipv4_io_cfg__ipv4_network.addresses.primary
    return IPv4Interface(unicode(addr.address + '/' + addr.netmask)).network

def get_interfaces(root, device_name):
    return root.devices.device[device_name].config.\
        ifmgr_cfg__interface_configurations.interface_configuration


# ------------------------
# SERVICE CALLBACK EXAMPLE
# ------------------------
class ServiceCallbacks(Service):

    # The create() callback is invoked inside NCS FASTMAP and
    # must always exist.
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        ip_networks = set(
            interface_to_ip_network(interface)
            for other_service in root.services.disable_ldp__disable_ldp
            if other_service.name != service.name
            for router in other_service.router
            for interface in get_interfaces(root, router.device_name)
        )

        for router in service.router:
            ldp_interfaces = root.devices.device[router.device_name]\
                .config.mpls_ldp_cfg__mpls_ldp.default_vrf.interfaces.interface
            for interface in get_interfaces(root, router.device_name):
                ip_network = interface_to_ip_network(interface)
                if ip_network in ip_networks: #Fast set operation
                    if interface.interface_name in ldp_interfaces:
                        del ldp_interfaces[interface.interface_name]
                else:
                    ip_networks.add(ip_network)



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
        self.register_service('disable-ldp', ServiceCallbacks)

        # If we registered any callback(s) above, the Application class
        # took care of creating a daemon (related to the service/action point).

        # When this setup method is finished, all registrations are
        # considered done and the application is 'started'.

    def teardown(self):
        # When the application is finished (which would happen if NCS went
        # down, packages were reloaded or some error occurred) this teardown
        # method will be called.

        self.log.info('Main FINISHED')
