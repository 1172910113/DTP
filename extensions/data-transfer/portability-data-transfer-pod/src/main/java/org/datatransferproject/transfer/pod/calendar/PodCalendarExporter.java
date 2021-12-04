package org.datatransferproject.transfer.pod.calendar;

import org.datatransferproject.api.launcher.Monitor;
import org.datatransferproject.spi.transfer.provider.ExportResult;
import org.datatransferproject.spi.transfer.provider.Exporter;
import org.datatransferproject.transfer.pod.common.PodClientFactory;
import org.datatransferproject.types.common.ExportInformation;
import org.datatransferproject.types.common.models.calendar.CalendarContainerResource;
import org.datatransferproject.types.transfer.auth.TokensAndUrlAuthData;

import java.util.Optional;
import java.util.UUID;

/**
 * @Author Neil Chao
 * @Date 2021/12/2 18:25
 */
public class PodCalendarExporter implements Exporter<TokensAndUrlAuthData, CalendarContainerResource> {
    public PodCalendarExporter() {

    }

    @Override
    public ExportResult<CalendarContainerResource> export(UUID jobId, TokensAndUrlAuthData authData, Optional<ExportInformation> exportInformation) throws Exception {
        return null;
    }
}
