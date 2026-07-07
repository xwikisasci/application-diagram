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

import javax.inject.Inject;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;

/**
 * Manages the state of the diagram runnable threads.
 *
 * @version $Id$
 * @since 2.0
 */
@Component(roles = DiagramRunnableThreadsManager.class)
@Singleton
public class DiagramRunnableThreadsManager
{
    /**
     * Thread that will handle updating diagram's content and attachment after a page rename.
     */
    private Thread diagramLinksThread;

    /**
     * Thread that will handle updating the reference of a diagram macro after a diagram rename.
     */
    private Thread diagramMacroThread;

    @Inject
    private DiagramLinksRunnable diagramLinksRunnable;

    @Inject
    private DiagramMacroRunnable diagramMacroRunnable;

    @Inject
    private Logger logger;

    /**
     * Responsible for starting the diagram runnable threads if they are not already started.
     */
    public void maybeStart()
    {
        if (this.diagramLinksThread == null || this.diagramMacroThread == null) {
            startThreads();
        }
    }

    /**
     * Multiple rename jobs could be started at very close dates in the moment when the threads were not initialized yet
     * (for example, at installation step) and we need to be sure that only a single instance of each thread is
     * created.
     */
    public synchronized void startThreads()
    {
        if (this.diagramLinksThread == null) {
            this.diagramLinksThread = startThread(this.diagramLinksRunnable, "Update Diagram Links Thread");
        }
        if (this.diagramMacroThread == null) {
            this.diagramMacroThread = startThread(this.diagramMacroRunnable, "Update Diagram Macro Thread");
        }
    }

    /**
     * Actions for starting a thread.
     *
     * @param diagramRunnable runnable object that implements the run method
     * @param threadName name of the thread
     * @return thread that was started
     */
    public Thread startThread(AbstractDiagramRunnable diagramRunnable, String threadName)
    {
        Thread diagramThread = new Thread(diagramRunnable);
        diagramThread.setName(threadName);
        diagramThread.setDaemon(true);
        diagramThread.start();

        return diagramThread;
    }

    /**
     * Responsible with stopping the diagram runnable threads.
     */
    public void stopThreads()
    {
        try {
            stopThread(this.diagramLinksThread, this.diagramLinksRunnable);
            stopThread(this.diagramMacroThread, this.diagramMacroRunnable);
        } catch (InterruptedException e) {
            logger.warn("Diagram backlinks update thread interrupted", e);
        }
    }

    /**
     * Actions for closing a thread.
     *
     * @param diagramThread thread to be stopped
     * @param diagramRunnable runnable object of the thread
     * @throws InterruptedException if any thread has interrupted the current thread
     */
    public void stopThread(Thread diagramThread, AbstractDiagramRunnable diagramRunnable) throws InterruptedException
    {
        if (diagramThread != null) {
            diagramRunnable.addToQueue(AbstractDiagramRunnable.STOP_RUNNABLE_ENTRY);
            diagramThread.join();
        }
    }

    /**
     * Submits a queue entry for processing by the diagram links thread, which updates the links inside standalone
     * diagrams.
     *
     * @param queueEntry the entry to process
     */
    public void submitDiagramLinksUpdate(DiagramQueueEntry queueEntry)
    {
        diagramLinksRunnable.addToQueue(queueEntry);
    }

    /**
     * Submits a queue entry for processing by the diagram macro thread, which updates the reference of a diagram macro.
     *
     * @param queueEntry the entry to process
     */
    public void submitDiagramMacroUpdate(DiagramQueueEntry queueEntry)
    {
        diagramMacroRunnable.addToQueue(queueEntry);
    }
}
