# THIS MAKFILE IS GENERATED

PACKAGES = resource-manager ti-lfa sr-ms prouter-ned disable-ldp sr

NETWORK = 

.PHONY: netsim netsim-clean netsim-start netsim-stop
netsim:

netsim-clean:

netsim-start:

netsim-stop:

.PHONY: packages packages-clean
packages:
	(for i in $(PACKAGES); do \
	        $(MAKE) -C packages/$${i}/src all || exit 1; \
	done)

packages-clean:
	(for i in $(PACKAGES); do \
	        $(MAKE) -C packages/$${i}/src clean || exit 1; \
	done)

