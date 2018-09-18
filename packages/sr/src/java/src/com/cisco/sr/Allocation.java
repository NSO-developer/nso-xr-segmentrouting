package com.cisco.sr;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Objects;

import org.apache.log4j.Logger;

import com.tailf.cdb.Cdb;
import com.tailf.conf.ConfException;
import com.tailf.conf.ConfIPPrefix;
import com.tailf.conf.ConfUInt32;
import com.tailf.dp.services.ServiceContext;
import com.tailf.dp.services.ServiceContextImpl;
import com.tailf.navu.NavuContext;
import com.tailf.navu.NavuNode;
import com.tailf.pkg.idallocator.IdAllocator;
import com.tailf.pkg.ipaddressallocator.IPAddressAllocator;
import com.tailf.pkg.resourcemanager.ResourceException;


public class Allocation {

  private static Logger LOGGER = Logger.getLogger(Allocation.class);

  public class AllocationId {
    public String type;
    public String pool;
    public String allocationId;

    public AllocationId(String type, String pool, String allocationId) {
      this.type = type;
      this.pool = pool;
      this.allocationId = allocationId;
    }

    public String tableFormat() {
      return String.format("%s %-30s %-30s", type, pool, allocationId);
    }

    public boolean isReady() {
      return allocations.get(this);
    }

    @Override
    public int hashCode() {
      return Objects.hash(type, pool, allocationId);
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      AllocationId other = (AllocationId) obj;
      if (!getOuterType().equals(other.getOuterType()))
        return false;
      if (allocationId == null) {
        if (other.allocationId != null)
          return false;
      } else if (!allocationId.equals(other.allocationId))
        return false;
      if (pool == null) {
        if (other.pool != null)
          return false;
      } else if (!pool.equals(other.pool))
        return false;
      if (type == null) {
        if (other.type != null)
          return false;
      } else if (!type.equals(other.type))
        return false;
      return true;
    }

    private Allocation getOuterType() {
      return Allocation.this;
    }

  }

  private Cdb cdb;
  private NavuNode service;
  private String user;
  private NavuContext context;
  private boolean error;
  private LinkedHashMap<AllocationId, Boolean> allocations;

  public Allocation(ServiceContext context, NavuNode service, Cdb cdb) {
    this.service = service;
    this.context = service.context();
    this.cdb = cdb;
    this.error = false;
    this.allocations = new LinkedHashMap<AllocationId, Boolean>();

    ServiceContextImpl contextImpl = (ServiceContextImpl) context;
    this.user = contextImpl.getCurrentDpTrans().getUserInfo().getUserName();
  }

  public AllocationId allocateSubnet(String pool, String allocationId, int prefixLength)
      throws ResourceException, ConfException, IOException {
    LOGGER.debug(String.format("allocateSubnet: pool:%s: allocationId:%s:", pool, allocationId));
    AllocationId id = new AllocationId("ip", pool, allocationId);
    IPAddressAllocator.subnetRequest(service, pool, user, prefixLength, allocationId);
    allocations.put(id, Boolean.FALSE);
    try {
      boolean ready = IPAddressAllocator.responseReady(context, cdb, pool, allocationId);
      allocations.put(id, ready);
    } catch (ResourceException re) {
      if (!re.getMessage().equals("Pool does not exist")) {
        error = true;
        throw re;
      }
    }
    return id;
  }

  public AllocationId allocateId(String pool, String allocationId)
      throws ResourceException, ConfException, IOException {

    LOGGER.debug(String.format("allocateId: pool:%s: allocationId:%s:", pool, allocationId));

    AllocationId id = new AllocationId("id", pool, allocationId);
    IdAllocator.idRequest(service, pool, user, allocationId, false, -1L);
    allocations.put(id, Boolean.FALSE);
    try {
      boolean ready = IdAllocator.responseReady(context, cdb, pool, allocationId);
      allocations.put(id, ready);
    } catch (ResourceException re) {
      if (!re.getMessage().equals("Pool does not exist")) {
        error = true;
        throw re;
      }
    }
    return id;
  }

  public ConfIPPrefix getSubnet(String pool, String allocationId)
      throws ResourceException, ConfException, IOException {
    return IPAddressAllocator.subnetRead(cdb, pool, allocationId);
  }

  public ConfIPPrefix getSubnet(AllocationId id)
      throws ResourceException, ConfException, IOException {
    return getSubnet(id.pool, id.allocationId);
  }

  public long getId(String pool, String allocationId)
      throws ResourceException, ConfException, IOException {
    ConfUInt32 val = (ConfUInt32) IdAllocator.idRead(cdb, pool, allocationId);
    return val.longValue();
  }

  public long getId(AllocationId id) throws ResourceException, ConfException, IOException {
    return getId(id.pool, id.allocationId);
  }

  public boolean readyId(String pool, String allocationId) {
    return allocations.get(new AllocationId("id", pool, allocationId));
  }

  public boolean readySubnet(String pool, String allocationId) {
    return allocations.get(new AllocationId("ip", pool, allocationId));
  }

  public boolean ready(AllocationId id) {
    return allocations.get(id);
  }

  public boolean error() {
    return error;
  }

  public String toTable() {
    StringBuilder sb = new StringBuilder();
    sb.append("   Pool                           Allocation ID                  Done\n");
    allocations.forEach((k, v) -> sb.append(k.tableFormat() + " " + v.toString() + "\n"));
    return sb.toString();
  }
}
