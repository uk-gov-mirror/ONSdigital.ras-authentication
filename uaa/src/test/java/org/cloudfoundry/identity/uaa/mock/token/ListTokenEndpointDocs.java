package org.cloudfoundry.identity.uaa.mock.token;

import org.cloudfoundry.identity.uaa.constants.OriginKeys;
import org.cloudfoundry.identity.uaa.mock.util.MockMvcUtils;
import org.cloudfoundry.identity.uaa.scim.ScimUser;
import org.cloudfoundry.identity.uaa.zone.IdentityZone;
import org.junit.Before;
import org.junit.Test;
import org.springframework.restdocs.snippet.Snippet;
import org.springframework.security.oauth2.common.util.RandomValueStringGenerator;
import org.springframework.security.oauth2.provider.ClientDetails;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import static org.cloudfoundry.identity.uaa.test.SnippetUtils.fieldWithPath;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.restdocs.headers.HeaderDocumentation.headerWithName;
import static org.springframework.restdocs.headers.HeaderDocumentation.requestHeaders;
import static org.springframework.restdocs.mockmvc.MockMvcRestDocumentation.document;
import static org.springframework.restdocs.mockmvc.RestDocumentationRequestBuilders.get;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.preprocessResponse;
import static org.springframework.restdocs.operation.preprocess.Preprocessors.prettyPrint;

import static org.cloudfoundry.identity.uaa.test.SnippetUtils.parameterWithName;
import static org.springframework.restdocs.payload.PayloadDocumentation.responseFields;
import static org.springframework.restdocs.request.RequestDocumentation.pathParameters;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public class ListTokenEndpointDocs extends AbstractTokenMockMvcTests{

  private ScimUser user;
  private ClientDetails client;
  private RandomValueStringGenerator generator = new RandomValueStringGenerator();
  MultiValueMap<String, String> tokensPerUser = new LinkedMultiValueMap<>();
  MultiValueMap<String, String> tokensPerClient = new LinkedMultiValueMap<>();

  private static final String TOKEN_ID_DESC = "the identifier for the revocable token";
  private static final String CLIENT_ID_DESC = "a unique string representing the registration information provided by the client";
  private static final String USER_ID_DESC = "unique user identifier. Only applicable for user tokens.";
  private static final String FORMAT_DESC = "format of the token can be 'JWT' or 'opaque'";
  private static final String RESPONSE_TYPE_DESC = "the type of token that should be issued i.e. access token or refresh token";
  private static final String ISSUED_AT_DESC = "time when the token is issued";
  private static final String EXPIRES_AT_DESC = "number of seconds until token expiry";
  private static final String SCOPE_DESC = "space-delimited list of scopes authorized by the user for this client";
  private static final String VALUE_DESC = "the token value";
  private static final String ZONE_ID_DESC = "unique identifier of the identity zone";


  private Snippet responseFields = responseFields(
    fieldWithPath("[].tokenId").description(TOKEN_ID_DESC),
    fieldWithPath("[].clientId").description(CLIENT_ID_DESC),
    fieldWithPath("[].userId").description(USER_ID_DESC),
    fieldWithPath("[].format").description(FORMAT_DESC),
    fieldWithPath("[].responseType").description(RESPONSE_TYPE_DESC),
    fieldWithPath("[].issuedAt").description(ISSUED_AT_DESC),
    fieldWithPath("[].expiresAt").description(EXPIRES_AT_DESC),
    fieldWithPath("[].scope").description(SCOPE_DESC),
    fieldWithPath("[].value").description(VALUE_DESC),
    fieldWithPath("[].zoneId").description(ZONE_ID_DESC)
  );

  @Before
  public void createUsersAndClients() throws Exception {
    user = setUpUser(generator.generate(), "tokens.list,scim.read,scim.write", OriginKeys.UAA, IdentityZone.getUaa().getId());
    client = setUpClients(generator.generate(), "", "tokens.list,scim.read","password,refresh_token", false);

    String token = MockMvcUtils.getUserOAuthAccessToken(
      getMockMvc(),
      client.getClientId(),
      SECRET,
      user.getUserName(),
      SECRET,
      null,
      null,
      true);
    tokensPerUser.add(user.getId(), token);
    tokensPerClient.add(client.getClientId(), token);
  }

  @Test
  public void listUserTokens() throws Exception {
    String userToken = tokensPerUser.getFirst(user.getId());

    ResultActions resultActions = getTokensHelper("/oauth/token/list/user/{id}", this.user.getId(), userToken);

    Snippet pathParameters = pathParameters(
      parameterWithName("id").description("")
    );

    String pathParameterDesc = "Bearer token containing `uaa.user` for retrieving only the tokens associated with the currently logged in user or UAA.admin or Zones.{zone-id}.admin for admin users";

    documentListTokens(resultActions, pathParameters, pathParameterDesc);
  }

  @Test
  public void listClientTokens() throws Exception {
    String clientToken = tokensPerClient.getFirst(client.getClientId());

    ResultActions resultActions = getTokensHelper("/oauth/token/list/client/{id}", this.client.getClientId(), clientToken);

    Snippet pathParameters = pathParameters(
      parameterWithName("id").description("")
    );

    String pathParameterDesc = "Bearer token containing UAA.admin or Zones.{zone-id}.admin";

    documentListTokens(resultActions, pathParameters, pathParameterDesc);
  }

  private ResultActions getTokensHelper(String endPoint, String id, String token) throws Exception {
    return getMockMvc().perform(get(endPoint, id)
      .header(AUTHORIZATION, "Bearer " + token))
      .andExpect(status().isOk());
  }

  private void documentListTokens(ResultActions resultActions, Snippet pathParameters, String authDesc) throws Exception {
    resultActions.andDo(document("{ClassName}/{methodName}",
      preprocessResponse(prettyPrint()),
      requestHeaders(
        headerWithName("Authorization").description(authDesc)
      ),
      pathParameters,
      responseFields
    ));
  }

}
