/*********************************************************************
* Copyright (c) 2021 Kentyou and others
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.sensinact.gateway.sthbnd.mqtt.util.api;

public class MqttSession {
    private static final Boolean DEFAULT_CLEAN_SESSION = Boolean.TRUE;
    private static final Boolean DEFAULT_AUTO_RECONNECT = Boolean.FALSE;
    private Integer maxInFlight;
    private Integer keepAliveInterval;
    private Boolean cleanSession;
    private Boolean autoReconnect;

    private MqttSession(MqttSession.Builder builder) {
        this.maxInFlight = builder.maxInFlight;
        this.keepAliveInterval = builder.keepAliveInterval;
        this.cleanSession = builder.cleanSession;
        this.autoReconnect = builder.autoReconnect;
    }

    public Integer getMaxInFlight() {
        return maxInFlight;
    }

    public Integer getKeepAliveInterval() {
        return keepAliveInterval;
    }

    public Boolean getCleanSession() {
        return cleanSession;
    }

    public Boolean getAutoReconnect() {
        return autoReconnect;
    }

    /**
     * The builder abstraction.
     */
    public static class Builder {
        private Integer maxInFlight = null;
        private Integer keepAliveInterval = null;
        private Boolean cleanSession = DEFAULT_CLEAN_SESSION;
        private Boolean autoReconnect = DEFAULT_AUTO_RECONNECT;

        public MqttSession.Builder maxInFlight(Integer maxInFlight) {
            this.maxInFlight = maxInFlight;
            return this;
        }

        public MqttSession.Builder keepAliveInterval(Integer keepAliveInterval) {
            this.keepAliveInterval = keepAliveInterval;
            return this;
        }

        public MqttSession.Builder cleanSession(Boolean cleanSession) {
            this.cleanSession = cleanSession;
            return this;
        }

        public MqttSession.Builder autoReconnect(Boolean autoReconnect) {
            this.autoReconnect = autoReconnect;
            return this;
        }

        public MqttSession build() {
            return new MqttSession(this);
        }
    }
}
