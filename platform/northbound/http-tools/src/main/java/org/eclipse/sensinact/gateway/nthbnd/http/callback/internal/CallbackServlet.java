/*********************************************************************
* Copyright (c) 2021 Kentyou and others
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.sensinact.gateway.nthbnd.http.callback.internal;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.sensinact.gateway.common.bundle.Mediator;
import org.eclipse.sensinact.gateway.nthbnd.http.callback.CallbackService;
import org.eclipse.sensinact.gateway.nthbnd.http.callback.HttpRequestWrapper;
import org.eclipse.sensinact.gateway.nthbnd.http.callback.HttpResponseWrapper;
import org.eclipse.sensinact.gateway.nthbnd.http.callback.ServletCallbackContext;

/**
 * Callback servlet
 *
 * @author <a href="mailto:cmunilla@kentyou.com">Christophe Munilla</a>
 */
@SuppressWarnings("serial")
public class CallbackServlet extends HttpServlet implements Servlet{
	
	private CallbackService callbackService;
	private Mediator mediator;

    /**
     * Constructor
     * 
     * @param mediator the {@link Mediator} allowing the CallbackServlet to be 
     * instantiated to interact with the OSGi host environment
     * @param callbackService the {@link CallbackService} allowing the
     * CallbackServlet to be instantiated to process the callback request
     */
    public CallbackServlet(Mediator mediator, CallbackService callbackService) {
        this.callbackService = callbackService;
        this.mediator = mediator;
    }
    
    /* (non-Javadoc)
     * @see javax.servlet.GenericServlet#init(javax.servlet.ServletConfig)
     */
    @Override
    public void init(ServletConfig config) throws ServletException {
    	try {
    		super.init(config);
    	}catch(Exception e) {
    		e.printStackTrace();
    		throw new ServletException(e);
    	} 
    }

    private final void doCall(HttpServletRequest request, HttpServletResponse response) throws IOException{
    	if (response.isCommitted()) {       	
    		return;
        }
        final ServletCallbackContext context = new ServletCallbackContext(mediator,
        		new HttpRequestWrapper(request), new HttpResponseWrapper(response));
        try {
            if (CallbackServlet.this.callbackService != null) {
            	CallbackServlet.this.callbackService.process(context);
            }
        } catch (Exception e) {
            e.printStackTrace();
            response.sendError(520, "Internal server error");
        } catch (Error e) {
            e.printStackTrace();
        } 
    }

    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doGet(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doCall(request, response);
    }

    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doPost(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doCall(request, response);
    }

    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doPut(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public void doPut(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doCall(request, response);
    }

    /* (non-Javadoc)
     * @see javax.servlet.http.HttpServlet#doDelete(javax.servlet.http.HttpServletRequest, javax.servlet.http.HttpServletResponse)
     */
    @Override
    public void doDelete(HttpServletRequest request, HttpServletResponse response) throws IOException {
        doCall(request, response);
    }
}
