all:	fxs
	cd ../netsim && $(MAKE) all
# Include standard NCS examples build definitions and rules
include $(NCS_DIR)/src/ncs/build/include.ncs.mk

NS  = namespaces

SRC = $(wildcard yang/*.yang)
FXS = $(SRC:yang/%.yang=ncsc-out/modules/fxs/%.fxs)
DIRS = ncsc-out ../load-dir
NED_ID_ARG = $(shell [ -x ${NCS_DIR}/support/ned-ncs-ned-id-arg ] && \
               ${NCS_DIR}/support/ned-ncs-ned-id-arg package-meta-data.xml.in)

## Uncomment and patch the line below if you have a dependency to
## other YANG files
# YANGPATH += ../../<pkt-name>/src/yang

NCSCPATH   = $(YANGPATH:%=--yangpath %)

fxs: ../package-meta-data.xml $(DIRS) ncsc-out/.done
.PHONY: fxs

$(DIRS):
	mkdir -p $@

../package-meta-data.xml: package-meta-data.xml.in
	rm -rf $@
	if [ -x ${NCS_DIR}/support/ned-make-package-meta-data ]; then \
	    ${NCS_DIR}/support/ned-make-package-meta-data $<;         \
	else                                                          \
	    cp $< $@;                                                 \
	fi
	chmod -w $@

ncsc-out/.done:	$(SRC)
	$(NCSC) --ncs-compile-bundle yang                        \
                 --ncs-device-dir ncsc-out   \
                 --ncs-device-type netconf  $(NCSCPATH)          \
	               $(NED_ID_ARG) $(NCSC_EXTRA_FLAGS) &&                          \
        cp ncsc-out/modules/fxs/*.fxs ../load-dir;
	for f in `echo ../load-dir/*.fxs`; do \
           n=`basename $$f | sed 's/\.fxs//'` || exit 1; \
        done
	touch -m ncsc-out/.done

clean:
	rm -rf $(DIRS)
	rm -f ../package-meta-data.xml
	cd ../netsim && $(MAKE) clean || true
