package org.datatransferproject.transfer.neil.order;

import com.alibaba.fastjson.JSONArray;
import com.google.common.base.Preconditions;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.http.client.utils.URIBuilder;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.transfer.provider.ExportResult;
import org.datatransferproject.spi.transfer.provider.Exporter;
import org.datatransferproject.spi.transfer.types.DestinationMemoryFullException;
import org.datatransferproject.spi.transfer.types.InvalidTokenException;
import org.datatransferproject.transfer.neil.common.NeilClient;
import org.datatransferproject.transfer.neil.common.NeilClientFactory;
import org.datatransferproject.types.common.ExportInformation;
import org.datatransferproject.types.common.models.order.OrderContainerResource;
import org.datatransferproject.types.common.models.order.OrderModel;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

import java.io.IOException;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * @Author Neil Chao
 * @Date 2021/9/3 15:06
 */
public class NeilOrderExporter implements Exporter<TokensAndUrlAuthData, OrderContainerResource> {

    private final Monitor monitor;

    private final NeilClientFactory neilClientFactory;

    private TokensAndUrlAuthData authData;

    private final static String BASE_URL = "http://49.233.12.153:53010";

    public NeilOrderExporter(NeilClientFactory neilClientFactory, Monitor monitor) {
        this.neilClientFactory = neilClientFactory;
        this.monitor = monitor;
    }

    @Override
    public ExportResult<OrderContainerResource> export(UUID jobId, TokensAndUrlAuthData authData, Optional<ExportInformation> exportInformation) throws Exception {
        Preconditions.checkNotNull(authData);
        //NeilClient neilClient = neilClientFactory.create(authData);
        this.authData = authData;
        List<OrderModel> orders = getOrders();
        OrderContainerResource containerResource = new OrderContainerResource(orders);
        return new ExportResult<>(ExportResult.ResultType.END, containerResource, null);
    }

    public List<OrderModel> getOrders() throws DestinationMemoryFullException, IOException {
        String url;
        try {
            URIBuilder builder = new URIBuilder(BASE_URL)
                    .setPath("/order/getAllOrders");
            url = builder.build().toString();
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Could not produce url.", e);
        }
        Request.Builder requestBuilder = getRequestBuilder(url);
        requestBuilder.get();
        // We need to reset the input stream because the request could already read some data
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
            String jsonStr = body.string();
            return JSONArray.parseArray(jsonStr, OrderModel.class);
        }

    }

    private Request.Builder getRequestBuilder(String url) {
        Request.Builder requestBuilder = new Request.Builder().url(url);
        requestBuilder.header("Authorization", "Bearer " + authData.getAccessToken());
        return requestBuilder;
    }


}
