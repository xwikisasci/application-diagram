package com.xwiki.diagram.internal;
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

import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.xwiki.model.reference.DocumentReference;

/**
 * Holds the state of a rename job. It contains a map with old-> new reference and a count of the entries added into the
 * queue for updating the backreferences of a diagram.
 *
 * @version $Id$
 * @since 2.0
 */
public class JobRenameState
{
    /**
     * Maps each original document reference to its destination reference for every document processed in this rename
     * job. Populated incrementally as DocumentRenamingEvents fire.
     */
    public final Map<DocumentReference, DocumentReference> renameMap = new ConcurrentHashMap<>();

    /**
     * Entries eligible for a diagram macro reference update, collected while the job is still running. They are only
     * submitted to the macro thread once the whole job has finished.
     */
    public final Queue<DiagramQueueEntry> collectedMacroEntries = new ConcurrentLinkedQueue<>();

    /**
     * Entries eligible for a diagram links update, collected while the job is still running. They are only submitted to
     * the links thread once the whole job has finished.
     */
    public final Queue<DiagramQueueEntry> collectedLinksEntries = new ConcurrentLinkedQueue<>();

    /**
     * Number of queue entries that have been submitted but not yet finished processing. Incremented when an entry is
     * added to a queue; decremented in the runnable's finally block.
     */
    public final AtomicInteger pendingEntries = new AtomicInteger(0);

    /**
     * Set to true when EntitiesRenamedEvent fires, meaning the rename job itself is done. Cleanup can only happen once
     * this is true AND pendingEntries reaches zero.
     */
    public volatile boolean jobFinished;
}
