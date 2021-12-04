package org.datatransferproject.transfer.pod.tasks;

import com.alibaba.fastjson.JSONObject;
import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.transfer.idempotentexecutor.IdempotentImportExecutor;
import org.datatransferproject.spi.transfer.provider.ImportResult;
import org.datatransferproject.spi.transfer.provider.Importer;
import org.datatransferproject.spi.transfer.types.DestinationMemoryFullException;
import org.datatransferproject.spi.transfer.types.InvalidTokenException;
import org.datatransferproject.transfer.pod.common.PodClient;
import org.datatransferproject.transfer.pod.common.PodClientFactory;
import org.datatransferproject.types.common.models.calendar.CalendarEventModel;
import org.datatransferproject.types.common.models.calendar.CalendarModel;
import org.datatransferproject.types.common.models.tasks.TaskContainerResource;
import org.datatransferproject.types.common.models.tasks.TaskListModel;
import org.datatransferproject.types.common.models.tasks.TaskModel;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;
import org.openrdf.model.Resource;
import org.openrdf.model.Statement;
import org.openrdf.model.URI;
import org.openrdf.model.impl.StatementImpl;
import org.openrdf.model.impl.URIImpl;
import org.openrdf.rio.RDFHandlerException;
import org.openrdf.rio.turtle.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * @Author Neil Chao
 * @Date 2021/12/3 13:24
 */
public class PodTasksImporter implements Importer<TokensAndUrlAuthData, TaskContainerResource> {

    private final PodClientFactory podClientFactory;
    private final Monitor monitor;
    private Map<String, String> idToStringMap;

    public PodTasksImporter(PodClientFactory podClientFactory, Monitor monitor) {
        this.podClientFactory = podClientFactory;
        this.monitor = monitor;
        idToStringMap = new HashMap<>();
    }

    @Override
    public ImportResult importItem(
            UUID jobId,
            IdempotentImportExecutor idempotentImportExecutor,
            TokensAndUrlAuthData authData,
            TaskContainerResource data
    ) throws Exception {
        System.out.println("list size: " + data.getLists().size());
        System.out.println("task size: " + data.getTasks().size());
        PodClient podClient = podClientFactory.create(authData);
        for (TaskListModel list : data.getLists()) {
            if(list == null) continue;
            System.out.println(JSONObject.toJSONString(list));
            idToStringMap.put(list.getId(), list.getName());
            idempotentImportExecutor.executeAndSwallowIOExceptions(
                    list.getId(),
                    list.getName(),
                    () -> importList(list, podClient));
        }

        for (TaskModel task : data.getTasks()) {
            if(task == null) continue;
            System.out.println(JSONObject.toJSONString(task));
            idempotentImportExecutor.executeAndSwallowIOExceptions(
                    UUID.randomUUID().toString(),
                    UUID.randomUUID().toString(),
                    () -> importSingleTask(task, podClient));
        }

        return ImportResult.OK;
    }

    private String importList(TaskListModel list, PodClient client) throws RDFHandlerException, InvalidTokenException, DestinationMemoryFullException, IOException {
        String folder = "tasks";
        String response = client.uploadTasksList(folder, list);
        return response;
    }

    private String importSingleTask(TaskModel task, PodClient client) throws InvalidTokenException, DestinationMemoryFullException, IOException {
        String folder = "tasks/";
        String title;
        if(task.getText() == null || task.getText() == "") {
            title = task.getText();
        } else {
            title = "untitled task--" + UUID.randomUUID();
        }
        if(idToStringMap.containsKey(title)) {
            folder += idToStringMap.get(task.getTaskListId());
        } else {
            return "Error: cannot find task list";
        }
        String response = client.uploadJson(folder, title, JSONObject.toJSONString(task));
        return response;
    }
}
