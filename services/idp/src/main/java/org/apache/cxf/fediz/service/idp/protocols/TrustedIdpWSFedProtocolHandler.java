/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.fediz.service.idp.protocols;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Collections;

import javax.servlet.http.HttpServletRequest;

import org.w3c.dom.Element;

import org.apache.cxf.fediz.core.FederationConstants;
import org.apache.cxf.fediz.core.FederationProcessor;
import org.apache.cxf.fediz.core.FederationProcessorImpl;
import org.apache.cxf.fediz.core.FederationRequest;
import org.apache.cxf.fediz.core.FederationResponse;
import org.apache.cxf.fediz.core.config.FederationContext;
import org.apache.cxf.fediz.core.config.TrustManager;
import org.apache.cxf.fediz.core.config.jaxb.AudienceUris;
import org.apache.cxf.fediz.core.config.jaxb.CertificateStores;
import org.apache.cxf.fediz.core.config.jaxb.ContextConfig;
import org.apache.cxf.fediz.core.config.jaxb.FederationProtocolType;
import org.apache.cxf.fediz.core.config.jaxb.KeyStoreType;
import org.apache.cxf.fediz.core.config.jaxb.TrustManagersType;
import org.apache.cxf.fediz.core.config.jaxb.TrustedIssuerType;
import org.apache.cxf.fediz.core.config.jaxb.TrustedIssuers;
import org.apache.cxf.fediz.core.config.jaxb.ValidationType;
import org.apache.cxf.fediz.core.exception.ProcessingException;
import org.apache.cxf.fediz.service.idp.domain.Idp;
import org.apache.cxf.fediz.service.idp.domain.TrustedIdp;
import org.apache.cxf.fediz.service.idp.spi.TrustedIdpProtocolHandler;
import org.apache.cxf.fediz.service.idp.util.WebUtils;
import org.apache.cxf.ws.security.tokenstore.SecurityToken;
import org.apache.wss4j.common.crypto.CertificateStore;
import org.apache.xml.security.exceptions.Base64DecodingException;
import org.apache.xml.security.stax.impl.util.IDGenerator;
import org.apache.xml.security.utils.Base64;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;
import org.springframework.webflow.execution.RequestContext;

@Component
public class TrustedIdpWSFedProtocolHandler implements TrustedIdpProtocolHandler {
    
    public static final String PROTOCOL = "http://docs.oasis-open.org/wsfed/federation/200706";

    private static final Logger LOG = LoggerFactory.getLogger(TrustedIdpWSFedProtocolHandler.class);

    @Override
    public boolean canHandleRequest(HttpServletRequest request) {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public String getProtocol() {
        return PROTOCOL;
    }

    @Override
    public URL mapSignInRequest(RequestContext context, Idp idp, TrustedIdp trustedIdp) {
        
        try {
            StringBuffer sb = new StringBuffer();
            sb.append(trustedIdp.getUrl());
            sb.append("?").append(FederationConstants.PARAM_ACTION).append('=');
            sb.append(FederationConstants.ACTION_SIGNIN);
            sb.append("&").append(FederationConstants.PARAM_TREALM).append('=');
            sb.append(URLEncoder.encode(idp.getRealm(), "UTF-8"));
            sb.append("&").append(FederationConstants.PARAM_REPLY).append('=');
            sb.append(URLEncoder.encode(idp.getIdpUrl().toString(), "UTF-8"));
            
            String wfresh = context.getFlowScope().getString(FederationConstants.PARAM_FRESHNESS);
            if (wfresh != null) {
                sb.append("&").append(FederationConstants.PARAM_FRESHNESS).append('=');
                sb.append(URLEncoder.encode(wfresh, "UTF-8"));
            }
            String wctx = context.getFlowScope().getString(FederationConstants.PARAM_CONTEXT);
            if (wctx != null) {
                sb.append("&").append(FederationConstants.PARAM_CONTEXT).append('=');
                sb.append(wctx);
            }
        
            return new URL(sb.toString());
        } catch (MalformedURLException ex) {
            LOG.error("Invalid Redirect URL for Trusted Idp", ex);
            throw new IllegalStateException("Invalid Redirect URL for Trusted Idp");
        } catch (UnsupportedEncodingException ex) {
            LOG.error("Invalid Redirect URL for Trusted Idp", ex);
            throw new IllegalStateException("Invalid Redirect URL for Trusted Idp");
        }
    }

    @Override
    public SecurityToken mapSignInResponse(RequestContext context, Idp idp, TrustedIdp trustedIdp) {

        try {
            String whr = (String) WebUtils.getAttributeFromFlowScope(context,
                                                                     FederationConstants.PARAM_HOME_REALM);
    
            if (whr == null) {
                LOG.warn("Home realm is null");
                throw new IllegalStateException("Home realm is null");
            }
    
            String wresult = (String) WebUtils.getAttributeFromFlowScope(context,
                                                                         FederationConstants.PARAM_RESULT);
    
            if (wresult == null) {
                LOG.warn("Parameter wresult not found");
                throw new IllegalStateException("No security token issued");
            }
    
            FederationContext fedContext = getFederationContext(idp, trustedIdp);
    
            FederationRequest wfReq = new FederationRequest();
            wfReq.setWa(FederationConstants.ACTION_SIGNIN);
            wfReq.setWresult(wresult);
    
            FederationProcessor wfProc = new FederationProcessorImpl();
            FederationResponse wfResp = wfProc.processRequest(wfReq, fedContext);
    
            fedContext.close();
    
            Element e = wfResp.getToken();
    
            // Create new Security token with new id. 
            // Parameters for freshness computation are copied from original IDP_TOKEN
            String id = IDGenerator.generateID("_");
            SecurityToken idpToken = new SecurityToken(id,
                                                       wfResp.getTokenCreated(), wfResp.getTokenExpires());
    
            idpToken.setToken(e);
            LOG.info("[IDP_TOKEN=" + id + "] for user '" + wfResp.getUsername()
                     + "' created from [RP_TOKEN=" + wfResp.getUniqueTokenId()
                     + "] issued by home realm [" + whr + "/"
                     + wfResp.getIssuer() + "].");
            if (LOG.isDebugEnabled()) {
                LOG.debug("Created date=" + wfResp.getTokenCreated());
                LOG.debug("Expired date=" + wfResp.getTokenExpires());
            }
            if (LOG.isDebugEnabled()) {
                LOG.debug("Validated 'wresult' : "
                    + System.getProperty("line.separator") + wresult);
            }
            return idpToken;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            LOG.warn("Unexpected exception occured", ex);
            throw new IllegalStateException("Unexpected exception occured: " + ex.getMessage());
        }
    }
    
    
    private FederationContext getFederationContext(Idp idpConfig,
            TrustedIdp trustedIdpConfig) throws ProcessingException {

        ContextConfig config = new ContextConfig();

        config.setName("whatever");

        // Configure certificate store
        String certificate = trustedIdpConfig.getCertificate();
        boolean isCertificateLocation = !certificate.startsWith("-----BEGIN CERTIFICATE");
        if (isCertificateLocation) {
            CertificateStores certStores = new CertificateStores();
            TrustManagersType tm0 = new TrustManagersType();
            KeyStoreType ks0 = new KeyStoreType();
            ks0.setType("PEM");
            // ks0.setType("JKS");
            // ks0.setPassword("changeit");
            ks0.setFile(trustedIdpConfig.getCertificate());
            tm0.setKeyStore(ks0);
            certStores.getTrustManager().add(tm0);
            config.setCertificateStores(certStores);
        }
        
        // Configure trusted IDP
        TrustedIssuers trustedIssuers = new TrustedIssuers();
        TrustedIssuerType ti0 = new TrustedIssuerType();
        ti0.setCertificateValidation(ValidationType.PEER_TRUST);
        ti0.setName(trustedIdpConfig.getName());
        // ti0.setSubject(".*CN=www.sts.com.*");
        trustedIssuers.getIssuer().add(ti0);
        config.setTrustedIssuers(trustedIssuers);

        FederationProtocolType protocol = new FederationProtocolType();
        config.setProtocol(protocol);

        AudienceUris audienceUris = new AudienceUris();
        audienceUris.getAudienceItem().add(idpConfig.getRealm());
        config.setAudienceUris(audienceUris);

        FederationContext fedContext = new FederationContext(config);
        if (!isCertificateLocation) {
            CertificateStore cs = null;
            
            X509Certificate cert;
            try {
                cert = parseCertificate(trustedIdpConfig.getCertificate());
            } catch (Exception ex) {
                LOG.error("Failed to parse trusted certificate", ex);
                throw new ProcessingException("Failed to parse trusted certificate");
            }
            cs = new CertificateStore(Collections.singletonList(cert).toArray(new X509Certificate[0]));
            
            TrustManager tm = new TrustManager(cs);
            fedContext.getCertificateStores().add(tm);
        }
        
        fedContext.init();
        return fedContext;
    }
    
    private X509Certificate parseCertificate(String certificate)
        throws CertificateException, Base64DecodingException {
        
        //before decoding we need to get rod off the prefix and suffix
        byte [] decoded = Base64.decode(certificate.replaceAll("-----BEGIN CERTIFICATE-----", "").
                                        replaceAll("-----END CERTIFICATE-----", ""));

        return (X509Certificate)CertificateFactory.getInstance("X.509").
            generateCertificate(new ByteArrayInputStream(decoded));
    }
    

}