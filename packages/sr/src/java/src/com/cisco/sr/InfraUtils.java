package com.cisco.sr;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.LinkedList;
import com.tailf.conf.ConfException;
import com.tailf.navu.NavuException;
import com.tailf.navu.NavuList;
import com.tailf.navu.NavuNode;
import com.tailf.navu.NavuContainer;
import com.cisco.sr.namespaces.infrastructure;
import com.tailf.conf.ConfPath;

/**
 * @author krunpate
 *
 */
public class InfraUtils {
    private InfraUtils() {}

    /**
     * Get campus-fabric NavuNode object from infrastructure model.
     *
     * @param ncsRoot
     * @param cfName
     * @return
     * @throws NavuException
     */
    public static NavuContainer getInfraNode(NavuNode ncsRoot) throws NavuException {
	return ncsRoot.getParent().container(infrastructure.uri).container(infrastructure.prefix,
									   infrastructure._sr_infrastructure_);
    }

    /**
     * Get resource-pool name from infrastructure model.
     *
     * @param fabricInfra
     * @param poolNodeName
     * @return
     * @throws NavuException
     */
    public static String getResourcePoolName(NavuNode infra,String poolName)
	throws NavuException {
	String pool = null;
	for (final NavuNode node : infra.list(poolName)) {
	    // Check for pool exhuastion; pick the next one.
	    pool = node.leaf("name").valueAsString();
	    break;
	}
	return pool;
    }

    public static List<String> getResourcePoolRange(NavuNode ncsRoot,String poolName)
	throws ConfException {
	List<String> ranges = new LinkedList<String>();

	if(poolName == null)
	    throw new NavuException("pool name  is null to get the range ");
	NavuNode idPool = ncsRoot.getParent()
	    .getNavuNode(new ConfPath("/ralloc:resource-pools/idalloc:id-pool{%s}", poolName));
	NavuContainer range = idPool.container("range");
	ranges.add(0, range.leaf("start").valueAsString());
	ranges.add(1, range.leaf("end").valueAsString());
	return ranges;
    }

    /**
     * Get isis,loopback from Infrastructure
     *
     * @param ncsRoot
     * @param  key  key whose value needs to be fetched from infrastructure
     * @return value of the key
     * @throws NavuException
     */
    public static String getInfraNodeValue(NavuNode ncsRoot, String key) throws NavuException {
	String value = null;
	final NavuContainer infra = getInfraNode(ncsRoot);
	if (null != infra.leaf(key).value()) {
	    value = infra.leaf(key).valueAsString();
	}
	if(key.equals("instance-name") && value != null && value.equals(""))
	      throw new NavuException("Router ISIS Instance Name is missing in the sr-infrastructure. Please set it up!!!");
	return value;
    }
}
