/*
 * (C) Copyright 2014 Nuxeo SA (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Arnaud Kervern
 */
package org.nuxeo.ecm.platform.oauth.tests;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.ws.rs.core.MultivaluedMap;

import org.codehaus.jackson.map.ObjectMapper;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.ecm.core.transientstore.api.TransientStore;
import org.nuxeo.ecm.core.transientstore.api.TransientStoreService;
import org.nuxeo.ecm.platform.oauth2.OAuth2Error;
import org.nuxeo.ecm.platform.oauth2.clients.ClientRegistry;
import org.nuxeo.ecm.platform.oauth2.clients.OAuth2Client;
import org.nuxeo.ecm.platform.oauth2.request.AuthorizationRequest;
import org.nuxeo.ecm.platform.ui.web.auth.oauth2.NuxeoOAuth2Servlet;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;
import org.nuxeo.runtime.test.runner.Jetty;
import org.nuxeo.runtime.transaction.TransactionHelper;
import org.nuxeo.transientstore.test.TransientStoreFeature;

import com.sun.jersey.api.client.Client;
import com.sun.jersey.api.client.ClientResponse;
import com.sun.jersey.api.client.WebResource;
import com.sun.jersey.core.util.MultivaluedMapImpl;

/**
 * @author <a href="mailto:ak@nuxeo.com">Arnaud Kervern</a>
 * @since 5.9.2
 */
@RunWith(FeaturesRunner.class)
@Features({ OAuthFeature.class, OAuth2JettyFeature.class, TransientStoreFeature.class })
@Jetty(port = 18090)
public class OAuth2ChallengeFixture {

    protected static final String CLIENT_ID = "testClient";

    protected static final String CLIENT_SECRET = "testSecret";

    protected static final String REDIRECT_URI = "https://redirect.uri";

    protected static final String RESPONSE_TYPE = "code";

    protected static final String STATE = "testState";

    protected static final String BASE_URL = "http://localhost:18090";

    private static final Integer TIMEOUT = Integer.valueOf(1000 * 60 * 5); // 5min

    @Inject
    protected ClientRegistry clientRegistry;

    @Inject
    protected TransientStoreService transientStoreService;

    protected Client client;

    protected TransientStore store;

    @Before
    public void initOAuthClient() {

        registerClient("Dummy", CLIENT_ID, CLIENT_SECRET, REDIRECT_URI);

        // First client to request like a "Client" as OAuth RFC describe it
        client = Client.create();
        client.setConnectTimeout(TIMEOUT);
        client.setReadTimeout(TIMEOUT);
        client.setFollowRedirects(Boolean.FALSE);

        store = transientStoreService.getStore(AuthorizationRequest.STORE_NAME);
    }

    public void authorizationShouldReturn200() {
        Map<String, String> params = new HashMap<>();
        params.put("redirect_uri", REDIRECT_URI);
        params.put("client_id", CLIENT_ID);
        params.put("response_type", RESPONSE_TYPE);
        params.put("state", STATE);

        ClientResponse cr = responseFromGetAuthorizationWith(params);
        assertEquals(200, cr.getStatus());
    }

    @Test
    public void authorizationShouldRejectMissingState() {
        Map<String, String> params = new HashMap<>();
        params.put("redirect_uri", REDIRECT_URI);
        params.put("client_id", CLIENT_ID);
        params.put("response_type", RESPONSE_TYPE);

        ClientResponse cr = responseFromGetAuthorizationWith(params);
        assertEquals(302, cr.getStatus());
        String redirect = cr.getHeaders().get("Location").get(0);
        assertTrue(redirect.contains("error=" + OAuth2Error.INVALID_REQUEST.toString().toLowerCase()));
    }

    @Test
    public void authorizationShouldRejectUnknownClient() {
        Map<String, String> params = new HashMap<>();
        params.put("redirect_uri", REDIRECT_URI);
        params.put("client_id", "unknown");
        params.put("response_type", RESPONSE_TYPE);
        params.put("state", STATE);

        ClientResponse cr = responseFromGetAuthorizationWith(params);
        assertEquals(302, cr.getStatus());
        String redirect = cr.getHeaders().get("Location").get(0);
        assertTrue(redirect.contains("error=" + OAuth2Error.UNAUTHORIZED_CLIENT.toString().toLowerCase()));
    }

    @Test
    public void authorizationShouldValidateRedirectURI() {
        // Invalid: no redirect URI
        Map<String, String> params = new HashMap<>();
        params.put("client_id", CLIENT_ID);
        params.put("response_type", RESPONSE_TYPE);
        params.put("state", STATE);

        ClientResponse cr = responseFromGetAuthorizationWith(params);
        assertEquals(400, cr.getStatus());

        // Invalid: not starting with https
        params.put("redirect_uri", "http://redirect.uri");

        cr = responseFromGetAuthorizationWith(params);
        assertEquals(302, cr.getStatus());
        String redirect = cr.getHeaders().get("Location").get(0);
        assertTrue(redirect.contains("error=" + OAuth2Error.INVALID_REQUEST.toString().toLowerCase()));

        // Invalid: starting with http://localhost with localhost part of the domain name
        params.put("redirect_uri", "http://localhost.somecompany.com");

        cr = responseFromGetAuthorizationWith(params);
        assertEquals(302, cr.getStatus());
        redirect = cr.getHeaders().get("Location").get(0);
        assertTrue(redirect.contains("error=" + OAuth2Error.INVALID_REQUEST.toString().toLowerCase()));

        // Invalid: not matching the one from the registered client
        params.put("redirect_uri", "https://unknown.uri");

        cr = responseFromGetAuthorizationWith(params);
        assertEquals(302, cr.getStatus());
        redirect = cr.getHeaders().get("Location").get(0);
        assertTrue(redirect.contains("error=" + OAuth2Error.INVALID_REQUEST.toString().toLowerCase()));

        // Valid: not starting with http
        registerClient("Nuxeo Mobile", "nuxeo-mobile-app", "", "nuxeo://authorize");
        params.put("client_id", "nuxeo-mobile-app");
        params.put("redirect_uri", "nuxeo://authorize");

        cr = responseFromGetAuthorizationWith(params);
        assertEquals(200, cr.getStatus());

        // Valid: starting with http://localhost with localhost not part of the domain name
        registerClient("Localhost", "localhost", "", "http://localhost:8080/nuxeo");
        params.put("client_id", "localhost");
        params.put("redirect_uri", "http://localhost:8080/nuxeo");

        cr = responseFromGetAuthorizationWith(params);
        assertEquals(200, cr.getStatus());

        // Valid: starting with https
        registerClient("Secure", "secure", "", REDIRECT_URI);
        params.put("client_id", "secure");
        params.put("redirect_uri", REDIRECT_URI);

        cr = responseFromGetAuthorizationWith(params);
        assertEquals(200, cr.getStatus());
    }

    @Test
    public void shouldDenyAccess() {
        AuthorizationRequest authorizationRequest = initAuthorizationRequestCall(CLIENT_ID, REDIRECT_URI);
        String key = authorizationRequest.getAuthorizationKey();

        // missing "grant_access" parameter to grant access
        Map<String, String> params = new HashMap<>();
        params.put("redirect_uri", REDIRECT_URI);
        params.put("client_id", CLIENT_ID);
        params.put("state", STATE);
        params.put(NuxeoOAuth2Servlet.AUTHORIZATION_KEY, key);
        ClientResponse cr = responseFromPostAuthorizationWith(params);
        assertEquals(302, cr.getStatus());
        String redirect = cr.getHeaders().get("Location").get(0);
        assertTrue(redirect.contains("error=access_denied"));

        // ensure authorization request has been removed
        Set<String> keys = store.keySet();
        assertFalse(keys.contains(key));
    }

    protected AuthorizationRequest initAuthorizationRequestCall(String clientId, String redirectURI) {
        Map<String, String> params = new HashMap<>();
        params.put("redirect_uri", redirectURI);
        params.put("client_id", clientId);
        params.put("response_type", "code");
        params.put("state", STATE);

        ClientResponse cr = responseFromGetAuthorizationWith(params);
        assertEquals(200, cr.getStatus());

        // get back the authorization request from the store for the needed authorization key
        Set<String> keys = store.keySet();
        assertEquals(1, keys.size());
        String key = keys.toArray(new String[0])[0];
        return AuthorizationRequest.get(key);
    }

    @Test
    public void shouldRetrieveAccessAndRefreshToken() throws IOException {
        AuthorizationRequest authorizationRequest = initAuthorizationRequestCall(CLIENT_ID, REDIRECT_URI);
        String key = authorizationRequest.getAuthorizationKey();

        // get an authorization code
        Map<String, String> params = new HashMap<>();
        params.put("redirect_uri", REDIRECT_URI);
        params.put("client_id", CLIENT_ID);
        params.put(NuxeoOAuth2Servlet.AUTHORIZATION_KEY, key);
        params.put(NuxeoOAuth2Servlet.GRANT_ACCESS_PARAM, "true");
        ClientResponse cr = responseFromPostAuthorizationWith(params);
        assertEquals(302, cr.getStatus());
        String redirect = cr.getHeaders().get("Location").get(0);
        assertTrue(redirect.contains("code="));
        String code = redirect.substring(redirect.indexOf("code=") + 5, redirect.indexOf("&state"));

        // ensure we have only one authorization request
        Set<String> keys = store.keySet();
        assertTrue(keys.contains(code));
        assertFalse(keys.contains(key));

        // get access and refresh tokens
        params = new HashMap<>();
        params.put("redirect_uri", REDIRECT_URI);
        params.put("client_id", CLIENT_ID);
        params.put("grant_type", "authorization_code");
        params.put("client_secret", CLIENT_SECRET);
        params.put("state", STATE);
        params.put("code", code);
        cr = responseFromTokenWith(params);
        assertEquals(200, cr.getStatus());
        String json = cr.getEntity(String.class);
        ObjectMapper obj = new ObjectMapper();
        Map<?, ?> token = obj.readValue(json, Map.class);
        assertNotNull(token);
        String accessToken = (String) token.get("access_token");
        assertEquals(32, accessToken.length());
        String refreshToken = (String) token.get("refresh_token");
        assertEquals(64, refreshToken.length());

        // ensure authorization request has been removed
        keys = store.keySet();
        assertFalse(keys.contains(code));
        assertFalse(keys.contains(key));

        // Refresh this token
        params.remove("code");
        params.put("grant_type", "refresh_token");
        params.put("refresh_token", refreshToken);
        cr = responseFromTokenWith(params);
        assertEquals(200, cr.getStatus());
        json = cr.getEntity(String.class);
        Map<?, ?> refreshed = obj.readValue(json, Map.class);
        assertNotSame(refreshed.get("access_token"), token.get("access_token"));
    }

    protected ClientResponse responseFromTokenWith(Map<String, String> queryParams) {
        WebResource wr = client.resource(BASE_URL).path("oauth2").path("token");

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            params.add(entry.getKey(), entry.getValue());
        }

        return wr.queryParams(params).get(ClientResponse.class);
    }

    protected ClientResponse responseFromGetAuthorizationWith(Map<String, String> queryParams) {
        WebResource wr = client.resource(BASE_URL).path("oauth2").path("authorization");

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            params.add(entry.getKey(), entry.getValue());
        }

        return wr.queryParams(params).get(ClientResponse.class);
    }

    protected ClientResponse responseFromPostAuthorizationWith(Map<String, String> queryParams) {
        WebResource wr = client.resource(BASE_URL).path("oauth2").path("authorization_submit");

        MultivaluedMap<String, String> params = new MultivaluedMapImpl();
        for (Map.Entry<String, String> entry : queryParams.entrySet()) {
            params.add(entry.getKey(), entry.getValue());
        }

        return wr.queryParams(params).post(ClientResponse.class);
    }

    protected void registerClient(String name, String id, String secret, String redirectURI) {
        if (!clientRegistry.hasClient(id)) {
            OAuth2Client oauthClient = new OAuth2Client(name, id, secret, redirectURI);
            assertTrue(clientRegistry.registerClient(oauthClient));

            // Commit the transaction so that the HTTP thread finds the newly created directory entry
            if (TransactionHelper.isTransactionActiveOrMarkedRollback()) {
                TransactionHelper.commitOrRollbackTransaction();
                TransactionHelper.startTransaction();
            }
        }
    }
}