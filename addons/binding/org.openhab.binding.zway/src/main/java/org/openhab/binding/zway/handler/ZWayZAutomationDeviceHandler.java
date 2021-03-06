/**
 * Copyright (c) 2014-2016 by the respective copyright holders.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 */
package org.openhab.binding.zway.handler;

import static org.openhab.binding.zway.ZWayBindingConstants.THING_TYPE_VIRTUAL_DEVICE;

import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingStatusInfo;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.openhab.binding.zway.config.ZWayZAutomationDeviceConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.fh_zwickau.informatik.sensor.model.devices.Device;
import de.fh_zwickau.informatik.sensor.model.devices.DeviceList;

/**
 * The {@link ZWayZAutomationDeviceHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Patrick Hecker - Initial contribution
 */
public class ZWayZAutomationDeviceHandler extends ZWayDeviceHandler {
    public final static ThingTypeUID SUPPORTED_THING_TYPE = THING_TYPE_VIRTUAL_DEVICE;

    private Logger logger = LoggerFactory.getLogger(this.getClass());

    private ZWayZAutomationDeviceConfiguration mConfig = null;

    private class Initializer implements Runnable {

        @Override
        public void run() {
            ZWayBridgeHandler zwayBridgeHandler = getZWayBridgeHandler();
            if (zwayBridgeHandler != null && zwayBridgeHandler.getThing().getStatus().equals(ThingStatus.ONLINE)) {
                ThingStatusInfo statusInfo = zwayBridgeHandler.getThing().getStatusInfo();

                logger.debug("Change Z-Way device status to bridge status: {}", statusInfo.getStatus());

                // Set thing status to bridge status
                updateStatus(statusInfo.getStatus(), statusInfo.getStatusDetail(), statusInfo.getDescription());

                // Add all available channels
                DeviceList deviceList = getZWayBridgeHandler().getZWayApi().getDevices();
                if (deviceList != null) {
                    logger.debug("Z-Way devices loaded ({} virtual devices)", deviceList.getDevices().size());

                    // https://community.openhab.org/t/oh2-major-bug-with-scheduled-jobs/12350/11
                    // If any execution of the task encounters an exception, subsequent executions are
                    // suppressed. Otherwise, the task will only terminate via cancellation or
                    // termination of the executor.
                    try {
                        Device device = deviceList.getDeviceById(mConfig.getDeviceId());

                        if (device != null) {
                            logger.debug("Add channel for virtual device: {}", device.getMetrics().getTitle());

                            addDeviceAsChannel(device);

                            // starts polling job and register all linked items
                            completeInitialization();
                        } else {
                            logger.warn("Initializing Z-Way device handler failed (virtual device not found): {}",
                                    getThing().getLabel());
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                                    "Z-Way virtual device with id " + mConfig.getDeviceId() + " not found.");
                        }

                    } catch (Throwable t) {
                        if (t instanceof Exception) {
                            logger.error(((Exception) t).getMessage());
                        } else if (t instanceof Error) {
                            logger.error(((Error) t).getMessage());
                        } else {
                            logger.error("Unexpected error");
                        }
                        if (getThing().getStatus() == ThingStatus.ONLINE) {
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                                    "Error occurred when adding device as channel.");
                        }
                    }
                } else {
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                            "Devices not loaded");
                }
            } else {
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.HANDLER_INITIALIZING_ERROR,
                        "Z-Way bridge handler not found or not ONLINE.");
            }
        }
    };

    public ZWayZAutomationDeviceHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void initialize() {
        logger.debug("Initializing Z-Way ZAutomation device handler ...");

        // Set thing status to a valid status
        updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_PENDING,
                "Checking configuration and bridge...");

        // Configuration - thing status update with a error message
        mConfig = loadAndCheckConfiguration();

        if (mConfig != null) {
            logger.debug("Configuration complete: {}", mConfig);

            // Start an extra thread to check the connection, because it takes sometimes more
            // than 5000 milliseconds and the handler will suspend (ThingStatus.UNINITIALIZED).
            scheduler.schedule(new Initializer(), 2, TimeUnit.SECONDS);
        } else {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR, "Z-Way device id required!");
        }
    }

    private void completeInitialization() {
        super.initialize(); // starts polling job and register all linked items
    }

    private ZWayZAutomationDeviceConfiguration loadAndCheckConfiguration() {
        ZWayZAutomationDeviceConfiguration config = getConfigAs(ZWayZAutomationDeviceConfiguration.class);

        if (StringUtils.trimToNull(config.getDeviceId()) == null) {
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.CONFIGURATION_ERROR,
                    "Z-Wave device couldn't create, because the device id is missing.");
            return null;
        }

        return config;
    }

    @Override
    public void dispose() {
        logger.debug("Dispose Z-Way ZAutomation handler ...");

        if (mConfig.getDeviceId() != null) {
            mConfig.setDeviceId(null);
        }

        super.dispose();
    }
}
