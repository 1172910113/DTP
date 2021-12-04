/*
 * Copyright 2018 The Data Transfer Project Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.datatransferproject.auth;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.datatransferproject.types.common.PortabilityCommon.AuthProtocol.OAUTH_2;

import com.google.api.client.http.HttpContent;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.UrlEncodedContent;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.common.io.BaseEncoding;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.security.DigestException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import org.apache.http.client.utils.URIBuilder;
import org.datatransferproject.spi.api.auth.AuthDataGenerator;
import org.datatransferproject.spi.api.auth.AuthServiceProviderRegistry.AuthMode;
import org.datatransferproject.spi.api.types.AuthFlowConfiguration;
import org.datatransferproject.types.transfer.auth.AppCredentials;
import org.datatransferproject.types.transfer.auth.AuthData;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

/**
 * General implementation of an {@link AuthDataGenerator} for OAuth2.
 */
public class OAuth2DataGenerator implements AuthDataGenerator {

  private final OAuth2Config config;
  private final Set<String> scopes;
  // TODO: handle dynamic updates of client ids and secrets #597
  private final String clientId;
  private final String clientSecret;
  private final HttpTransport httpTransport;
  private final String code_challenge;
  private final String code_verifier;

  OAuth2DataGenerator(OAuth2Config config, AppCredentials appCredentials,
      HttpTransport httpTransport,
      String dataType, AuthMode authMode) {
    this.config = config;
    validateConfig();
    this.clientId = appCredentials.getKey();
    this.clientSecret = appCredentials.getSecret();
    this.httpTransport = httpTransport;
    this.scopes = authMode == AuthMode.EXPORT
        ? config.getExportScopes().get(dataType)
        : config.getImportScopes().get(dataType);
    if(config.getServiceName() == "Pod") {
      this.code_verifier = generateRandomVerifier();
      this.code_challenge = generateCodeChallenge(this.code_verifier);
    } else {
      this.code_challenge = "";
      this.code_verifier = "";
    }
  }

  @Override
  public AuthFlowConfiguration generateConfiguration(String callbackBaseUrl, String id) {
    String encodedJobId = BaseEncoding.base64Url().encode(id.getBytes(UTF_8));
    String scope = scopes.isEmpty() ? "" : String.join(" ", scopes);
    try {
      URIBuilder builder = new URIBuilder(config.getAuthUrl())
          .setParameter("response_type", "code")
          .setParameter("client_id", clientId)
          .setParameter("redirect_uri", callbackBaseUrl)
          .setParameter("scope", scope)
          .setParameter("state", encodedJobId);
      if(this.config.getServiceName() == "Pod") {
          builder.setParameter("code_challenge_method", "S256")
                  .setParameter("code_challenge", this.code_challenge);
      }
      if (config.getAdditionalAuthUrlParameters() != null) {
        for (Entry<String, String> entry : config.getAdditionalAuthUrlParameters().entrySet()) {
          builder.setParameter(entry.getKey(), entry.getValue());
        }
      }

      String url = builder.build().toString();
      return new AuthFlowConfiguration(url, OAUTH_2, getTokenUrl());
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not produce url.", e);
    }
  }

  @Override
  public AuthData generateAuthData(String callbackBaseUrl, String authCode, String id,
      AuthData initialAuthData, String extra) {

    System.out.println("callbackBaseUrl:" + callbackBaseUrl);
    System.out.println("authCode:" + authCode);
    System.out.println("id:" + id);
    System.out.println("initialAuthData:" + initialAuthData);
    System.out.println("extra:" + extra);
    System.out.println("clientId:" + clientId);
    System.out.println("clientSecret:" + clientSecret);

    /*if(this.config.getServiceName() == "Pod") {
      TokensAndUrlAuthData res = new TokensAndUrlAuthData(
              "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCIsImtpZCI6IjFNdWdsY1lBZ1FqeVpXa0tkZUxlRk9fLVg2OFI1Q3Y2U3VNSEs2eDNfa0kifQ.eyJ3ZWJpZCI6Imh0dHBzOi8vODIuMTU3LjE2Ny4yNDQ6MzAwMC9uZWlsY2hhby9wcm9maWxlL2NhcmQjbWUiLCJjbGllbnRfaWQiOiJkdHAiLCJqdGkiOiJBSzVmajZDR3Q4eVNqUzQ3MFRTa1ciLCJzdWIiOiJodHRwczovLzgyLjE1Ny4xNjcuMjQ0OjMwMDAvbmVpbGNoYW8vcHJvZmlsZS9jYXJkI21lIiwiaWF0IjoxNjM4MzI0NTAwLCJleHAiOjE2MzgzMjgxMDAsInNjb3BlIjoib3BlbmlkIiwiaXNzIjoiaHR0cHM6Ly84Mi4xNTcuMTY3LjI0NDozMDAwLyIsImF1ZCI6InNvbGlkIiwiYXpwIjoiZHRwIn0.qPM4PqLzA3zgfRITmDPzVeCFL51btgsM3hKc4A4DjuPC9GNmrtpVnxyucxfKHfhqwvGkD-iFytzvu6j_iZd6lFfdgpLFKxzbOXRFFHPMqAjqRhEvXkMegvOGXZyllXXqWaHSE1NQF_GrPgh2rHitVnbunYhyubMHTXqyi4mF9NRi383pmyvYk0RLZp1RPOCCWjy7rmebGZSrbV1ih69JEZUiaHjSzGrZ9LBS9VSgHJ6UA8Hw89ksvoGMaSPUTpRa01VuyiQsEYsNoekdd4hyeuFbFV9ugnSnnWA4RShYxJaEYEpalXLV2TlxNPWdJ4-UMkBJXAf0NO654CIIE8CSaQ",
              "123",
              "http://82.157.167.244:3000/idp/token");
      return res;
    }*/

    Preconditions.checkArgument(
        Strings.isNullOrEmpty(extra), "Extra data not expected for OAuth flow");
    Preconditions.checkArgument(initialAuthData == null,
        "Initial auth data not expected for " + config.getServiceName());

    Map<String, String> params = new LinkedHashMap<>();
    params.put("client_id", clientId);
    params.put("grant_type", "authorization_code");
    params.put("redirect_uri", callbackBaseUrl);
    params.put("code", authCode);
    if(this.config.getServiceName() == "Pod") {
      params.put("code_verifier", this.code_verifier);
    } else {
      params.put("client_secret", clientSecret);
    }

    HttpContent content = new UrlEncodedContent(params);

    try {
      String tokenResponse = OAuthUtils.makeRawPostRequest(
          httpTransport, config.getTokenUrl(), content);

      return config.getResponseClass(tokenResponse);
    } catch (IOException e) {
      throw new RuntimeException("Error getting token", e); // TODO
    }
  }

  private void validateConfig() {
    Preconditions.checkArgument(!Strings.isNullOrEmpty(config.getServiceName()),
        "Config is missing service name");
    Preconditions
        .checkArgument(!Strings.isNullOrEmpty(config.getAuthUrl()), "Config is missing auth url");
    Preconditions
        .checkArgument(!Strings.isNullOrEmpty(config.getTokenUrl()), "Config is missing token url");

    // This decision is not OAuth spec, but part of an effort to prevent accidental scope omission
    Preconditions
        .checkArgument(config.getExportScopes() != null, "Config is missing export scopes");
    Preconditions
        .checkArgument(config.getImportScopes() != null, "Config is missing import scopes");
  }

  private static String generateCodeChallenge(String s) {
    byte[] res = new byte[0];
    try {
      res = getSHA256(s);
    } catch (NoSuchAlgorithmException e) {
      e.printStackTrace();
    } catch (DigestException e) {
      e.printStackTrace();
    } catch (UnsupportedEncodingException e) {
      e.printStackTrace();
    }
    return Base64.getEncoder().encodeToString(res).replace('+', '-').replace('/', '_').replaceAll("=", "");
  }

  private static String generateRandomVerifier() {
    String text = "";
    String possible = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    for (int i = 0; i < 43; i++) {
      text += possible.charAt((int) Math.floor((double) Math.random() * possible.length()));
    }
    return text;
  }

  public static byte[] getSHA256(String str) throws NoSuchAlgorithmException, DigestException, UnsupportedEncodingException {
    MessageDigest md = MessageDigest.getInstance("SHA-256");
    try {
      md.update(str.getBytes("UTF-8"));
      MessageDigest tc1 = (MessageDigest) md.clone();
      byte[] toChapter1Digest = tc1.digest();
      return toChapter1Digest;
    } catch (CloneNotSupportedException cnse) {
      throw new DigestException("couldn't make digest of partial content");
    }
  }
}