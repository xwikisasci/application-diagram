/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package com.xwiki.diagram.internal;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;

/**
 * Manages the rename state map for active rename jobs, allowing both the listener and the runnables to interact with it
 * without creating circular dependencies.
 *
 * @version $Id$
 * @since 2.0
 */
@Component(roles = DiagramRenameStateManager.class)
@Singleton
public class DiagramRenameStateManager
{
    /**
     * Holds the rename state for each active rename job, keyed by job ID.
     */
    private final Map<String, JobRenameState> stateByJobId = new ConcurrentHashMap<>();

    @Inject
    private Logger logger;

    /**
     * Gets or creates the state for a given job ID.
     *
     * @param jobId the job ID
     * @return the existing or newly created JobRenameState
     */
    public JobRenameState getOrCreateState(String jobId)
    {
        return stateByJobId.computeIfAbsent(jobId, k -> new JobRenameState());
    }

    /**
     * Gets the state for a given job ID, or null if it doesn't exist.
     *
     * @param jobId the job ID
     * @return the JobRenameState or null
     */
    public JobRenameState getState(String jobId)
    {
        return stateByJobId.get(jobId);
    }

    /**
     * Records a single document rename in the job's rename map.
     *
     * @param jobId the job ID
     * @param originalRef the document reference before rename
     * @param destinationRef the document reference after rename
     */
    public void recordRename(String jobId, DocumentReference originalRef, DocumentReference destinationRef)
    {
        getOrCreateState(jobId).renameMap.put(originalRef, destinationRef);
    }

    /**
     * Marks the job as finished and cleans up if all queue entries are also done.
     *
     * @param jobId the job ID
     */
    public void markJobFinished(String jobId)
    {
        JobRenameState state = stateByJobId.get(jobId);
        if (state != null) {
            state.jobFinished = true;
            cleanupIfDone(jobId, state);
        }
    }

    /**
     * Decrements the pending entry count for the job and cleans up if both the job is finished and no entries remain.
     *
     * @param queueEntry the queue entry that just finished processing
     */
    public void decrementAndCleanup(DiagramQueueEntry queueEntry)
    {
        if (queueEntry.jobID != null) {
            JobRenameState state = stateByJobId.get(queueEntry.jobID);
            if (state != null) {
                state.pendingEntries.decrementAndGet();
                cleanupIfDone(queueEntry.jobID, state);
            }
        }
    }

    private void cleanupIfDone(String jobId, JobRenameState state)
    {
        if (state.jobFinished && state.pendingEntries.get() == 0) {
            stateByJobId.remove(jobId);
            logger.info("Cleaned up rename state for job [{}]", jobId);
        }
    }
}
