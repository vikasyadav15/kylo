package com.thinkbiganalytics.nifi.provenance.model.stats;

import com.thinkbiganalytics.nifi.provenance.model.ActiveFlowFile;
import com.thinkbiganalytics.nifi.provenance.model.ProvenanceEventRecordDTO;

import java.util.Set;

/**
 * Created by sr186054 on 8/17/16.
 */
public class StatsModel {

    public static ProvenanceEventStats toFailureProvenanceEventStats(String feedName, ProvenanceEventRecordDTO event) {
        ActiveFlowFile rootFlowFile = event.getFlowFile().getRootFlowFile();
        ProvenanceEventStats stats = new ProvenanceEventStats(feedName);
        stats.setTotalCount(0L);
        stats.setEventId(event.getEventId());
        stats.setProcessorId(event.getComponentId());
        stats.setProcessorName(event.getProcessorName());
        stats.setClusterNodeId(event.getClusterNodeId());
        stats.setTime(event.getEventTime());
        stats.setProcessorsFailed(1L);
        stats.setFlowFileId(event.getFlowFileUuid());
        stats.setRootFlowFileId(rootFlowFile != null ? rootFlowFile.getId() : null);
        stats.setEventDetails(event.getDetails());
        stats.setRootProcessGroupId((rootFlowFile != null && rootFlowFile.hasFeedInformationAssigned()) ? rootFlowFile.getFeedProcessGroupId() : null);
        return stats;
    }

    public static ProvenanceEventStats toProvenanceEventStats(String feedName, ProvenanceEventRecordDTO event) {
        ActiveFlowFile rootFlowFile = event.getFlowFile().getRootFlowFile();

        ProvenanceEventStats stats = new ProvenanceEventStats(feedName);
        stats.setTotalCount(1L);
        stats.setEventId(event.getEventId());
        stats.setProcessorId(event.getComponentId());
        stats.setProcessorName(event.getProcessorName());
        stats.setClusterNodeId(event.getClusterNodeId());
        stats.setTime(event.getEventTime());
        stats.setDuration(event.getEventDuration() != null ? event.getEventDuration() : 0L);
        stats.setBytesIn(event.getInputContentClaimFileSizeBytes() != null ? event.getInputContentClaimFileSizeBytes() : 0L);
        stats.setBytesOut(event.getOutputContentClaimFileSizeBytes() != null ? event.getOutputContentClaimFileSizeBytes() : 0L);
        stats.setFlowFileId(event.getFlowFileUuid());
        stats.setRootFlowFileId(rootFlowFile != null ? rootFlowFile.getId() : null);
        stats.setEventDetails(event.getDetails());
        stats.setRootProcessGroupId((rootFlowFile != null && rootFlowFile.hasFeedInformationAssigned()) ? rootFlowFile.getFeedProcessGroupId() : null);
        stats.setJobsStarted((event.getFlowFile().isRootFlowFile() && rootFlowFile.getFirstEvent() != null && event.equals(rootFlowFile.getFirstEvent())) ? 1L : 0L);
        stats.setJobsFinished((event.isEndingFlowFileEvent() && rootFlowFile != null && rootFlowFile.isFlowComplete()) ? 1L : 0L);
        stats.setFlowFilesStarted(event.isStartOfCurrentFlowFile() ? 1L : 0L);
        stats.setFlowFilesFinished(event.getFlowFile().isCurrentFlowFileComplete() ? 1L : 0L);
        if (event.isTerminatedByFailureRelationship()) {
            //mark the flow file as having a failed event.
            event.getFlowFile().addFailedEvent(event);
        }


        if (stats.getJobsFinished() == 1L) {
            event.setIsEndOfJob(true);
            Long jobTime = null;
            if (event.getFlowFile().getFirstEvent() != null) {
                jobTime = event.getEventTime().getMillis() - event.getFlowFile().getFirstEvent().getEventTime().getMillis();
                stats.setJobDuration(jobTime);
            }

            Set<ProvenanceEventRecordDTO> failedEvents = event.getFlowFile().getRootFlowFile().getFailedEvents(true);
            if (failedEvents != null && !failedEvents.isEmpty()) {
                stats.setJobsFailed(1L);
            } else {
                if (jobTime != null) {
                    stats.setSuccessfulJobDuration(jobTime);
                }
            }


        }

        return stats;
    }


}