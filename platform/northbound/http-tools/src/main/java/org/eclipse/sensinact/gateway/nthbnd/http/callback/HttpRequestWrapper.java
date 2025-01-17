/*********************************************************************
* Copyright (c) 2021 Kentyou
*
* This program and the accompanying materials are made
* available under the terms of the Eclipse Public License 2.0
* which is available at https://www.eclipse.org/legal/epl-2.0/
*
* SPDX-License-Identifier: EPL-2.0
**********************************************************************/
package org.eclipse.sensinact.gateway.nthbnd.http.callback;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;

import javax.servlet.ServletInputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;

import org.eclipse.sensinact.gateway.util.IOUtils;


/**
 * 
 * @author <a href="mailto:cmunilla@kentyou.com">Christophe Munilla</a>
 */
public class HttpRequestWrapper extends HttpServletRequestWrapper implements RequestWrapper {

	private Map<String, List<String>> queryMap;
    private String content;

    /**
     * Constructor
     *
     * @param request  the {@link HttpServletRequest} to be wrapped by
     * the HttpRestAccessRequest to be instantiated
     */
    public HttpRequestWrapper(HttpServletRequest request) {
        super(request);
    }
    
    @Override
    public Map<String, List<String>> getQueryMap() {
        if (this.queryMap == null) {
            try {
                this.queryMap = AbstractRequestWrapper.processRequestQuery(super.getQueryString());
            } catch (UnsupportedEncodingException e) {
                this.queryMap = Collections.<String, List<String>>emptyMap();
            }
        }
        return queryMap;
    }

    @Override
    public String getContent() {
        if (this.content == null) {
            try {
                ServletInputStream input = super.getInputStream();
                byte[] stream = IOUtils.read(input, super.getContentLength(), true);
                this.content = new String(stream);
            } catch (IOException e) {
            	AbstractRequestWrapper.LOG.log(Level.SEVERE, e.getMessage(),e);
            }
        }
        return this.content;
    }

	@Override
	public Map<String, List<String>> getAttributes() {
        Map<String, List<String>> map = new HashMap<String, List<String>>();
        
		for(Enumeration en = super.getHeaderNames();en.hasMoreElements();) {
			String name = (String)en.nextElement();
			List<String> value = Collections.list(super.getHeaders(name));
			map.put(name, value);
		}
		return map;
	}
}
