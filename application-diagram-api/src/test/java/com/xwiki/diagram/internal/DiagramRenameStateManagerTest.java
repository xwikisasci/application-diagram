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

import java.util.Collections;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link DiagramRenameStateManager}.
 *
 * @version $Id$
 * @since 2.0
 */
@ComponentTest
class DiagramRenameStateManagerTest
{
    @InjectMockComponents
    private DiagramRenameStateManager manager;

    @MockComponent
    private Logger logger;

    @Test
    void testGetOrCreateStateCreatesNewState()
    {
        String jobId = UUID.randomUUID().toString();
        JobRenameState state = manager.getOrCreateState(jobId);

        assertNotNull(state);
        assertFalse(state.jobFinished);
        assertEquals(0, state.pendingEntries.get());
        assertTrue(state.renameMap.isEmpty());
    }

    @Test
    void testGetOrCreateStateReturnsSameInstanceForSameJobId()
    {
        String jobId = UUID.randomUUID().toString();
        JobRenameState first = manager.getOrCreateState(jobId);
        JobRenameState second = manager.getOrCreateState(jobId);

        assertSame(first, second);
    }

    @Test
    void testGetStateReturnsNullForUnknownJobId()
    {
        assertNull(manager.getState("unknown-job"));
    }

    @Test
    void testGetStateReturnsExistingState()
    {
        String jobId = UUID.randomUUID().toString();

        JobRenameState created = manager.getOrCreateState(jobId);
        JobRenameState fetched = manager.getState(jobId);

        assertSame(created, fetched);
    }

    @Test
    void testRecordRenameAddsEntryToRenameMap()
    {
        String jobId = UUID.randomUUID().toString();

        DocumentReference original = new DocumentReference("wiki", "Space", "OldPage");
        DocumentReference destination = new DocumentReference("wiki", "Space", "NewPage");

        manager.recordRename(jobId, original, destination);

        JobRenameState state = manager.getState(jobId);
        assertNotNull(state);
        assertEquals(destination, state.renameMap.get(original));
    }

    @Test
    void testMarkJobFinishedWithNoPendingEntriesRemovesState()
    {
        String jobId = UUID.randomUUID().toString();

        manager.getOrCreateState(jobId);

        manager.markJobFinished(jobId);

        assertNull(manager.getState(jobId));
    }

    @Test
    void testMarkJobFinishedWithPendingEntriesKeepsState()
    {
        String jobId = UUID.randomUUID().toString();

        JobRenameState state = manager.getOrCreateState(jobId);
        state.pendingEntries.set(1);

        manager.markJobFinished(jobId);

        assertNotNull(manager.getState(jobId));
        assertTrue(state.jobFinished);
    }

    @Test
    void testMarkJobFinishedForUnknownJobIdDoesNothing()
    {
        manager.markJobFinished("nonexistent-job");

        assertNull(manager.getState("nonexistent-job"));
    }

    @Test
    void testDecrementAndCleanupWithJobFinishedAndZeroPendingRemovesState()
    {
        String jobId = UUID.randomUUID().toString();

        JobRenameState state = manager.getOrCreateState(jobId);
        state.pendingEntries.set(1);
        state.jobFinished = true;

        DiagramQueueEntry entry = new DiagramQueueEntry(
            new DocumentReference("wiki", "Space", "OldPage"),
            new DocumentReference("wiki", "Space", "NewPage"),
            Collections.emptyList(),
            jobId,
            state.renameMap
        );

        manager.decrementAndCleanup(entry);

        assertNull(manager.getState(jobId));
    }

    @Test
    void testDecrementAndCleanupWithJobNotFinishedKeepsState()
    {
        String jobId = UUID.randomUUID().toString();

        JobRenameState state = manager.getOrCreateState(jobId);
        state.pendingEntries.set(1);
        state.jobFinished = false;

        DiagramQueueEntry entry = new DiagramQueueEntry(
            new DocumentReference("wiki", "Space", "OldPage"),
            new DocumentReference("wiki", "Space", "NewPage"),
            Collections.emptyList(),
            jobId,
            state.renameMap
        );

        manager.decrementAndCleanup(entry);

        assertNotNull(manager.getState(jobId));
        assertEquals(0, state.pendingEntries.get());
    }

    @Test
    void testDecrementAndCleanupWithMultiplePendingEntriesKeepsState()
    {
        String jobId = UUID.randomUUID().toString();

        JobRenameState state = manager.getOrCreateState(jobId);
        state.pendingEntries.set(2);
        state.jobFinished = true;

        DiagramQueueEntry entry = new DiagramQueueEntry(
            new DocumentReference("wiki", "Space", "OldPage"),
            new DocumentReference("wiki", "Space", "NewPage"),
            Collections.emptyList(),
            jobId,
            state.renameMap
        );

        manager.decrementAndCleanup(entry);

        assertNotNull(manager.getState(jobId));
        assertEquals(1, state.pendingEntries.get());
    }

    @Test
    void testDecrementAndCleanupWithNullJobIdDoesNothing()
    {
        DiagramQueueEntry entry = new DiagramQueueEntry(
            new DocumentReference("wiki", "Space", "OldPage"),
            new DocumentReference("wiki", "Space", "NewPage"),
            Collections.emptyList(),
            null,
            Collections.emptyMap()
        );

        manager.decrementAndCleanup(entry);
    }

    @Test
    void testDecrementAndCleanupForUnknownJobIdDoesNothing()
    {
        DiagramQueueEntry entry = new DiagramQueueEntry(
            new DocumentReference("wiki", "Space", "OldPage"),
            new DocumentReference("wiki", "Space", "NewPage"),
            Collections.emptyList(),
            "nonexistent-job",
            Collections.emptyMap()
        );

        manager.decrementAndCleanup(entry);

        assertNull(manager.getState("nonexistent-job"));
    }
}
