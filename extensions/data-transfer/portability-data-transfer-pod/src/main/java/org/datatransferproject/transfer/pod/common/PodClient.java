/*
 * Copyright 2020 The Data-Portability Project Authors.
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
package org.datatransferproject.transfer.pod.common;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.auth.oauth2.Credential;
import com.google.common.collect.ImmutableList;

import java.io.*;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.http.client.methods.RequestBuilder;
import org.apache.http.client.utils.URIBuilder;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.transfer.types.DestinationMemoryFullException;
import org.datatransferproject.spi.transfer.types.InvalidTokenException;
import org.datatransferproject.types.common.models.tasks.TaskListModel;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;
import org.openrdf.model.Statement;
import org.openrdf.model.impl.BNodeImpl;
import org.openrdf.model.impl.LiteralImpl;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.turtle.TurtleUtil;
import org.openrdf.rio.turtle.TurtleWriter;

/** A minimal Pod REST API client. */
public class PodClient {
  private final String baseUrl;
  private final OkHttpClient client;
  private final OkHttpClient fileUploadClient;
  private final ObjectMapper objectMapper;
  private final Monitor monitor;
  private final PodCredentialFactory credentialFactory;
  private Credential credential;
  private boolean rootEnsured;

  private static final String API_PATH_PREFIX = "/api/v2";
  private static final String CONTENT_API_PATH_PREFIX = "/123";
  private static final String ROOT_NAME = "r";

  public PodClient(
      String baseUrl,
      OkHttpClient client,
      OkHttpClient fileUploadClient,
      ObjectMapper objectMapper,
      Monitor monitor,
      PodCredentialFactory credentialFactory) {
    this.baseUrl = baseUrl;
    this.client = client;
    this.fileUploadClient = fileUploadClient;
    this.objectMapper = objectMapper;
    this.monitor = monitor;
    this.credentialFactory = credentialFactory;
    this.credential = null;
    this.rootEnsured = false;
  }

  public boolean fileExists(String path) throws IOException, InvalidTokenException {
    String url;
    try {
      url =
          getUriBuilder()
              .setPath("/mounts/primary/files/info")
              .setParameter("path", path)
              .build()
              .toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not produce url.", e);
    }

    //System.out.println(url);
    Request.Builder requestBuilder = getRequestBuilder(url);

    try (Response response = getResponse(requestBuilder)) {
      int code = response.code();
      if (code == 200) {
        return true;
      }
      if (code == 404) {
        return false;
      }
      throw new IOException(
          "Got error code: "
              + code
              + " message: "
              + response.message()
              + " body: "
              + response.body().string());
    }
  }

  public void ensureFolder(String parentPath, String name)
      throws IOException, InvalidTokenException {
    Map<String, Object> rawFolder = new LinkedHashMap<>();
    rawFolder.put("name", name);

    String url;
    try {
      url =
          getUriBuilder()
              .setPath(API_PATH_PREFIX + "/mounts/primary/files/folder")
              .setParameter("path", parentPath)
              .build()
              .toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not produce url.", e);
    }

    Request.Builder requestBuilder = getRequestBuilder(url);
    requestBuilder.post(
        RequestBody.create(
            MediaType.parse("application/json"), objectMapper.writeValueAsString(rawFolder)));

    try (Response response = getResponse(requestBuilder)) {
      int code = response.code();
      // 409 response code means that the folder already exists
      if ((code < 200 || code > 299) && code != 409) {
        throw new IOException(
            "Got error code: "
                + code
                + " message: "
                + response.message()
                + " body: "
                + response.body().string());
      }
    }
  }

  public void addDescription(String path, String description)
      throws IOException, InvalidTokenException {
    Map<String, String[]> tags = new LinkedHashMap<>();
    tags.put("description", new String[] {description});
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("tags", tags);

    String url;
    try {
      url =
          getUriBuilder()
              .setPath(API_PATH_PREFIX + "/mounts/primary/files/tags/add")
              .setParameter("path", path)
              .build()
              .toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not produce url.", e);
    }

    Request.Builder requestBuilder = getRequestBuilder(url);
    requestBuilder.post(
        RequestBody.create(
            MediaType.parse("application/json"), objectMapper.writeValueAsString(body)));

    try (Response response = getResponse(requestBuilder)) {
      int code = response.code();
      if ((code < 200 || code > 299) && code != 409) {
        throw new IOException(
            "Got error code: "
                + code
                + " message: "
                + response.message()
                + " body: "
                + response.body().string());
      }
    }
  }

  @SuppressWarnings("unchecked")
  public String uploadFile(
      String parentPath,
      String name,
      InputStream inputStream,
      String mediaType,
      Date modified,
      String description)
      throws IOException, InvalidTokenException, DestinationMemoryFullException {
    String url;
    try {
      URIBuilder builder =
          getUriBuilder()
              .setPath(
                      ensureFrontSlash(this.getUsernameFromAuthData()) +
                      "/photos" +
                      ensureFrontSlash(removeTrailingSlash(parentPath))
              );
      url = builder.build().toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not produce url.", e);
    }

    Request.Builder requestBuilder = getRequestBuilder(url);

    RequestBody uploadBody = new InputStreamRequestBody(MediaType.parse(mediaType), inputStream);

    requestBuilder.put(uploadBody);

    sentRequest(requestBuilder, inputStream);

    return name;
  }

  public String uploadJson(String folder, String title, String json)
          throws InvalidTokenException, IOException, DestinationMemoryFullException {
    String url;
    try {
      URIBuilder builder =
              getUriBuilder()
                      .setPath(
                              ensureFrontSlash(this.getUsernameFromAuthData()) +
                                      ensureFrontSlash(removeTrailingSlash(folder)) +
                                      ensureFrontSlash(removeTrailingSlash(title))
                      );
      url = builder.build().toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not produce url.", e);
    }

    Request.Builder requestBuilder = getRequestBuilder(url);

    ByteArrayInputStream stream = new ByteArrayInputStream(json.getBytes());

    RequestBody uploadBody = new InputStreamRequestBody(MediaType.parse("application/json"), stream);

    requestBuilder.put(uploadBody);

    sentRequest(requestBuilder, stream);

    return title;
  }

  public String uploadTasksList(String folder, TaskListModel list) throws RDFHandlerException, InvalidTokenException, DestinationMemoryFullException, IOException {
    String name = list.getName();
    String url;
    try {
      URIBuilder builder =
              getUriBuilder()
                      .setPath(
                              ensureFrontSlash(this.getUsernameFromAuthData()) +
                                      ensureFrontSlash(removeTrailingSlash(folder)) +
                                      ensureFrontSlash(ensureTrailingSlash(name))
                      );
      url = builder.build().toString();
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not produce url.", e);
    }
    String id = list.getId();
    Statement statement = new StatementImpl(
            new URIImpl(url),
            new URIImpl("http://purl.org/dc/terms/identifier"),
            new LiteralImpl(id));
    ByteArrayOutputStream stream = new ByteArrayOutputStream();
    TurtleWriter writer = new TurtleWriter(stream);
    writer.startRDF();
    writer.handleNamespace("dc", "http://purl.org/dc/terms/");
    writer.handleStatement(statement);
    writer.endRDF();
    String turtle = TurtleUtil.encodeString(stream.toString());

    Request.Builder requestBuilder = getRequestBuilder(url);

    ByteArrayInputStream inputStream = new ByteArrayInputStream(turtle.getBytes());

    RequestBody uploadBody = new InputStreamRequestBody(MediaType.parse("text/turtle"), inputStream);

    requestBuilder.put(uploadBody);

    sentRequest(requestBuilder, inputStream);

    return id;
  }

  private int sentRequest(Request.Builder requestBuilder, InputStream stream) throws InvalidTokenException, IOException, DestinationMemoryFullException {
    // We need to reset the input stream because the request could already read some data
    try (Response response =
                 getResponse(fileUploadClient, requestBuilder, () -> stream.reset())) {
      int code = response.code();
      ResponseBody body = response.body();
      if (code == 413) {
        throw new DestinationMemoryFullException(
                "Pod quota exceeded", new Exception("Pod file upload response code " + code));
      }
      if (code < 200 || code > 299) {
        throw new IOException(
                "Got error code: "
                        + code
                        + " message: "
                        + response.message()
                        + " body: "
                        + body.string());
      }
      return code;
    }
  }

  public String getRootPath() {
    return "/" + ROOT_NAME;
  }

  public String ensureRootFolder() throws IOException, InvalidTokenException {
    if (!rootEnsured) {
      ensureFolder("/", ROOT_NAME);
      rootEnsured = true;
    }

    return getRootPath();
  }

  public Credential getOrCreateCredential(TokensAndUrlAuthData authData) {
    if (this.credential == null) {
      this.credential = this.credentialFactory.createCredential(authData);
    }
    return this.credential;
  }

  private Request.Builder getRequestBuilder(String url) {
    Request.Builder requestBuilder = new Request.Builder().url(url);
    requestBuilder.header("Authorization", "Bearer " + credential.getAccessToken());
    return requestBuilder;
  }

  private URIBuilder getUriBuilder() {
    try {
      return new URIBuilder(baseUrl);
    } catch (URISyntaxException e) {
      throw new IllegalStateException("Could not produce url.", e);
    }
  }

  private Response getResponse(
      OkHttpClient httpClient, Request.Builder requestBuilder, OnRetry onRetry)
      throws IOException, InvalidTokenException {
    Response response = client.newCall(requestBuilder.build()).execute();

    if (response.code() == 401) {
      response.close();

      // If there was an unauthorized error, then try refreshing the creds
      credentialFactory.refreshCredential(credential);
      monitor.info(() -> "Refreshed authorization token successfuly");

      requestBuilder.header("Authorization", "Bearer " + credential.getAccessToken());

      if (onRetry != null) {
        onRetry.run();
      }

      response = httpClient.newCall(requestBuilder.build()).execute();
    }

    return response;
  }

  private Response getResponse(Request.Builder requestBuilder)
      throws IOException, InvalidTokenException {
    return getResponse(client, requestBuilder, null);
  }

  public static String trimDescription(String description) {
    if (description == null) {
      return description;
    }
    if (description.length() > 1000) {
      return description.substring(0, 1000);
    }
    return description;
  }

  @FunctionalInterface
  private interface OnRetry {
    void run() throws IOException, InvalidTokenException;
  }

  private String getUsernameFromAuthData() {
    try {
      String jwt = this.credential.getAccessToken();
      //System.out.println("jwt: " + jwt);
      String payload = jwt.split("\\.")[1];
      byte[] bytes = Base64.getDecoder().decode(payload);
      String json = new String(bytes, "utf-8");
      JSONObject object = JSON.parseObject(json);
      //System.out.println("Start!!!!!!!!!!");
      String webid = object.get("webid").toString();
      //System.out.println("webid: " + webid);
      String reg = "https?:\\/\\/[^\\/]*\\/([^\\/]*).*";
      Pattern pattern = Pattern.compile(reg);
      Matcher m = pattern.matcher(webid);
      if(m.find()) {
        return m.group(1);
      }
      return "";
    } catch (Exception e) {
      return "";
    }
  }

  private String ensureFrontSlash(String str) {
    if(str == null || str.length() == 0) {
      return "";
    }
    return str.replaceFirst("^[\\\\\\/]*", "/");
  }

  private String ensureTrailingSlash(String str) {
    if(str == null || str.length() == 0) {
      return "";
    }
    return str.replaceFirst("[\\\\\\/]*$", "/");
  }

  private String removeTrailingSlash(String str) {
    if(str == null || str.length() == 0) {
      return "";
    }
    return str.replaceFirst("[\\\\\\/]*$", "");
  }
}
