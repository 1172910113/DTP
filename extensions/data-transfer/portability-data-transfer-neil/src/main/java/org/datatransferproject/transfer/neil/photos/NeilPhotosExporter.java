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
package org.datatransferproject.transfer.neil.photos;

import com.google.common.base.Preconditions;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.google.common.collect.ImmutableList;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.transfer.provider.ExportResult;
import org.datatransferproject.spi.transfer.provider.Exporter;
import org.datatransferproject.spi.transfer.types.CopyExceptionWithFailureReason;
import org.datatransferproject.transfer.neil.common.NeilClientFactory;
import org.datatransferproject.types.common.ExportInformation;
import org.datatransferproject.types.common.models.photos.PhotoAlbum;
import org.datatransferproject.types.common.models.photos.PhotoModel;
import org.datatransferproject.types.common.models.photos.PhotosContainerResource;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

public class NeilPhotosExporter
    implements Exporter<TokensAndUrlAuthData, PhotosContainerResource> {

  private final Monitor monitor;

  private final NeilClientFactory neilClientFactory;

  public NeilPhotosExporter(NeilClientFactory neilClientFactory, Monitor monitor) {
    this.neilClientFactory = neilClientFactory;
    this.monitor = monitor;
  }

  @Override
  public ExportResult<PhotosContainerResource> export(
      UUID jobId, TokensAndUrlAuthData authData, Optional<ExportInformation> exportInformation)
      throws CopyExceptionWithFailureReason {
    Preconditions.checkNotNull(authData);
    System.out.println("access_token:" + authData.getAccessToken());
    System.out.println("refresh_token:" + authData.getRefreshToken());
    System.out.println("token:" + authData.getToken());
    System.out.println("TokenServerEncodedUrl:" + authData.getTokenServerEncodedUrl());
    List<PhotoAlbum> expectedAlbums =
            ImmutableList.of(
                    new PhotoAlbum("/Album 1", "Album 1", "Neil's album"));
    List<PhotoModel> expectedPhotos =
            ImmutableList.of(
                    new PhotoModel(
                            "bg02.jpg",
                            "http://49.233.12.153:53010/photo/download",
                            "Photo 1 description",
                            "image/jpeg",
                            "/Album 1/bg02.jpg",
                            "/Album 1",
                            false,
                            new Date(1324824491000L))
                    );
    PhotosContainerResource containerResource =
            new PhotosContainerResource(expectedAlbums, expectedPhotos);
    return new ExportResult<>(ExportResult.ResultType.END, containerResource, null);
    /*NeilClient neilClient = neilClientFactory.create(authData);
    NeilMediaExport export = new NeilMediaExport(neilClient, monitor);



    try {
      export.export();

      List<PhotoAlbum> exportAlbums = export.getPhotoAlbums();
      List<PhotoModel> exportPhotos = export.getPhotos();

      PhotosContainerResource containerResource =
          new PhotosContainerResource(exportAlbums, exportPhotos);

      return new ExportResult<>(ExportResult.ResultType.END, containerResource, null);
    } catch (IOException e) {
      return new ExportResult<>(e);
    }*/
  }
}
