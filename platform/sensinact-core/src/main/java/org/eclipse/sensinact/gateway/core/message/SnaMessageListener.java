/*
 * Copyright (c) 2017 CEA.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    CEA - initial API and implementation
 */
package org.eclipse.sensinact.gateway.core.message;


import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.sensinact.gateway.common.bundle.Mediator;
import org.eclipse.sensinact.gateway.common.execution.ErrorHandler;
import org.eclipse.sensinact.gateway.common.execution.Executable;
import org.eclipse.sensinact.gateway.common.primitive.Name;
import org.eclipse.sensinact.gateway.core.SensiNactResourceModelConfiguration;
import org.eclipse.sensinact.gateway.core.method.AccessMethod;
import org.eclipse.sensinact.gateway.core.method.AccessMethodResponse;
import org.eclipse.sensinact.gateway.core.security.AccessLevelOption;
import org.eclipse.sensinact.gateway.core.security.MethodAccessibility;
import org.eclipse.sensinact.gateway.util.UriUtils;
import org.eclipse.sensinact.gateway.util.stack.AbstractStackEngineHandler;

/**
 * {@link SnaMessage} handler managing a set of 
 * callbacks mapped to {@link SnaFilter}s defining 
 * whether to transmit or not triggered messages
 * 
 * @author <a href="mailto:christophe.munilla@cea.fr">Christophe Munilla</a>
 */
public class SnaMessageListener 
extends AbstractStackEngineHandler<SnaMessage<?>> 
implements MessageHandler
{
	/**
	 * The set of {@link SnaCallback} mapped to 
	 * {@link SnaFilter}s defining whether to call
	 * them or not
	 */
	protected final Map<SnaFilter, List<SnaCallback>> callbacks;
	
	/**
	 * The set of {@link SnaCallback} mapped to 
	 * {@link SnaFilter}s defining whether to call
	 * them or not
	 */
	protected final Map<String, List<MethodAccessibility>> agentsAccessibility;
	
	/**
	 * The sensiNact resource model configuration providing 
	 * access rules applying on potentially registered 
	 * {@link SnaAgent}
	 */
	private SensiNactResourceModelConfiguration configuration;
	
	/**
	 * the {@link Mediator} allowing to interact with the OSGi
	 * host environment
	 */
	private Mediator mediator;
	
	/**
	 * Constructor
	 * 
	 * @param mediator
	 * @param configuration
	 */
	public SnaMessageListener(Mediator mediator, 
		SensiNactResourceModelConfiguration configuration)
	{
		super();
		this.mediator = mediator;
		this.configuration = configuration;
		this.callbacks = new HashMap<SnaFilter, List<SnaCallback>>();	
		this.agentsAccessibility = new HashMap<String, List<MethodAccessibility>>();
	}
	
	/**
	 * @inheritDoc
	 *
	 * @see org.eclipse.sensinact.gateway.core.message.MessageHandler#
	 * addCallback(org.eclipse.sensinact.gateway.core.message.SnaFilter,
	 *  org.eclipse.sensinact.gateway.core.message.SnaCallback)
	 */
	@Override
	public void addCallback(SnaFilter filter, SnaCallback callback)
	{
		if(filter == null ||callback == null)
		{
			return;
		}
		synchronized(this.callbacks)
		{
			List<SnaCallback> list = this.callbacks.get(filter);
			if(list == null)
			{
				list = new LinkedList<SnaCallback>();
				this.callbacks.put(filter, list);
			}
			list.add(callback);
		}
	}

	/**
	 * @inheritDoc
	 *
	 * @see org.eclipse.sensinact.gateway.core.message.MessageHandler#
	 * deleteCallback(java.lang.String)
	 */
	@Override
	public void deleteCallback(String callback)
	{		 
		synchronized(this.callbacks)
		{			
			Iterator<Entry<SnaFilter, List<SnaCallback>>> 
			iterator = this.callbacks.entrySet().iterator();
			
			while(iterator.hasNext())
			{
				Entry<SnaFilter,List<SnaCallback>> 
				entry = iterator.next();
				
				List<SnaCallback> list = entry.getValue();				
				if(list.remove(new Name<SnaCallback>(callback)))
				{						
					if(list.isEmpty())
					{
						iterator.remove();
					}
					break;
				}
			}
		}
	}
	
	/**
	 * @inheritDoc
	 *
	 * @see MessageHandler#count(java.lang.String)
	 */
	@Override
	public int count(String uri)
	{
		int count = 0;
		String formatedUri = UriUtils.formatUri(uri);	
		synchronized(this.callbacks)
		{
			Iterator<SnaFilter> iterator = this.callbacks.keySet(
					).iterator();		
			while(iterator.hasNext())
			{
				SnaFilter snaFilter = iterator.next();
				if(snaFilter.sender.equals(formatedUri))
				{
					count++;
				}
			}
		}
		return count;
	}
	
	/**
	 * Removes the {@link SnaFilter} passed as parameter 
	 * and all mapped {@link SnaCallback}s
	 * 
	 * @param filter
	 * 		the {@link SnaFilter} to remove
	 */
	public void removeFilter(SnaFilter filter)
	{		
		synchronized(this.callbacks)
		{
			this.callbacks.remove(filter);
		}
	}
	
	/** 
	 * @inheritDoc
	 * 
	 * @see org.eclipse.sensinact.gateway.core.message.MessageHandler#
	 * handle(org.eclipse.sensinact.gateway.core.message.SnaMessage)
	 */
	@Override 
	public void handle(SnaMessage message) 
	{
		super.eventEngine.push(message);   
	}

	/** 
	 * @inheritDoc
	 * 
	 * @see org.eclipse.sensinact.gateway.util.stack.StackEngineHandler#
	 * doHandle(java.lang.Object)
	 */
	@Override
	public void doHandle(SnaMessage<?> message)
	{		
		doHandleSubscribers(message);		
		String messageMethod = null;
		
		switch(((SnaMessageSubType)message.getType()
				).getSnaMessageType())
		{
			case RESPONSE:
				switch((AccessMethodResponse.Response
						)message.getType())
				{
					case ACT_RESPONSE:
						messageMethod = AccessMethod.ACT;
						break;
					case DESCRIBE_RESPONSE:
						messageMethod = AccessMethod.DESCRIBE;
						break;
					case GET_RESPONSE:
						messageMethod = AccessMethod.GET;
						break;
					case SET_RESPONSE:
						messageMethod = AccessMethod.SET;
						break;
					case SUBSCRIBE_RESPONSE:
						messageMethod = AccessMethod.SUBSCRIBE;
						break;
					case UNSUBSCRIBE_RESPONSE:
						messageMethod = AccessMethod.UNSUBSCRIBE;
						break;
					default:
						break;						
				}
				break;
			case ERROR:
			case LIFECYCLE:
				messageMethod = AccessMethod.DESCRIBE;
				break;
			case UPDATE:
				messageMethod = AccessMethod.GET;
				break;					
			default:
				break;					
		}
		if(messageMethod != null)
		{
			doHandleAgents(message, messageMethod);
		}
	}
	
	/**
	 * Transmits the {@link SnaMessage} passed as parameter
	 * to the appropriate subscribers according to their
	 * associated filter, target, and message type
	 * 
	 * @param message the {@link SnaMessage} to transmit
	 */
	private void doHandleSubscribers(final SnaMessage<?> message)
	{			
		synchronized(this.callbacks)
		{
			Iterator<Entry<SnaFilter, List<SnaCallback>>> 
			iterator = this.callbacks.entrySet().iterator();
			
			while(iterator.hasNext())
			{
				Entry<SnaFilter, List<SnaCallback>> 
				entry = iterator.next();
				
				SnaFilter filter = entry.getKey();				
				if(!filter.matches(message))
				{
					continue;
				}
				Iterator<SnaCallback> callbackIterator =
						entry.getValue().iterator();
				
				while(callbackIterator.hasNext())
				{
					ErrorHandler handler = null;
					SnaCallback callback = callbackIterator.next();
					
					if((callback.getTimeout() != SnaCallback.ENDLESS 
						&& System.currentTimeMillis() > callback.getTimeout())
						||(AccessMethodResponse.Status.ERROR == callback.getStatus()
						&& (handler=callback.getCallbackErrorHandler())!=null 
						&& ErrorHandler.ErrorHandlerPolicy.contains(
								handler.getPolicy(), 
								ErrorHandler.ErrorHandlerPolicy.REMOVE)))
					{
						callbackIterator.remove();
						continue;
					}
					callback.register(message);
				}
			}
		}
	}

	/**
	 * Transmits the {@link SnaMessage} passed as parameter
	 * to the appropriate agents according to their access
	 * rights
	 * 
	 * @param message the {@link SnaMessage} to transmit
	 */
	private void doHandleAgents(final SnaMessage<?> message, 
			final String method)
	{		
		final String path = message.getPath();
		mediator.callServices(SnaAgent.class, new Executable<SnaAgent,Void>()
		{
			@Override
			public Void execute(SnaAgent agent) throws Exception 
			{
				String agentKey = agent.getPublicKey();
				
				List<MethodAccessibility> methodAccessibilities = 
				SnaMessageListener.this.agentsAccessibility.get(agentKey);
				
				int index =-1;
				if(methodAccessibilities==null)
				{
					AccessLevelOption option =
					SnaMessageListener.this.configuration.getAuthenticatedAccessLevelOption(
							path, agentKey);
					methodAccessibilities  = 
					SnaMessageListener.this.configuration.getAccessibleMethods(
							path, option);
					SnaMessageListener.this.agentsAccessibility.put(
							agentKey, methodAccessibilities);
				}	
				if((index = methodAccessibilities.indexOf(
					new Name<MethodAccessibility>(method))) > -1
					&& methodAccessibilities.get(index).isAccessible())
				{			
					agent.register(message);
				}		
				return null;
			}			
		});
	}

	/** 
	 * @inheritDoc
	 * 
	 * @see MessageHandler#close(boolean)
	 */
	@Override
	public void close(boolean wait)
	{
		if(wait)
		{
			super.close();
			
		} else
		{
			super.stop();
		}
	}
}
