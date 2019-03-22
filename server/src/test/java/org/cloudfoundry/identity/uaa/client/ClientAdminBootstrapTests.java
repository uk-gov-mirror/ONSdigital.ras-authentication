package org.cloudfoundry.identity.uaa.client;

import org.cloudfoundry.identity.uaa.audit.event.EntityDeletedEvent;
import org.cloudfoundry.identity.uaa.authentication.SystemAuthentication;
import org.cloudfoundry.identity.uaa.oauth.client.ClientConstants;
import org.cloudfoundry.identity.uaa.test.JdbcTestBase;
import org.cloudfoundry.identity.uaa.util.FakePasswordEncoder;
import org.cloudfoundry.identity.uaa.zone.IdentityZoneHolder;
import org.cloudfoundry.identity.uaa.zone.MultitenantJdbcClientDetailsService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.security.oauth2.provider.ClientAlreadyExistsException;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.security.oauth2.provider.client.BaseClientDetails;
import org.springframework.util.StringUtils;
import org.yaml.snakeyaml.Yaml;

import java.util.*;

import static java.util.Collections.singletonList;
import static org.cloudfoundry.identity.uaa.oauth.token.TokenConstants.*;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.same;

public class ClientAdminBootstrapTests extends JdbcTestBase {

    private ClientAdminBootstrap clientAdminBootstrap;

    private MultitenantJdbcClientDetailsService multitenantJdbcClientDetailsService;
    private ClientMetadataProvisioning clientMetadataProvisioning;
    @Rule
    public ExpectedException exception = ExpectedException.none();
    private ApplicationEventPublisher applicationEventPublisher;

    @Before
    public void setUpClientAdminTests() {
        PasswordEncoder encoder = new FakePasswordEncoder();
        clientAdminBootstrap = new ClientAdminBootstrap(encoder);
        multitenantJdbcClientDetailsService = spy(new MultitenantJdbcClientDetailsService(jdbcTemplate));
        clientMetadataProvisioning = new JdbcClientMetadataProvisioning(multitenantJdbcClientDetailsService, jdbcTemplate);
        clientAdminBootstrap.setClientRegistrationService(multitenantJdbcClientDetailsService);
        clientAdminBootstrap.setClientMetadataProvisioning(clientMetadataProvisioning);
        multitenantJdbcClientDetailsService.setPasswordEncoder(encoder);
        applicationEventPublisher = mock(ApplicationEventPublisher.class);
        clientAdminBootstrap.setApplicationEventPublisher(applicationEventPublisher);
    }

    @Test
    public void simpleAddClient() throws Exception {
        simpleAddClient("foo", clientAdminBootstrap, multitenantJdbcClientDetailsService);
    }

    @Test
    public void clientSlatedForDeletionDoesNotGetInserted() throws Exception {
        String autoApproveId = "autoapprove-" + new RandomValueStringGenerator().generate().toLowerCase();
        simpleAddClient(autoApproveId, clientAdminBootstrap, multitenantJdbcClientDetailsService);
        reset(multitenantJdbcClientDetailsService);
        clientAdminBootstrap = spy(clientAdminBootstrap);
        String clientId = "client-" + new RandomValueStringGenerator().generate().toLowerCase();
        Map<String, Map<String, Object>> clients = Collections.singletonMap(clientId, createClientMap(clientId));
        clientAdminBootstrap.setClients(clients);
        clientAdminBootstrap.setAutoApproveClients(singletonList(autoApproveId));
        clientAdminBootstrap.setClientsToDelete(Arrays.asList(clientId, autoApproveId));
        clientAdminBootstrap.afterPropertiesSet();
        verify(multitenantJdbcClientDetailsService, never()).addClientDetails(any(), anyString());
        verify(multitenantJdbcClientDetailsService, never()).updateClientDetails(any(), anyString());
        verify(multitenantJdbcClientDetailsService, never()).updateClientSecret(any(), any(), anyString());
    }

    @Test
    public void deleteFromYamlExistingClient() throws Exception {
        clientAdminBootstrap = spy(clientAdminBootstrap);
        String clientId = "client-" + new RandomValueStringGenerator().generate().toLowerCase();
        simpleAddClient(clientId, clientAdminBootstrap, multitenantJdbcClientDetailsService);
        verify(clientAdminBootstrap, never()).publish(any());
        clientAdminBootstrap.setClientsToDelete(Collections.singletonList(clientId));
        clientAdminBootstrap.onApplicationEvent(new ContextRefreshedEvent(mock(ApplicationContext.class)));
        ArgumentCaptor<EntityDeletedEvent> captor = ArgumentCaptor.forClass(EntityDeletedEvent.class);
        verify(clientAdminBootstrap, times(1)).publish(captor.capture());
        assertNotNull(captor.getValue());
        verify(applicationEventPublisher, times(1)).publishEvent(same(captor.getValue()));
        assertEquals(clientId, captor.getValue().getObjectId());
        assertEquals(clientId, ((ClientDetails) captor.getValue().getDeleted()).getClientId());
        assertSame(SystemAuthentication.SYSTEM_AUTHENTICATION, captor.getValue().getAuthentication());
        assertNotNull(captor.getValue().getAuditEvent());
    }

    @Test
    public void deleteFromYamlNonExistingClient() {
        ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
        clientAdminBootstrap = spy(clientAdminBootstrap);
        clientAdminBootstrap.setApplicationEventPublisher(publisher);
        String clientId = "client-" + new RandomValueStringGenerator().generate().toLowerCase();
        verify(clientAdminBootstrap, never()).publish(any());
        clientAdminBootstrap.setClientsToDelete(Collections.singletonList(clientId));
        clientAdminBootstrap.onApplicationEvent(new ContextRefreshedEvent(mock(ApplicationContext.class)));
        verify(clientAdminBootstrap, never()).publish(any());
        verify(publisher, never()).publishEvent(any());
    }

    @Test(expected = InvalidClientDetailsException.class)
    public void noRegisteredRedirectUrlForAuthCode() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "foo");
        map.put("secret", "bar");
        map.put("scope", "openid");
        map.put("authorized-grant-types", GRANT_TYPE_AUTHORIZATION_CODE);
        map.put("authorities", "uaa.none");
        doSimpleTest(map, clientAdminBootstrap, multitenantJdbcClientDetailsService);
    }

    @Test(expected = InvalidClientDetailsException.class)
    public void noRegisteredRedirectUrlForImplicit() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "foo");
        map.put("secret", "bar");
        map.put("scope", "openid");
        map.put("authorized-grant-types", GRANT_TYPE_IMPLICIT);
        map.put("authorities", "uaa.none");
        doSimpleTest(map, clientAdminBootstrap, multitenantJdbcClientDetailsService);
    }

    @Test
    public void redirectUrlNotRequired() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "foo");
        map.put("secret", "bar");
        map.put("scope", "openid");
        map.put("authorities", "uaa.none");
        for (String grantType : Arrays.asList("password", "client_credentials", GRANT_TYPE_SAML2_BEARER, GRANT_TYPE_JWT_BEARER, GRANT_TYPE_USER_TOKEN, GRANT_TYPE_REFRESH_TOKEN)) {
            map.put("authorized-grant-types", grantType);
            doSimpleTest(map, clientAdminBootstrap, multitenantJdbcClientDetailsService);
        }

    }

    @Test
    public void simpleAddClientWithSignupSuccessRedirectUrl() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "foo");
        map.put("secret", "bar");
        map.put("scope", "openid");
        map.put("authorized-grant-types", GRANT_TYPE_AUTHORIZATION_CODE);
        map.put("authorities", "uaa.none");
        map.put("signup_redirect_url", "callback_url");
        ClientDetails clientDetails = doSimpleTest(map, clientAdminBootstrap, multitenantJdbcClientDetailsService);
        assertTrue(clientDetails.getRegisteredRedirectUri().contains("callback_url"));
    }

    @Test
    public void clientMetadata_getsBootstrapped() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "foo");
        map.put("secret", "bar");
        map.put("show-on-homepage", true);
        map.put("app-launch-url", "http://takemetothispage.com");
        map.put("app-icon", "bAsE64encODEd/iMAgE=");
        map.put("redirect-uri", "http://localhost/callback");
        map.put("authorized-grant-types", "client_credentials");
        clientAdminBootstrap.setClients(Collections.singletonMap((String) map.get("id"), map));
        clientAdminBootstrap.afterPropertiesSet();

        ClientMetadata clientMetadata = clientMetadataProvisioning.retrieve("foo", IdentityZoneHolder.get().getId());
        assertTrue(clientMetadata.isShowOnHomePage());
        assertEquals("http://takemetothispage.com", clientMetadata.getAppLaunchUrl().toString());
        assertEquals("bAsE64encODEd/iMAgE=", clientMetadata.getAppIcon());
    }

    @Test
    public void additionalInformation() throws Exception {
        List<String> idps = Arrays.asList("idp1", "idp1");
        Map<String, Object> map = new HashMap<>();
        map.put("id", "foo");
        map.put("secret", "bar");
        map.put("scope", "openid");
        map.put("authorized-grant-types", GRANT_TYPE_AUTHORIZATION_CODE);
        map.put("authorities", "uaa.none");
        map.put("signup_redirect_url", "callback_url");
        map.put("change_email_redirect_url", "change_email_url");
        map.put(ClientConstants.ALLOWED_PROVIDERS, idps);
        ClientDetails created = doSimpleTest(map, clientAdminBootstrap, multitenantJdbcClientDetailsService);
        assertEquals(idps, created.getAdditionalInformation().get(ClientConstants.ALLOWED_PROVIDERS));
        assertTrue(created.getRegisteredRedirectUri().contains("callback_url"));
        assertTrue(created.getRegisteredRedirectUri().contains("change_email_url"));
    }

    @Test
    public void simpleAddClientWithChangeEmailRedirectUrl() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "foo");
        map.put("secret", "bar");
        map.put("scope", "openid");
        map.put("authorized-grant-types", GRANT_TYPE_AUTHORIZATION_CODE);
        map.put("authorities", "uaa.none");
        map.put("change_email_redirect_url", "change_email_callback_url");
        ClientDetails created = doSimpleTest(map, clientAdminBootstrap, multitenantJdbcClientDetailsService);
        assertTrue(created.getRegisteredRedirectUri().contains("change_email_callback_url"));
    }

    @Test
    public void simpleAddClientWithAutoApprove() throws Exception {
        ClientMetadataProvisioning clientMetadataProvisioning = mock(ClientMetadataProvisioning.class);
        clientAdminBootstrap.setClientMetadataProvisioning(clientMetadataProvisioning);

        Map<String, Object> map = createClientMap("foo");
        BaseClientDetails output = new BaseClientDetails("foo", "none", "openid", "authorization_code,refresh_token", "uaa.none", "http://localhost/callback");
        output.setClientSecret("bar");
        clientAdminBootstrap.setAutoApproveClients(Arrays.asList("foo", "non-existent-client"));

        when(clientMetadataProvisioning.update(any(ClientMetadata.class), anyString())).thenReturn(new ClientMetadata());
        doReturn(output).when(multitenantJdbcClientDetailsService).loadClientByClientId(eq("foo"), anyString());
        clientAdminBootstrap.setClients(Collections.singletonMap((String) map.get("id"), map));

        BaseClientDetails expectedAdd = new BaseClientDetails(output);

        clientAdminBootstrap.afterPropertiesSet();
        verify(multitenantJdbcClientDetailsService).addClientDetails(expectedAdd, IdentityZoneHolder.get().getId());
        BaseClientDetails expectedUpdate = new BaseClientDetails(expectedAdd);
        expectedUpdate.setAdditionalInformation(Collections.singletonMap(ClientConstants.AUTO_APPROVE, true));
        verify(multitenantJdbcClientDetailsService).updateClientDetails(expectedUpdate, "uaa");
    }

    @Test
    public void overrideClient() throws Exception {
        ClientMetadataProvisioning clientMetadataProvisioning = mock(ClientMetadataProvisioning.class);
        clientAdminBootstrap.setClientMetadataProvisioning(clientMetadataProvisioning);

        BaseClientDetails foo = new BaseClientDetails("foo", "", "openid", "client_credentials,password", "uaa.none");
        foo.setClientSecret("secret");
        multitenantJdbcClientDetailsService.addClientDetails(foo);
        reset(multitenantJdbcClientDetailsService);
        Map<String, Object> map = new HashMap<>();
        map.put("secret", "bar");
        map.put("override", true);
        map.put("authorized-grant-types", "client_credentials");
        clientAdminBootstrap.setClients(Collections.singletonMap("foo", map));
        when(clientMetadataProvisioning.update(any(ClientMetadata.class), anyString())).thenReturn(new ClientMetadata());

        doThrow(new ClientAlreadyExistsException("Planned"))
                .when(multitenantJdbcClientDetailsService).addClientDetails(any(ClientDetails.class), anyString());
        clientAdminBootstrap.afterPropertiesSet();
        verify(multitenantJdbcClientDetailsService, times(1)).addClientDetails(any(ClientDetails.class), anyString());
        ArgumentCaptor<ClientDetails> captor = ArgumentCaptor.forClass(ClientDetails.class);
        verify(multitenantJdbcClientDetailsService, times(1)).updateClientDetails(captor.capture(), anyString());
        verify(multitenantJdbcClientDetailsService, times(1)).updateClientSecret("foo", "bar", IdentityZoneHolder.get().getId());
        assertEquals(new HashSet(Collections.singletonList("client_credentials")), captor.getValue().getAuthorizedGrantTypes());
    }

    @Test
    public void overrideClientWithEmptySecret() throws Exception {
        ClientMetadataProvisioning clientMetadataProvisioning = mock(ClientMetadataProvisioning.class);
        clientAdminBootstrap.setClientMetadataProvisioning(clientMetadataProvisioning);

        BaseClientDetails foo = new BaseClientDetails("foo", "", "openid", "client_credentials,password", "uaa.none");
        foo.setClientSecret("secret");
        multitenantJdbcClientDetailsService.addClientDetails(foo);

        reset(multitenantJdbcClientDetailsService);

        Map<String, Object> map = new HashMap<>();
        map.put("secret", null);
        map.put("override", true);
        map.put("authorized-grant-types", "client_credentials");
        clientAdminBootstrap.setClients(Collections.singletonMap("foo", map));
        when(clientMetadataProvisioning.update(any(ClientMetadata.class), anyString())).thenReturn(new ClientMetadata());

        doThrow(new ClientAlreadyExistsException("Planned"))
                .when(multitenantJdbcClientDetailsService).addClientDetails(any(ClientDetails.class), anyString());
        clientAdminBootstrap.afterPropertiesSet();
        verify(multitenantJdbcClientDetailsService, times(1)).addClientDetails(any(ClientDetails.class), anyString());
        ArgumentCaptor<ClientDetails> captor = ArgumentCaptor.forClass(ClientDetails.class);
        verify(multitenantJdbcClientDetailsService, times(1)).updateClientDetails(captor.capture(), anyString());
        verify(multitenantJdbcClientDetailsService, times(1)).updateClientSecret("foo", "", IdentityZoneHolder.get().getId());
        assertEquals(new HashSet(Collections.singletonList("client_credentials")), captor.getValue().getAuthorizedGrantTypes());
    }

    @Test
    public void overrideClientByDefault() throws Exception {
        ClientMetadataProvisioning clientMetadataProvisioning = mock(ClientMetadataProvisioning.class);
        clientAdminBootstrap.setClientMetadataProvisioning(clientMetadataProvisioning);
        BaseClientDetails foo = new BaseClientDetails("foo", "", "openid", "client_credentials,password", "uaa.none");
        foo.setClientSecret("secret");
        multitenantJdbcClientDetailsService.addClientDetails(foo);
        reset(multitenantJdbcClientDetailsService);

        Map<String, Object> map = new HashMap<>();
        map.put("secret", "bar");
        map.put("redirect-uri", "http://localhost/callback");
        map.put("authorized-grant-types", "client_credentials");
        clientAdminBootstrap.setClients(Collections.singletonMap("foo", map));
        when(clientMetadataProvisioning.update(any(ClientMetadata.class), anyString())).thenReturn(new ClientMetadata());
        doThrow(new ClientAlreadyExistsException("Planned")).when(multitenantJdbcClientDetailsService)
                .addClientDetails(
                        any(ClientDetails.class),
                        anyString()
                );
        clientAdminBootstrap.afterPropertiesSet();
        verify(multitenantJdbcClientDetailsService, times(1)).addClientDetails(any(ClientDetails.class), anyString());
        verify(multitenantJdbcClientDetailsService, times(1)).updateClientDetails(any(ClientDetails.class), anyString());
        verify(multitenantJdbcClientDetailsService, times(1)).updateClientSecret("foo", "bar", IdentityZoneHolder.get().getId());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void overrideClientWithYaml() throws Exception {
        ClientMetadataProvisioning clientMetadataProvisioning = mock(ClientMetadataProvisioning.class);
        clientAdminBootstrap.setClientMetadataProvisioning(clientMetadataProvisioning);

        @SuppressWarnings("rawtypes")
        Map fooBeforeClient = new Yaml().loadAs("id: foo\noverride: true\nsecret: somevalue\n"
                + "access-token-validity: 100\nredirect-uri: http://localhost/callback\n"
                + "authorized-grant-types: client_credentials", Map.class);
        @SuppressWarnings("rawtypes")
        Map barBeforeClient = new Yaml().loadAs("id: bar\noverride: true\nsecret: somevalue\n"
                + "access-token-validity: 100\nredirect-uri: http://localhost/callback\n"
                + "authorized-grant-types: client_credentials", Map.class);
        @SuppressWarnings("rawtypes")
        Map clients = new HashMap();
        clients.put("foo", fooBeforeClient);
        clients.put("bar", barBeforeClient);
        clientAdminBootstrap.setClients(clients);
        clientAdminBootstrap.afterPropertiesSet();

        Map fooUpdateClient = new HashMap(fooBeforeClient);
        fooUpdateClient.put("secret", "bar");
        Map barUpdateClient = new HashMap(fooBeforeClient);
        barUpdateClient.put("secret", "bar");
        clients = new HashMap();
        clients.put("foo", fooUpdateClient);
        clients.put("bar", barUpdateClient);
        clientAdminBootstrap.setClients(clients);

        reset(multitenantJdbcClientDetailsService);
        when(clientMetadataProvisioning.update(any(ClientMetadata.class), anyString())).thenReturn(new ClientMetadata());
        doThrow(new ClientAlreadyExistsException("Planned")).when(multitenantJdbcClientDetailsService).addClientDetails(
                any(ClientDetails.class), anyString());
        clientAdminBootstrap.afterPropertiesSet();
        verify(multitenantJdbcClientDetailsService, times(2)).addClientDetails(any(ClientDetails.class), anyString());
        verify(multitenantJdbcClientDetailsService, times(2)).updateClientDetails(any(ClientDetails.class), anyString());
        verify(multitenantJdbcClientDetailsService, times(1)).updateClientSecret("foo", "bar", IdentityZoneHolder.get().getId());
        verify(multitenantJdbcClientDetailsService, times(1)).updateClientSecret("bar", "bar", IdentityZoneHolder.get().getId());
    }

    @Test
    public void changePasswordDuringBootstrap() throws Exception {
        Map<String, Object> map = createClientMap("foo");
        ClientDetails created = doSimpleTest(map, clientAdminBootstrap, multitenantJdbcClientDetailsService);
        assertSet((String) map.get("redirect-uri"), null, created.getRegisteredRedirectUri(), String.class);
        ClientDetails details = multitenantJdbcClientDetailsService.loadClientByClientId("foo");
        assertTrue("Password should match bar:", clientAdminBootstrap.getPasswordEncoder().matches("bar", details.getClientSecret()));
        map.put("secret", "bar1");
        created = doSimpleTest(map, clientAdminBootstrap, multitenantJdbcClientDetailsService);
        assertSet((String) map.get("redirect-uri"), null, created.getRegisteredRedirectUri(), String.class);
        details = multitenantJdbcClientDetailsService.loadClientByClientId("foo");
        assertTrue("Password should match bar1:", clientAdminBootstrap.getPasswordEncoder().matches("bar1", details.getClientSecret()));
        assertFalse("Password should not match bar:", clientAdminBootstrap.getPasswordEncoder().matches("bar", details.getClientSecret()));
    }

    @Test
    public void passwordHashDidNotChangeDuringBootstrap() throws Exception {
        Map<String, Object> map = createClientMap("foo");
        ClientDetails created = doSimpleTest(map, clientAdminBootstrap, multitenantJdbcClientDetailsService);
        assertSet((String) map.get("redirect-uri"), null, created.getRegisteredRedirectUri(), String.class);
        ClientDetails details = multitenantJdbcClientDetailsService.loadClientByClientId("foo");
        assertTrue("Password should match bar:", clientAdminBootstrap.getPasswordEncoder().matches("bar", details.getClientSecret()));
        String hash = details.getClientSecret();
        created = doSimpleTest(map, clientAdminBootstrap, multitenantJdbcClientDetailsService);
        assertSet((String) map.get("redirect-uri"), null, created.getRegisteredRedirectUri(), String.class);
        details = multitenantJdbcClientDetailsService.loadClientByClientId("foo");
        assertTrue("Password should match bar:", clientAdminBootstrap.getPasswordEncoder().matches("bar", details.getClientSecret()));
        assertEquals("Password hash must not change on an update:", hash, details.getClientSecret());
    }

    @Test
    public void clientWithoutGrantTypeFails() throws Exception {
        Map<String, Object> map = new HashMap<>();
        map.put("id", "foo");
        map.put("secret", "bar");
        map.put("scope", "openid");
        map.put("authorities", "uaa.none");
        exception.expect(InvalidClientDetailsException.class);
        exception.expectMessage("Client must have at least one authorized-grant-type");
        clientAdminBootstrap.setClients(Collections.singletonMap((String) map.get("id"), map));
        clientAdminBootstrap.afterPropertiesSet();
    }

    private static ClientDetails doSimpleTest(
            final Map<String, Object> map,
            final ClientAdminBootstrap bootstrap,
            final MultitenantJdbcClientDetailsService clientRegistrationService) throws Exception {
        bootstrap.setClients(Collections.singletonMap((String) map.get("id"), map));
        bootstrap.afterPropertiesSet();

        ClientDetails created = clientRegistrationService.loadClientByClientId((String) map.get("id"));
        assertNotNull(created);
        assertSet((String) map.get("scope"), Collections.singleton("uaa.none"), created.getScope(), String.class);
        assertSet((String) map.get("resource-ids"), new HashSet(Collections.singletonList("none")), created.getResourceIds(), String.class);

        String authTypes = (String) map.get("authorized-grant-types");
        if (authTypes != null && authTypes.contains(GRANT_TYPE_AUTHORIZATION_CODE)) {
            authTypes += ",refresh_token";
        }
        assertSet(authTypes, Collections.emptySet(), created.getAuthorizedGrantTypes(), String.class);

        Integer validity = (Integer) map.get("access-token-validity");
        assertEquals(validity, created.getAccessTokenValiditySeconds());
        validity = (Integer) map.get("refresh-token-validity");
        assertEquals(validity, created.getRefreshTokenValiditySeconds());

        assertSet((String) map.get("authorities"), Collections.emptySet(), created.getAuthorities(), GrantedAuthority.class);

        Map<String, Object> info = new HashMap<>(map);

        for (String key : Arrays.asList("resource-ids", "scope", "authorized-grant-types", "authorities",
                "redirect-uri", "secret", "id", "override", "access-token-validity",
                "refresh-token-validity")) {
            info.remove(key);
        }
        for (Map.Entry<String, Object> entry : info.entrySet()) {
            assertTrue("Client should contain additional information key:" + entry.getKey(), created.getAdditionalInformation().containsKey(entry.getKey()));
            if (entry.getValue() != null) {
                assertEquals(entry.getValue(), created.getAdditionalInformation().get(entry.getKey()));
            }
        }

        return created;
    }

    private static void assertSet(String expectedValue, Collection defaultValueIfNull, Collection actualValue, Class<?> type) {
        Collection assertScopes = defaultValueIfNull;
        if (expectedValue != null) {
            if (String.class.equals(type)) {
                assertScopes = StringUtils.commaDelimitedListToSet(expectedValue);
            } else {
                assertScopes = AuthorityUtils.commaSeparatedStringToAuthorityList(expectedValue);
            }
        }
        assertEquals(assertScopes, actualValue);
    }

    private static void simpleAddClient(
            final String clientId,
            final ClientAdminBootstrap bootstrap,
            final MultitenantJdbcClientDetailsService clientRegistrationService) throws Exception {
        Map<String, Object> map = createClientMap(clientId);
        ClientDetails created = doSimpleTest(map, bootstrap, clientRegistrationService);
        assertSet((String) map.get("redirect-uri"), null, created.getRegisteredRedirectUri(), String.class);
    }

    private static Map<String, Object> createClientMap(String clientId) {
        Map<String, Object> map = new HashMap<>();
        map.put("id", clientId);
        map.put("secret", "bar");
        map.put("scope", "openid");
        map.put("authorized-grant-types", GRANT_TYPE_AUTHORIZATION_CODE);
        map.put("authorities", "uaa.none");
        map.put("redirect-uri", "http://localhost/callback");
        return map;
    }

}
