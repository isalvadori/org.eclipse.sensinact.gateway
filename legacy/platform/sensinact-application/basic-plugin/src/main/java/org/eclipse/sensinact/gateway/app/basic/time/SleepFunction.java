/*********************************************************************
* Copyright (c) 2021 Kentyou and others
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.sensinact.gateway.app.basic.time;

import java.io.IOException;
import java.util.List;

import org.eclipse.sensinact.gateway.app.api.function.AbstractFunction;
import org.eclipse.sensinact.gateway.app.api.function.DataItf;
import org.eclipse.sensinact.gateway.util.CastUtils;
import org.eclipse.sensinact.gateway.util.json.JsonProviderFactory;
import org.osgi.framework.BundleContext;

import jakarta.json.JsonObject;

/**
 * This class implements the sleep function
 *
 * @author Remi Druilhe
 */
public class SleepFunction extends TimeFunction<Boolean> {
    private static final String JSON_SCHEMA = "sleep.json";

    public SleepFunction() {
    }

    /**
     * Gets the JSON schema of the function from the plugin
     *
     * @param context the context of the bundle
     * @return the JSON schema of the function
     */
    public static JsonObject getJSONSchemaFunction(BundleContext context) {
        try {
        	return JsonProviderFactory.getProvider().createReader(context.getBundle().getResource("/" + JSON_SCHEMA).openStream()).readObject();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @see AbstractFunction#process(List)
     */
    public void process(List<DataItf> datas) {
        long duration = CastUtils.cast(long.class, datas.get(0).getValue());
        try {
            Thread.sleep(duration);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        super.update(true);
    }
}