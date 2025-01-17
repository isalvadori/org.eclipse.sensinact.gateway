/*********************************************************************
* Copyright (c) 2021 Kentyou and others
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.sensinact.gateway.sthbnd.liveobjects;

/**
 * @author Rémi Druilhe
 */
public class LiveObjectsConstant {
    /**
     * Root URL for contacting the Orange LiveObjects platform.
     */
    public static final String ROOT_URL = "https://liveobjects.orange-business.com";
    /**
     * Root PATH for getting information from the LiveObjects platform.
     */
    public static final String ROOT_PATH = "/api/v0/";
    /**
     * Correspond to the name of the stream in the LiveObjects platform
     */
    public static final String URN = "urn:lo:nsid:";
    /**
     * The fields in the JSON response for the authentication
     */
    public static final String JSON_FIELD_APIKEY = "apiKey";
    public static final String JSON_FIELD_APIKEY_VALUE = "value";
    /**
     * The required header to set after the authentication with the Orange Datavenue servers.
     */
    public static final String X_API_KEY = "X-API-Key";
}
