/*********************************************************************
* Copyright (c) 2021 Kentyou and others
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.sensinact.gateway.app.manager.internal;

import org.eclipse.sensinact.gateway.app.api.exception.InvalidApplicationException;
import org.eclipse.sensinact.gateway.app.api.persistence.ApplicationPersistenceService;
import org.eclipse.sensinact.gateway.app.api.persistence.exception.ApplicationPersistenceException;
import org.eclipse.sensinact.gateway.app.api.persistence.listener.ApplicationAvailabilityListenerAbstract;
import org.eclipse.sensinact.gateway.core.ServiceProviderImpl;
import org.eclipse.sensinact.gateway.core.method.AccessMethodExecutor;
import org.eclipse.sensinact.gateway.core.method.AccessMethodResponseBuilder;
import org.eclipse.sensinact.gateway.util.json.JsonProviderFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Remi Druilhe
 * @see AccessMethodExecutor
 */
public class AppUninstallExecutor extends ApplicationAvailabilityListenerAbstract implements AccessMethodExecutor {
    private static Logger LOG = LoggerFactory.getLogger(AppUninstallExecutor.class);
    private final ServiceProviderImpl device;
    private final ApplicationPersistenceService persistenceService;

    /**
     * Constructor
     *
     * @param device the AppManager service provider
     */
    AppUninstallExecutor(ServiceProviderImpl device, ApplicationPersistenceService persistenceService) {
        this.device = device;
        this.persistenceService = persistenceService;
    }

    /**
     * @see Executable#execute(java.lang.Object)
     */
    public Void execute(AccessMethodResponseBuilder jsonObjects) {
        String name = (String) jsonObjects.getParameter(0);
        try {
            uninstall(name);
            jsonObjects.setAccessMethodObjectResult(JsonProviderFactory.getProvider().createObjectBuilder()
            		.add("message", "The application " + name + " has been uninstalled").build());
        } catch (Exception e) {
            jsonObjects.setAccessMethodObjectResult(JsonProviderFactory.getProvider().createObjectBuilder()
            		.add("message", "The application " + name + " has failed to be uninstalled")
            		.build());
        }
        return null;
    }

    public void uninstall(String name) throws InvalidApplicationException {
        if (name == null)
            throw new InvalidApplicationException("Wrong parameters. Unable to uninstall the application");
        else {
            try {
                persistenceService.delete(name);
            } catch (ApplicationPersistenceException e) {
                LOG.warn("Impossible to remove application '{}' from persistence system");
            }
/*
            if (device.getService(name) != null) {
                ApplicationService applicationService = (ApplicationService) device.getService(name);
                if (applicationService != null && applicationService.getApplication() != null) {
                    applicationService.getApplication().stop();
                    applicationService.getApplication().uninstall();
                    if (device.removeService(applicationService.getName())) {
                        LOG.info("Application " + name + " successfully uninstalled.");
                    } else {
                        LOG.warn("Failed to remove application '{}'",name);
                    }
                } else {
                    throw new InvalidApplicationException("Unable to uninstall the application.");
                }
            } else {
                throw new InvalidApplicationException("Unable to uninstall the application." +
                        "Application " + name + " does not exist");
            }
*/
        }
    }
}
