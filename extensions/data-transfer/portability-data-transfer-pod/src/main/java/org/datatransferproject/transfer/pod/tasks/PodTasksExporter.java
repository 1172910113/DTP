package org.datatransferproject.transfer.pod.tasks;

import org.datatransferproject.spi.transfer.provider.ExportResult;
import org.datatransferproject.spi.transfer.provider.Exporter;
import org.datatransferproject.types.common.ExportInformation;
import org.datatransferproject.types.common.models.tasks.TaskContainerResource;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

import java.util.Optional;
import java.util.UUID;

/**
 * @Author Neil Chao
 * @Date 2021/12/3 22:16
 */
public class PodTasksExporter implements Exporter<TokensAndUrlAuthData, TaskContainerResource> {
    @Override
    public ExportResult<TaskContainerResource> export(UUID jobId, TokensAndUrlAuthData authData, Optional<ExportInformation> exportInformation) throws Exception {
        return null;
    }
}
