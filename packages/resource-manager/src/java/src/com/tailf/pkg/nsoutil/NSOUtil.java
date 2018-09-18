package com.tailf.pkg.nsoutil;

import java.io.IOException;
import java.net.Socket;
import org.apache.log4j.Logger;

import com.tailf.conf.*;
import com.tailf.maapi.*;
import com.tailf.ncs.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;


public class NSOUtil {
    private static final Logger LOGGER = Logger.getLogger(NSOUtil.class);
    /**
     * Pre-requirement: HA mode is enabled.
     */
    public static boolean isMaster(Maapi maapi, int tid)

        throws ConfException, IOException {

        ConfEnumeration ha_mode_enum =  (ConfEnumeration)
            maapi.getElem(tid, "/tfnm:ncs-state/ha/mode");

        String ha_mode =
            ConfEnumeration.getLabelByEnum(
                   "/tfnm:ncs-state/ha/mode",
                   ha_mode_enum);

        if ("none".equals(ha_mode) ||
              "normal".equals(ha_mode) ||
              "master".equals(ha_mode)) {
            return true;
        }

        // slave or relay-slave
        return false;
    }

    public static boolean isHaEnabled(Maapi maapi, int tid)
        throws ConfException, IOException {

        return maapi.exists(tid, "/tfnm:ncs-state/ha");
    }


    /**
     * Redeploy a service.
     * The service pointed to in <code>path</code> will be re-deployed
     * using one of the actions <code>touch</code>,
     * <code>reactive-re-deploy</code> or <code>re-deploy</code> depending
     * on NSO version used.
     * The redeploy takes place in a new thread making this method safe
     * to call from a CDB subscriber.
     * The action will be called using the user <code>admin</code>
     * and the context <code>system</code>.
     *
     * @param path Path to the service to redeploy.
     */
    public static void redeploy(String path) {
        redeploy(path, "admin");
    }

    /**
     * Redeploy a service.
     * The service pointed to in <code>path</code> will be re-deployed
     * using one of the actions <code>touch</code>,
     * <code>reactive-re-deploy</code> or <code>re-deploy</code> depending
     * on NSO version used.
     * The redeploy takes place in a new thread making this method safe
     * to call from a CDB subscriber.
     * The action will be called using the specified <code>user</code>
     * and the context <code>system</code>.
     *
     * @param path Path to the service to redeploy.
     * @param user Name of the user used for the redeploy session.
     */
    public static void redeploy(String path, String user) {
        Set<ToRedeploy> redeps = new HashSet<ToRedeploy>();
        redeps.add(new ToRedeploy(path, user));
        redeploy(redeps);
    }

    /**
     * Redeploy some services.
     * All services in in the <code>toRedeploy</code> set will be
     * re-deployed using the corresponding <code>user</code>.
     * Each service will be re-deployed using one of the actions
     * <code>touch</code>, <code>reactive-re-deploy</code> or
     * <code>re-deploy</code> depending on NSO version used.
     * The redeploy takes place in a new thread making this method safe
     * to call from a CDB subscriber.
     * The action will be called using the specified user and
     * the context <code>system</code>.
     *
     * @param toRedeploy A Set of ToRedeploy instances.
     */
    public static void redeploy(Set<ToRedeploy> toRedeploy) {
        Redeployer r = new Redeployer(toRedeploy);
        Thread t = new Thread(r);
        t.start();
    }

    private static class Redeployer implements Runnable {
        private Maapi redepMaapi;
        private Socket redepSock;
        private Map<String, List<String> > redeps =
                                        new HashMap<String, List<String> >();
        private String actionFmt;
        private boolean trans = false;

        public Redeployer(Set<ToRedeploy> toRedeploy) {
            try {
                /* set up Maapi socket */
                redepSock = new Socket(NcsMain.getInstance().getNcsHost(),
                                       NcsMain.getInstance().getNcsPort());
                redepMaapi = new Maapi(redepSock);

                /* set up action depending on NSO version */
                if (Conf.LIBVSN >= 0x06020000) {
                    actionFmt = "%s/touch";
                    trans = true;
                }
                else if (Conf.LIBVSN >= 0x06010000) {
                    actionFmt = "%s/reactive-re-deploy";
                }
                else {
                    actionFmt = "%s/re-deploy";
                }

                /* map user -> services */
                for (ToRedeploy item : toRedeploy) {
                    String user = item.getUsername();
                    List<String> l = redeps.get(user);
                    if (l == null) {
                        l = new ArrayList<String>();
                        redeps.put(user, l);
                    }
                    l.add(item.getAllocatingService());
                }

            } catch (Exception e) {
                LOGGER.error("redeployer exception", e);
            }
        }


        public void run() {
            try {
                for (String user : this.redeps.keySet()) {
                    redepMaapi.startUserSession(user,
                                                redepMaapi.getSocket().getInetAddress(),
                                                "system",
                                                new String[] {},
                                                MaapiUserSessionFlag.PROTO_TCP);
                    int tid = -1;

                    if (trans) {
                        tid = redepMaapi.startTrans(Conf.DB_RUNNING, Conf.MODE_READ_WRITE);
                    }

                    for (String path : this.redeps.get(user)) {
                        LOGGER.debug(String.format("re-deploying %s as user %s", path, user));
                        if (tid != -1) {
                            redepMaapi.requestActionTh(tid, new ConfXMLParam[] {},
                                                    String.format(this.actionFmt, path));
                        } else {
                            redepMaapi.requestAction(new ConfXMLParam[] {},
                                                    String.format(this.actionFmt, path));
                        }
                    }

                    if (tid != -1) {
                        redepMaapi.applyTrans(tid, false);
                        redepMaapi.finishTrans(tid);
                    }
                    redepMaapi.endUserSession();
                }
            } catch (Exception e) {
                LOGGER.error("error in re-deploy", e);
            }
            finally {
                try {
                    redepSock.close();
                }
                catch (IOException e) {
                    // ignore
                }
            }
        }
    }
}
