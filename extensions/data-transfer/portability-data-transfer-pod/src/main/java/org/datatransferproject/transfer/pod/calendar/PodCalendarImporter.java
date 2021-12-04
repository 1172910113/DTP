package org.datatransferproject.transfer.pod.calendar;

import com.alibaba.fastjson.JSONObject;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.cloud.storage.JobStore;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.provider.ImportResult;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.spi.transfer.types.DestinationMemoryFullException;
import org.datatransferproject.spi.transfer.types.InvalidTokenException;
import org.datatransferproject.transfer.pod.PodTransmogrificationConfig;
import org.datatransferproject.transfer.pod.common.PodClient;
import org.datatransferproject.transfer.pod.common.PodClientFactory;
import org.datatransferproject.types.common.models.calendar.CalendarContainerResource;
import org.datatransferproject.types.common.models.calendar.CalendarEventModel;
import org.datatransferproject.types.common.models.calendar.CalendarModel;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * @Author Neil Chao
 * @Date 2021/12/2 18:25
 */
public class PodCalendarImporter implements Importer<TokensAndUrlAuthData, CalendarContainerResource> {

    private final PodClientFactory podClientFactory;
    private final Monitor monitor;


    public PodCalendarImporter(PodClientFactory podClientFactory, Monitor monitor) {
        this.podClientFactory = podClientFactory;
        this.monitor = monitor;
    }

    @Override
    public ImportResult importItem(
            UUID jobId,
            IdempotentImportExecutor idempotentImportExecutor,
            TokensAndUrlAuthData authData,
            CalendarContainerResource data
    ) throws Exception {
        System.out.println("calendar size: " + data.getCalendars().size());
        System.out.println("event size: " + data.getEvents().size());
        //System.out.println(data.getEvents().toString());

        PodClient podClient = podClientFactory.create(authData);

        for (CalendarModel calendar : data.getCalendars()) {
            idempotentImportExecutor.executeAndSwallowIOExceptions(
                    calendar.getId(),
                    calendar.getName(),
                    () -> importSingleCalendar(calendar, podClient));
        }

        for (CalendarEventModel event : data.getEvents()) {
            //String name = (event.getTitle() == null || event.getTitle() == "") ? ("untitled event--" +  UUID.randomUUID()) : event.getTitle();
            idempotentImportExecutor.executeAndSwallowIOExceptions(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    () -> importSingleEvent(event, podClient));
        }

        return ImportResult.OK;
    }

    private String  importSingleEvent(
            CalendarEventModel event,
            PodClient podClient) throws InvalidTokenException, DestinationMemoryFullException, IOException {
        System.out.println(JSONObject.toJSONString(event));
        if(event == null) {
            return "Error: it is an empty event";
        }
        String folder = "calendar_event";
        String title;
        if(event.getTitle() == null || event.getTitle() == "") {
            title = "untitled event--" + UUID.randomUUID();
        } else {
            title = event.getTitle() + "--" + UUID.randomUUID();
        }
        String response = podClient.uploadJson(folder, removeStringAfterNewLine(title), JSONObject.toJSONString(event));
        return response;
    }

    private String importSingleCalendar(
            CalendarModel calendar,
            PodClient podClient) throws InvalidTokenException, DestinationMemoryFullException, IOException {
        System.out.println(JSONObject.toJSONString(calendar));
        String folder = "calendar";
        String title = calendar.getName() == "" ? UUID.randomUUID().toString() : calendar.getName() + "--" + UUID.randomUUID();
        String response = podClient.uploadJson(folder, title, JSONObject.toJSONString(calendar));
        return response;
    }

    private String removeStringAfterNewLine(String str) {
        int index = str.indexOf("\n");
        if(index == -1) {
            return str;
        }
        return str.substring(0, index);
    }

}
