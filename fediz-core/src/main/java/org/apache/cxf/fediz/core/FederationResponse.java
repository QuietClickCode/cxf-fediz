/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.cxf.fediz.core;

import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.w3c.dom.Element;

public class FederationResponse {
	
	private String audience = null;
	private String username = null;
	private List<String> roles = null;
	private String issuer = null;
	private List<Claim> claims = null;
	private Element token = null;
	private String uniqueTokenId = null;
	
	/**
	* Created time
	*/
	private Date tokenCreated = null;
	
	/**	* Expiration time
	*/
	private Date tokenExpires = null;
	

	
	private FederationResponse() {}

	public FederationResponse(String username, String issuer, List<String> roles, List<Claim> claims, String audience, Date created, Date expires, Element token, String uniqueTokenId) {
		this.username = username;
		this.issuer = issuer;
		this.roles = roles;
		this.claims = claims;
		this.audience = audience;
		this.tokenCreated = created;
		this.tokenExpires = expires;
		this.token = token;
		this.uniqueTokenId = uniqueTokenId;
	}
	
	

	public String getUniqueTokenId() {
		return uniqueTokenId;
	}

	public String getAudience() {
		return audience;
	}



	public String getUsername() {
		return username;
	}



	public List<String> getRoles() {
		if (roles == null) return null;
		else return Collections.unmodifiableList(roles);
	}



	public String getIssuer() {
		return issuer;
	}



	public List<Claim> getClaims() {
		if (claims == null) return null;
		else return Collections.unmodifiableList(claims);
	}



	public Date getTokenCreated() {
		return tokenCreated;
	}



	public Date getTokenExpires() {
		return tokenExpires;
	}
	
	public Element getToken() {
		return token;
	}
	

	
}
