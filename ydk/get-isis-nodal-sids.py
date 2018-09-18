#!/usr/bin/env python
#
# Copyright 2016 Cisco Systems, Inc.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

"""
Read SR nodal-SIDs from Cisco_IOS_XR_clns_isis_oper and print in the 
style of "show isis segment-routing label table." Includes all learned
nodal-SIDs in the ISIS LSDB (not including the locally configured SID).

usage: ./get-isis-nodal-sids.py ssh://cisco:cisco@172.16.1.132:830 1 

positional arguments:
  device         NETCONF device (ssh://user:password@host:port)
  isis_instance	 ISIS instance name

optional arguments:
  --af           ISIS address family 
  --saf		 ISIS SAF
  -h, --help     show this help message and exit
  -v, --verbose  print debugging messages

"""

from argparse import ArgumentParser
from urlparse import urlparse

from ydk.services import CRUDService
from ydk.providers import NetconfServiceProvider
from ydk.models.cisco_ios_xr import Cisco_IOS_XR_clns_isis_oper as xr_clns_isis_oper
from ydk.filters import YFilter
import re
import logging

def process_isis(isis):
    """Process data in isis object."""
    # format string for isis sr label table 
    isis_header = ("\nIS-IS {instance} IS Label Table:\n"
                   "Label          Prefix/Interface\n"
                   "----------     ----------------")
    # format string for isis neighbor row
    isis_row = ("{label:<14} {prefixintf:<16}")

    if isis.instances.instance:
        show_isis_label = str()
    else:
        show_isis_label = "No IS-IS instances found"

    # iterate over all instances
    for instance in isis.instances.instance:
        if show_isis_label:
            show_isis_label += "\n\n"

        show_isis_label += isis_header.format(instance=instance.instance_name)
        label = 0
        srgb_start = int(instance.protocol.srgb_start)
        if (srgb_start):
            # iterate over all neighbors
            for topology in instance.topologies.topology:
                for ipv4_route in topology.ipv4_routes.ipv4_route:
                    for source in ipv4_route.native_status.native_details.primary.source:
                        for nodal_sid in source.nodal_sid:
                            show_isis_label += ("\n" + isis_row.format(label=(srgb_start+int(nodal_sid.sid_value)), 
                                                prefixintf=(source.source_address+"/"+ipv4_route.prefix_length)))
    
    # return formatted string
    return(show_isis_label)


if __name__ == "__main__":
    """Execute main program."""
    parser = ArgumentParser()
    parser.add_argument("-v", "--verbose", help="print debugging messages",
                        action="store_true")
    parser.add_argument("device",
                        help="NETCONF device (ssh://user:password@host:port)")
    parser.add_argument("isis_instance",
                        help="ISIS instance name")
    parser.add_argument("--af",
                        help="ISIS Address Family (ipv4)", default = "ipv4")
    parser.add_argument("--saf",
                        help="ISIS SAF (unicast)", default = "unicast")
    args = parser.parse_args()
    device = urlparse(args.device)
    isis_instance = args.isis_instance
    af_name = args.af
    saf_name = args.saf

    # log debug messages if verbose argument specified
    if args.verbose:
        logger = logging.getLogger("ydk")
        logger.setLevel(logging.INFO)
        handler = logging.StreamHandler()
        formatter = logging.Formatter(("%(asctime)s - %(name)s - "
                                      "%(levelname)s - %(message)s"))
        handler.setFormatter(formatter)
        logger.addHandler(handler)

    # create NETCONF provider
    provider = NetconfServiceProvider(address=device.hostname,
                                      port=device.port,
                                      username=device.username,
                                      password=device.password,
                                      protocol=device.scheme)

    crud = CRUDService()
    
    #Create Top-Level Object
    isis = xr_clns_isis_oper.Isis()

    #Create List Instance() 
    inst = xr_clns_isis_oper.Isis.Instances.Instance()
    inst.instance_name = isis_instance 
    
    #Create Topo,Protocol, IPv4Route, and SRC Nested List Instances 
    topo = xr_clns_isis_oper.Isis.Instances.Instance.Topologies.Topology()
    topo.af_name=af_name
    topo.saf_name=saf_name
    v4r = xr_clns_isis_oper.Isis.Instances.Instance.Topologies.Topology.Ipv4Routes.Ipv4Route()
    src = xr_clns_isis_oper.Isis.Instances.Instance.Topologies.Topology.Ipv4Routes.Ipv4Route.NativeStatus.NativeDetails.Primary.Source()
    proto = xr_clns_isis_oper.Isis.Instances.Instance.Protocol() 

    #Set the Yfilter attribute for src and proto
    v4r.yfilter = YFilter.read
    src.yfilter = YFilter.read
    proto.yfilter = YFilter.read
    
    #Append each of the list instances to their respective parents
    v4r.native_status.native_details.primary.source.append(src)
    topo.ipv4_routes.ipv4_route.append(v4r)
    inst.topologies.topology.append(topo)
    inst.protocol=proto
    isis.instances.instance.append(inst)
    
    #Call the CRUD read on the top-level isis instance
    isis = crud.read(provider, isis)
    
    print(process_isis(isis))  # process object data

    exit()
