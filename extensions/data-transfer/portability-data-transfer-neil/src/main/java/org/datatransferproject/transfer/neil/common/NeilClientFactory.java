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
package org.datatransferproject.transfer.neil.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

/** A factory for NeilClient instances. */
public class NeilClientFactory {
  private final String baseUrl;
  private final OkHttpClient client;
  private final OkHttpClient fileUploadClient;
  private final ObjectMapper objectMapper;
  private final Monitor monitor;
  private final NeilCredentialFactory credentialFactory;

  public NeilClientFactory(
      String baseUrl,
      OkHttpClient client,
      OkHttpClient fileUploadClient,
      ObjectMapper objectMapper,
      Monitor monitor,
      NeilCredentialFactory credentialFactory) {
    this.baseUrl = baseUrl;
    this.client = client;
    this.fileUploadClient = fileUploadClient;
    this.objectMapper = objectMapper;
    this.monitor = monitor;
    this.credentialFactory = credentialFactory;
  }

  public NeilClient create(TokensAndUrlAuthData authData) {
    NeilClient neilClient =
        new NeilClient(
            baseUrl, client, fileUploadClient, objectMapper, monitor, credentialFactory);

    // Ensure credential is populated
    neilClient.getOrCreateCredential(authData);

    return neilClient;
  }
}
