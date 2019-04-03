# Include standard NCS build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

PACKAGES = prouter-ned \
	sr \
	sr-ms \
	ti-lfa \
	disable-ldp \
	drain \
	sr-demo

# Packages that are not version controlled, i.e. not local
REMOTE_PACKAGES = resource-manager


.PHONY: netsim netsim-clean netsim-start netsim-stop

ncs-setup:
	if [ ! -d ncs-cdb ]; then mkdir ncs-cdb; fi
	if [ ! -d state ]; then mkdir state; fi
	if [ ! -d logs ]; then mkdir logs; fi

netsim:
	if [ ! -d netsim ]; then mkdir netsim; fi

	if [ ! -d ./netsim/P- ]; then \
		ncs-netsim create-network packages/prouter-ned 5 P- --dir ./netsim; \
		ncs-netsim --dir ./netsim ncs-xml-init > ./ncs-cdb/netsim_devices_init.xml; \
	fi

netsim-clean:
	-$(MAKE) netsim-stop
	rm -rf ./netsim
	rm -f ./ncs-cdb/netsim_devices_init.xml

netsim-start:
	ncs-netsim start --dir ./netsim
	ncs -c ncs.conf
	./nodeinit.sh

netsim-stop:
	ncs --stop
	-ncs-netsim stop --dir ./netsim

cli:
	ncs_cli -u admin

.PHONY: packages packages-clean

packages:

	(for i in $(REMOTE_PACKAGES) $(PACKAGES); do \
	        $(MAKE) -C packages/$${i}/src all || exit 1; \
	done)

packages-clean:
	(for i in $(PACKAGES); do \
	     $(MAKE) -C packages/$${i}/src clean || exit 1; \
	done)

.PHONY: all start starti stop clean db-clean demo-restart
all:
	$(MAKE) packages
	$(MAKE) ncs-setup
	$(MAKE) netsim

start: stop netsim-start


starti: stop netsim-start
	ncs -i

stop: netsim-stop


clean:  packages-clean
	-$(MAKE) netsim-clean
	if [  -d ncs-cdb ]; then rm -rf ncs-cdb; fi
	if [  -d state ]; then rm -rf  state; fi
	if [  -d logs ]; then rm -rf logs; fi

db-clean:
	rm -rf ./state/* ./ncs-cdb/*.cdb .//logs/*; \


### HERE FOLLOWS SOME HANDY GIT TARGETS WHEN WORKING WITH REMOTE REPOS
###
.PHONY: gstat glog
gstat:
	@for i in `grep GIT_PACKAGES .build-meta 2> /dev/null | cut -d= -f2`; \
	  do \
	    echo ""; \
	    echo "--- $$i ---"; \
	    (cd "packages/$$i"; \
	    git status -uno --ignore-submodules;); \
	  done

# Set N=<n> on the command line for more log output.
N = 1
glog:
	@for i in `grep GIT_PACKAGES .build-meta 2> /dev/null | cut -d= -f2`; \
	  do \
	    echo ""; \
	    echo "--- $$i ---"; \
	    (cd "packages/$$i"; \
	     git --no-pager log -n "$(N)";); \
	    echo ""; \
	  done

.PHONY: pkg-update cleaninit luxcleaninit
pkg-update:
	(for i in $(REMOTE_PACKAGES); do \
		rm -rf packages/$${i}; \
	done)
	ncs-project update -v -y
	rm -rf packages/*.tar.gz

# Convenient/familiar alias for demo-restart
luxcleaninit: demo-restart
