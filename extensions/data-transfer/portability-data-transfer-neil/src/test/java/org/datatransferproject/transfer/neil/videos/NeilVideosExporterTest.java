package org.datatransferproject.transfer.neil.videos;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.transfer.provider.ExportResult;
import org.datatransferproject.transfer.neil.common.Fixtures;
import org.datatransferproject.transfer.neil.common.NeilClient;
import org.datatransferproject.transfer.neil.common.NeilClientFactory;
import org.datatransferproject.types.common.models.videos.VideoAlbum;
import org.datatransferproject.types.common.models.videos.VideoObject;
import org.datatransferproject.types.common.models.videos.VideosContainerResource;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class NeilVideosExporterTest {

  private NeilClientFactory clientFactory;
  private NeilClient client;
  private Monitor monitor;
  private NeilVideosExporter exporter;
  private TokensAndUrlAuthData authData;

  @Before
  public void setUp() throws Exception {
    client = mock(NeilClient.class);

    clientFactory = mock(NeilClientFactory.class);
    when(clientFactory.create(any())).thenReturn(client);

    monitor = mock(Monitor.class);

    exporter = new NeilVideosExporter(clientFactory, monitor);

    authData = new TokensAndUrlAuthData("acc", "refresh", "");
  }

  @Test
  public void testExport() throws Exception {
    when(client.getRootPath()).thenReturn("/Data transfer");
    when(client.listRecursive("/Data transfer")).thenReturn(Fixtures.listRecursiveItems);
    when(client.fileLink("/Data transfer/Album 2 :heart:/Video 1.mp4"))
        .thenReturn("https://app-1.koofr.net/content/files/get/Video+1.mp4?base=TESTBASE");
    when(client.fileLink("/Data transfer/Videos/Video 2.mp4"))
        .thenReturn("https://app-1.koofr.net/content/files/get/Video+2.mp4?base=TESTBASE");

    UUID jobId = UUID.randomUUID();

    ExportResult<VideosContainerResource> result =
        exporter.export(jobId, authData, Optional.empty());

    assertEquals(ExportResult.ResultType.END, result.getType());
    assertNull(result.getContinuationData());
    VideosContainerResource exportedData = result.getExportedData();

    List<VideoAlbum> expectedAlbums =
        ImmutableList.of(
            new VideoAlbum("/Album 2 :heart:", "Album 2 ❤️", "Album 2 description ❤️"),
            new VideoAlbum("/Videos", "Videos", null));
    assertEquals(expectedAlbums, exportedData.getAlbums());

    List<VideoObject> expectedVideos =
        ImmutableList.of(
            new VideoObject(
                "Video 1.mp4",
                "https://app-1.koofr.net/content/files/get/Video+1.mp4?base=TESTBASE",
                null,
                "video/mp4",
                "/Album 2 :heart:/Video 1.mp4",
                "/Album 2 :heart:",
                false),
            new VideoObject(
                "Video 2.mp4",
                "https://app-1.koofr.net/content/files/get/Video+2.mp4?base=TESTBASE",
                "Video 3 description",
                "video/mp4",
                "/Videos/Video 2.mp4",
                "/Videos",
                false));
    assertEquals(expectedVideos, exportedData.getVideos());
  }
}
