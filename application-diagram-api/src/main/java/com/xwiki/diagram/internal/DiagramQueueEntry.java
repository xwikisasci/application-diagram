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

import java.util.List;
import java.util.Map;

import org.xwiki.model.reference.DocumentReference;

/**
 * Diagrams Queue Entry.
 * 
 * @version $Id$
 * @since 1.13
 */
public class DiagramQueueEntry
{
    /**
     * Reference to the page before rename operation.
     */
    public final DocumentReference originalDocRef;

    /**
     * Reference to the page after rename operation.
     */
    public final DocumentReference currentDocRef;

    /**
     * Backlinks of the original document, captured at DocumentRenamingEvent time while they still exist in the
     * database.
     * @since 2.0
     */
    public final List<DocumentReference> backlinks;

    /**
     * The ID of the rename job this entry belongs to, used to look up and clean up the shared JobRenameState.
     * @since 2.0
     */
    public final String jobID;

    /**
     * Live reference to the job's rename map, shared across all queue entries for the same job. Maps old
     * DocumentReference -> new DocumentReference for every document renamed in this job. Used to resolve backlinks
     * that were themselves also renamed before processing the current entry.
     * @since 2.0
     */
    public final Map<DocumentReference, DocumentReference> renameMap;
    /**
     * Constructor.
     * 
     * @param originalDocRef document reference before rename
     * @param currentDocRef document reference after rename
     * @param backlinks of the original document
     * @param jobID the rename job ID
     * @param renameMap the shared old→new reference map for the job
     */
    public DiagramQueueEntry(DocumentReference originalDocRef, DocumentReference currentDocRef,
        List<DocumentReference> backlinks, String jobID,
        Map<DocumentReference, DocumentReference> renameMap)
    {
        this.originalDocRef = originalDocRef;
        this.currentDocRef = currentDocRef;
        this.backlinks = backlinks;
        this.jobID = jobID;
        this.renameMap = renameMap;
    }

    /**
     * @since 2.0
     */
    @Override
    public String toString()
    {
        // Used for debug
        return String.format("DiagramQueueEntry{original=%s, current=%s, backlinks=%s, jobId=%s}",
            originalDocRef, currentDocRef, backlinks, jobID);
    }
}
