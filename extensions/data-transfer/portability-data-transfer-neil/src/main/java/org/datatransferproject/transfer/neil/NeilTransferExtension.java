package org.datatransferproject.transfer.neil;

import static java.lang.String.format;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.util.concurrent.TimeUnit;
import okhttp3.OkHttpClient;
import org.datatransferproject.api.launcher.ExtensionContext;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.cloud.storage.AppCredentialStore;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.transfer.extension.TransferExtension;
import org.datatransferproject.spi.transfer.provider.Exporter;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.transfer.neil.common.NeilClientFactory;
import org.datatransferproject.transfer.neil.common.NeilCredentialFactory;
import org.datatransferproject.transfer.neil.order.NeilOrderExporter;
import org.datatransferproject.transfer.neil.order.NeilOrderImporter;
import org.datatransferproject.transfer.neil.photos.NeilPhotosExporter;
import org.datatransferproject.transfer.neil.photos.NeilPhotosImporter;
import org.datatransferproject.transfer.neil.videos.NeilVideosExporter;
import org.datatransferproject.transfer.neil.videos.NeilVideosImporter;
import org.datatransferproject.types.transfer.auth.AppCredentials;

/** Bootstraps the Neil data transfer services. */
public class NeilTransferExtension implements TransferExtension {
  public static final String SERVICE_ID = "neil";
  private static final String PHOTOS = "PHOTOS";
  private static final String VIDEOS = "VIDEOS";
  private static final String ORDER = "ORDER";
  private static final ImmutableList<String> SUPPORTED_IMPORT_SERVICES =
      ImmutableList.of(PHOTOS, VIDEOS, ORDER);
  private static final ImmutableList<String> SUPPORTED_EXPORT_SERVICES =
      ImmutableList.of(PHOTOS, VIDEOS, ORDER);
  private static final String BASE_API_URL = "http://49.233.12.153:53010";
  private ImmutableMap<String, Importer> importerMap;
  private ImmutableMap<String, Exporter> exporterMap;
  private boolean initialized = false;

  // Needed for ServiceLoader to load this class.
  public NeilTransferExtension() {}

  @Override
  public String getServiceId() {
    return SERVICE_ID;
  }

  @Override
  public Exporter<?, ?> getExporter(String transferDataType) {
    Preconditions.checkState(initialized);
    Preconditions.checkArgument(SUPPORTED_EXPORT_SERVICES.contains(transferDataType));
    return exporterMap.get(transferDataType);
  }

  @Override
  public Importer<?, ?> getImporter(String transferDataType) {
    Preconditions.checkState(initialized);
    Preconditions.checkArgument(SUPPORTED_IMPORT_SERVICES.contains(transferDataType));
    return importerMap.get(transferDataType);
  }

  @Override
  public void initialize(ExtensionContext context) {
    // Note: initialize could be called twice in an account migration scenario
    // where we import and export to the same service provider. So just return
    // rather than throwing if called multiple times.
    if (initialized) return;

    JobStore jobStore = context.getService(JobStore.class);
    HttpTransport httpTransport = context.getService(HttpTransport.class);
    JsonFactory jsonFactory = context.getService(JsonFactory.class);
    OkHttpClient client = new OkHttpClient.Builder().build();
    ObjectMapper mapper = new ObjectMapper();

    AppCredentials appCredentials;
    try {
      appCredentials =
          context
              .getService(AppCredentialStore.class)
              .getAppCredentials("NEIL_KEY", "NEIL_SECRET");
    } catch (IOException e) {
      Monitor monitor = context.getMonitor();
      monitor.info(
          () -> "Unable to retrieve Neil AppCredentials. Did you set NEIL_KEY and NEIL_SECRET?");
      return;
    }

    // Create the NeilCredentialFactory with the given {@link AppCredentials}.
    NeilCredentialFactory credentialFactory =
        new NeilCredentialFactory(httpTransport, jsonFactory, appCredentials);

    Monitor monitor = context.getMonitor();

    int fileUploadReadTimeout = context.getSetting("neilFileUploadReadTimeout", 60000);
    int fileUploadWriteTimeout = context.getSetting("neilFileUploadWriteTimeout", 60000);

    monitor.info(
        () ->
            format(
                "Configuring Neil HTTP file upload client with read timeout %d ms and write timeout %d ms",
                fileUploadReadTimeout, fileUploadWriteTimeout));

    OkHttpClient fileUploadClient =
        client
            .newBuilder()
            .readTimeout(fileUploadReadTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(fileUploadReadTimeout, TimeUnit.MILLISECONDS)
            .build();

    NeilClientFactory neilClientFactory =
        new NeilClientFactory(
            BASE_API_URL, client, fileUploadClient, mapper, monitor, credentialFactory);

    ImmutableMap.Builder<String, Importer> importBuilder = ImmutableMap.builder();
    importBuilder.put(PHOTOS, new NeilPhotosImporter(neilClientFactory, monitor, jobStore));
    importBuilder.put(ORDER, new NeilOrderImporter(neilClientFactory, monitor, jobStore));
    importBuilder.put(VIDEOS, new NeilVideosImporter(neilClientFactory, monitor));
    importerMap = importBuilder.build();

    ImmutableMap.Builder<String, Exporter> exportBuilder = ImmutableMap.builder();
    exportBuilder.put(PHOTOS, new NeilPhotosExporter(neilClientFactory, monitor));
    exportBuilder.put(ORDER, new NeilOrderExporter(neilClientFactory, monitor));
    exportBuilder.put(VIDEOS, new NeilVideosExporter(neilClientFactory, monitor));
    exporterMap = exportBuilder.build();

    initialized = true;
  }
}
