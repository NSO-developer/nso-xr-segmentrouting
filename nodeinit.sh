#!/bin/bash

set -e

ncs_cli -u admin >/dev/null <<EOF
request devices fetch-ssh-host-keys
request devices sync-from
configure
set java-vm java-logging logger com.cisco level level-all
set devices global-settings trace raw
load merge payloads/authgroups.cfg
load merge payloads/realdev.cfg
commit
exit
request devices fetch-ssh-host-keys
request devices sync-from
configure
set devices device real2 address 172.27.153.58 state admin-state southbound-locked
commit
EOF