/*********************************************************************
* Copyright (c) 2021 Kentyou and others
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.sensinact.gateway.sthbnd.mqtt.util.listener;

import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.sensinact.gateway.sthbnd.mqtt.util.api.MqttBroker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class MqttConnectionHandler implements MqttCallback {
    private static final Logger LOG = LoggerFactory.getLogger(MqttConnectionHandler.class);
    private final MqttBroker broker;

    public MqttConnectionHandler(MqttBroker broker) {
        this.broker = broker;
    }

    @Override
    public void connectionLost(Throwable cause) {
        LOG.error("Connection with the broker lost", cause);
        this.connectionLost(broker);
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) throws Exception {
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {
    }

    /**
     * Indicated that the connection failed on the first attempt.
     *
     * @param broker
     */
    public abstract void connectionFailed(MqttBroker broker);

    /**
     * Called when the connection is established, either first or consecutive time (if recovering from a disconnection)
     *
     * @param broker
     */
    public abstract void connectionEstablished(MqttBroker broker);

    /**
     * Method fired when the connection was established at some point but lost
     *
     * @param broker
     */
    public abstract void connectionLost(MqttBroker broker);
}
