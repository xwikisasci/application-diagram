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

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link DiagramRunnableThreadsManager}.
 *
 * @version $Id$
 * @since 2.0
 */
@ComponentTest
class DiagramRunnableThreadsManagerTest
{
    @InjectMockComponents
    private DiagramRunnableThreadsManager manager;

    @MockComponent
    private DiagramLinksRunnable diagramLinksRunnable;

    @MockComponent
    private DiagramMacroRunnable diagramMacroRunnable;

    @MockComponent
    private Logger logger;

    @BeforeEach
    void setUp()
    {
        ReflectionUtils.setFieldValue(manager, "logger", this.logger);
    }

    private Thread getThread(String fieldName) throws Exception
    {
        Field field = DiagramRunnableThreadsManager.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return (Thread) field.get(manager);
    }

    @Test
    void testMaybeStartInitializesThreadsWhenNull() throws Exception
    {
        manager.maybeStart();

        assertNotNull(getThread("diagramLinksThread"));
        assertNotNull(getThread("diagramMacroThread"));
    }

    @Test
    void testMaybeStartDoesNotReinitializeExistingThreads() throws Exception
    {
        manager.maybeStart();
        Thread linksThread = getThread("diagramLinksThread");
        Thread macroThread = getThread("diagramMacroThread");

        manager.maybeStart();

        assertSame(linksThread, getThread("diagramLinksThread"));
        assertSame(macroThread, getThread("diagramMacroThread"));
    }

    @Test
    void testStartThreadsIsIdempotent() throws Exception
    {
        manager.startThreads();
        Thread linksThread = getThread("diagramLinksThread");
        Thread macroThread = getThread("diagramMacroThread");

        manager.startThreads();

        assertSame(linksThread, getThread("diagramLinksThread"));
        assertSame(macroThread, getThread("diagramMacroThread"));
    }

    @Test
    void testStartThreadCreatesNamedDaemonThread()
    {
        Thread thread = manager.startThread(diagramLinksRunnable, "Test Thread");

        assertTrue(thread.isDaemon());
        assertTrue(thread.getName().equals("Test Thread"));
    }

    @Test
    void testStopThreadWithNullThreadDoesNothing() throws InterruptedException
    {
        manager.stopThread(null, diagramLinksRunnable);

        verify(diagramLinksRunnable, never()).addToQueue(any());
    }

    @Test
    void testStopThreadWithNonNullThreadAddsStopEntry() throws InterruptedException
    {
        Thread thread = new Thread(() -> { });
        thread.start();
        thread.join();

        manager.stopThread(thread, diagramLinksRunnable);

        verify(diagramLinksRunnable).addToQueue(AbstractDiagramRunnable.STOP_RUNNABLE_ENTRY);
    }

    @Test
    void testStopThreadsWithNullThreadsDoesNothing()
    {
        assertDoesNotThrow(() -> manager.stopThreads());

        verify(diagramLinksRunnable, never()).addToQueue(any());
        verify(diagramMacroRunnable, never()).addToQueue(any());
    }

    @Test
    void testStopThreadsStopsBothThreads() throws InterruptedException
    {
        manager.startThreads();

        manager.stopThreads();

        verify(diagramLinksRunnable).addToQueue(AbstractDiagramRunnable.STOP_RUNNABLE_ENTRY);
        verify(diagramMacroRunnable).addToQueue(AbstractDiagramRunnable.STOP_RUNNABLE_ENTRY);
    }

    @Test
    void testStopThreadsLogsWarningOnInterruptedException() throws InterruptedException
    {
        CountDownLatch latch = new CountDownLatch(1);
        Thread blockingThread = new Thread(() -> {
            try {
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        blockingThread.start();
        ReflectionUtils.setFieldValue(manager, "diagramLinksThread", blockingThread);

        Thread.currentThread().interrupt();
        manager.stopThreads();

        verify(logger).warn(eq("Diagram backlinks update thread interrupted"), any(InterruptedException.class));

        latch.countDown();
        blockingThread.join();
        Thread.interrupted();
    }

    @Test
    void testSubmitDiagramLinksUpdateDelegatesToRunnable()
    {
        DiagramQueueEntry entry = new DiagramQueueEntry(
            new DocumentReference("wiki", "Space", "OldPage"),
            new DocumentReference("wiki", "Space", "NewPage"),
            Collections.emptyList(),
            "job-1",
            Collections.emptyMap()
        );

        manager.submitDiagramLinksUpdate(entry);

        verify(diagramLinksRunnable).addToQueue(entry);
    }

    @Test
    void testSubmitDiagramMacroUpdateDelegatesToRunnable()
    {
        DiagramQueueEntry entry = new DiagramQueueEntry(
            new DocumentReference("wiki", "Space", "OldPage"),
            new DocumentReference("wiki", "Space", "NewPage"),
            Collections.emptyList(),
            "job-1",
            Collections.emptyMap()
        );

        manager.submitDiagramMacroUpdate(entry);

        verify(diagramMacroRunnable).addToQueue(entry);
    }
}
