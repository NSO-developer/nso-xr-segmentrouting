# -*- mode: python; python-indent: 4 -*-
from ipaddress import IPv4Interface
import ncs
from ncs.application import Service

def interface_to_ip_network(interface):
    addr = interface.ipv4_io_cfg__ipv4_network.addresses.primary
    return IPv4Interface(unicode(addr.address + '/' + addr.netmask)).network


# ------------------------
# SERVICE CALLBACK EXAMPLE
# ------------------------
class ServiceCallbacks(Service):

    # The create() callback is invoked inside NCS FASTMAP and
    # must always exist.
    @Service.create
    def cb_create(self, tctx, root, service, proplist):
        self.log.info('Service create(service=', service._path, ')')

        connections = dict()
        def delete_interface((device_name, interface_name)):
            if device_name in service.router:
                device = root.devices.device[device_name]
                ldp_interfaces = device.config.mpls_ldp_cfg__mpls_ldp.\
                                 default_vrf.interfaces.interface
                if interface_name in ldp_interfaces:
                    del ldp_interfaces[interface_name]

        def get_interfaces(device_name):
            return root.devices.device[device_name].config.\
                ifmgr_cfg__interface_configurations.interface_configuration

        for current_service in root.services.disable_ldp__disable_ldp:
            for router in current_service.router:
                for interface in get_interfaces(router.device_name):
                    entry = (router.device_name, interface.interface_name)
                    ip_network = interface_to_ip_network(interface)
                    if ip_network in connections:
                        connections[ip_network].append(entry)
                    else:
                        connections[ip_network] = [entry]

        for connection in connections.values():
            if len(connection) == 2:
                delete_interface(connection[0])
                delete_interface(connection[1])


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
