package com.cisco.tifla;

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.tailf.cdb.Cdb;
import com.cisco.sr.namespaces.*;
import com.cisco.tifla.namespaces.*;
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

public class tiflaRFS {
    private static final Logger LOGGER = Logger.getLogger(tiflaRFS.class);
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

    @ServiceCallback(servicePoint="tilfa-servicepoint",
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
	    LOGGER.info("Deploy start ti-fla " + servicePath);
	    final String srName = service.leaf("name").valueAsString();
	    LOGGER.info("Service Name :"+srName);
	    final String addressFamily = service.leaf("address-family").valueAsString();
	    LOGGER.info("Address Family :"+addressFamily);

	    final NavuContainer infra = InfraUtils.getInfraNode(ncsRoot);
	    String isisInfra = InfraUtils.getInfraNodeValue(ncsRoot,"instance-name");
	    String loopback = InfraUtils.getInfraNodeValue(ncsRoot,"loopback");
	    LOGGER.info("Found ISIS Instance Name :"+isisInfra);

	    Template myTemplate = new Template(context, "ti-fla-template-frr");
	    TemplateVariables myVars = new TemplateVariables();

	    myVars.putQuoted("ADDRESS-FAMILY",addressFamily);

	    String pat = "^([[a-zA-z]*\\-*]*)([[0-9]*/*]*)";
	    Pattern pattern = Pattern.compile(pat,Pattern.CASE_INSENSITIVE);

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
		myVars.putQuoted("INSTANCE-NAME", instName);

		LOGGER.info("Gathering interface list for device :"+devName);

		NavuContainer interfacePreference = dev.container("interface-preference");

		CSCase choiceCase = interfacePreference.getSelectedCase("interfaces");
		if (choiceCase == null) {
		    LOGGER.error("No Interfaces Have Been Selected. ");
		    throw new NavuException("No Interfaces Have Been Selected ");
		}

		String selectedChoice = choiceCase.getTag();
		if(selectedChoice.equalsIgnoreCase(tiLfa._all_interfaces_)){
		    LOGGER.info(String.format(" All Interfaces under instance-name %s  of the device will be provisioned",instName));
		    // READ FROM DEVICE
		    Set<String> interfaces = getDeviceInterfaces(ncsRoot,devName,instName);
		    if(interfaces == null)
			throw new Exception("Error Reading Interfaces from device :"+devName);
		    if(interfaces.size() == 0)
			LOGGER.info(String.format(" !!! No Interfaces found under router isis %s on device %s",instName,devName));

		    for(String ifc : interfaces){
			Matcher matcher = pattern.matcher(ifc);
			if(matcher.find()) {
			    String iface = matcher.group(1);
			    String intId = matcher.group(2);
			    if(null != iface && !iface.startsWith(LOOPBACK)) {
				LOGGER.info("Selected Interface :"+iface+" "+intId);
				myVars.putQuoted("INTERFACE-NAME", matcher.group(1));
				myVars.putQuoted("INTERFACE-ID",intId);
				myTemplate.apply(service, myVars);
			    }
			}
		    }
		}
		else {
		    // USE FROM USER INPUT
		    for(NavuNode node : interfacePreference.list("select-interface")) {
			String iface = node.leaf("interface-type").valueAsString();
			if(iface!=null && (iface.equals("") || iface.startsWith(LOOPBACK) || iface.startsWith("loopback")))
			    throw new NavuException("Interface Name cannot be empty or Start with Loopback");

			String intId = node.leaf("interface-id").valueAsString();
			if(intId !=null && intId.equals(""))
			    throw new NavuException("Interface Id is not valid");

			LOGGER.info("Selected Interface :"+iface+" "+intId);

			myVars.putQuoted("INTERFACE-NAME", iface);
			myVars.putQuoted("INTERFACE-ID",intId);
			myTemplate.apply(service, myVars);
		    }
		}
	    }

	} catch (Exception e) {
	    throw new DpCallbackException(e.getMessage(), e);
	}
	return opaque;
    }

    private Set<String> getDeviceInterfaces(NavuNode ncsRoot,String deviceName,String isis)
	throws NavuException, ConfException {

	NavuList interfaces = ncsRoot
	    .getNavuNode(new ConfPath(CFConstants.NCS_DEVICE_TREE_PATH +
				      "/config/clns-isis-cfg:isis/instances/instance{%s}/",deviceName,isis)).container("interfaces").list("interface");
	Set<String> set = new HashSet<String>();
	for(NavuNode node : interfaces){
	    String value= node.leaf("interface-name").valueAsString();
	    LOGGER.info("Found interface :"+value);
	    set.add(value);
	}
	return set;
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
    @ActionCallback(callPoint="tifla-self-test", callType=ActionCBType.INIT)
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
