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
package org.datatransferproject.transfer.neil.videos;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.UUID;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.provider.ImportResult;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.spi.transfer.types.DestinationMemoryFullException;
import org.datatransferproject.spi.transfer.types.InvalidTokenException;
import org.datatransferproject.transfer.ImageStreamProvider;
import org.datatransferproject.transfer.neil.NeilTransmogrificationConfig;
import org.datatransferproject.transfer.neil.common.NeilClient;
import org.datatransferproject.transfer.neil.common.NeilClientFactory;
import org.datatransferproject.types.common.models.videos.VideoAlbum;
import org.datatransferproject.types.common.models.videos.VideoObject;
import org.datatransferproject.types.common.models.videos.VideosContainerResource;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

/** Imports albums and videos to Neil. */
public class NeilVideosImporter
    implements Importer<TokensAndUrlAuthData, VideosContainerResource> {

  private final NeilClientFactory neilClientFactory;
  private final ImageStreamProvider imageStreamProvider;
  private final Monitor monitor;

  public NeilVideosImporter(NeilClientFactory neilClientFactory, Monitor monitor) {
    this.neilClientFactory = neilClientFactory;
    this.imageStreamProvider = new ImageStreamProvider();
    this.monitor = monitor;
  }

  @Override
  public ImportResult importItem(
      UUID jobId,
      IdempotentImportExecutor idempotentImportExecutor,
      TokensAndUrlAuthData authData,
      VideosContainerResource resource)
      throws Exception {
    NeilClient neilClient = neilClientFactory.create(authData);

    monitor.debug(
        () ->
            String.format(
                "%s: Importing %s albums and %s videos",
                jobId, resource.getAlbums().size(), resource.getVideos().size()));

    // TODO: VideosContainerResource does not support transmogrification

    for (VideoAlbum album : resource.getAlbums()) {
      // Create a Neil folder and then save the id with the mapping data
      idempotentImportExecutor.executeAndSwallowIOExceptions(
          album.getId(), album.getName(), () -> createAlbumFolder(album, neilClient));
    }

    for (VideoObject videoObject : resource.getVideos()) {
      String id;
      if (videoObject.getAlbumId() == null) {
        id = videoObject.getDataId();
      } else {
        id = videoObject.getAlbumId() + "-" + videoObject.getDataId();
      }
      idempotentImportExecutor.executeAndSwallowIOExceptions(
          id,
          videoObject.getName(),
          () -> importSingleVideo(videoObject, jobId, idempotentImportExecutor, neilClient));
    }
    return ImportResult.OK;
  }

  private String createAlbumFolder(VideoAlbum album, NeilClient neilClient)
      throws IOException, InvalidTokenException {
    String albumName = NeilTransmogrificationConfig.getAlbumName(album.getName());

    monitor.debug(() -> String.format("Create Neil folder %s", albumName));

    String rootPath = neilClient.ensureRootFolder();
    String fullPath = rootPath + "/" + albumName;

    neilClient.ensureFolder(rootPath, albumName);

    String description = NeilClient.trimDescription(album.getDescription());

    if (description != null && description.length() > 0) {
      neilClient.addDescription(fullPath, description);
    }

    return fullPath;
  }

  private String importSingleVideo(
      VideoObject video,
      UUID jobId,
      IdempotentImportExecutor idempotentImportExecutor,
      NeilClient neilClient)
      throws IOException, InvalidTokenException, DestinationMemoryFullException {
    monitor.debug(() -> String.format("Import single video %s", video.getName()));

    HttpURLConnection conn = imageStreamProvider.getConnection(video.getContentUrl().toString());
    BufferedInputStream inputStream = new BufferedInputStream(conn.getInputStream());

    try {
      String parentPath;
      if (video.getAlbumId() == null) {
        parentPath = neilClient.ensureVideosFolder();
      } else {
        parentPath = idempotentImportExecutor.getCachedValue(video.getAlbumId());
      }

      String name = video.getName();
      String description = neilClient.trimDescription(video.getDescription());

      String fullPath = parentPath + "/" + name;

      if (neilClient.fileExists(fullPath)) {
        monitor.debug(() -> String.format("Video already exists %s", video.getName()));

        return fullPath;
      }

      return neilClient.uploadFile(
          parentPath, name, inputStream, video.getEncodingFormat(), null, description);
    } finally {
      inputStream.close();
    }
  }
}
