
####################################################################
Assumptions
###################################################################
1. Router isis instance-name : We assume that this instance name already exists and is active for a customer deployment as we sought to migrate.
2. Inferfaces under Router isis instance name should be already present. It may not have ti-fla configurations, which nso would tend to configure on them.
3. Loopback used for segment routing is assumed to be already present on the device.
4. Compass Service Models/Configurations that get pushed are platform independent. They should work on all the platforms that run ios-xr.




Note : - NSO get-modifciations command for a serivce on a netsim devices might show "+" on the above assumptions; that does not mean it is a real test. Please do not get confused thinking NSO is supposed to create those or will create those.  The real test should be on a real device showing no "+" sign infront of the above assumptions.

#####################################################################
Device Version and Platform Used for Testing
#####################################################################

RP/0/RP0/CPU0:PE4_Zermat#show version
Thu Jun 21 21:51:49.783 UTC
Cisco IOS XR Software, Version 6.5.1.24I
Copyright (c) 2013-2018 by Cisco Systems, Inc.

Build Information:
 Built By     : ahoang
 Built On     : Tue May 29 13:04:05 PDT 2018
 Build Host   : iox-ucs-025
 Workspace    : /auto/iox-ucs-025-san2/prod/6.5.1.24I.SIT_IMAGE/ncs5500/ws
 Version      : 6.5.1.24I
 Location     : /opt/cisco/XR/packages/
 Label        : 6.5.1.24I


cisco NCS-5500 () processor
System uptime is 1 week 6 days 16 hours 36 minutes

RP/0/RP0/CPU0:PE4_Zermat#show platform
Thu Jun 21 21:52:02.701 UTC
Node              Type                       State             Config state
--------------------------------------------------------------------------------
0/RP0/CPU0        NCS-5502-SE(Active)        IOS XR RUN        NSHUT
0/RP0/NPU0        Slice                      UP
0/RP0/NPU1        Slice                      UP
0/RP0/NPU2        Slice                      UP
0/RP0/NPU3        Slice                      UP
0/RP0/NPU4        Slice                      UP
0/RP0/NPU5        Slice                      UP
0/RP0/NPU6        Slice                      UP
0/RP0/NPU7        Slice                      UP
0/FT0             NC55-2RU-FAN-FW            OPERATIONAL       NSHUT
0/FT1             NC55-2RU-FAN-FW            OPERATIONAL       NSHUT
0/FT2             NC55-2RU-FAN-FW            OPERATIONAL       NSHUT
0/PM1             NC55-2KW-ACFW              OPERATIONAL       NSHUT
0/PM3             NC55-2KW-ACFW              OPERATIONAL       NSHUT
RP/0/RP0/CPU0:PE4_Zermat#exit


######################################################################
Check the service and ned packages status
######################################################################

admin@ncs> show packages package oper-status
                                                                                     PACKAGE
                      PROGRAM                                                        META     FILE
                      CODE     JAVA           BAD NCS  PACKAGE  PACKAGE  CIRCULAR    DATA     LOAD   ERROR
NAME              UP  ERROR    UNINITIALIZED  VERSION  NAME     VERSION  DEPENDENCY  ERROR    ERROR  INFO
------------------------------------------------------------------------------------------------------------
disable-ldp       X   -        -              -        -        -        -           -        -      -
prouter-ned       X   -        -              -        -        -        -           -        -      -
resource-manager  X   -        -              -        -        -        -           -        -      -
sr                X   -        -              -        -        -        -           -        -      -
sr-ms             X   -        -              -        -        -        -           -        -      -
ti-fla            X   -        -              -        -        -        -           -        -      -

[ok][2018-06-21 15:07:00]


######################################################################
Set Resource pool for SR Block Range; Unique ids will be used from this pool to configure  prefix-sid on devices
######################################################################

admin@ncs% set resource-pools id-pool sr-pool range start 16000
Value for 'end' (<unsignedInt>): 17000
[ok][2018-06-18 10:58:35]

[edit]
admin@ncs% commit
Commit complete.
[ok][2018-06-18 10:58:37]

[edit]

######################################################################
Set the Global infrastructe to define isis instance-name , loopback and assign the sr-global-block-pool refrencing the above resource pool
######################################################################

admin@ncs% set sr-infrastructure
Possible completions:
  instance-name         - IS-IS Instance Name
  loopback              - IS-IS loopback for prefix-sid
  sr-global-block-pools - SR Global Block Range
admin@ncs% set sr-infrastructure instance-name CORE loopback 0 sr-global-block-pools sr-pool
[ok][2018-06-18 10:58:51]

[edit]
admin@ncs% commit
Commit complete.
[ok][2018-06-18 10:58:52]

[edit]

######################################################################
Lis the global infrastructure
######################################################################

admin@ncs% show sr-infrastructure
sr-global-block-pools sr-pool;
instance-name  CORE;
loopback 0;
[ok][2018-06-18 10:58:56]

[edit]

######################################################################
List the resource pool
######################################################################
admin@ncs% show resource-pools
id-pool sr-pool {
    range {
        start 16000;
        end   17000;
    }
}
[ok][2018-06-18 10:58:59]

[edit]

######################################################################
Configure Segment Routing on a Device P-0. Here P-0 is a netsim device that is brougt up during make netsim target
######################################################################

admin@ncs% set sr
Possible completions:
  User Defined Segment-Routing Instance Name

admin@ncs% set sr Customer1 router
Possible completions:
  P-0  P-1  P-2  P-3  P-4  real2

admin@ncs% set sr Customer1 router P-0
[ok][2018-06-18 11:01:18]

[edit]
admin@ncs% commit
Commit complete.
[ok][2018-06-18 11:01:19]

[edit]

######################################################################
Show What Changes have our Customer1 Segment Routing Service has done on the devices
######################################################################

admin@ncs> request sr Customer1 get-modifications
cli {
    local-node {
        data  devices {
                   device P-0 {
                       config {
                           clns-isis-cfg:isis {
                               instances {
              +                    instance CORE {
              +                        srgb {
              +                            lower-bound 16000;
              +                            upper-bound 17000;
              +                        }
              +                        afs {
              +                            af ipv4 unicast {
              +                                af-data {
              +                                    segment-routing {
              +                                        mpls ldp;
              +                                    }
              +                                    mpls {
              +                                        router-id {
              +                                            interface-name Loopback0;
              +                                        }
              +                                        level {
              +                                            level2 true;
              +                                        }
              +                                    }
              +                                }
              +                            }
              +                        }
              +                        interfaces {
              +                            interface Loopback0 {
              +                                interface-afs {
              +                                    interface-af ipv4 unicast {
              +                                        interface-af-data {
              +                                            prefix-sid {
              +                                                type absolute;
              +                                                value 16000;
              +                                                php enable;
              +                                                explicit-null disable;
              +                                                nflag-clear disable;
              +                                            }
              +                                        }
              +                                    }
              +                                }
              +                            }
              +                        }
              +                    }
                               }
                           }
                       }
                   }
               }
               resource-pools {
                   id-pool sr-pool {
              +        allocation Customer1-P-0 {
              +            username admin;
              +            allocating-service /sr[name='Customer1'];
              +            request {
              +                sync false;
              +            }
              +        }
                   }
               }

    }
}
[ok][2018-06-18 11:01:42]

######################################################################
Lets add one more device to our Segment Routing Service instance Customer1
######################################################################

admin@ncs> configure
Entering configuration mode private
[ok][2018-06-18 11:01:47]

[edit]
admin@ncs% set sr Customer1 router
Possible completions:
  P-0  P-1  P-2  P-3  P-4  real2
admin@ncs% set sr Customer1 router P-1
[ok][2018-06-18 11:02:00]

[edit]
admin@ncs% show | compare
 sr Customer1 {
+    router P-1 {
+    }
 }
[ok][2018-06-18 11:02:03]

[edit]
admin@ncs% commit
Commit complete.
[ok][2018-06-18 11:02:08]

[edit]
admin@ncs% exit
[ok][2018-06-18 11:02:10]


######################################################################
Show the changes done by the service instance on the device P-0 and P-1
######################################################################

admin@ncs> request sr Customer1 get-modifications
cli {
    local-node {
        data  devices {
                   device P-0 {
                       config {
                           clns-isis-cfg:isis {
                               instances {
              +                    instance CORE {
              +                        srgb {
              +                            lower-bound 16000;
              +                            upper-bound 17000;
              +                        }
              +                        afs {
              +                            af ipv4 unicast {
              +                                af-data {
              +                                    segment-routing {
              +                                        mpls ldp;
              +                                    }
              +                                    mpls {
              +                                        router-id {
              +                                            interface-name Loopback0;
              +                                        }
              +                                        level {
              +                                            level2 true;
              +                                        }
              +                                    }
              +                                }
              +                            }
              +                        }
              +                        interfaces {
              +                            interface Loopback0 {
              +                                interface-afs {
              +                                    interface-af ipv4 unicast {
              +                                        interface-af-data {
              +                                            prefix-sid {
              +                                                type absolute;
              +                                                value 16000;
              +                                                php enable;
              +                                                explicit-null disable;
              +                                                nflag-clear disable;
              +                                            }
              +                                        }
              +                                    }
              +                                }
              +                            }
              +                        }
              +                    }
                               }
                           }
                       }
                   }
                   device P-1 {
                       config {
                           clns-isis-cfg:isis {
                               instances {
              +                    instance CORE {
              +                        srgb {
              +                            lower-bound 16000;
              +                            upper-bound 17000;
              +                        }
              +                        afs {
              +                            af ipv4 unicast {
              +                                af-data {
              +                                    segment-routing {
              +                                        mpls ldp;
              +                                    }
              +                                    mpls {
              +                                        router-id {
              +                                            interface-name Loopback0;
              +                                        }
              +                                        level {
              +                                            level2 true;
              +                                        }
              +                                    }
              +                                }
              +                            }
              +                        }
              +                        interfaces {
              +                            interface Loopback0 {
              +                                interface-afs {
              +                                    interface-af ipv4 unicast {
              +                                        interface-af-data {
              +                                            prefix-sid {
              +                                                type absolute;
              +                                                value 16001;
              +                                                php enable;
              +                                                explicit-null disable;
              +                                                nflag-clear disable;
              +                                            }
              +                                        }
              +                                    }
              +                                }
              +                            }
              +                        }
              +                    }
                               }
                           }
                       }
                   }
               }
               resource-pools {
                   id-pool sr-pool {
              +        allocation Customer1-P-0 {
              +            username admin;
              +            allocating-service /sr[name='Customer1'];
              +            request {
              +                sync false;
              +            }
              +        }
              +        allocation Customer1-P-1 {
              +            username admin;
              +            allocating-service /sr[name='Customer1'];
              +            request {
              +                sync false;
              +            }
              +        }
                   }
               }

    }
}



######################################################################
 Configure TI-FLA on the same devices
######################################################################

[ok][2018-06-18 11:02:12]
admin@ncs> configure
Entering configuration mode private
[ok][2018-06-18 11:02:19]

[edit]
admin@ncs% set ti-lfa
Possible completions:
  Unique service id
admin@ncs% set ti-lfa Customer1-tifla
Possible completions:
  address-family - ISIS Address Family for TI-LFA.
  router         - Core Node Device List
admin@ncs% set ti-lfa Customer1-tifla address-family ipv4 router
Possible completions:
  P-0  P-1  P-2  P-3  P-4  real2
admin@ncs% set ti-lfa Customer1-tifla address-family ipv4 router P-0
[ok][2018-06-18 11:02:35]

[edit]
admin@ncs% commit
Aborted: Exception in callback: No Interfaces Have Been Selected
[error][2018-06-18 11:02:36]

[edit]
admin@ncs% set ti-lfa Customer1-tifla address-family ipv4 router P-0
Possible completions:
  all-interfaces   - All interfaces will be applied ti-fla configuration
  select-interface - Name the interfaces to apply ti-fla configuration
admin@ncs% set ti-lfa Customer1-tifla address-family ipv4 router P-0 all-interfaces
[ok][2018-06-18 11:02:41]

[edit]
admin@ncs% commit
Commit complete.
[ok][2018-06-18 11:02:43]

[edit]
admin@ncs% exit
[ok][2018-06-18 11:02:59]


######################################################################
See the modifications done by the service ti-fla. The netsim device only had one interface under router isis section, which it then went ahead and applied the config. Ideally netsim device will not have
any interfaces. The interface is here because of the previous sr configuration which resulted into addition of Loopback. Real Test should be done on the Real Devices that has
couple to many interfaces under the router isis instance.  Anything + after the interface should be the real configuration that will result.
######################################################################


admin@ncs> request ti-lfa Customer1-tifla get-modifications
cli {
    local-node {
        data  devices {
                   device P-0 {
                       config {
                           clns-isis-cfg:isis {
                               instances {
                                   instance CORE {
                                       interfaces {
                                           interface Loopback0 {
                                               interface-afs {
                                                   interface-af ipv4 unicast {
                                                       interface-af-data {
                                                           interface-frr-table {
                                                               frr-types {
              +                                                    frr-type not-set {
              +                                                        type per-prefix;
              +                                                    }
                                                               }
                                                               frrtilfa-types {
              +                                                    frrtilfa-type not-set {
              +                                                    }
                                                               }
                                                           }
                                                       }
                                                   }
                                               }
                                           }
                                       }
                                   }
                               }
                           }
                       }
                   }
               }

    }
}
[ok][2018-06-18 11:03:06]



######################################################################
Let's add one more device to ti-fla and in this case we will pick interfaces on which we will apply the config. Remember P-1 is a netsim device and it does not have any interfaces. What ever interfaces we specify here will be seen as they were created (+) which will not be the case on the real device. Anything + after the interface should be the real configuration that will result.
######################################################################

admin@ncs> configure
Entering configuration mode private
[ok][2018-06-18 11:03:12]

[edit]
admin@ncs% set ti-lfa Customer1-tifla address-family ipv4 router P-1
Possible completions:
  all-interfaces   - All interfaces will be applied ti-fla configuration
  select-interface - Name the interfaces to apply ti-fla configuration
admin@ncs% set ti-lfa Customer1-tifla address-family ipv4 router P-1 select-interface
Possible completions:
  Bundle-Ether  FortyGigabitEthernet  GigabitEthernet  HundredGigabitEthernet  TenGigabitEthernet
admin@ncs% set ti-lfa Customer1-tifla address-family ipv4 router P-1 select-interface Bundle-Ether 01
[ok][2018-06-18 11:03:35]

[edit]
admin@ncs% set ti-lfa Customer1-tifla address-family ipv4 router P-1 select-interface TenGigabitEthernet 0/0/0
[ok][2018-06-18 11:03:41]

[edit]
admin@ncs% set ti-lfa Customer1-tifla address-family ipv4 router P-1 select-interface GigabitEthernet 1/0/1
[ok][2018-06-18 11:03:54]

[edit]
admin@ncs% commit
Commit complete.
[ok][2018-06-18 11:03:56]

[edit]
admin@ncs% exit
[ok][2018-06-18 11:03:57]
admin@ncs> request ti-lfa Customer1-tifla get-modifications
cli {
    local-node {
        data  devices {
                   device P-0 {
                       config {
                           clns-isis-cfg:isis {
                               instances {
                                   instance CORE {
                                       interfaces {
                                           interface Loopback0 {
                                               interface-afs {
                                                   interface-af ipv4 unicast {
                                                       interface-af-data {
                                                           interface-frr-table {
                                                               frr-types {
              +                                                    frr-type not-set {
              +                                                        type per-prefix;
              +                                                    }
                                                               }
                                                               frrtilfa-types {
              +                                                    frrtilfa-type not-set {
              +                                                    }
                                                               }
                                                           }
                                                       }
                                                   }
                                               }
                                           }
                                       }
                                   }
                               }
                           }
                       }
                   }
                   device P-1 {
                       config {
                           clns-isis-cfg:isis {
                               instances {
                                   instance CORE {
                                       interfaces {
              +                            interface Bundle-Ether01 {
              +                                interface-afs {
              +                                    interface-af ipv4 unicast {
              +                                        interface-af-data {
              +                                            interface-frr-table {
              +                                                frr-types {
              +                                                    frr-type not-set {
              +                                                        type per-prefix;
              +                                                    }
              +                                                }
              +                                                frrtilfa-types {
              +                                                    frrtilfa-type not-set;
              +                                                }
              +                                            }
              +                                        }
              +                                    }
              +                                }
              +                            }
              +                            interface GigabitEthernet1/0/1 {
              +                                interface-afs {
              +                                    interface-af ipv4 unicast {
              +                                        interface-af-data {
              +                                            interface-frr-table {
              +                                                frr-types {
              +                                                    frr-type not-set {
              +                                                        type per-prefix;
              +                                                    }
              +                                                }
              +                                                frrtilfa-types {
              +                                                    frrtilfa-type not-set;
              +                                                }
              +                                            }
              +                                        }
              +                                    }
              +                                }
              +                            }
              +                            interface TenGigabitEthernet0/0/0 {
              +                                interface-afs {
              +                                    interface-af ipv4 unicast {
              +                                        interface-af-data {
              +                                            interface-frr-table {
              +                                                frr-types {
              +                                                    frr-type not-set {
              +                                                        type per-prefix;
              +                                                    }
              +                                                }
              +                                                frrtilfa-types {
              +                                                    frrtilfa-type not-set;
              +                                                }
              +                                            }
              +                                        }
              +                                    }
              +                                }
              +                            }
                                       }
                                   }
                               }
                           }
                       }
                   }
               }

    }
}
