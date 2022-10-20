/*********************************************************************
* Copyright (c) 2021 Kentyou and others
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/

package org.eclipse.sensinact.gateway.sthbnd.http.test.bundle3;

import org.eclipse.sensinact.gateway.sthbnd.http.HttpPacket;
import org.eclipse.sensinact.gateway.sthbnd.http.annotation.ChainedHttpTask;
import org.eclipse.sensinact.gateway.sthbnd.http.annotation.ChainedHttpTasks;
import org.eclipse.sensinact.gateway.sthbnd.http.annotation.HttpChildTaskConfiguration;
import org.eclipse.sensinact.gateway.sthbnd.http.annotation.HttpTaskConfiguration;
import org.eclipse.sensinact.gateway.sthbnd.http.smpl.HttpActivator;
import org.osgi.annotation.bundle.Header;
import org.osgi.framework.Constants;

@ChainedHttpTasks(tasks = {
	@ChainedHttpTask(
		configuration = 
			@HttpTaskConfiguration(
				host = "127.0.0.1", 
				port="8899"),
		chain = { 
			@HttpChildTaskConfiguration(
				identifier="serviceProviderId",
				path = "/json1"),
			@HttpChildTaskConfiguration(
				identifier="serviceId",
				path = "/json2"),
			@HttpChildTaskConfiguration(
				identifier="resourceId",
				path = "/json3")
		}
	)
})
@Header(name = Constants.BUNDLE_ACTIVATOR, value = "${@class}")
public class Activator extends HttpActivator
{

	/**
	 * @inheritDoc
	 *
	 * @see HttpActivator#getPacketType()
	 */
	@Override
	protected Class<? extends HttpPacket> getPacketType()
	{
		return HttpTestPacket.class;
	}
}