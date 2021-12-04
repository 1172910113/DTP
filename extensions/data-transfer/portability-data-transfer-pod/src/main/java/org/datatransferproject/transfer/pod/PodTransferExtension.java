package org.datatransferproject.transfer.pod;

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
import org.datatransferproject.transfer.pod.calendar.PodCalendarExporter;
import org.datatransferproject.transfer.pod.calendar.PodCalendarImporter;
import org.datatransferproject.transfer.pod.common.PodClientFactory;
import org.datatransferproject.transfer.pod.common.PodCredentialFactory;
import org.datatransferproject.transfer.pod.photos.PodPhotosExporter;
import org.datatransferproject.transfer.pod.photos.PodPhotosImporter;
import org.datatransferproject.transfer.pod.tasks.PodTasksExporter;
import org.datatransferproject.transfer.pod.tasks.PodTasksImporter;
import org.datatransferproject.types.transfer.auth.AppCredentials;

/** Bootstraps the Pod data transfer services. */
public class PodTransferExtension implements TransferExtension {
  public static final String SERVICE_ID = "pod";
  private static final String PHOTOS = "PHOTOS";
  private static final String CALENDAR = "CALENDAR";
  private static final String TASKS = "TASKS";
  private static final ImmutableList<String> SUPPORTED_IMPORT_SERVICES =
      ImmutableList.of(PHOTOS, CALENDAR, TASKS);
  private static final ImmutableList<String> SUPPORTED_EXPORT_SERVICES =
      ImmutableList.of(PHOTOS, CALENDAR, TASKS);
  private static final String BASE_API_URL = "http://82.157.167.244:3000";
  private ImmutableMap<String, Importer> importerMap;
  private ImmutableMap<String, Exporter> exporterMap;
  private boolean initialized = false;

  // Needed for ServiceLoader to load this class.
  public PodTransferExtension() {}

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
              .getAppCredentials("POD_KEY", "POD_SECRET");
    } catch (IOException e) {
      Monitor monitor = context.getMonitor();
      monitor.info(
          () -> "Unable to retrieve Pod AppCredentials. Did you set POD_KEY and POD_SECRET?");
      return;
    }

    // Create the PodCredentialFactory with the given {@link AppCredentials}.
    PodCredentialFactory credentialFactory =
        new PodCredentialFactory(httpTransport, jsonFactory, appCredentials);

    Monitor monitor = context.getMonitor();

    int fileUploadReadTimeout = context.getSetting("podFileUploadReadTimeout", 60000);
    int fileUploadWriteTimeout = context.getSetting("podFileUploadWriteTimeout", 60000);

    monitor.info(
        () ->
            format(
                "Configuring Pod HTTP file upload client with read timeout %d ms and write timeout %d ms",
                fileUploadReadTimeout, fileUploadWriteTimeout));

    OkHttpClient fileUploadClient =
        client
            .newBuilder()
            .readTimeout(fileUploadReadTimeout, TimeUnit.MILLISECONDS)
            .writeTimeout(fileUploadReadTimeout, TimeUnit.MILLISECONDS)
            .build();

    PodClientFactory podClientFactory =
        new PodClientFactory(
            BASE_API_URL, client, fileUploadClient, mapper, monitor, credentialFactory);

    ImmutableMap.Builder<String, Importer> importBuilder = ImmutableMap.builder();
    importBuilder.put(PHOTOS, new PodPhotosImporter(podClientFactory, monitor, jobStore));
    importBuilder.put(CALENDAR, new PodCalendarImporter(podClientFactory, monitor));
    importBuilder.put(TASKS, new PodTasksImporter(podClientFactory, monitor));
    importerMap = importBuilder.build();

    ImmutableMap.Builder<String, Exporter> exportBuilder = ImmutableMap.builder();
    exportBuilder.put(PHOTOS, new PodPhotosExporter());
    exportBuilder.put(CALENDAR, new PodCalendarExporter());
    exportBuilder.put(TASKS, new PodTasksExporter());
    exporterMap = exportBuilder.build();

    initialized = true;
  }
}
