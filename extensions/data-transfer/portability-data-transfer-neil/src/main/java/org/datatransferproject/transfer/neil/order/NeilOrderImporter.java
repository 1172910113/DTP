package org.datatransferproject.transfer.neil.order;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.sun.org.apache.xpath.internal.operations.Or;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.http.client.utils.URIBuilder;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.provider.ImportResult;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.spi.transfer.types.DestinationMemoryFullException;
import org.datatransferproject.transfer.ImageStreamProvider;
import org.datatransferproject.transfer.neil.NeilTransmogrificationConfig;
import org.datatransferproject.transfer.neil.common.NeilClientFactory;
import org.datatransferproject.types.common.models.order.OrderContainerResource;
import org.datatransferproject.types.common.models.order.OrderModel;
import org.datatransferproject.types.common.models.photos.PhotoModel;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

import java.io.IOException;
import java.net.URISyntaxException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.UUID;

/**
 * @Author Neil Chao
 * @Date 2021/9/3 15:06
 */
public class NeilOrderImporter implements Importer<TokensAndUrlAuthData, OrderContainerResource> {

    private final NeilClientFactory neilClientFactory;
    private final JobStore jobStore;
    private final Monitor monitor;

    private TokensAndUrlAuthData authData;

    private final static String BASE_URL = "http://49.233.12.153:53010";

    public NeilOrderImporter(
            NeilClientFactory neilClientFactory, Monitor monitor, JobStore jobStore) {
        this.neilClientFactory = neilClientFactory;
        this.monitor = monitor;
        this.jobStore = jobStore;
    }

    @Override
    public ImportResult importItem(UUID jobId, IdempotentImportExecutor idempotentExecutor, TokensAndUrlAuthData authData, OrderContainerResource data) throws Exception {
        this.authData = authData;

        monitor.debug(
                () ->
                        String.format(
                                "%s: Importing %s orders",
                                jobId, data.getOrders().size()));
        for (OrderModel orderModel : data.getOrders()) {
            idempotentExecutor.executeAndSwallowIOExceptions(
                    orderModel.getSerial(),
                    orderModel.getItem(),
                    () -> importSingleOrder(orderModel, jobId, idempotentExecutor));
        }
        return ImportResult.OK;
    }

    public String importSingleOrder(OrderModel orderModel, UUID jobId, IdempotentImportExecutor importExecutor) throws IOException, DestinationMemoryFullException {
        monitor.debug(() -> String.format("Import single order %s", orderModel.getItem()));
        String jsonStr = JSON.toJSONString(orderModel);
        String url;
        try {
            URIBuilder builder = new URIBuilder(BASE_URL)
                    .setPath("/order/importOrder")
                    .setParameter("jsonStr", jsonStr);
            url = builder.build().toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not produce url.", e);
        }
        Request.Builder requestBuilder = getRequestBuilder(url);
        requestBuilder.get();
        try (Response response =
                     new OkHttpClient().newCall(requestBuilder.build()).execute()) {
            int code = response.code();
            ResponseBody body = response.body();
            if (code == 413) {
                throw new DestinationMemoryFullException(
                        "Neil quota exceeded", new Exception("Neil file upload response code " + code));
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
        }
        return "";
    }

    private Request.Builder getRequestBuilder(String url) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        requestBuilder.header("Authorization", "Bearer " + authData.getAccessToken());
        return requestBuilder;
    }


}
