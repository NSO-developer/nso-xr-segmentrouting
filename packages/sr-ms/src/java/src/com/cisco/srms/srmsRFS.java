package com.cisco.srms;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;

import org.apache.log4j.Logger;

import com.cisco.sr.CFConstants;
import com.cisco.sr.InfraUtils;
import com.cisco.sr.namespaces.sr;
import com.tailf.cdb.Cdb;
import com.tailf.conf.ConfBool;
import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfKey;
import com.tailf.conf.ConfObject;
import com.tailf.conf.ConfPath;
import com.tailf.conf.ConfTag;
import com.tailf.conf.ConfXMLParam;
import com.tailf.conf.ConfXMLParamValue;
import com.tailf.dp.DpActionTrans;
import com.tailf.dp.DpCallbackException;
import com.tailf.dp.annotations.ActionCallback;
import com.tailf.dp.annotations.ServiceCallback;
import com.tailf.dp.proto.ActionCBType;
import com.tailf.dp.proto.ServiceCBType;
import com.tailf.dp.services.ServiceContext;
import com.tailf.maapi.MaapiSchemas.CSCase;
import com.tailf.navu.NavuContainer;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuLeaf;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;
import com.tailf.ncs.annotations.Resource;
import com.tailf.ncs.annotations.ResourceType;
import com.tailf.ncs.annotations.Scope;
import com.tailf.ncs.template.Template;
import com.tailf.ncs.template.TemplateVariables;

public class srmsRFS {
	private static final Logger LOGGER = Logger.getLogger(srmsRFS.class);
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

	@ServiceCallback(servicePoint="srms-servicepoint",
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
			LOGGER.info("Deploy start srms " + servicePath);
			final String srName = service.leaf("name").valueAsString();
			LOGGER.info("Srms Service Name :"+srName);

			String isisInfra = InfraUtils.getInfraNodeValue(ncsRoot,"instance-name");


			Template myTemplate = new Template(context, "sr-ms-template");
			Template enableTemplate = new Template(context,"enable-srms");
			TemplateVariables myVars = new TemplateVariables();

			String[] keys = {"device-name","address-family","ipv4-address","prefix-length","first-sid-value","number-of-allocated-sids"};
			for(NavuNode dev : service.list("router")) {
				String instName = isisInfra;
				String devName = getNotNullable("device-name",dev);
				Arrays.stream(keys).map(s -> {
					try {
						return s+":"+getNotNullable(s,dev);
					} catch (ConfException e) {
						throw new RuntimeException(e.getMessage());
					}

				}).forEach(s -> myVars.putQuoted(s.split(":")[0], s.split(":")[1]));

				//	myVars.entrySet().stream().map(s -> s.getKey()+"-"+s.getValue()).forEach(System.out::println);

				NavuContainer instancePreference = dev.container("instance-name-preference");
				if(!isSelected(instancePreference,"instance-name-choice",sr._use_sr_infrastructure_)){
					NavuContainer instance = instancePreference.container("custom-instance");
					String instanceName = getNotNullable("instance-name",instance);
					instName = instanceName;
					LOGGER.info(String.format("Custom Instance-name %s for device %s ",instanceName,devName));
				}
				LOGGER.info(" ISIS Instance Name :"+instName);
				myVars.putQuoted("instance-name", instName);
				myTemplate.apply(service, myVars);
				enableTemplate.apply(service, myVars);
			}
		} catch (Exception e) {
			throw new DpCallbackException(e.getMessage(), e);
		}
		LOGGER.info("Deploy srms end");
		return opaque;
	}


	private String getNotNullable(String name,NavuNode container) throws ConfException{
		Optional<String> leaf = Optional.ofNullable(container.leaf(name).valueAsString());
		leaf.filter(s -> !s.equals("null")).orElseThrow(() -> new ConfException(String.format("Missing %s , not specified ",name)));
		return leaf.get();

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
	@ActionCallback(callPoint="srms-self-test", callType=ActionCBType.INIT)
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
