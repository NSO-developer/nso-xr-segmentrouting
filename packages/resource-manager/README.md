# Introduction

## General idea
The problem we are trying to solve is that of a generic resource
allocation mechanism that works well with services and in a high
availability (HA) configuration.  We want to be able to connect to
external resource allocators as well as using allocators that are
implemented in Java in NCS.  We want to have different allocators for
different resources.

Since the create in a service code isn't allowed to perform side
effects, ie do callouts, we use the reactive fastmap design pattern.

Furthermore, we want a design where we can swap allocators depending
on the deployment scenario, ie a plug and play design.

We solve this be creating one interface package called
resource-manager, and expect specific resource allocation
implementations to be implemented as separate NCS packages.

Resource manager includes id and ipaddress allocators.

## Reactive Fastmap for Resource Allocation

The basic idea is as follows. A service does not allocate a resource
directly, instead it creates a configuration item stating that it
requests a resource. This request will be written to the DB when the
transaction commits. A CDB-subscriber detects that a resource request
has been added, allocates the resource using some mechanism of its
choice, writes the result to a CDB-oper leaf, and re-deploys the
service.

When re-deployed the service will perform the same allocation request
as the first time, and check the CDB-oper leaf to see if the result is
present. This time the result will be there and the service create
code can proceed.

When the service is deleted the resource allocation request written by
the service will be removed from CDB and the CDB-subscriber will be
notified that the allocation has been removed. It can then release the
resource.

## HA Considerations

It is important that the resource manager will work well in a HA
setup.  This concerns both the resource manager itself, and all
allocator packages.  They must all be ready for failover at any given
time, and they must not try to perform the allocation on more that one
node at any given time.

An allocator must make sure its state is replicated on all failover
nodes, or stored externally. An allocator is free to use CDB-oper for
this purpose.

# Design

We have created a package called resource-manager that can be
augmented with different resource pools, for example id numbers
and ip addresses. Each pool has an `allocation` list where services
are expected to create instances to signal that they request an
allocation. Request parameters are stored in the `request` container
and the allocation response is written in the `response` container.

Since the allocation request may fail the response container contains
a choice where one case is for error and one for success.

Each allocation list entry also contains an `allocating-service`
leaf. This is an instance-identifier that points to the service that
requested the resource. This is the service that will be re-deployed
when the resource has been allocated.

Resource allocation packages should subscribe to several points in
this resource-pool tree.  First, they must detect when a new resource
pool is created or deleted, secondly they must detect when an
allocation request is created or deleted. A package may also augment
the pool definition with additional parameters, for example, an ip
address allocator may wish to add configuration parameters for
defining the available subnets to allocate from, in which case it must
also subscribe to changes to these settings.

## Resource Allocator Data Model

The resource manager data model is defined as:

```
  grouping resource-pool-grouping {
    leaf name {
      tailf:info "Unique name for the pool";
      type string;
    }

    list allocation {
      key id;

      leaf id {
        type string;
      }

      leaf username {
        description
          "Authenticated user for invoking the service";
        type string;
        mandatory true;
      }

      leaf allocating-service {
        tailf:info "Instance identifier of service that owns resouce";
        type instance-identifier;
      }

      container request {
        description
          "When creating a request for a resource the
           implementing package augments here.";
      }

      container response {
        config false;
        tailf:cdb-oper {
          tailf:persistent true;
        }
        choice response-choice {
          case error {
            leaf error {
              type string;
            }
          }
          case ok {
            // The implementing package augments here
          }
        }
      }
    }
  }
```

## HA Considerations

There are two things we need to consider - the allocator state needs
to be replicated, and the allocation needs only to be performed on one
node.

The easiest way to replicate the state is to write it into CDB-oper and let CDB
perform the replication. This is what we do in the ipaddress-allocator.

We only want the allocator to allocate addresses on the master
node. Since the allocations are written into CDB they will be visible
on both master and slave nodes, and the CDB subscriber will be
notified on both nodes. In this case we only want the allocator on the
master node to perform the allocation. We therefore read the HA mode
leaf from CDB to determine which HA mode the current subscriber is
running in, if none or master, we proceed with the allocation.

# Test

In order to test the allocator there is a simple service and a lux
script that verifies that it works. The lux script is located at
`ipaddress-allocator-project/test/internal/lux`


# Description: Service id-allocator
This is an implementation of an id allocator adhering to the interface
defined by the resource-manager package. Above the interface in
resource-manager it supports a sync feature. It allows multiple
allocations in different pools to be synchronized such that they
get the same id in all pools, for example

    suppose we have three id pools

            id-pool lan1 {
                range {
                    start 20;
                    end   200;
                }
            }
            id-pool lan2 {
                range {
                    start 30;
                    end   200;
                }
            }
            id-pool lan3 {
                range {
                    start 40;
                    end   200;
                }
            }

    and we create a sync allocation in pool lan3, ie

    % set resource-pools id-pool lan3 allocation a request sync true
    % commit
    % run show status resource-pools id-pool lan3
    allocation a {
        response {
            id 40;
        }
    }

    Now, if we create another allocation in pool lan1 with the same
    allocation id a and the sync leaf set to 'true', the allocator
    will try to allocate the same id as it allocated in the lan3 pool
    for the same allocation id, ie

    % set resource-pools id-pool lan2 allocation a request sync true
    % commit
    % run show status resource-pools id-pool lan2
    allocation a {
        response {
            id 40;
        }
    }

    If it cannot allocate the same id it will not allocate any id
    at all and instead report an error

    Similarly, if multiple allocations are done in the same
    transaction using the same allocation id in different pools,
    and the sync leaf set to 'true', then the allocator will
    try to find an allocation id that is available in all pools.
    If it fails it will report an error and not allocate anything.



