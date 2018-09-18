package com.tailf.pkg.nsoutil;

import com.tailf.conf.ConfBuf;
import com.tailf.conf.ConfDatetime;
import com.tailf.conf.ConfIdentityRef;
import com.tailf.conf.ConfException;
import com.tailf.navu.NavuException;
import java.io.IOException;
import com.tailf.ncs.alarmman.common.ManagedDevice;
import com.tailf.ncs.alarmman.common.ManagedObject;
import com.tailf.ncs.alarmman.common.PerceivedSeverity;
import com.tailf.ncs.alarmman.producer.AlarmSink;

import org.apache.log4j.Logger;

public abstract class Pool {
    private static final Logger LOGGER = Logger.getLogger(Pool.class);
    protected boolean alarmsEnabled = false;
    protected boolean exhaustedAlarmRaised = false;
    protected boolean lowThresholdAlarmRaised = false;
    private String pool;
    private ConfIdentityRef emptyAlarm;
    private ConfIdentityRef lowThresholdAlarm;

    protected int threshold;

    public Pool (String pool, ConfIdentityRef emptyAlarm,
                 ConfIdentityRef lowThresholdAlarm,
                 boolean alarmsEnabled,
                 int threshold) {
        this.pool = pool;
        this.emptyAlarm = emptyAlarm;
        this.lowThresholdAlarm = lowThresholdAlarm;
        this.alarmsEnabled = alarmsEnabled;
        this.threshold = threshold;
    }

    protected void raiseAlarm(String pool, String reason,
                           ConfIdentityRef alarmType) {
        if (alarmsEnabled) {
            PerceivedSeverity severity = PerceivedSeverity.MAJOR;
            updateAlarm(pool, reason, alarmType, severity);
        }
    }

    protected void clearAlarm(String pool, String reason,
                                  ConfIdentityRef alarmType) {
        PerceivedSeverity severity = PerceivedSeverity.CLEARED;
        updateAlarm(pool, reason, alarmType, severity);
    }

    protected void updateAlarm(String pool, String reason,
                             ConfIdentityRef alarmType,
                             PerceivedSeverity severity) {
        AlarmSink sink = new AlarmSink();

        LOGGER.debug(String.format("Raising alarm for %s", pool));
        ManagedDevice managedDevice = new ManagedDevice("ncs");
        ManagedObject managedObject = new ManagedObject(pool);

        try {
            sink.submitAlarm(managedDevice,
                             managedObject,
                             alarmType,
                             new ConfBuf(""),
                             severity,
                             reason,
                             null, /* No impacted objects */
                             null, /* No related alarms */
                             null, /* No root cause objects */
                             ConfDatetime.getConfDatetime());
        } catch (NavuException ne) {
            LOGGER.error(String.format("Error trying to raise alarm %s", ne));
        } catch (ConfException ce) {
            LOGGER.error(String.format("Error trying to raise alarm %s", ce));
        } catch (IOException ce) {
            LOGGER.error(String.format("Error trying to raise alarm %s", ce));
        }
    }

    public void enableAlarms() {
        this.alarmsEnabled = true;
        if (isEmpty()) {
            raiseEmptyAlarm();
        } else if (isLowThresholdReached()) {
            raiseLowThresholdAlarm();
        }
    }

    public void disableAlarms() {
        this.alarmsEnabled = false;
        clearAllAlarms();
    }

    public void setThreshold(int threshold) {
        this.threshold = threshold;
        updateAlarms();
    }

    public synchronized void clearAllAlarms() {
        clearLowThresholdAlarm();
        clearEmptyAlarm();
    }

    protected synchronized void raiseEmptyAlarm() {
        if (alarmsEnabled) {
            try {
                raiseAlarm(pool, "Pool is empty", emptyAlarm);
                exhaustedAlarmRaised = true;
            } catch (Exception e) {
                LOGGER.error("Could not raise alarm. error: ",e);
            }
        }
    }

    protected synchronized void clearEmptyAlarm() {
        if (exhaustedAlarmRaised) {
            try {
                clearAlarm(pool, "Pool is empty", emptyAlarm);
                exhaustedAlarmRaised = false;
            } catch (Exception e) {
                LOGGER.error("Could not lower alarm. error: ",e);
            }
        }
    }

    protected synchronized void raiseLowThresholdAlarm() {
        if (alarmsEnabled) {
            try {
                raiseAlarm(pool, "Pool is getting close to empty",
                           lowThresholdAlarm);
                lowThresholdAlarmRaised= true;
            } catch (Exception e) {
                LOGGER.error("Could not raise alarm. error: ",e);
            }
        }
    }

    protected synchronized void clearLowThresholdAlarm() {
        if (lowThresholdAlarmRaised) {
            try {
                clearAlarm(pool, "Pool is getting close to empty",
                           lowThresholdAlarm);
                lowThresholdAlarmRaised = false;
            } catch (Exception e) {
                LOGGER.error("Could not clear alarm. error: ",e);
            }
        }
    }

    public void updateAlarms() {
        if (lowThresholdAlarmRaised && !isLowThresholdReached()) {
            clearLowThresholdAlarm();
        } else if (!lowThresholdAlarmRaised && isLowThresholdReached()) {
            raiseLowThresholdAlarm();
        }
    }

    protected void reviewAlarms() {
        if (alarmsEnabled) {
            if (!exhaustedAlarmRaised && isEmpty()) {
                raiseEmptyAlarm();
            } else if (!lowThresholdAlarmRaised && isLowThresholdReached()) {
                raiseLowThresholdAlarm();
            }
        }

        if (exhaustedAlarmRaised && !isEmpty()) {
            clearEmptyAlarm();
        } else if (lowThresholdAlarmRaised && !isLowThresholdReached()) {
            clearLowThresholdAlarm();
        }
    }

    public boolean isLowThresholdReached() {
        long availablesLeft = getNumberOfAvailables();
        long threshold = (long) (getTotalSize() * (this.threshold/100f));
        if (availablesLeft > threshold) {
            LOGGER.debug(String.format("There are more availables left %s  %s",
                                       availablesLeft, threshold));
            return false; }
        else {
            LOGGER.debug(String.format("The amount of availables %s is below" +
                                       " %s", availablesLeft,
                                       threshold));
            return true; }
    }

    protected abstract long getNumberOfAvailables();

    protected abstract long getTotalSize();

    protected abstract boolean isEmpty();

}
