/*
 * Copyright 2020 The Data Transfer Project Authors.
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

package org.datatransferproject.auth.pod;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import java.util.Map;
import java.util.Set;
import org.datatransferproject.auth.OAuth2Config;

public class PodOAuthConfig implements OAuth2Config {

  @Override
  public String getServiceName() {
    return "Pod";
  }

  @Override
  public String getAuthUrl() {
    return "http://82.157.167.244:3000/idp/auth";
  }

  @Override
  public String getTokenUrl() {
    return "http://82.157.167.244:3000/idp/token";
  }

  @Override
  public Map<String, Set<String>> getExportScopes() {
    // NOTE: PodTransferExtension does not implement export at the moment
    return ImmutableMap.<String, Set<String>>builder()
            .put("PHOTOS", ImmutableSet.of("openid"))
            .put("CALENDAR", ImmutableSet.of("openid"))
            .put("TASKS", ImmutableSet.of("openid"))
            .build();
  }

  @Override
  public Map<String, Set<String>> getImportScopes() {
    return ImmutableMap.<String, Set<String>>builder()
            .put("PHOTOS", ImmutableSet.of("openid"))
            .put("CALENDAR", ImmutableSet.of("openid"))
            .put("TASKS", ImmutableSet.of("openid"))
            .build();
  }
}
