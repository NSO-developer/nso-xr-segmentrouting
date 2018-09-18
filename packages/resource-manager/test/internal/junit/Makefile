all: test

test:
	ant -lib $(DEV_SUPPORT_HOME)/tools/lib/test junit
	ant -lib $(DEV_SUPPORT_HOME)/tools/lib/test report

clean:
	rm -rf reports classes codecoverage
