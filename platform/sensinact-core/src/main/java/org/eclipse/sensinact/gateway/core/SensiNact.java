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
package org.eclipse.sensinact.gateway.core;

import java.security.AccessController;
import java.security.InvalidKeyException;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Dictionary;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;

import org.eclipse.sensinact.gateway.common.bundle.Mediator;
import org.eclipse.sensinact.gateway.common.constraint.Constraint;
import org.eclipse.sensinact.gateway.common.constraint.ConstraintFactory;
import org.eclipse.sensinact.gateway.common.constraint.InvalidConstraintDefinitionException;
import org.eclipse.sensinact.gateway.common.execution.Executable;
import org.eclipse.sensinact.gateway.core.Sessions.KeyExtractor;
import org.eclipse.sensinact.gateway.core.Sessions.KeyExtractorType;
import org.eclipse.sensinact.gateway.core.message.MidAgentCallback;
import org.eclipse.sensinact.gateway.core.message.Recipient;
import org.eclipse.sensinact.gateway.core.message.SnaAgent;
import org.eclipse.sensinact.gateway.core.message.SnaAgentImpl;
import org.eclipse.sensinact.gateway.core.message.SnaErrorfulMessage;
import org.eclipse.sensinact.gateway.core.message.SnaFilter;
import org.eclipse.sensinact.gateway.core.method.AccessMethod;
import org.eclipse.sensinact.gateway.core.method.AccessMethodResponse;
import org.eclipse.sensinact.gateway.core.method.RemoteAccessMethodExecutable;
import org.eclipse.sensinact.gateway.core.method.legacy.ActResponse;
import org.eclipse.sensinact.gateway.core.method.legacy.GetResponse;
import org.eclipse.sensinact.gateway.core.method.legacy.SetResponse;
import org.eclipse.sensinact.gateway.core.method.legacy.SubscribeResponse;
import org.eclipse.sensinact.gateway.core.method.legacy.UnsubscribeResponse;
import org.eclipse.sensinact.gateway.core.security.AccessLevelOption;
import org.eclipse.sensinact.gateway.core.security.AccessNode;
import org.eclipse.sensinact.gateway.core.security.AccessTree;
import org.eclipse.sensinact.gateway.core.security.Authentication;
import org.eclipse.sensinact.gateway.core.security.AuthenticationService;
import org.eclipse.sensinact.gateway.core.security.AuthenticationToken;
import org.eclipse.sensinact.gateway.core.security.Credentials;
import org.eclipse.sensinact.gateway.core.security.InvalidCredentialException;
import org.eclipse.sensinact.gateway.core.security.MutableAccessTree;
import org.eclipse.sensinact.gateway.core.security.SecuredAccess;
import org.eclipse.sensinact.gateway.core.security.SecuredAccessException;
import org.eclipse.sensinact.gateway.core.security.SecuredAccessFactory;
import org.eclipse.sensinact.gateway.core.security.UserKey;
import org.eclipse.sensinact.gateway.datastore.api.DataStoreException;
import org.eclipse.sensinact.gateway.security.signature.api.BundleValidation;
import org.eclipse.sensinact.gateway.util.CryptoUtils;
import org.eclipse.sensinact.gateway.util.UriUtils;
import org.json.JSONArray;
import org.json.JSONObject;
import org.osgi.framework.Bundle;
import org.osgi.framework.BundleException;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceReference;

/**
 * {@link Core} service implementation
 * 
 * @author <a href="mailto:christophe.munilla@cea.fr">Christophe Munilla</a>
 */
public class SensiNact implements Core
{	
	//********************************************************************//
	//						NESTED DECLARATIONS		    				  //
	//********************************************************************//		
	
	/**
	 * {@link Session} service implementation
	 */
	final class SensiNactSession implements Session
	{	    		
		private final String identifier;

		/**
		 * Constructor
		 * 
		 * @param identifier the String identifier of the Session
		 * to be instantiated
		 */
		public SensiNactSession(String identifier)
		{
			this.identifier = identifier;
		}

		private String getUri(boolean resolved, String...pathElements)
		{
			if(pathElements == null || pathElements.length==0)
			{
				return null;
			}
			String providerId=resolved?new StringBuilder().append(
				SensiNact.this.namespace()).append(":").append(pathElements[0]
					).toString():pathElements[0];
					
			String[] uriElements = new String[pathElements.length];
			if(pathElements.length>1)
			{
				System.arraycopy(pathElements, 1, uriElements, 1, pathElements.length-1);
			}
			uriElements[0] = providerId;
			return UriUtils.getUri(uriElements);
		}

		private <R extends AccessMethodResponse> R invalidMethodErrorResponse(
				AccessMethod.Type type, String providerId, String serviceId, 
				String resourceId)
		{
			String uri = getUri(false, providerId, serviceId, resourceId);
			R response = (R) AccessMethodResponse.error(mediator, uri, type, 420, 
					"Unknown method", null);			
			return response;
		}

		/**
		 * return String identifier of this Session
		 * 
		 * @return this Session's identifier
		 */
		public String getSessionId()
		{
			return this.identifier;
		}
		
		/** 
		 * @inheritDoc
		 * 
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getServiceProviders()
		 */
		@Override
	    public Set<ServiceProvider> serviceProviders()
	    {
		    return this.serviceProviders(null);
	    }

		/** 
		 * @inheritDoc
		 * 
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getServiceProviders()
		 */
		@Override
	    public Set<ServiceProvider> serviceProviders(final String filter)
	    {
			return AccessController.doPrivileged(
			 new PrivilegedAction<Set<ServiceProvider>>()
			 {
				@Override
	            public Set<ServiceProvider> run()
	            {
			    	return SensiNact.this.serviceProviders(
			    		SensiNactSession.this.getSessionId(),
			    		filter);
	            }
			 });
	    }
	    
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getServiceProvider(java.lang.String)
		 */
	    @Override
	    public ServiceProvider serviceProvider(
	    		final String serviceProviderName)
	    {
	    	ServiceProvider provider = AccessController.doPrivileged(
	    				new PrivilegedAction<ServiceProvider>()
			{
				@Override
                public ServiceProvider run()
                {
					return SensiNact.this.serviceProvider(
			    			SensiNactSession.this.getSessionId(),
							serviceProviderName);
                }
			});
	    	return provider;        	    	
	    }

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getService(java.lang.String, java.lang.String)
		 */
	    @Override
	    public Service service(
	    		final String serviceProviderName, 
	    		final String serviceName)
	    {
	    	Service service = null;
	    	ServiceProvider provider = this.serviceProvider(serviceProviderName);
	    	if(provider != null)
	    	{
	    		service = provider.getService(serviceName);
	    	}
	    	return service;
	    }

		/**
		 * @inheritDoc
		 * 
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getResource(java.lang.String, java.lang.String, java.lang.String)
		 */
	    @Override
	    public Resource resource(
	    		final String serviceProviderName, 
	    		final String serviceName,
	            final String resourceName)
	    {
	    	Resource resource = null;
	    	Service service = null;
	    	if((service = this.service(serviceProviderName, serviceName))!= null)
	    	{
	    		resource = service.getResource(resourceName);
	    	}
	    	return resource;
	    }

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * registerSessionAgent(org.eclipse.sensinact.gateway.core.message.MidAgentCallback, org.eclipse.sensinact.gateway.core.message.SnaFilter)
		 */
	    @Override
		public ResultHolder<JSONObject> registerSessionAgent(MidAgentCallback callback, 
				SnaFilter filter)
		{	
	    	return registerSessionAgent(null,callback,filter);
		}

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * registerSessionAgent(org.eclipse.sensinact.gateway.core.message.MidAgentCallback, org.eclipse.sensinact.gateway.core.message.SnaFilter)
		 */
	    @Override
		public  ResultHolder<JSONObject> registerSessionAgent(
				String requestId, 
				final MidAgentCallback callback, 
				final SnaFilter filter)
		{			
			String agentId = AccessController.<String>doPrivileged(
			new PrivilegedAction<String>()
			{
				@Override
				public String run()
				{
					SessionKey key = SensiNact.this.sessions.get(
						new KeyExtractor<KeyExtractorType>(
								KeyExtractorType.TOKEN, getSessionId()));
					if(key == null || key.getPublicKey()==null)
					{
						return null;
					}
					return key.registerAgent(callback, filter);
				}
			});			
			String uri = null;
			if(filter!=null)
			{
				uri = new JSONObject(filter.getJSON()).getString("sender");
			} else
			{
				uri = UriUtils.ROOT;
			}
	    	JSONObject json = new JSONObject();
    		if(requestId != null)
    		{
    			json.put(AccessMethod.REQUEST_ID_KEY,requestId);
    		}
	    	json.put("type", "SUBSCRIBE_RESPONSE");
	    	json.put("uri", uri);
	    	int status = 0;
	        if (agentId != null)
	        {      	
	        	status = 200;
	        	json.put("response", new JSONObject().put(
	        		"subscriptionId", agentId));
	        	
	        } else
	        {	
	        	status = 520;
	        	json.put("error" , "Internal server error");
	        }
	        json.put("statusCode", status);
	        return new ResultHolder<JSONObject>(status, json);
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * unregisterSessionAgent(java.lang.String)
		 */
		@Override
		public ResultHolder<JSONObject> unregisterSessionAgent(
				String agentId)
		{			
			return unregisterSessionAgent(null,agentId);
		}

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * unregisterSessionAgent(java.lang.String)
		 */
		@Override
		public ResultHolder<JSONObject> unregisterSessionAgent(
				String requestId, final String agentId)
		{			
			boolean unregistered = AccessController.<Boolean>doPrivileged(
			new PrivilegedAction<Boolean>()
			{
				@Override
				public Boolean run()
				{
					SessionKey key = SensiNact.this.sessions.get(
						new KeyExtractor<KeyExtractorType>(
								KeyExtractorType.TOKEN, getSessionId()));
					
					if(key != null && key.getPublicKey()!=null)
					{
						return key.unregisterAgent(agentId);
					}
					return false;
				}
			});
	    	JSONObject json = new JSONObject();
    		if(requestId != null)
    		{
    			json.put(AccessMethod.REQUEST_ID_KEY,requestId);
    		}
	    	json.put("type", "UNSUBSCRIBE_RESPONSE");
	    	json.put("uri", UriUtils.ROOT);
	    	int status =0;
	        if (unregistered)
	        {      	
	        	status = 200;
	        	json.put("response", new JSONObject().put("message", 
	        			"The agent has been properly unregistered"));
	        	
	        } else
	        {
	        	status = 520;
	        	json.put("error" , "Internal server error");
	        }
	        json.put("statusCode", status);
	        return new ResultHolder<JSONObject>(status, json);
		}
		
		/**
	     * @inheritDoc
	     *
	     * @see org.eclipse.sensinact.gateway.core.Session#
	     * get(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	     */
		@Override
	    public ResultHolder<JSONObject> get(String serviceProviderId, 
	    	String serviceId, String resourceId, String attributeId)
	    {	
	    	return get(null, serviceProviderId ,serviceId, 
	    			resourceId, attributeId);
	    }	    

	    /**
	     * @inheritDoc
	     *
	     * @see org.eclipse.sensinact.gateway.core.Session#
	     * get(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
	     */
	    @Override
	    public ResultHolder<JSONObject> get(String requestId, 
	    	final String serviceProviderId, final String serviceId, 
	    	final String resourceId, final String attributeId)
	    {	
	    	SessionKey sessionKey = SensiNact.this.sessions.get(
	    			new KeyExtractor<KeyExtractorType>(KeyExtractorType.TOKEN, 
	    				this.getSessionId()));
	    	
			String uri =  getUri((sessionKey.localID()!=0), serviceProviderId, 
					serviceId, resourceId);
			
	        Resource resource = this.resource(serviceProviderId, serviceId, 
	        		resourceId);

	        if(resource == null)
	        {
	        	if(sessionKey.localID()!=0)
	        	{
	        		AccessMethodResponse response = AccessMethodResponse.error(mediator, 
	        			uri, AccessMethod.GET, SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
	        			new StringBuilder().append("Resource ").append(uri
	        				).append("not found").toString(), null);
	        		
	        		if(requestId != null)
	        		{
	        			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
	        		}
	        		return new ResultHolder<JSONObject>(
	        				SnaErrorfulMessage.NOT_FOUND_ERROR_CODE,
	        				new JSONObject(response.getJSON()));
	        	}
        		JSONObject object =  AccessController.doPrivileged(
	        	new PrivilegedAction<JSONObject>()
				{
					@Override
		            public JSONObject run()
		            {
				    	return SensiNact.this.get(SensiNactSession.this.getSessionId(),
				    		serviceProviderId, serviceId, resourceId, 
				    		    attributeId);
		            }
				});
        		if(requestId != null)
        		{
        			object.put(
        				AccessMethod.REQUEST_ID_KEY, requestId);
        		}
        		return new ResultHolder<JSONObject>(
        				object.getInt("statusCode"), object);
	        }
	        GetResponse response = null;     	        
	        if(attributeId==null)
	        {
	        	if(!resource.getType().equals(Resource.Type.ACTION))
				{
					response = ((DataResource) resource).get();
					
				} else
				{
					response = invalidMethodErrorResponse(
						AccessMethod.Type.valueOf(AccessMethod.GET), 
						    serviceProviderId, serviceId, resourceId);
				}
	        } else
	        {
	        	response = resource.get(attributeId);
	        }
    		if(requestId != null)
    		{
    			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
    		}
    		int status = response.getStatusCode();
    		JSONObject object = new JSONObject(response.getJSON());
    		
        	if(sessionKey.localID()!=0)
        	{				
        		object.remove("uri");
        		object.put("uri", uri);
        	}
        	return new ResultHolder<JSONObject>(status,object);
	    }

	    /**
	     * @inheritDoc
	     *
	     * @see org.eclipse.sensinact.gateway.core.Session#
	     * set(java.lang.String, java.lang.String, java.lang.String, java.lang.String, 
	     * java.lang.Object)
	     */
	    @Override
	    public ResultHolder<JSONObject> set(final String serviceProviderId, final String serviceId,
        final String resourceId, final String attributeId, final Object parameter)
		{	 
	    	return set(null, serviceProviderId, serviceId, resourceId, 
	    		attributeId, parameter);
	    }

	    /**
	     * @inheritDoc
	     *
	     * @see org.eclipse.sensinact.gateway.core.Session#
	     * set(java.lang.String, java.lang.String, java.lang.String, java.lang.String, 
	     * java.lang.Object)
	     */
	    @Override
	    public ResultHolder<JSONObject> set(String requestId, final String serviceProviderId, final String serviceId,
        final String resourceId, final String attributeId, final Object parameter)
		{	 
	    	SessionKey sessionKey = SensiNact.this.sessions.get(
	    			new KeyExtractor<KeyExtractorType>(KeyExtractorType.TOKEN, 
	    				this.getSessionId()));
	    	
			String uri =  getUri((sessionKey.localID()!=0), serviceProviderId, 
					serviceId, resourceId);
			
	        Resource resource = this.resource(serviceProviderId, serviceId, 
	        		resourceId);

	        if(resource == null)
	        {
	        	if(sessionKey.localID()!=0)
	        	{
	        		AccessMethodResponse response = AccessMethodResponse.error(mediator, 
	        			uri, AccessMethod.SET, SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
	        			new StringBuilder().append("Resource ").append(uri
	        				).append("not found").toString(), null);

	        		if(requestId != null)
	        		{
	        			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
	        		}
	        		return new ResultHolder<JSONObject>(
	        				SnaErrorfulMessage.NOT_FOUND_ERROR_CODE,
	        				new JSONObject(response.getJSON()));
	        	}
        		JSONObject object =  AccessController.doPrivileged(
	        	new PrivilegedAction<JSONObject>()
				{
					@Override
		            public JSONObject run()
		            {
				    	return SensiNact.this.set(SensiNactSession.this.getSessionId(),
				    		serviceProviderId, serviceId, resourceId, 
				    		    attributeId, parameter);
		            }
				});
	    		if(requestId != null)
	    		{
	    			object.put(AccessMethod.REQUEST_ID_KEY, requestId);
	    		}
	    		return new ResultHolder<JSONObject>(object.getInt(
	    				"statusCode"), object);
	        }			 
	        SetResponse response = null;
	        if(attributeId==null)
	        {
	        	if(!resource.getType().equals(Resource.Type.ACTION))
				{
					response = ((DataResource) resource).set(parameter);
					
				}else
				{
					response = invalidMethodErrorResponse(AccessMethod.Type.valueOf(
						AccessMethod.SET), serviceProviderId,serviceId, resourceId);
				}
	        } else
	        {
	        	response = resource.set(attributeId,parameter);
	        }       
    		if(requestId != null)
    		{
    			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
    		}
    		int status = response.getStatusCode();
    		JSONObject object = new JSONObject(response.getJSON());
    		
        	if(sessionKey.localID()!=0)
        	{				
        		object.remove("uri");
        		object.put("uri", uri);
        	}
        	return new ResultHolder<JSONObject>(status,object);
	    }

	    /**
	     * @inheritDoc
	     *
	     * @see org.eclipse.sensinact.gateway.core.Session#
	     * act(java.lang.String, java.lang.String, java.lang.String, java.lang.Object[])
	     */
	    public ResultHolder<JSONObject> act(String serviceProviderId, String serviceId,
		     String resourceId, Object[] parameters)
		{ 
	    	return act(null,serviceProviderId, serviceId, resourceId, 
	    		parameters);
	    }

	    /**
	     * @inheritDoc
	     *
	     * @see org.eclipse.sensinact.gateway.core.Session#
	     * act(java.lang.String, java.lang.String, java.lang.String, java.lang.Object[])
	     */
	    @Override
	    public ResultHolder<JSONObject> act(String requestId, final String serviceProviderId,
	    	final String serviceId, final String resourceId, final Object[] parameters)
		{ 
	    	SessionKey sessionKey = SensiNact.this.sessions.get(
	    			new KeyExtractor<KeyExtractorType>(KeyExtractorType.TOKEN, 
	    				this.getSessionId()));
	    	
			String uri =  getUri((sessionKey.localID()!=0), serviceProviderId, 
					serviceId, resourceId);
			
	        Resource resource = this.resource(serviceProviderId, serviceId, 
	        		resourceId);

	        if(resource == null)
	        {
	        	if(sessionKey.localID()!=0)
	        	{
	        		AccessMethodResponse response = AccessMethodResponse.error(mediator, 
	        			uri, AccessMethod.ACT, SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
	        			new StringBuilder().append("Resource ").append(uri
	        				).append("not found").toString(), null);
	        		
	        		if(requestId != null)
	        		{
	        			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
	        		}
	        		return new ResultHolder<JSONObject>(
	        			SnaErrorfulMessage.NOT_FOUND_ERROR_CODE,
	        			new JSONObject(response.getJSON()));
	        	}
        		JSONObject object =  AccessController.doPrivileged(
	        	new PrivilegedAction<JSONObject>()
				{
					@Override
		            public JSONObject run()
		            {
				    	return SensiNact.this.act(SensiNactSession.this.getSessionId(),
				    	serviceProviderId, serviceId, resourceId, parameters);
		            }
				});
        		if(requestId != null)
        		{
        			object.put(AccessMethod.REQUEST_ID_KEY, requestId);
        		}
        		return new ResultHolder<JSONObject>(
        				object.getInt("statusCode"),object);
	        }
			ActResponse response = null;
			if(!resource.getType().equals(Resource.Type.ACTION))
			{
				response = invalidMethodErrorResponse(AccessMethod.Type.valueOf(
					AccessMethod.ACT), serviceProviderId, serviceId, 
						resourceId);
			} 
			else
			{
				if (parameters != null && parameters.length > 0) 
			    {
			           response = ((ActionResource) resource
			        		   ).act(parameters);
			           
			    }  else
			    {
			    	response = ((ActionResource) resource).act();
			    }
			}
    		if(requestId != null)
    		{
    			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
    		}
    		int status = response.getStatusCode();
    		JSONObject object = new JSONObject(response.getJSON());
    		
        	if(sessionKey.localID()!=0)
        	{				
        		object.remove("uri");
        		object.put("uri", uri);
        	}
        	return new ResultHolder<JSONObject>(status,object);
	    }
	    
		/**
		 * @inheritDoc
		 * 
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * subscribe(java.lang.String, java.lang.String, java.lang.String, 
		 * org.eclipse.sensinact.gateway.core.message.Recipient, org.json.JSONArray)
		 */
	    @Override
		public ResultHolder<JSONObject> subscribe(final String serviceProviderId,
		    final String serviceId, final String resourceId, 
		    final Recipient recipient, final JSONArray conditions)
		{  	
			return subscribe(null, serviceProviderId, serviceId, resourceId, 
				recipient, conditions);
		}

		/**
		 * @inheritDoc
		 * 
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * subscribe(java.lang.String, java.lang.String, java.lang.String, 
		 * org.eclipse.sensinact.gateway.core.message.Recipient, org.json.JSONArray)
		 */
	    @Override
		public ResultHolder<JSONObject> subscribe(String requestId, 
			final String serviceProviderId,
		    final String serviceId, final String resourceId, 
		    final Recipient recipient, 
		    final JSONArray conditions)
		{  	
	    	SessionKey sessionKey = SensiNact.this.sessions.get(
	    			new KeyExtractor<KeyExtractorType>(KeyExtractorType.TOKEN, 
	    				this.getSessionId()));
	    	
			String uri =  getUri((sessionKey.localID()!=0), serviceProviderId, 
					serviceId, resourceId);
			
	        Resource resource = this.resource(serviceProviderId, serviceId, 
	        		resourceId);

	        if(resource == null)
	        {
	        	if(sessionKey.localID()!=0)
	        	{
	        		AccessMethodResponse response = AccessMethodResponse.error(mediator, 
	        			uri, AccessMethod.SUBSCRIBE,SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
	        			new StringBuilder().append("Resource ").append(
	        				uri).append("not found").toString(), null);
	        		
			        if(requestId != null)
			        {
			        	response.put(AccessMethod.REQUEST_ID_KEY, requestId);
			        }
			        return new ResultHolder<JSONObject>(
			        	SnaErrorfulMessage.NOT_FOUND_ERROR_CODE,
			        	new JSONObject(response.getJSON()));
	        	}
        		JSONObject object =  AccessController.doPrivileged(
	        	new PrivilegedAction<JSONObject>()
				{
					@Override
		            public JSONObject run()
		            {
				    	return SensiNact.this.subscribe(SensiNactSession.this.getSessionId(),
				    		serviceProviderId, serviceId, resourceId, recipient,
				    		conditions);
		            }
				});
		        if(requestId != null)
		        {
		        	object.put(AccessMethod.REQUEST_ID_KEY, requestId);
		        }
		        return new ResultHolder<JSONObject>(
		        		object.getInt("statusCode"),object);
	        }	
			SubscribeResponse response = null;
        	if(!resource.getType().equals(Resource.Type.ACTION))
			{
    	        Constraint constraint = null;
    	        if(conditions!=null && conditions.length()>0)
    	        {
    		        try 
    		        {
    					constraint = ConstraintFactory.Loader.load(
    							mediator.getClassLoader(), conditions);
    					
    				} catch (InvalidConstraintDefinitionException e)
    		        {
    					mediator.error(e);	
    				}
    	        }
				response = ((DataResource) resource
	        	        	).subscribe(recipient, (constraint==null
	        	        	?Collections.<Constraint>emptySet()
	        	        	:Collections.<Constraint>singleton(constraint)));						
			} else
			{
				response = invalidMethodErrorResponse(AccessMethod.Type.valueOf(
					AccessMethod.SUBSCRIBE), serviceProviderId, serviceId, 
						resourceId);
			}
	        if(requestId != null)
	        {
	        	response.put(AccessMethod.REQUEST_ID_KEY, requestId);
	        }
    		int status = response.getStatusCode();
    		JSONObject object = new JSONObject(response.getJSON());
    		
        	if(sessionKey.localID()!=0)
        	{				
        		object.remove("uri");
        		object.put("uri", uri);
        	}
        	return new ResultHolder<JSONObject>(status,object);
		}

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * unsubscribe(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
		 */
	    @Override
		public ResultHolder<JSONObject> unsubscribe(String serviceProviderId,
		     String serviceId, final String resourceId,  String subscriptionId)
		{ 
	    	return  unsubscribe(null, serviceProviderId, serviceId, 
	    			resourceId,  subscriptionId);
		}
	    
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * unsubscribe(java.lang.String, java.lang.String, java.lang.String, java.lang.String)
		 */
	    @Override
		public ResultHolder<JSONObject> unsubscribe(String requestId, 
				final String serviceProviderId,
		        final String serviceId, final String resourceId, 
		        final String subscriptionId)
		{ 
	    	SessionKey sessionKey = SensiNact.this.sessions.get(
	    			new KeyExtractor<KeyExtractorType>(KeyExtractorType.TOKEN, 
	    				this.getSessionId()));
	    	
			String uri =  getUri((sessionKey.localID()!=0), serviceProviderId, 
					serviceId, resourceId);
			
	        Resource resource = this.resource(serviceProviderId, serviceId, 
	        		resourceId);

	        if(resource == null)
	        {
	        	if(sessionKey.localID()!=0)
	        	{
	        		AccessMethodResponse response = AccessMethodResponse.error(mediator, 
	        			uri, AccessMethod.UNSUBSCRIBE, SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
	        			new StringBuilder().append("Resource ").append(uri
	        				).append("not found").toString(), null);

			        if(requestId != null)
			        {
			        	response.put(AccessMethod.REQUEST_ID_KEY, requestId);
			        }
			        return new ResultHolder<JSONObject>(
			        	SnaErrorfulMessage.NOT_FOUND_ERROR_CODE,
			        	new JSONObject(response.getJSON()));
	        	}
        		JSONObject object =  AccessController.doPrivileged(
	        	new PrivilegedAction<JSONObject>()
				{
					@Override
		            public JSONObject run()
		            {
				    	return SensiNact.this.unsubscribe(SensiNactSession.this.getSessionId(),
				    		serviceProviderId, serviceId, resourceId, subscriptionId);
		            }
				});
		        if(requestId != null)
		        {
		        	object.put(AccessMethod.REQUEST_ID_KEY, requestId);
		        }
		        return new ResultHolder<JSONObject>(
		        		object.getInt("statusCode"), object);
	        }
			UnsubscribeResponse response =null;
        	if(!resource.getType().equals(Resource.Type.ACTION))
			{ 	        
				response =  ((DataResource) resource).unsubscribe(subscriptionId);						
			} else
			{
				response = invalidMethodErrorResponse(AccessMethod.Type.valueOf(
					AccessMethod.UNSUBSCRIBE), serviceProviderId,serviceId, resourceId);
			}
	        if(requestId != null)
	        {
	        	response.put(AccessMethod.REQUEST_ID_KEY, requestId);
	        }
    		int status = response.getStatusCode();
    		JSONObject object = new JSONObject(response.getJSON());
    		
        	if(sessionKey.localID()!=0)
        	{				
        		object.remove("uri");
        		object.put("uri", uri);
        	}
        	return new ResultHolder<JSONObject>(status,object);
		}
	    
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#getAll()
		 */
		@Override
	    public ResultHolder<String> getAll()
	    {
			return this.getAll(null, null, null);
	    }

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getAll(org.eclipse.sensinact.gateway.core.FilteringDefinition)
		 */
		@Override
		public ResultHolder<String> getAll(FilteringDefinition 
				filterDefinition)
		{
			return getAll(null,  null, filterDefinition);
		}

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Endpoint#jsonAll(java.lang.String)
		 */
		@Override
	    public ResultHolder<String> getAll(String filter)
	    {
			 return getAll(null, filter, null);
	    }

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getAll(java.lang.String, org.eclipse.sensinact.gateway.core.FilteringDefinition)
		 */
		@Override
		public ResultHolder<String> getAll(
				final String filter,
		        final FilteringDefinition filterDefinition)
		{
			return getAll(null, filter, filterDefinition);
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getAll(java.lang.String, org.eclipse.sensinact.gateway.core.FilteringDefinition)
		 */
		@Override
		public ResultHolder<String> getAll(
				final String requestId,
				final String filter,
		        final FilteringDefinition filterDefinition)
		{
			 String result = AccessController.doPrivileged(
				new PrivilegedAction<String>()
			 {
				@Override
	            public String run()
	            {
			    	return SensiNact.this.getAll(
			    		SensiNactSession.this.getSessionId(),
			    		requestId, filter, filterDefinition);
	            }
			 });
			return new ResultHolder<String>(200,result);
		}

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getProviders()
		 */
		@Override
		public ResultHolder<String> getProviders()
		{
			return this.getProviders(null,null);
		}

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getProviders(org.eclipse.sensinact.gateway.core.FilteringDefinition)
		 */
		@Override
		public ResultHolder<String> getProviders(final FilteringDefinition 
				filterDefinition)
		{ 
			return getProviders(null,filterDefinition);
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getProviders(java.lang.String, org.eclipse.sensinact.gateway.core.FilteringDefinition)
		 */
		@Override
		public ResultHolder<String> getProviders(final String requestId, 
				final FilteringDefinition filterDefinition)
		{ 
			String result = AccessController.doPrivileged(
				 new PrivilegedAction<String>()
			 {
				@Override
	           public String run()
	           {
			    	return SensiNact.this.getProviders(
			    		SensiNactSession.this.getSessionId(), 
			    		requestId, filterDefinition);
	           }
			 });
			return new ResultHolder<String>(200,result);
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Endpoint#jsonProvider(java.lang.String)
		 */
		@Override
		public ResultHolder<String> getProvider(String serviceProviderId)
		{	 
	    	return getProvider(null, serviceProviderId);
		}

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Endpoint#jsonProvider(java.lang.String)
		 */
		@Override
		public ResultHolder<String> getProvider(final String requestId, 
				final String serviceProviderId)
		{	 
	    	SessionKey sessionKey = SensiNact.this.sessions.get(
	    			new KeyExtractor<KeyExtractorType>(KeyExtractorType.TOKEN, 
	    				this.getSessionId()));
	    	
			String uri =  getUri((sessionKey.localID()!=0), serviceProviderId);
			ServiceProvider provider = this.serviceProvider(serviceProviderId);
			
	        if(provider == null)
	        { 
	        	if(sessionKey.localID()!=0)
	        	{
	        		AccessMethodResponse response = AccessMethodResponse.error(mediator, 
	        			uri, AccessMethod.DESCRIBE, SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
	        			new StringBuilder().append("Service provider ").append(uri
	        				).append("not found").toString(), null);
	        		
	        		if(requestId != null)
	        		{
	        			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
	        		}
	        		return new ResultHolder<String>(
	        			SnaErrorfulMessage.NOT_FOUND_ERROR_CODE,
	        			response.getJSON());
	        	}
	        	JSONObject object =  AccessController.doPrivileged(
	    	    new PrivilegedAction<JSONObject>()
	    		{
	    			@Override
	    		    public JSONObject run()
	    		    {
				    	return SensiNact.this.getProvider(
				    		SensiNactSession.this.getSessionId(),
				    		serviceProviderId);
		            }
				});
	        	return new ResultHolder<String>(object.getInt(
	        			"statusCode"), object.toString());
	        }
	        StringBuilder builder = new StringBuilder();
	        builder.append("{");
    		if(requestId != null)
    		{
    			builder.append("\"");
    			builder.append(AccessMethod.REQUEST_ID_KEY);
    			builder.append("\" : \"");
    			builder.append(requestId);
    			builder.append("\",");
    		}
	        builder.append("\"type\": \"DESCRIBE_RESPONSE\", \"uri\":\"");
	        builder.append(uri);
	        builder.append("\",\"statusCode\": 200, \"response\":");
	        builder.append(provider.getDescription().getJSON());
	        builder.append("}");
	        return new ResultHolder<String>(200, builder.toString());
		}

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#getServices(java.lang.String)
		 */
		@Override
		public ResultHolder<String> getServices(final String serviceProviderId)
		{	
			return this.getServices(serviceProviderId, null);
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getServices(java.lang.String, org.eclipse.sensinact.gateway.core.FilteringDefinition)
		 */
		@Override
		public ResultHolder<String> getServices(final String serviceProviderId,
		        FilteringDefinition filterDefinition)
		{	    	
            return getServices(null,serviceProviderId, filterDefinition);
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getServices(java.lang.String, org.eclipse.sensinact.gateway.core.FilteringDefinition)
		 */
		@Override
		public ResultHolder<String> getServices(final String requestId,
				final String serviceProviderId,
		        FilteringDefinition filterDefinition)
		{
	    	SessionKey sessionKey = SensiNact.this.sessions.get(
	    			new KeyExtractor<KeyExtractorType>(KeyExtractorType.TOKEN, 
	    				this.getSessionId()));
	    	
			String uri =  getUri((sessionKey.localID()!=0), serviceProviderId);

			ServiceProvider provider= this.serviceProvider(serviceProviderId);
			String services = null;
			
	        if(provider == null)
	        {
	        	if(sessionKey.localID()!=0)
	        	{
	        		AccessMethodResponse response = AccessMethodResponse.error(mediator, 
	        			uri, AccessMethod.DESCRIBE, SnaErrorfulMessage.NOT_FOUND_ERROR_CODE,
	        			new StringBuilder().append("Service provider ").append(
	        				uri).append("not found").toString(), null);
	        		
	        		if(requestId != null)
	        		{
	        			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
	        		}
	        		return new ResultHolder<String>(
	        			SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, response.getJSON());
	        	}
	        	services = AccessController.doPrivileged(
	        		new PrivilegedAction<String>()
				{
					@Override
		            public String run()
		            {
				    	return SensiNact.this.getServices(
				    		SensiNactSession.this.getSessionId(),
				    		serviceProviderId);
		            }
				});
	        } else
	        {
		        StringBuilder servicesBuilder = new StringBuilder();
		        List<Service> servicesList = provider.getServices();
		        int index = 0;
		        int length = servicesList==null?0:servicesList.size();
		        for(;index < length;index++)
		        {
		        	if(index > 0)
		        	{
		        		servicesBuilder.append(",");
		        	}
		        	servicesBuilder.append("\"");
		        	servicesBuilder.append(servicesList.get(index).getName());
		        	servicesBuilder.append("\"");
		        }
		        services = servicesBuilder.toString();
	        	if(sessionKey.localID()!=0)
	        	{
	        		return new ResultHolder<String>(0,services);
	        	}
	        }
	        String result = null;
	        StringBuilder builder = new StringBuilder();
	        builder.append("{");
    		if(requestId != null)
    		{
    			builder.append("\"");
    			builder.append(AccessMethod.REQUEST_ID_KEY);
    			builder.append("\" : \"");
    			builder.append(requestId);
    			builder.append("\",");
    		}
        	if(filterDefinition != null)
    		{
        		builder.append("\"filter\":{");
        		builder.append("\"type\": \"");
        		builder.append(filterDefinition.type);
        		builder.append("\", \"definition\": \"");
        		builder.append(filterDefinition.filter);
        		builder.append("\"}");
        		
    			result = SensiNact.this.callPostFilter(
    				filterDefinition.type, filterDefinition.filter, 
    				new StringBuilder().append("[").append(services
    						).append("]").toString());
    		} else
    		{
    			result = new StringBuilder().append("[").append(
    				services).append("]").toString();
    		}
	        builder.append("\"type\": \"SERVICES_LIST\", \"uri\":\"");
	        builder.append(uri);
	        builder.append("\",\"statusCode\": 200, \"services\":");
	        builder.append(result);
	        builder.append("}");
            return new ResultHolder<String>(200, builder.toString());
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Endpoint#jsonService(java.lang.String, java.lang.String)
		 */
		@Override
		public ResultHolder<String> getService(final String serviceProviderId,
				final String serviceId)
		{   
	        return getService(null,serviceProviderId, serviceId);
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Endpoint#jsonService(java.lang.String, java.lang.String)
		 */
		@Override
		public ResultHolder<String> getService(final String requestId,
			final String serviceProviderId, final String serviceId)
		{   
	    	SessionKey sessionKey = SensiNact.this.sessions.get(
	    			new KeyExtractor<KeyExtractorType>(KeyExtractorType.TOKEN, 
	    				this.getSessionId()));
	    	
			String uri =  getUri((sessionKey.localID()!=0), serviceProviderId,
					serviceId);

			Service service= this.service(serviceProviderId, serviceId);
			
	        if(service == null)
	        {
	        	if(sessionKey.localID()!=0)
	        	{
	        		AccessMethodResponse response = AccessMethodResponse.error(mediator, 
	        			uri, AccessMethod.DESCRIBE, SnaErrorfulMessage.NOT_FOUND_ERROR_CODE,
	        			new StringBuilder().append("Service ").append(uri
	        				).append("not found").toString(), null);
	        		
	        		if(requestId != null)
	        		{
	        			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
	        		}
	        		return new ResultHolder<String>(
	        			SnaErrorfulMessage.NOT_FOUND_ERROR_CODE,
	        				response.getJSON());
	        	}
	        	JSONObject object =  AccessController.doPrivileged(
	    	    new PrivilegedAction<JSONObject>()
	    		{
	    			@Override
	    		    public JSONObject run()
	    		    {
				    	return SensiNact.this.getService(
				    		SensiNactSession.this.getSessionId(),
				    		serviceProviderId, serviceId);
		            }
				});
	        	return new ResultHolder<String>(object.getInt(
	        			"statusCode"), object.toString());
	        }
	        StringBuilder builder = new StringBuilder();
	        builder.append("{");
    		if(requestId != null)
    		{
    			builder.append("\"");
    			builder.append(AccessMethod.REQUEST_ID_KEY);
    			builder.append("\"");
    			builder.append(": \"");
    			builder.append(requestId);
    			builder.append("\",");
    		}
    		if(requestId != null)
    		{
    			builder.append("\"");
    			builder.append(AccessMethod.REQUEST_ID_KEY);
    			builder.append("\" : \"");
    			builder.append(requestId);
    			builder.append("\",");
    		}	        
	        builder.append("\"type\": \"DESCRIBE_RESPONSE\", \"uri\":\"");
	        builder.append(uri);
	        builder.append("\",\"statusCode\": 200, \"response\":");
	        builder.append(service.getDescription().getJSON());
	        builder.append("}");
	        return new ResultHolder<String>(200,builder.toString());
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Endpoint#
		 * jsonResources(java.lang.String, java.lang.String)
		 */
		@Override
		public ResultHolder<String> getResources(
				String serviceProviderId, String serviceId)
		{    
			return getResources(serviceProviderId, serviceId, null);
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getResources(java.lang.String, java.lang.String, 
		 * org.eclipse.sensinact.gateway.core.FilteringDefinition)
		 */
		@Override
		public ResultHolder<String> getResources(final String serviceProviderId,
		        final String serviceId, FilteringDefinition filterDefinition)
		{ 		 
            return getResources(null, serviceProviderId, serviceId,
            		filterDefinition);
		}		

		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getResources(java.lang.String, java.lang.String, 
		 * org.eclipse.sensinact.gateway.core.FilteringDefinition)
		 */
		@Override
		public ResultHolder<String> getResources(String requestId, 
			final String serviceProviderId, final String serviceId, 
			FilteringDefinition filterDefinition)
		{ 		 
	    	SessionKey sessionKey = SensiNact.this.sessions.get(
	    		new KeyExtractor<KeyExtractorType>(KeyExtractorType.TOKEN, 
	    			this.getSessionId()));
	    	
			String uri =  getUri((sessionKey.localID()!=0), serviceProviderId,
					serviceId);

			Service service= this.service(serviceProviderId, serviceId);
			String resources = null;
			
	        if(service == null)
	        {
	        	if(sessionKey.localID()!=0)
	        	{
	        		AccessMethodResponse response = AccessMethodResponse.error(mediator, 
	        			uri, AccessMethod.DESCRIBE, SnaErrorfulMessage.NOT_FOUND_ERROR_CODE,
	        			new StringBuilder().append("Service ").append(uri).append(
	        				"not found").toString(), null);
	        		
	        		if(requestId != null)
	        		{
	        			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
	        		}
	        		return new ResultHolder<String>(
	        			SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
	        				response.getJSON());
	        	}
	        	resources = AccessController.doPrivileged(
	        		new PrivilegedAction<String>()
				{
					@Override
		            public String run()
		            {
				    	return SensiNact.this.getResources(
				    		SensiNactSession.this.getSessionId(),
				    		serviceProviderId, serviceId);
		            }
				});
	        } else
	        {
		        StringBuilder resourcesBuilder = new StringBuilder();
		        List<Resource> resourcesList = service.getResources();
		        int index = 0;
		        int length = resourcesList==null?0:resourcesList.size();
		        for(;index < length;index++)
		        {
		        	if(index > 0)
		        	{
		        		resourcesBuilder.append(",");
		        	}
		        	resourcesBuilder.append("\"");
		        	resourcesBuilder.append(resourcesList.get(index).getName());
		        	resourcesBuilder.append("\"");
		        }
		        resources = resourcesBuilder.toString();
	        	if(sessionKey.localID()!=0)
	        	{
	        		return new ResultHolder<String>(0,resources);
	        	}
	        }
	        String result = null;
	        StringBuilder builder = new StringBuilder();
	        builder.append("{");

    		if(requestId != null)
    		{
    			builder.append("\"");
    			builder.append(AccessMethod.REQUEST_ID_KEY);
    			builder.append("\"");
    			builder.append(": \"");
    			builder.append(requestId);
    			builder.append("\",");
    		}
        	if(filterDefinition != null)
    		{
        		builder.append("\"filter\":{");
        		builder.append("\"type\": \"");
        		builder.append(filterDefinition.type);
        		builder.append("\", \"definition\": \"");
        		builder.append(filterDefinition.filter);
        		builder.append("\"}");
        		
    			result = SensiNact.this.callPostFilter(
    				filterDefinition.type, filterDefinition.filter, 
    				new StringBuilder().append("[").append(resources
    						).append("]").toString());
    		} else
    		{
    			result = new StringBuilder().append("[").append(
    				resources).append("]").toString();
    		}
	        builder.append("\"type\": \"RESOURCES_LIST\", \"uri\":\"");
	        builder.append(uri);
	        builder.append("\",\"statusCode\": 200, \"resources\":");
	        builder.append(result);
	        builder.append("}");
            return new ResultHolder<String>(200,builder.toString());
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getResource(java.lang.String, java.lang.String, java.lang.String)
		 */
		@Override
		public ResultHolder<String> getResource(
				final String serviceProviderId,
				final String serviceId,
		        final String resourceId)
		{   	
            return getResource(null,serviceProviderId, serviceId, resourceId);
		}
		
		/**
		 * @inheritDoc
		 *
		 * @see org.eclipse.sensinact.gateway.core.Session#
		 * getResource(java.lang.String, java.lang.String, java.lang.String)
		 */
		@Override
		public ResultHolder<String> getResource(
				final String requestId,
				final String serviceProviderId,
				final String serviceId,
		        final String resourceId)
		{      		 
    		SessionKey sessionKey = SensiNact.this.sessions.get(
    				new KeyExtractor<KeyExtractorType>(KeyExtractorType.TOKEN, 
    						this.getSessionId()));
    		
    		String uri = getUri((sessionKey.localID()!=0), serviceProviderId,
    				serviceId, resourceId);
    		
			Resource resource = this.resource(serviceProviderId, serviceId, resourceId);
    		
    		if(resource == null)
			{
	        	if(sessionKey.localID()!=0)
	        	{
	        		AccessMethodResponse response = AccessMethodResponse.error(mediator, 
	        			uri, AccessMethod.DESCRIBE, SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
	        		    new StringBuilder().append("Resource ").append(uri
	        		    	).append("not found").toString(), null);
	        		
	        		if(requestId != null)
	        		{
	        			response.put(AccessMethod.REQUEST_ID_KEY, requestId);
	        		}
	        		return new ResultHolder<String>(
	        			SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
	        			response.getJSON());
	        	 }
	        	JSONObject object =  AccessController.doPrivileged(
	    	    new PrivilegedAction<JSONObject>()
	    		{
	    			@Override
	    		    public JSONObject run()
	    		    {
				    	return SensiNact.this.getResource(
    			    		SensiNactSession.this.getSessionId(),
    			    		serviceProviderId, serviceId, resourceId);
    	            }
    			 });
	        	return new ResultHolder<String>(object.getInt(
	        			"statusCode"), object.toString());
			}
        	StringBuilder builder = new StringBuilder();
        	builder.append("{");
    		if(requestId != null)
    		{
    			builder.append("\"");
    			builder.append(AccessMethod.REQUEST_ID_KEY);
    			builder.append("\" : \"");
    			builder.append(requestId);
    			builder.append("\",");
    		}
        	builder.append("\"type\":\"DESCRIBE_RESPONSE\",\"uri\":\"");
        	builder.append(uri);
        	builder.append("\",\"statusCode\":200,\"response\":");
        	builder.append(resource.getDescription().getJSONDescription());
        	builder.append("}");        	
            return new ResultHolder<String>(200,builder.toString());
		}
	};
	
	/**
	 * endpoint of the local OSGi host environment
	 */
	final class RegistryEndpoint 
	{		
		/**
		 * @param publicKey
		 * @param filter
		 * @return
		 */
		private Collection<ServiceReference<SensiNactResourceModel>> 
		getReferences(SessionKey sessionKey, String filter)
		{
			AccessTree<? extends AccessNode> tree = sessionKey.getAccessTree();
			AccessMethod.Type describe = AccessMethod.Type.valueOf(
					AccessMethod.DESCRIBE);
			
			Collection<ServiceReference<SensiNactResourceModel>> result = 
					new ArrayList<ServiceReference<SensiNactResourceModel>>();
			
			Collection<ServiceReference<SensiNactResourceModel>> references = 
					null;  
			try
	        {
	            references = SensiNact.this.mediator.getContext(
	                ).getServiceReferences(SensiNactResourceModel.class,
	            	    filter);
	            Iterator<ServiceReference<SensiNactResourceModel>> iterator = 
						references.iterator();
	            
				while(iterator.hasNext())
				{
					ServiceReference<SensiNactResourceModel> reference = iterator.next();					
					String name = (String) reference.getProperty("name");
					Integer level = (Integer) reference.getProperty(name.concat(".DESCRIBE"));
					if(level == null)
					{
						level = new Integer(
							AccessLevelOption.OWNER.getAccessLevel().getLevel());
					}
					AccessNode node = sessionKey.getAccessTree().getRoot().get(
						UriUtils.getUri(new String[] {name}));
					
					if(node == null)
					{
						node = tree.getRoot();
					}
					if(node.getAccessLevelOption(describe
						).getAccessLevel().getLevel() >= level.intValue())
					{
						result.add(reference);
					}
				}
	        }
	        catch (InvalidSyntaxException e)
	        {
	            mediator.error(e.getMessage(),e);
	        }  
			return result;
		}
		
		/**
		 * @param publicKey
		 * @param filter
		 * @return
		 */
		private Set<ServiceProvider> serviceProviders(
				final SessionKey sessionKey, 
				String filter)
		{
			String activeFilter = "(lifecycle.status=ACTIVE)";
			String providersFilter = null;
			
			if(filter == null)
			{
				providersFilter = activeFilter;
				
			} else
			{
				StringBuilder filterBuilder = 
						new StringBuilder().append("(&");
				if(!filter.startsWith("("))
				{
					filterBuilder.append("(");					
				}
				filterBuilder.append(filter);
				if(!filter.endsWith(")"))
				{
					filterBuilder.append(")");					
				}
				filterBuilder.append(activeFilter);
				filterBuilder.append(")");
				providersFilter = filterBuilder.toString();
			}
			final String fltr = providersFilter;
						
			Set<ServiceProvider> serviceProviders =
			AccessController.<Set<ServiceProvider>>doPrivileged(
			new PrivilegedAction<Set<ServiceProvider>>()
			{
				@Override
				public Set<ServiceProvider> run()
				{									
					Collection<ServiceReference<SensiNactResourceModel>> 
					references = RegistryEndpoint.this.getReferences(sessionKey, 
							fltr);
					
					Iterator<ServiceReference<SensiNactResourceModel>> iterator= 
						references.iterator();
					
					Set<ServiceProvider> providers = new HashSet<ServiceProvider>();
					
					while(iterator.hasNext())
					{					
						ServiceReference<SensiNactResourceModel> ref = iterator.next();						
						SensiNactResourceModel model = SensiNact.this.mediator.getContext(
								).getService(ref);					
						ServiceProvider provider = null;
						try
						{
							provider = (ServiceProvider) model.getRootElement().getProxy(
									sessionKey.getAccessTree());
							
						}catch (ModelElementProxyBuildException e) 
						{
							SensiNact.this.mediator.error(e);
						}
						if(provider != null && provider.isAccessible())
						{
							providers.add(provider);
						}
					}			
					return providers;
				}
			});
			return serviceProviders;
		}

		/**
		 * @param publicKey
		 * @param serviceProviderName
		 * @return
		 */
		private ServiceProvider serviceProvider(SessionKey sessionKey, 
				final String serviceProviderName)
		{
			ServiceProvider provider = null;
			
			Set<ServiceProvider> providers = this.serviceProviders(
			    sessionKey, new StringBuilder().append("(name="
			    		).append(serviceProviderName).append(
			    				")").toString());

			if(providers == null || providers.size()!=1)
			{
				return provider;
			}
			provider = providers.iterator().next();
			return provider;
		}

		
	    /**
	     * @param publicKey
	     * @param serviceProviderName
	     * @param serviceName
	     * @return
	     */
	    private Service service(SessionKey sessionKey,  
	    	String serviceProviderName, String serviceName)
	    {
	    	ServiceProvider serviceProvider = serviceProvider(sessionKey, 
	    			serviceProviderName); 
	    	Service service = null;
	    	if(serviceProvider != null)
		    {
	    		service = serviceProvider.getService(serviceName);
		    }
		    return service;
	    }

	    /**
	     * @param publicKey
	     * @param serviceProviderName
	     * @param serviceName
	     * @param resourceName
	     * @return
	     */
	    private Resource resource(SessionKey sessionKey,  
	    	String serviceProviderName, String serviceName, 
	    	    String resourceName)
	    {
	    	Service service = this.service(sessionKey, serviceProviderName, 
	    			serviceName);
	    	Resource resource = null ;
	    	if(service != null)
	    	{
	    		resource = service.getResource(resourceName);
	    	}
		    return resource;
	    }

		/**
		 * @param publicKey
		 * @param resolveNamespace
		 * @param filter
		 * @param filterDefinition 
		 * @return
		 */
		private String getAll(SessionKey sessionKey, 
			boolean resolveNamespace, String filter)
	    {
			StringBuilder builder = new StringBuilder();
	        String prefix = resolveNamespace?new StringBuilder().append(
	        	SensiNact.this.namespace()).append(":").toString():"";
	        	
	        int index = -1;

        	Collection<ServiceReference<SensiNactResourceModel>> references = 
					RegistryEndpoint.this.getReferences(sessionKey, filter);	
			Iterator<ServiceReference<SensiNactResourceModel>> iterator = 
					references.iterator();

			AccessTree<? extends AccessNode> tree = sessionKey.getAccessTree();
			AccessMethod.Type describe = AccessMethod.Type.valueOf(AccessMethod.DESCRIBE);
			
			while(iterator.hasNext())
			{
	        	index++;
	        	ServiceReference<SensiNactResourceModel> reference = iterator.next();
	        	String name = (String) reference.getProperty("name");
	        	
	        	String provider = new StringBuilder().append(prefix).append(name).toString();
	        	String location =(String)reference.getProperty(LocationResource.LOCATION);	        	
	        	location = (location==null||location.length()==0)?defaultLocation:location;	        	
	            List<String> serviceList = (List<String>)reference.getProperty("services");

				builder.append(index>0?',':"");
				builder.append('{');
				builder.append("\"name\":");
				builder.append('"');
				builder.append(provider);
				builder.append('"');
				builder.append(",\"location\":");
				builder.append('"');
				builder.append(location);
				builder.append('"');
				builder.append(",\"services\":");
				builder.append('[');

	            int sindex = 0;
	            int slength = serviceList==null?0:serviceList.size();
	            for(;sindex < slength; sindex++)
	            {
	            	String service = serviceList.get(sindex);
	            	String serviceUri = UriUtils.getUri(new String[] {name,service});
	            	Integer serviceLevel = (Integer) reference.getProperty(
	            			service.concat(".DESCRIBE"));
					if(serviceLevel == null)
					{
						serviceLevel = new Integer(
							AccessLevelOption.OWNER.getAccessLevel().getLevel());
					}
					AccessNode node = sessionKey.getAccessTree(
							).getRoot().get(serviceUri);					
					if(node == null)
					{
						node = tree.getRoot();
					}
					int describeAccessLevel = 
							node.getAccessLevelOption(describe).getAccessLevel().getLevel();
					int serviceLevelLevel = serviceLevel.intValue();
					
					if(node.getAccessLevelOption(describe
						).getAccessLevel().getLevel() < serviceLevel.intValue())
					{
						continue;
					}
	            	List<String> resourceList = (List<String>) 
	            		reference.getProperty(service.concat(".resources"));
	            	
					builder.append(sindex>0?',':"");
					builder.append('{');
					builder.append("\"name\":");
					builder.append('"');
					builder.append(service);
					builder.append('"');
//					builder.append(",\"uri\":");
//					builder.append('"');
//					builder.append(new StringBuilder(
//						).append("/").append(provider).append(
//						"/").append(service));
//					builder.append('"');					
					builder.append(",\"resources\":");
					builder.append('[');
	            	
	                int rindex = 0;
	                int rlength = resourceList==null?0:resourceList.size();
	                for(;rindex < rlength; rindex++)
	                {
	                	String resource = resourceList.get(rindex);	 
	                	String resolvedResource = new StringBuilder().append(service
				            	).append(".").append(resource).toString();
		            	String resourceUri = UriUtils.getUri(new String[] {name, service, resource});
		            	Integer resourceLevel = (Integer) reference.getProperty(
		            			resolvedResource.concat(".DESCRIBE"));		            	
						if(resourceLevel == null)
						{
							resourceLevel = new Integer(
								AccessLevelOption.OWNER.getAccessLevel().getLevel());
						}
						node = sessionKey.getAccessTree().getRoot(
								).get(resourceUri);					
						if(node == null)
						{
							node = tree.getRoot();
						}
						if(node.getAccessLevelOption(describe
							).getAccessLevel().getLevel() < resourceLevel.intValue())
						{
							continue;
						}
	                	String type =(String) reference.getProperty(
	                			resolvedResource.concat(".type"));
						builder.append(rindex>0?',':"");
						builder.append('{');
						builder.append("\"name\":");
						builder.append('"');
						builder.append(resource);
						builder.append('"');
						builder.append(",\"type\":");
						builder.append('"');
						builder.append(type);
						builder.append('"');
//						builder.append(",\"uri\":");
//						builder.append('"');
//						builder.append(new StringBuilder(
//							).append("/").append(provider).append(
//							"/").append(service).append("/").append(
//									resource));
//						builder.append('"');
						builder.append('}');
	                }      
					builder.append(']');
					builder.append('}');      	
	            }
				builder.append(']');
				builder.append('}');
	        }
			String content = builder.toString();
			return content;
	    }
		
	    /**
	     * @param publicKey
	     * @param resolveNamespace
	     * @param filter
	     * @return
	     */
	    private String getProviders(SessionKey sessionKey, 
	    		boolean resolveNamespace, String filter)
	    {
	        String prefix = resolveNamespace?new StringBuilder().append(
	        	SensiNact.this.namespace()).append(":").toString():"";

        	Collection<ServiceReference<SensiNactResourceModel>> references = 
					this.getReferences(sessionKey, filter);	
			Iterator<ServiceReference<SensiNactResourceModel>> iterator = 
					references.iterator();

			StringBuilder builder = new StringBuilder();
			int index=0;
			while(iterator.hasNext())
			{
				ServiceReference<SensiNactResourceModel> reference = iterator.next();
	        	String name = (String)reference.getProperty("name");	        	
	        	String provider = new StringBuilder().append(prefix).append(
	        			name).toString();
	        	if(index > 0)
	        	{
		            builder.append(",");
	        	}
	        	builder.append('"');
	            builder.append(provider);
	        	builder.append('"');
	            index++;
	        }
			String content = builder.toString();
			return content;
	    }
	}; 
	
	//********************************************************************//
	//						ABSTRACT DECLARATIONS						  //
	//********************************************************************//
	
	//********************************************************************//
	//						STATIC DECLARATIONS		      				  //
	//********************************************************************//

	/**
	 * Retrieves or creates a namespace for the instance of sensiNact whose 
	 * {@link Mediator} is passed as parameter
	 * 
	 * @param mediator the {@link Mediator} allowing to retrieve the 
	 * namespace if it exists
	 * 
	 * @return the namespace for the instance of sensiNact with the 
	 * specified {@link Mediator}
	 * 
	 */
	private static final String namespace(Mediator mediator)
	{
		String prop = (String) mediator.getProperty(Core.NAMESPACE_PROP);
		if(prop == null)
		{
			prop = new StringBuilder().append("sNa").append(Math.round(
				(float)(System.currentTimeMillis()/100000L))+ 
					mediator.hashCode()).toString();
		}
		return prop;
	}
	
	protected static final int LOCAL_ID = 0;

	//********************************************************************//
	//						INSTANCE DECLARATIONS						  //
	//********************************************************************//

	final AccessTree<? extends AccessNode> anonymousTree;
	final Sessions sessions;
	
	Mediator mediator;
	private RegistryEndpoint registry;
	
	private volatile int count = LOCAL_ID+1;
	private final String namespace;
	private final String defaultLocation;
	
    /**
     * Constructor
     * 
     * @param mediator the extended {@link Mediator} allowing the 
     * {@link Core} to be instantiated to interact with the OSGi host 
     * environment
     * 
     * @throws SecuredAccessException 
     * @throws BundleException 
     */
    public SensiNact(final Mediator mediator) 
    		throws SecuredAccessException, BundleException
    {
    	this.namespace = SensiNact.namespace(mediator);
    	SecuredAccess securedAccess = null;
    	
		ServiceLoader<SecuredAccessFactory> serviceLoader =
			ServiceLoader.load(SecuredAccessFactory.class, 
					mediator.getClassLoader());

		Iterator<SecuredAccessFactory> iterator = serviceLoader.iterator();

		if (iterator.hasNext())
		{
			SecuredAccessFactory factory = iterator.next();
			if (factory != null)
			{
				securedAccess = factory.newInstance(mediator);
			}
		}
		if (securedAccess == null)
		{
			throw new BundleException("A SecuredAccess service was excepted");
		}		
		securedAccess.createAuthorizationService();
		final SecuredAccess sa = securedAccess;
		
		AccessController.doPrivileged(new PrivilegedAction<Void>() 
		{
			@Override
			public Void run() 
			{
				mediator.register(sa, SecuredAccess.class, null);
				return null;
			}
		});
		
        this.defaultLocation = ModelInstance.defaultLocation(mediator);
        this.sessions = new Sessions();

		this.anonymousTree = mediator.callService(
		SecuredAccess.class, new Executable<SecuredAccess, 
		AccessTree<? extends AccessNode>>() 
		{
			@Override
			public AccessTree<? extends AccessNode> execute(
					SecuredAccess securedAccess)
			        throws Exception
			{
				return securedAccess.getUserAccessTree(
						SecuredAccess.ANONYMOUS_PKEY);
			}
		});
        this.mediator = mediator;
        this.registry = new RegistryEndpoint();
    }
    
    protected final String callPostFilter(final String filterType, 
    	final String filter, final Object result)
    {
    	return SensiNact.this.mediator.callService(
    		Filtering.class, String.format("(type=%s)", 
    		filterType), new Executable<Filtering, String>()
			{
				@Override
				public String execute(Filtering postFilter)
				        throws Exception
				{
					if(postFilter.handle(filterType))
					{
						return postFilter.apply(filter, result);
					}
					return null;
				}
			}
    	);
    }
    
    /**
     * @param p
     * @param f
     * @param e
     * @return
     */
    private final <R,P> R doPrivilegedService(
    		final Class<P> p,
    		final String f,
    		final Executable<P,R> e)
    {
    	R r= AccessController.<R>doPrivileged(new PrivilegedAction<R>()
		{
			@Override
			public R run()
			{
				return mediator.callService(p, f, e);				
			}
		});
    	return r;
    }

    /**
     * @param p
     * @param f
     * @param e
     * @return
     */
    private final <P> Void doPrivilegedVoidServices(
    		final Class<P> p,
    		final String f,
    		final Executable<P,Void> e)
    {
    	return AccessController.<Void>doPrivileged(
    			new PrivilegedAction<Void>()
		{
			@Override
			public Void run()
			{
				mediator.<P>callServices(p, f, e);	
				return null;
			}
		});
    }

    /**
     * @return
     */
    private final AccessTree<?> getAnonymousTree()
    {
    	AccessTree<?> tree = null;
		if(MutableAccessTree.class.isAssignableFrom(
				this.anonymousTree.getClass()))
		{
			tree = ((MutableAccessTree<?>)
				this.anonymousTree).clone();
		} else
		{
			tree = this.anonymousTree;
		}
		return tree;
    }
    
    /**
     * @param publicKey
     * 
     * @return
     */
    private final AccessTree<?> getUserAccessTree(final String publicKey)
    {
    	AccessTree<? extends AccessNode> tree = null;
		if(publicKey != null && !publicKey.startsWith(SecuredAccess.ANONYMOUS_PKEY))
		{
			tree =  doPrivilegedService(SecuredAccess.class, null,
			new Executable<SecuredAccess, AccessTree<? extends AccessNode>>() 
			{
				@Override
				public AccessTree<? extends AccessNode> execute(
					SecuredAccess securedAccess) throws Exception
				{
					AccessTree<? extends AccessNode> tree =
						securedAccess.getUserAccessTree(publicKey);					
					return tree;
				}
			});				
		}
		if(tree == null)
		{
			tree = getAnonymousTree();
		}
		return tree;
    }
    
	/**
	 * @inheritDoc
	 *
	 * @see org.eclipse.sensinact.gateway.core.Core#
	 * getSession(org.eclipse.sensinact.gateway.core.security.Authentication)
	 */
	@Override
	public Session getSession(final Authentication<?> authentication)
			throws InvalidKeyException, DataStoreException, InvalidCredentialException {

		Session session = null;

		if(authentication == null) 
		{
            return this.getAnonymousSession();
            
        } else if(Credentials.class.isAssignableFrom(authentication.getClass()))
        {
            UserKey userKey = this.doPrivilegedService(AuthenticationService.class, null,
                        new Executable<AuthenticationService, UserKey>() 
            {
                @Override
                public UserKey execute(AuthenticationService service)
                        throws Exception 
                {
                    return service.buildKey((Credentials) authentication);
                }
            });
            if(userKey == null) 
            {
                throw new InvalidCredentialException("Invalid credentials");
            }
            String pkey = userKey.getPublicKey();
            session = this.sessions.getSessionFromPublicKey(pkey);
			
			if (session == null)
			{
			    AccessTree<? extends AccessNode> tree = this.getUserAccessTree(pkey);
				SessionKey sessionKey = new SessionKey(mediator, LOCAL_ID, 
						SensiNact.this.nextToken(), tree);
				sessionKey.setUserKey(userKey);
				session = new SensiNactSession(sessionKey.getToken());
				sessions.put(sessionKey, session);	
			}
		} else if(AuthenticationToken.class.isAssignableFrom(authentication.getClass())) 
		{
			session = this.getSession(((AuthenticationToken) authentication
					).getAuthenticationMaterial());
		}
		return session;
	}
	
	/**
	 * @inheritDoc
	 *
	 * @see org.eclipse.sensinact.gateway.core.Core#getSession(java.lang.String)
	 */
	@Override
	public Session getSession(final String token)
	{
		Session session = this.sessions.getSessionFromToken(token);
		return session;
	}
	
	/**
	 * @inheritDoc
	 *
	 * @see org.eclipse.sensinact.gateway.core.Core#getAnonymousSession()
	 */
	@Override
	public Session getAnonymousSession() 
	{
		AccessTree<?> tree = this.getUserAccessTree(null);
				
		count++;
		String pkey = new StringBuilder().append(
			SecuredAccess.ANONYMOUS_PKEY).append("_"
				).append(count).toString();

		SessionKey sessionKey = new SessionKey(mediator, 
			LOCAL_ID, this.nextToken(), tree);
		
		sessionKey.setUserKey(new UserKey(pkey));		
		Session session = new SensiNactSession(sessionKey.getToken());
		
		this.sessions.put(sessionKey,session);
		return session;
	}

	/**
	 * @inheritDoc
	 *
	 * @see org.eclipse.sensinact.gateway.core.Core#
	 * getApplicationSession(java.lang.String)
	 */
	@Override
	public Session getApplicationSession(final Mediator mediator, 
			final String privateKey)
	{
		SessionKey skey = this.doPrivilegedService(
		SecuredAccess.class, null, new Executable<SecuredAccess, SessionKey>() 
		{
			@Override
			public SessionKey execute(SecuredAccess securedAccess)
			        throws Exception
			{
			    String publicKey = securedAccess.getApplicationPublicKey(
						privateKey);
			    AccessTree<? extends AccessNode> tree = null;
			    
				if(publicKey == null)
				{
					count++;
					publicKey = new StringBuilder().append(
						SecuredAccess.ANONYMOUS_PKEY).append("_"
							).append(count).toString();

					tree = SensiNact.this.getAnonymousTree();
					
				} else
				{
					tree = securedAccess.getApplicationAccessTree(
							publicKey);
				}
				SessionKey sessionKey = new SessionKey(mediator, 
					LOCAL_ID, SensiNact.this.nextToken(), tree);
				
				sessionKey.setUserKey(new UserKey(publicKey));
				return sessionKey;
			}
		});		
		Session session = new SensiNactSession(skey.getToken());		
		sessions.put(skey, session);
		return session;
	}
	
	/**
	 * @inheritDoc
	 *
	 * @see org.eclipse.sensinact.gateway.core.Core#namespace()
	 */
	@Override
	public String namespace()
	{
		return this.namespace;
	}
    

	/**
	 * @inheritDoc
	 *
	 * @see org.eclipse.sensinact.gateway.core.Core#
	 * registerAgent(org.eclipse.sensinact.gateway.common.bundle.Mediator, org.eclipse.sensinact.gateway.core.message.MidAgentCallback, org.eclipse.sensinact.gateway.core.message.SnaFilter)
	 */
	@Override
	public String registerAgent(final Mediator mediator, 
			final MidAgentCallback callback, final SnaFilter filter)
	{			
		final Bundle bundle = mediator.getContext().getBundle();

		final String bundleIdentifier = this.doPrivilegedService(
		BundleValidation.class, null, new Executable<BundleValidation,String>()
	    {
			@Override
			public String execute(BundleValidation bundleValidation)
					throws Exception
			{
				return bundleValidation.check(bundle);
			}
	    });    	
    	final String agentKey = this.doPrivilegedService(
		SecuredAccess.class, null, new Executable<SecuredAccess, String>()
	    {
			@Override
			public String execute(SecuredAccess securedAccess)
					throws Exception
			{
				return securedAccess.getAgentPublicKey(bundleIdentifier);
			}
	    });    	
		final SnaAgentImpl agent = SnaAgentImpl.createAgent(
			mediator, callback, filter, agentKey);
		
		String identifier = new StringBuilder().append(
			"agent_").append(agent.hashCode()).toString();
		
		final Dictionary<String,Object> props = new Hashtable<String,Object>();
		props.put("org.eclipse.sensinact.gateway.agent.id", identifier);
	    props.put("org.eclipse.sensinact.gateway.agent.local", true);
	    
	    AccessController.<Void>doPrivileged(new PrivilegedAction<Void>()
		{
			@Override
			public Void run()
			{
				agent.start(props);
				return null;
			}
		});
		return identifier;
	}
	
	/**
	 * Unregisters the {@link SnaAgent} whose identifier
	 * is passed as parameter
	 * 
	 * @param identifier
	 * 		the identifier of the {@link SnaAgent} to 
	 * 		register
	 */
	public void unregisterAgent(final String identifier)
	{
		doPrivilegedService(SnaAgent.class,new StringBuilder(
		).append("(&(org.eclipse.sensinact.gateway.agent.id=").append(
		identifier).append(")(org.eclipse.sensinact.gateway.agent.local=true))"
		).toString(), new Executable<SnaAgent,Void>()
		{
			@Override
			public Void execute(SnaAgent agent) throws Exception 
			{
				agent.stop();
				return null;
			}			
		});

		doPrivilegedVoidServices(RemoteCore.class, null, 
		new Executable<RemoteCore,Void>()
		{
			@Override
			public Void execute(RemoteCore remoteCore) throws Exception 
			{
				remoteCore.endpoint().unregisterAgent(identifier);
				return null;
			}			
		});
	} 

	/**
	 * @inheritDoc
	 *
	 * @see org.eclipse.sensinact.gateway.core.Core#
	 * createRemoteCore(org.eclipse.sensinact.gateway.core.AbstractRemoteEndpoint)
	 */
	@Override
	public void createRemoteCore(final AbstractRemoteEndpoint remoteEndpoint)
	{   
		count++;
		final RemoteSensiNact remoteCore = new RemoteSensiNact(
    	mediator, remoteEndpoint, new LocalEndpoint(count)
		{
    		private Map<String, Session> remoteSessions = 
    				new HashMap<String, Session>();
    		
			private Session createSession(final String publicKey)
			{
				AccessTree<? extends AccessNode> tree = 
					SensiNact.this.getUserAccessTree(publicKey);
					
				SessionKey sessionKey = new SessionKey(mediator, localID(), 
					SensiNact.this.nextToken(), tree);
				
				sessionKey.setUserKey(new UserKey(publicKey));				
				Session session = new SensiNactSession(sessionKey.getToken());				
				SensiNact.this.sessions.put(sessionKey, session, 
					remoteEndpoint);
				
				this.remoteSessions.put(publicKey, session);
				return session;
			}
			
			@Override
			public Session getSession(String publicKey)
			{
				String filteredKey = publicKey;
				if(SecuredAccess.ANONYMOUS_PKEY.equals(publicKey))
				{
					filteredKey = new StringBuilder().append(
					SecuredAccess.ANONYMOUS_PKEY).append("_R").append(
								localID()).toString();
				}
				Session session = this.remoteSessions.get(filteredKey);				
				if(session == null)
				{
					session = createSession(filteredKey);
				}
				return session;
			}
			
			@Override
			void closeSession(String publicKey)
			{
				String filteredKey = publicKey;
				if(SecuredAccess.ANONYMOUS_PKEY.equals(publicKey))
				{
					filteredKey = new StringBuilder().append("remote_").append(
						SecuredAccess.ANONYMOUS_PKEY).append("_").append(
								localID()).toString();
				}
				this.remoteSessions.remove(filteredKey);
			}

			@Override
			void close()
			{
				this.remoteSessions.clear();
			}
		}); 
		
    	remoteCore.endpoint().onConnected(new Executable<String,Void>()
		{
			@Override
			public Void execute(String namespace) throws Exception
			{
		    	remoteCore.open(namespace);
				return null;
			}
		});  
    	
    	remoteCore.endpoint().onDisconnected(new Executable<String,Void>()
		{
			@Override
			public Void execute(String namespace) throws Exception
			{
		    	remoteCore.close();
				return null;
			}
		}); 
    	
    	//TODO: alert the requirer
		if(!remoteCore.connect())
		{
			return;
		}
    
    	Collection<ServiceReference<SnaAgent>> serviceReferences = null;
		try
		{
			serviceReferences = this.mediator.getContext(
			).getServiceReferences(SnaAgent.class, 
			"(org.eclipse.sensinact.gateway.agent.local=true)");
		}
		catch (InvalidSyntaxException e)
		{
			this.mediator.error(e);
		}
    	if(serviceReferences== null || serviceReferences.isEmpty())
    	{
    		return;
    	}
		Iterator<ServiceReference<SnaAgent>> iterator = 
				serviceReferences.iterator();
		SnaAgent agent = null;
		
		while(iterator.hasNext())
		{
			ServiceReference<SnaAgent> serviceReference = iterator.next();
			if((agent =  this.mediator.getContext().getService(
					serviceReference))!=null)
	    	{
	    		try
	    		{
	    			String identifierProp = (String)
	    				serviceReference.getProperty(
	    				"org.eclipse.sensinact.gateway.agent.id");
	    			
	    			((SnaAgentImpl)agent).registerRemote(
	    				remoteCore, identifierProp);
	    			
	    		}catch(Exception e)
	    		{
	    			continue;
	    			
	    		} finally
	    		{
	    			if(agent != null)
	    			{
	    				agent = null;
	    				this.mediator.getContext().ungetService(
	    						serviceReference);
	    			}
	    		}
	    	}
		}
	}	

	/**
	 * Unregisters the {@link RemoteCore} whose String namespace
	 * is passed as parameter
	 * 
	 * @param namespace the String namespace of the {@link RemoteCore}
	 * to be unregistered
	 */
	protected void unregisterEndpoint(final String namespace)
	{
		if(namespace == null)
		{
			return;
		}		
		this.doPrivilegedService(RemoteCore.class,
		String.format("(namespace=%s)",namespace), 
		new Executable<RemoteCore,Void>()
		{
			@Override
			public Void execute(RemoteCore remoteCore) 
				throws Exception
			{					
				remoteCore.close();
				return null;
			}			
		});
	}

    /**
     * Returns the Set of available {@link ServiceProvider}s compliant 
     * to the LDAP formated filter passed as parameter
     * 
     * @param identifier the String identifier of the {@link Session} 
     * requiring the list of available service providers
     * @param filter the String LDAP formated filter 
     * 
     * @return the Set of available {@link ServiceProvider}s compliant to
     * the specified filter and for the specified {@link Session}
     */
	protected Set<ServiceProvider> serviceProviders(String identifier, String filter)
	{
		final SessionKey sessionKey = sessions.get(
				new KeyExtractor<KeyExtractorType>(
					KeyExtractorType.TOKEN,identifier));
			
		Set<ServiceProvider> set = new HashSet<ServiceProvider>();
		set.addAll(this.registry.serviceProviders(sessionKey,
				filter));
		return set;
	}
		
	/**
     * Returns the {@link ServiceProvider} whose String identifier is 
     * passed as parameter
     * 
     * @param identifier the String identifier of the {@link Session} 
     * requiring the service provider
     * @param serviceProviderId the String identifier of the service provider
     * 
     * @return the {@link ServiceProvider}
     */
	protected ServiceProvider serviceProvider(String identifier, 
			String serviceProviderId)
	{
		final SessionKey sessionKey = sessions.get(
				new KeyExtractor<KeyExtractorType>(
					KeyExtractorType.TOKEN,identifier));
			
		return this.registry.serviceProvider(sessionKey,
				serviceProviderId);
	}

	/**
     * Returns the {@link Service} whose String identifier is passed as 
     * parameter, held by the specified service provider
     * 
     * @param identifier the String identifier of the {@link Session} 
     * requiring the service
     * @param serviceProviderId the String identifier of the service provider
     * holding the service
     * @param servideId the String identifier of the service
     * 
     * @return the {@link Service}
     */
	protected Service service(String identifier, String serviceProviderId,
	        String serviceId)
	{
		final SessionKey sessionKey = sessions.get(
				new KeyExtractor<KeyExtractorType>(
					KeyExtractorType.TOKEN,identifier));
			
		return this.registry.service(sessionKey,
				serviceProviderId, serviceId);
	}

	/**
     * Returns the {@link Resource} whose String identifier is passed as 
     * parameter, held by the specified service provider and service
     * 
     * @param identifier the String identifier of the {@link Session} 
     * requiring the resource
     * @param serviceProviderId the String identifier of the service provider
     * holding the service providing the resource
     * @param servideId the String identifier of the service providing the 
     * resource
     * @param resourceId the String identifier of the resource
     * 
     * @return the {@link Resource}
     */
	protected Resource resource(String identifier, String serviceProviderId,
	        String serviceId, String resourceId)
	{
		final SessionKey sessionKey = sessions.get(
				new KeyExtractor<KeyExtractorType>(
					KeyExtractorType.TOKEN,identifier));
			
		return this.registry.resource(sessionKey,
				serviceProviderId, serviceId, resourceId);
	}
	
	/**
	 * Executes the {@link Executable} passed as parameter which 
	 * expects a {@link RemoteCore} parameter, and returns its JSON 
	 * formated execution result
	 * 
	 * @param serviceProviderId the String identifier of the 
	 * service provider whose prefix allows to identified the
	 * targeted  {@link RemoteCore}
	 * @param executable the {@link Executable} to be executed
	 * 
	 * @return the JSON formated result of the {@link Executable}
	 * execution
	 */
	private <F> F remoteCoreInvocation(String serviceProviderId,
			Executable<RemoteCore,F> executable)
	{
		String[] serviceProviderIdElements = serviceProviderId.split(":");
		String domain = serviceProviderIdElements[0];
		F object = null;
		
		if(serviceProviderIdElements.length==1 || domain.length()==0)
		{
			return (F)null;
		}	
		object = mediator.callService(RemoteCore.class, new StringBuilder(
			).append("(namespace=").append(domain).append(")").toString(), 
				executable);
		
		return object;
	}
	
	 /**
     * Returns the JSON formated description of the resource whose String
     * identifier is passed as parameter, and held by the service 
     * provider and service whose String identifiers are also passed as 
     * parameter
     * 
     * @param identifier the String identifier of the {@link Session} requiring 
     * the resource description 
     * @param serviceProviderId the String identifier of the 
     * service provider holding the service, providing the resource
     * @param serviceId the String identifier of the service providing
     * the resource
     * @param resourceId the String identifier  of the resource 
     * to return the description of
     * 
     * @return the JSON formated description of the specified resource
     */
	protected JSONObject getResource(String identifier, 
		final String serviceProviderId, final String serviceId, 
		final String resourceId)
	{
		final SessionKey sessionKey = sessions.get(
				new KeyExtractor<KeyExtractorType>(
					KeyExtractorType.TOKEN,identifier));
			
		JSONObject object = remoteCoreInvocation(serviceProviderId, 
				new Executable<RemoteCore,JSONObject>()
		{
			@Override
			public JSONObject execute(RemoteCore connector)
					throws Exception 
			{
				if(connector == null)
				{
					return null;
				}
				return new JSONObject(connector.endpoint().getResource(
				sessionKey.getPublicKey(), serviceProviderId.substring(
					serviceProviderId.indexOf(':')+1), serviceId, 
					    resourceId));
			}
		});
		if(object == null)
		{
			AccessMethodResponse response = AccessMethodResponse.error(
			   mediator, UriUtils.getUri(new String[]{serviceProviderId}),
			   AccessMethod.DESCRIBE, SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
				   "sensiNact service provider '" + serviceProviderId 
				   + "' not found",null);
			
			object = new JSONObject(response.getJSON());
		}
		return object;
	}

	/**
     * Returns the JSON formated list of available resources, for the service 
     * and service provider whose String identifiers are passed as parameter
     * 
     * @param identifier the String identifier of the {@link Session} requiring 
     * the description 
     * @param serviceProviderId the String identifier of the 
     * service provider holding the service
     * @param serviceId the String identifier of the service providing 
     * the resources
     * 
     * @return the JSON formated list of available resources for the 
     * specified service and service provider
     */
	protected String getResources(String identifier, 
			final String serviceProviderId, final String serviceId) 
	{
		final SessionKey sessionKey = sessions.get(
				new KeyExtractor<KeyExtractorType>(
					KeyExtractorType.TOKEN,identifier));
			
		String object = remoteCoreInvocation(serviceProviderId, 
				new Executable<RemoteCore,String>()
		{
			@Override
			public String execute(RemoteCore connector)throws Exception 
			{
				if(connector == null)
				{
					return null;
				}
				return connector.endpoint().getResources(sessionKey.getPublicKey(), 
					serviceProviderId.substring(serviceProviderId.indexOf(':')+1), 
					serviceId);
			}
		});
		if(object == null)
		{
		    return AccessMethodResponse.error(mediator, UriUtils.getUri(
			    new String[]{serviceProviderId}), AccessMethod.DESCRIBE, 
				    SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
					"sensiNact service provider '" + serviceProviderId 
					+ "' not found",null).getJSON();
		}
		return object;
	}

	/**
     * Returns the JSON formated description of the service whose String
     * identifier is passed as parameter, and held by the specified service 
     * provider
     * 
     * @param identifier the String identifier of the {@link Session} requiring 
     * the service description 
     * @param serviceProviderId the String identifier of the  service provider 
     * holding the service
     * @param serviceId the String identifier of the service to return the 
     * description of
     * 
     * @return the JSON formated description of the specified service
     */
	protected JSONObject getService(String identifier, 
			final String serviceProviderId, final String serviceId) 
	{
		final SessionKey sessionKey = sessions.get(
				new KeyExtractor<KeyExtractorType>(
					KeyExtractorType.TOKEN,identifier));
			
		JSONObject object = remoteCoreInvocation(serviceProviderId, 
		new Executable<RemoteCore,JSONObject>()
		{
			@Override
			public JSONObject execute(RemoteCore connector)
					throws Exception 
			{
				if(connector == null)
				{
					return null;
				}
				return new JSONObject(connector.endpoint().getService(
					sessionKey.getPublicKey(), serviceProviderId.substring(
					serviceProviderId.indexOf(':')+1), 
 					   serviceId));
			}
		});
		if(object == null)
		{
		    AccessMethodResponse response = AccessMethodResponse.error(mediator, UriUtils.getUri(
			    new String[]{serviceProviderId}), AccessMethod.DESCRIBE, 
				    SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
					"sensiNact service provider '" + serviceProviderId 
					+ "' not found",null);
		    object = new JSONObject(response.getJSON());
		}
		return object;
	}

	/**
     * Returns the JSON formated list of available services for the service 
     * provider whose String identifier is passed as parameter
     * 
     * @param identifier the String identifier of the {@link Session} requiring 
     * the list of available services
     * @param serviceProviderId the String identifier of the  service provider 
     * holding the services
     * 
     * @return the JSON formated list of available services for the 
     * specified service provider
     */
	protected String getServices(String identifier, 
			final String serviceProviderId)
    {
		final SessionKey sessionKey = sessions.get(
				new KeyExtractor<KeyExtractorType>(
					KeyExtractorType.TOKEN,identifier));
			
		String object = remoteCoreInvocation(serviceProviderId, 
		new Executable<RemoteCore,String>()
		{
			@Override
			public String execute(RemoteCore connector)
					throws Exception 
			{
				if(connector == null)
				{
					return null;
				}
				return connector.endpoint().getServices(
					sessionKey.getPublicKey(), serviceProviderId.substring(
						serviceProviderId.indexOf(':')+1));
			}
		});
		if(object == null)
		{
		    return AccessMethodResponse.error(mediator, UriUtils.getUri(
			    new String[]{serviceProviderId}), AccessMethod.DESCRIBE, 
				    SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
					"sensiNact service provider '" + serviceProviderId 
					+ "' not found",null).getJSON();
		}
		return object;
	}

	/**
     * Returns the JSON formated description of the service provider whose
     * String identifier is passed as parameter
     * 
     * @param identifier the String identifier of the {@link Session} 
     * requiring the service provider description
     * @param serviceProviderId the String identifier of the service provider
     * to return the description of
     * 
     * @return the JSON formated description of the specified service provider
     */
	protected JSONObject getProvider(String identifier,  
			final String serviceProviderId)
	{
		final SessionKey sessionKey = sessions.get(
			new KeyExtractor<KeyExtractorType>(
				KeyExtractorType.TOKEN,identifier));
			
		JSONObject object = remoteCoreInvocation(serviceProviderId,
		new Executable<RemoteCore, JSONObject>()
		{
			@Override
			public JSONObject execute(RemoteCore connector)
					throws Exception 
			{
				if(connector == null)
				{
					return null;
				}
				return new JSONObject(connector.endpoint().getProvider(
				sessionKey.getPublicKey(), serviceProviderId.substring(
					serviceProviderId.indexOf(':')+1)));
			}
		});
		if(object == null)
		{
			AccessMethodResponse response =  AccessMethodResponse.error(mediator, UriUtils.getUri(
			    new String[]{serviceProviderId}), AccessMethod.DESCRIBE, 
				    SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
					"sensiNact service provider '" + serviceProviderId 
					+ "' not found",null);
			
			object = new JSONObject(response.getJSON());
		}
		return object;
	}

	/** 
     * Invokes the UNSUBSCRIBE access method on the resource whose String 
     * identifier is passed as parameter, held by the specified service 
     * provider and service
     * 
     * @param identifier the String identifier of the {@link Session} invoking
     * the access method 
     * @param serviceProviderId the String identifier of the 
     * service provider holding the service providing the resource
     * on which applies the access method call
     * @param serviceId the String identifier of the service providing
     * the resource on which applies the access method call
     * @param resourceId the String identifier  of the resource 
     * on which applies the access method call
     * @param subscriptionId the String identifier of the subscription
     * to be deleted
     * 
     * @return the JSON formated response of the UNSUBSCRIBE access method 
     * invocation
     */
	protected JSONObject unsubscribe(String identifier, 
		final String serviceProviderId, final String serviceId, 
		final String resourceId, final String subscriptionId) 
	{
		final SessionKey sessionKey = sessions.get(
			new KeyExtractor<KeyExtractorType>(
				KeyExtractorType.TOKEN,identifier));
		
		JSONObject object = remoteCoreInvocation(serviceProviderId, new RemoteAccessMethodExecutable(
			AccessMethod.Type.valueOf(AccessMethod.UNSUBSCRIBE),sessionKey.getPublicKey()
			).withServiceProvider(serviceProviderId
			).withService(serviceId
		    ).withResource(resourceId
			).with(RemoteAccessMethodExecutable.SUBSCRIPTION_ID_TK, subscriptionId));

		if(object == null)
		{
		    return new JSONObject(AccessMethodResponse.error(mediator, UriUtils.getUri(
			    new String[]{serviceProviderId}), AccessMethod.UNSUBSCRIBE, 
				    SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
					"sensiNact service provider '" + serviceProviderId 
					+ "' not found",null).getJSON());
		}
		return object;
	}

	 /** 
     * Invokes the SUBSCRIBE access method on the resource whose String 
     * identifier is passed as parameter, held by the specified service 
     * provider and service
     * 
     * @param identifier the String identifier of the {@link Session} invoking
     * the access method 
     * @param serviceProviderId the String identifier of the 
     * service provider holding the service providing the resource
     * on which applies the access method call
     * @param serviceId the String identifier of the service providing
     * the resource on which applies the access method call
     * @param resourceId the String identifier  of the resource 
     * on which applies the access method call
     * @param recipient the {@link Recipient} to which the update events
     * generated by the subscription will be transmitted
     * @param conditions the JSON formated set of constraints applying
     * on the subscription to be created
     * 
     * @return the JSON formated response of the SUBSCRIBE access method 
     * invocation
     */
	protected JSONObject subscribe(String identifier, 
		final String serviceProviderId, final String serviceId,
		    final String resourceId, final Recipient recipient, 
			    final JSONArray conditions) 
	{
		final SessionKey sessionKey = sessions.get(new KeyExtractor<KeyExtractorType>(
					KeyExtractorType.TOKEN,identifier));

		JSONObject  object = remoteCoreInvocation(serviceProviderId, new RemoteAccessMethodExecutable(
			AccessMethod.Type.valueOf(AccessMethod.SUBSCRIBE),sessionKey.getPublicKey()
			).withServiceProvider(serviceProviderId
			).withService(serviceId
			).withResource(resourceId
			).with(RemoteAccessMethodExecutable.RECIPIENT_TK, recipient
			).with(RemoteAccessMethodExecutable.CONDITIONS_TK, conditions));

		if(object == null)
		{
		    return new JSONObject(AccessMethodResponse.error(mediator, UriUtils.getUri(
			    new String[]{serviceProviderId}), AccessMethod.SUBSCRIBE, 
				    SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
					"sensiNact service provider '" + serviceProviderId 
					+ "' not found",null).getJSON());
		}
		return object;
	}

	 /** 
     * Invokes the ACT access method on the resource whose String identifier
     * is passed as parameter, held by the specified service provider and 
     * service
     * 
     * @param identifier the String identifier of the {@link Session} invoking
     * the access method 
     * @param serviceProviderId the String identifier of the 
     * service provider holding the service providing the resource
     * on which applies the access method call
     * @param serviceId the String identifier of the service providing
     * the resource on which applies the access method call
     * @param resourceId the String identifier  of the resource 
     * on which applies the access method call
     * @param parameters the Objects array parameterizing the 
     * call 
     * 
     * @return the JSON formated response of the ACT access method 
     * invocation
     */
	protected JSONObject act(String identifier, 
		final String serviceProviderId, final String serviceId, 
		    final String resourceId, final Object[] parameters) 
	{
		final SessionKey sessionKey =  sessions.get(new KeyExtractor<KeyExtractorType>(
		KeyExtractorType.TOKEN,identifier));		

		JSONObject object = remoteCoreInvocation(serviceProviderId, new RemoteAccessMethodExecutable(
			AccessMethod.Type.valueOf(AccessMethod.ACT),sessionKey.getPublicKey()
			).withServiceProvider(serviceProviderId
			).withService(serviceId
			).withResource(resourceId
			).with(RemoteAccessMethodExecutable.ARGUMENTS_TK, parameters));

		if(object == null)
		{
		    return new JSONObject(AccessMethodResponse.error(mediator, UriUtils.getUri(
			    new String[]{serviceProviderId}), AccessMethod.ACT, 
				    SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
					"sensiNact service provider '" + serviceProviderId 
					+ "' not found",null).getJSON());
		}
		return object;
	}

	/** 
     * Invokes the SET access method on the resource whose String identifier
     * is passed as parameter, held by the specified service provider and 
     * service
     * 
     * @param identifier the String identifier of the {@link Session} invoking
     * the access method 
     * @param serviceProviderId the String identifier of the 
     * service provider holding the service providing the resource
     * on which applies the access method call
     * @param serviceId the String identifier of the service providing
     * the resource on which applies the access method call
     * @param resourceId the String identifier  of the resource 
     * on which applies the access method call
     * @param attributeId the String identifier of the resource's attribute 
     * targeted by the access method call 
     * @param parameter the value object to be set
     * 
     * @return the JSON formated response of the SET access method 
     * invocation
     */
	protected JSONObject set(String identifier,  
		final String serviceProviderId, final String serviceId, 
		    final String resourceId,  final String attributeId, 
		        final Object parameter)
	{
		final SessionKey sessionKey =  sessions.get(
			new KeyExtractor<KeyExtractorType>(
				KeyExtractorType.TOKEN,identifier));

		JSONObject object = remoteCoreInvocation(serviceProviderId, new RemoteAccessMethodExecutable(
			AccessMethod.Type.valueOf(AccessMethod.SET),sessionKey.getPublicKey()
			).withServiceProvider(serviceProviderId
			).withService(serviceId
			).withResource(resourceId
			).withAttribute(attributeId
			).with(RemoteAccessMethodExecutable.VALUE_TK, parameter));

		if(object == null)
		{
		    return new JSONObject(AccessMethodResponse.error(mediator, UriUtils.getUri(
			    new String[]{serviceProviderId}), AccessMethod.SET, 
				    SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
					"sensiNact service provider '" + serviceProviderId 
					+ "' not found",null).getJSON());
		}
		return object;
	}

	/**
     * Invokes the GET access method on the resource whose String identifier
     * is passed as parameter, held by the specified service provider and 
     * service
     *  
     * @param identifier the String identifier of the {@link Session} invoking
     * the access method 
     * @param serviceProviderId the String identifier of the 
     * service provider holding the service providing the resource
     * on which applies the access method call
     * @param serviceId the String identifier of the service providing
     * the resource on which applies the access method call
     * @param resourceId the String identifier  of the resource 
     * on which applies the access method call
     * @param attributeId the String identifier of the resource's attribute 
     * targeted by the access method call 
     * 
     * @return the JSON formated response of the GET access method invocation
     */
	protected JSONObject get(String identifier, 
		final String serviceProviderId, final String serviceId, 
		    final String resourceId, final String attributeId)
	{
		final SessionKey sessionKey = sessions.get(new KeyExtractor<KeyExtractorType>(
				KeyExtractorType.TOKEN, identifier));

		JSONObject object = remoteCoreInvocation(serviceProviderId, new RemoteAccessMethodExecutable(
			AccessMethod.Type.valueOf(AccessMethod.GET), sessionKey.getPublicKey()
			).withServiceProvider(serviceProviderId
			).withService(serviceId
			).withResource(resourceId
			).withAttribute(attributeId));

		if(object == null)
		{
		    return new JSONObject(AccessMethodResponse.error(mediator, UriUtils.getUri(
			    new String[]{serviceProviderId}), AccessMethod.GET, 
				    SnaErrorfulMessage.NOT_FOUND_ERROR_CODE, 
					"sensiNact service provider '" + serviceProviderId 
					+ "' not found",null).getJSON());
		}
		return object;
	}
	
	/**
     * Returns the JSON formated list of available service providers for
     * the {@link Session} whose String  identifier is passed as parameter
     * 
     * @param identifier the String  identifier of the {@link Session} 
     * requiring the list of available service providers
	 * @param filterDefinition 
     * 
     * @return the JSON formated list of available service providers
     */
	protected String getProviders(String identifier,
			 String requestId, FilteringDefinition filterDefinition) 
	{		
		final SessionKey sessionKey =  sessions.get(
			new KeyExtractor<KeyExtractorType>(
			KeyExtractorType.TOKEN, identifier));
		
		String local = this.registry.getProviders(
		    sessionKey, sessionKey.localID()!=0, null);
		
		if(sessionKey.localID()!=0)
		{
			return local;
		}
		final StringBuilder content = new StringBuilder();
		if(local != null && local.length() > 0)
		{
			content.append(local);
		}
		SensiNact.this.doPrivilegedVoidServices(RemoteCore.class, null,
				new Executable<RemoteCore,Void>()
		{
			@Override
			public Void execute(RemoteCore core) throws Exception
			{
				String o = core.endpoint().getProviders(
					sessionKey.getPublicKey());
				
				if(o!=null && o.length()>0)
				{
					if(content.length() > 0)
					{
						content.append(",");
					}
					content.append(o);
				}
				return null;
			}	
		});
		String result = null;
		StringBuilder builder = new StringBuilder();
		builder.append("{");
		if(requestId != null)
		{
			builder.append("\"");
			builder.append(AccessMethod.REQUEST_ID_KEY);
			builder.append("\" : \"");
			builder.append(requestId);
			builder.append("\",");
		}		
		if(filterDefinition != null)
		{
			builder.append("\"filter\":{");
			builder.append("\"type\": \"");
			builder.append(filterDefinition.type);
			builder.append("\",");
			builder.append("\"definition\": \"");
			builder.append(filterDefinition.filter);
			builder.append("\"},");
			
			result = SensiNact.this.callPostFilter(
				filterDefinition.type, filterDefinition.filter, 
				new StringBuilder().append("[").append(
				content.toString()).append("]"
						).toString());

			builder.append("\"providers\":");
			builder.append(result);
			
		} else 
		{
			builder.append("\"providers\":");
			builder.append("[");
			builder.append(content.toString());
			builder.append("]");
		}
		builder.append(",\"type\":\"PROVIDERS_LIST\",");
		builder.append("\"uri\":\"");
		builder.append(UriUtils.PATH_SEPARATOR);
		builder.append("\",");
		builder.append("\"statusCode\":200");
		builder.append("}");
		return builder.toString();
	}

	/**
  	 * Returns the JSON formated list of all registered resource 
  	 * model instances, accessible by the {@link Session} whose 
  	 * String identifier is passed as parameter and compliant to  
  	 * the specified String LDAP formated filter.
  	 * 
  	 * @param identifier the String identifier of the {@link Session} for 
  	 * which to retrieve the list of accessible resource model instances
  	 * @param filter the String LDAP formated filter 
	 * @param filterDefinition 
  	 * 
  	 * @return the JSON formated list of the resource model instances for 
  	 * the specified {@link Session} and compliant to the specified filter.
  	 */
	protected String getAll(String identifier, String requestId, 
		final String filter, FilteringDefinition filterDefinition)
	{		
		final SessionKey sessionKey =  sessions.get(
			new KeyExtractor<KeyExtractorType>(
				KeyExtractorType.TOKEN, identifier));
		
		String local = this.registry.getAll(sessionKey,
				sessionKey.localID()!=0, filter);

		if(sessionKey.localID()!=0)
		{
			return local;
		}
		final StringBuilder content = new StringBuilder();
		if(local != null && local.length() > 0)
		{
			content.append(local);
		}
		SensiNact.this.doPrivilegedVoidServices(RemoteCore.class, null,
				new Executable<RemoteCore,Void>()
		{
			@Override
			public Void execute(RemoteCore core) throws Exception
			{
				String o = core.endpoint().getAll(
					sessionKey.getPublicKey(), filter);
				
				if(o!=null && o.length()>0)
				{
					if(content.length() > 0)
					{
						content.append(",");
					}
					content.append(o);
				}
				return null;
			}	
		});
		String result = null;
		StringBuilder builder = new StringBuilder();
		builder.append("{");
		if(requestId != null)
		{
			builder.append("\"");
			builder.append(AccessMethod.REQUEST_ID_KEY);
			builder.append("\" : \"");
			builder.append(requestId);
			builder.append("\",");
		}
		if(filterDefinition != null)
		{
			builder.append("\"filter\":{");
			builder.append("\"type\": \"");
			builder.append(filterDefinition.type);
			builder.append("\",");
			builder.append("\"definition\": \"");
			builder.append(filterDefinition.filter);
			builder.append("\"},");
			
			result = SensiNact.this.callPostFilter(
				filterDefinition.type, filterDefinition.filter, 
				new StringBuilder().append("[").append(
				content.toString()).append("]"
						).toString());
	
			builder.append("\"providers\":");
			builder.append(result);
			
		} else 
		{
			builder.append("\"providers\":");
			builder.append("[");
			builder.append(content.toString());
			builder.append("]");
		}
		builder.append(",\"statusCode\":200");
		builder.append("}");
		return builder.toString();
	}

    /**
     * @inheritDoc
     *
     * @see org.eclipse.sensinact.gateway.core.Core#close()
     */
    public void close()
    {
        mediator.debug("closing sensiNact core");
		AccessController.<Void>doPrivileged(new PrivilegedAction<Void>()
		{
			@Override
			public Void run()
			{
				SensiNact.this.mediator.callServices(SensiNactResourceModel.class, 
					new Executable<SensiNactResourceModel, Void>()
					{
						@Override
						public Void execute(SensiNactResourceModel instance)
						        throws Exception
						{
							instance.unregister();
							return null;
						}
					});
				SensiNact.this.mediator.callServices(RemoteCore.class, 
					new Executable<RemoteCore, Void>()
					{
						@Override
						public Void execute(RemoteCore instance)
						        throws Exception
						{
							instance.endpoint().disconnect();
							return null;
						}
					});
				SensiNact.this.mediator.callServices(SnaAgent.class, 
					new Executable<SnaAgent, Void>()
					{
						@Override
						public Void execute(SnaAgent instance)
						        throws Exception
						{
							instance.stop();
							return null;
						}
					});
				return null;
			}
		});    	
    }

	/**
	 */
	String nextToken() 
	{	
		boolean exists = false;
		String token = null;
		do
		{
			try
			{
				token = CryptoUtils.createToken();
			}
			catch (InvalidKeyException e)
			{
				token = Long.toHexString(
						System.currentTimeMillis());
			}
			synchronized(this.sessions)
			{	
				exists = this.sessions.get(
					new Sessions.KeyExtractor<Sessions.KeyExtractorType>(
						Sessions.KeyExtractorType.TOKEN, token))!=null;
			}
		} while(exists);		
		return token;		
	}
	
}
