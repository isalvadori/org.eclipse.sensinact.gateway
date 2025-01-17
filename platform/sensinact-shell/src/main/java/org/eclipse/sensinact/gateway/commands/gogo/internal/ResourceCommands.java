/*********************************************************************
* Copyright (c) 2021 Kentyou and others
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.sensinact.gateway.commands.gogo.internal;

import org.apache.felix.service.command.Descriptor;
import org.apache.felix.service.command.annotations.GogoCommand;
import org.eclipse.sensinact.gateway.commands.gogo.internal.shell.ShellAccess;
import org.eclipse.sensinact.gateway.util.json.JsonProviderFactory;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.Reference;

@Component(service = ResourceCommands.class)
@GogoCommand(
		scope = "sna", 
		function = {"resource", "resources"}
	)
public class ResourceCommands {
    
	@Reference
	private CommandComponent component;

    /**
     * Display the existing sensiNact service instances
     *
     * @param serviceProviderID the ID of the service provider
     * @param serviceID         the ID of the service
     */
    @Descriptor("display the existing sensiNact service instances")
    public void resources(@Descriptor("the service provider ID") String serviceProviderID, @Descriptor("the service ID") String serviceID) {
        ShellAccess.proceed(component.getCommandMediator(), JsonProviderFactory.getProvider().createObjectBuilder()
        		.add("uri", CommandServiceMediator.uri(serviceProviderID, serviceID, null, true)).build());
    }

    /**
     * Get the description of a specific resource of a sensiNact service
     *
     * @param serviceProviderID the ID of the service provider
     * @param serviceID         the ID of the service
     * @param resourceID        the ID of the resource
     */
    @Descriptor("get the description of a specific resource of a sensiNact service")
    public void resource(@Descriptor("the service provider ID") String serviceProviderID, @Descriptor("the service ID") String serviceID, @Descriptor("the resource IS") String resourceID) {
        ShellAccess.proceed(component.getCommandMediator(), JsonProviderFactory.getProvider().createObjectBuilder()
        		.add("uri", CommandServiceMediator.uri(serviceProviderID, serviceID, resourceID, false)).build());
    }
}
