/*
 * *****************************************************************************
 *      Cloud Foundry
 *      Copyright (c) [2009-2016] Pivotal Software, Inc. All Rights Reserved.
 *      This product is licensed to you under the Apache License, Version 2.0 (the "License").
 *      You may not use this product except in compliance with the License.
 *
 *      This product includes a number of subcomponents with
 *      separate copyright notices and license terms. Your use of these
 *      subcomponents is subject to the terms and conditions of the
 *      subcomponent's license, as noted in the LICENSE file.
 * *****************************************************************************
 */

package org.cloudfoundry.identity.client.integration;

import org.cloudfoundry.identity.client.UaaContext;
import org.cloudfoundry.identity.client.UaaContextFactory;
import org.cloudfoundry.identity.client.token.GrantType;
import org.cloudfoundry.identity.client.token.TokenRequest;
import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneConfiguration;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.net.URI;
import java.util.Arrays;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.cloudfoundry.identity.client.integration.ClientIntegrationTestUtilities.GENERATOR;
import static org.cloudfoundry.identity.client.integration.ClientIntegrationTestUtilities.UAA_URI;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.ACCEPT_LANGUAGE;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.http.HttpHeaders.CONTENT_LANGUAGE;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpMethod.POST;

public class IdentityZoneConfigurationIntegrationTest {

    private UaaContextFactory factory;

    @Rule
    public IsUAAListeningRule uaaListeningRule = new IsUAAListeningRule(UAA_URI, false);
    private TokenRequest clientCredentials;
    private UaaContext context;
    private IdentityZone zone;

    @Before
    public void setUp() throws Exception {
        factory =
            UaaContextFactory.factory(new URI(UAA_URI))
                .authorizePath("/oauth/authorize")
                .tokenPath("/oauth/token");

        clientCredentials = factory.tokenRequest()
            .setClientId("identity")
            .setClientSecret("identitysecret")
            .setGrantType(GrantType.CLIENT_CREDENTIALS);

        context = factory.authenticate(clientCredentials);

        String zoneId = GENERATOR.generate();
        zone = new IdentityZone()
            .setId(zoneId)
            .setName(zoneId)
            .setSubdomain(zoneId)
            .setConfig(new IdentityZoneConfiguration());

    }

    @Test
    public void create_zone_without_client_api() throws Exception {
        ResponseEntity<IdentityZone> created = context.getRestTemplate().exchange(
            UAA_URI+"/identity-zones",
            POST,
            new HttpEntity<>(zone),
            IdentityZone.class
        );

        assertEquals(HttpStatus.CREATED, created.getStatusCode());

    }

    @Test
    public void create_zone_with_default_cors_configuration() throws Exception {
        ResponseEntity<IdentityZone> created = context.getRestTemplate().exchange(
            UAA_URI+"/identity-zones",
            POST,
            new HttpEntity<>(zone),
            IdentityZone.class
        );

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertThat(created.getBody().getConfig().getCorsPolicy().getXhrConfiguration().getAllowedMethods(), containsInAnyOrder("GET", "OPTIONS"));
        assertThat(created.getBody().getConfig().getCorsPolicy().getDefaultConfiguration().getAllowedMethods(), containsInAnyOrder("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH"));
        assertThat(created.getBody().getConfig().getCorsPolicy().getXhrConfiguration().getAllowedHeaders(), containsInAnyOrder(ACCEPT, ACCEPT_LANGUAGE, CONTENT_TYPE, CONTENT_LANGUAGE,AUTHORIZATION, "X-Requested-With"));
        assertThat(created.getBody().getConfig().getCorsPolicy().getDefaultConfiguration().getAllowedHeaders(), containsInAnyOrder(ACCEPT, ACCEPT_LANGUAGE, CONTENT_TYPE, CONTENT_LANGUAGE,AUTHORIZATION));
        assertTrue(created.getBody().getConfig().getCorsPolicy().getXhrConfiguration().isAllowedCredentials());
        assertFalse(created.getBody().getConfig().getCorsPolicy().getDefaultConfiguration().isAllowedCredentials());

    }

    @Test
    public void create_zone_with_cors_configuration() throws Exception {
        zone.getConfig().getCorsPolicy().getDefaultConfiguration().setAllowedMethods(Arrays.asList("GET","POST","HEAD"));
        zone.getConfig().getCorsPolicy().getXhrConfiguration().setAllowedMethods(Arrays.asList("GET","POST","OPTIONS"));
        zone.getConfig().getCorsPolicy().getDefaultConfiguration().setAllowedHeaders(Arrays.asList(ACCEPT, ACCEPT_LANGUAGE, CONTENT_TYPE));
        zone.getConfig().getCorsPolicy().getXhrConfiguration().setAllowedHeaders(Arrays.asList(ACCEPT, ACCEPT_LANGUAGE));
        zone.getConfig().getCorsPolicy().getXhrConfiguration().setAllowedCredentials(false);
        zone.getConfig().getCorsPolicy().getDefaultConfiguration().setAllowedCredentials(true);

        ResponseEntity<IdentityZone> created = context.getRestTemplate().exchange(
            UAA_URI+"/identity-zones",
            POST,
            new HttpEntity<>(zone),
            IdentityZone.class
        );

        assertEquals(HttpStatus.CREATED, created.getStatusCode());
        assertThat(created.getBody().getConfig().getCorsPolicy().getXhrConfiguration().getAllowedMethods(), containsInAnyOrder("GET","POST","OPTIONS"));
        assertThat(created.getBody().getConfig().getCorsPolicy().getDefaultConfiguration().getAllowedMethods(), containsInAnyOrder("GET","POST","HEAD"));
        assertThat(created.getBody().getConfig().getCorsPolicy().getXhrConfiguration().getAllowedHeaders(), containsInAnyOrder(ACCEPT, ACCEPT_LANGUAGE));
        assertThat(created.getBody().getConfig().getCorsPolicy().getDefaultConfiguration().getAllowedHeaders(), containsInAnyOrder(ACCEPT, ACCEPT_LANGUAGE, CONTENT_TYPE));
        assertFalse(created.getBody().getConfig().getCorsPolicy().getXhrConfiguration().isAllowedCredentials());
        assertTrue(created.getBody().getConfig().getCorsPolicy().getDefaultConfiguration().isAllowedCredentials());

    }

}
