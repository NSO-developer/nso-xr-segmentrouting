package com.cisco.drain;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.tailf.cdb.Cdb;
import com.cisco.sr.namespaces.*;
import com.cisco.drain.namespaces.*;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.util.Properties;
import com.tailf.conf.*;
import com.tailf.navu.*;
import com.tailf.ncs.ns.Ncs;
import com.tailf.dp.*;
import com.tailf.dp.annotations.*;
import com.tailf.dp.proto.*;
import com.tailf.dp.services.*;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;
import org.apache.log4j.Logger;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.cisco.sr.InfraUtils;
import com.cisco.sr.CFConstants;
import com.tailf.maapi.MaapiSchemas.CSCase;

public class drainRFS {
    private static final Logger LOGGER = Logger.getLogger(drainRFS.class);
    private static final String LOOPBACK = "Loopback";
    @Resource(type = ResourceType.CDB, scope = Scope.CONTEXT, qualifier = "reactive")
    public Cdb cdb;

    /**

     * @param context - The current ServiceContext object
     * @param service - The NavuNode references the service node.
     * @param ncsRoot - This NavuNode references the ncs root.
     * @param opaque  - Parameter contains a Properties object.
     *                  This object may be used to transfer
     *                  additional information between consecutive
     *                  calls to the create callback.  It is always
     *                  null in the first call. I.e. when the service
     *                  is first created.
     * @return Properties the returning opaque instance
     * @throws ConfException
     */

    @ServiceCallback(servicePoint="drain-servicepoint",
        callType=ServiceCBType.CREATE)
    public Properties create(ServiceContext context,
                             NavuNode service,
                             NavuNode ncsRoot,
                             Properties opaque)
                             throws ConfException {

	if (opaque == null) {
	    opaque = new Properties();
	}
	String servicePath = null;
	try {

	    servicePath = service.getKeyPath();
	    LOGGER.info("Deploy start drain " + servicePath);
	    final String srName = service.leaf("name").valueAsString();
	    LOGGER.info("Drain Service Name :"+srName);

	    String isisInfra = InfraUtils.getInfraNodeValue(ncsRoot,"instance-name");
	   
	    Template myTemplate = new Template(context, "drain-template");
	    TemplateVariables myVars = new TemplateVariables();


	    for(NavuNode dev : service.list("router")) {
		String instName = isisInfra;
		String devName = dev.leaf("device-name").valueAsString();
		myVars.putQuoted("DEVICE", devName);

		NavuContainer instancePreference = dev.container("instance-name-preference");
		if(!isSelected(instancePreference,"instance-name-choice",sr._use_sr_infrastructure_)){
                    NavuContainer instance = instancePreference.container("custom-instance");
                    String instanceName = instance.leaf("instance-name").valueAsString();
                    if(instanceName!=null && instanceName.equals("")){
                        throw new ConfException("Instance Name cannot be empty");
                    }
		    instName = instanceName;
                    LOGGER.info(String.format("Custom Instance-name %s for device %s ",instanceName,devName));
                }
		LOGGER.info(" ISIS Instance Name :"+instName);
		myVars.putQuoted("INSTANCE-NAME", instName);
		myTemplate.apply(service, myVars);

	    }
	} catch (Exception e) {
	    throw new DpCallbackException(e.getMessage(), e);
	}
	LOGGER.info("Deploy drain end");
	return opaque;
    }


    /**
     *
     */
    private boolean isSelected(NavuContainer dev, String parent, String child) throws ConfException {
        CSCase choiceCase = dev.getSelectedCase(parent);
        if (choiceCase == null) {
            LOGGER.error("No "+parent+"  has Been Selected. ");
            throw new NavuException("No "+parent+" has been selected! This is mandatory ");
        }
        String selectedChoice = choiceCase.getTag();
        if(selectedChoice.equalsIgnoreCase(child))
            return true;

        return false;
    }

    /**
     * Init method for selftest action
     */
    @ActionCallback(callPoint="drain-self-test", callType=ActionCBType.INIT)
      public void init(DpActionTrans trans) throws DpCallbackException {
    }

    /**
     * Selftest action implementation for service
     */
    @ActionCallback(callPoint="sr-self-test", callType=ActionCBType.ACTION)
    public ConfXMLParam[] selftest(DpActionTrans trans, ConfTag name,
                                   ConfObject[] kp, ConfXMLParam[] params)
    throws DpCallbackException {
        try {
            // Refer to the service yang model prefix
            String nsPrefix = "sr";
            // Get the service instance key
            String str = ((ConfKey)kp[0]).toString();

          return new ConfXMLParam[] {
              new ConfXMLParamValue(nsPrefix, "success", new ConfBool(true)),
              new ConfXMLParamValue(nsPrefix, "message", new ConfBuf(str))};

        } catch (Exception e) {
            throw new DpCallbackException("self-test failed", e);
        }
    }
}
