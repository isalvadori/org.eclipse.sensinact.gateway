/*********************************************************************
* Copyright (c) 2021 Kentyou and others
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.sensinact.gateway.app.api.persistence;

import org.eclipse.sensinact.gateway.app.api.persistence.dao.Application;
import org.eclipse.sensinact.gateway.app.api.persistence.exception.ApplicationPersistenceException;
import org.eclipse.sensinact.gateway.app.api.persistence.listener.ApplicationAvailabilityListener;

import jakarta.json.JsonObject;

import java.util.Collection;

public interface ApplicationPersistenceService{
    void persist(Application application) throws ApplicationPersistenceException;

    void delete(String applicationName) throws ApplicationPersistenceException;

    JsonObject fetch(String applicationName) throws ApplicationPersistenceException;

    Collection<Application> list();

    void registerServiceAvailabilityListener(ApplicationAvailabilityListener listener);

    void unregisterServiceAvailabilityListener(ApplicationAvailabilityListener listener);
}
