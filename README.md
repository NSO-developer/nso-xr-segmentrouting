# Provisioning and Orchestration of Segment Routing using NSO and IOS-XR Devices


---------------------------------------------------------
 Maintaining Code/Repository
---------------------------------------------------------
Author : Krunal Patel

There can be many variations and uses-cases of Segment Routing Applications on different domains. This is an example and you can use it as is or fork a copy and make your changes.

---------------------------------------------------------
Pre-requisites
---------------------------------------------------------

1. NCS Version : 4.6 or greater

2. Java : Sun Microsystems Jdk 1.8 or above

3. Note : Package P3 has been renamed to prouter-ned. It is a iosxr netconf ned. (Date May 30th 2018)

4. All the packages are now under the packages directory. Any addition/update/deletion should be done inside the packages directory. We will get rid of the outside packages name "sr", "sr-ms", etc.


---------------------------------------------------------
Getting Started : Setup Netsim and NCS Environment
---------------------------------------------------------

First Time Setup
 1. make pkg-update
   This will get the required packages.

Next Steps

1. To Setup NSO Environment and netsim (if you dont intend to use netsim, following steps should be still okay expect the fact that you will deploy the services on real devices)
   make all : Execute the Target to compile all the packages and create ncs setup while creating netsim environment. At moment it will create 5 iosxr netsim devices. If you want to add more, modify the Makefile netsim target.

2. To start ncs and netsim
   make netsim-start

3. To Login into NSO
   make cli

4. To stop ncs and netsim
   make netsim-stop

5. To clean everything
   make clean

-----------------------------------------------------------------
Deploying Services
-----------------------------------------------------------------
Please refer to the following CompassCoreTechnicalDocument-UserGuide.pdf in the repo  Document that guides and explains in details on how to deploy services.
https://github.com/NSO-developer/nso-xr-segmentrouting/blob/develop/SP-Validated-Core-NSO-Technical-Document.pdf

