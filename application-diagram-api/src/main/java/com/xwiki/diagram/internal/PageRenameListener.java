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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.component.manager.ComponentLifecycleException;
import org.xwiki.component.phase.Disposable;
import org.xwiki.job.JobContext;
import org.xwiki.job.event.JobStartedEvent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.AbstractEventListener;
import org.xwiki.observation.ObservationContext;
import org.xwiki.observation.event.Event;
import org.xwiki.refactoring.event.DocumentRenamingEvent;
import org.xwiki.refactoring.event.EntitiesRenamedEvent;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.diagram.internal.handlers.DiagramContentHandler;

/**
 * Listens to rename of pages and starts a thread that will update the content of backlinked diagrams. Also, for diagram
 * pages it will start a thread for updating references of the diagram macro.
 *
 * @version $Id$
 * @since 1.13
 */
@Component
@Named(PageRenameListener.ROLE_HINT)
@Singleton
public class PageRenameListener extends AbstractEventListener implements Disposable
{
    /**
     * The role hint of the document.
     */
    protected static final String ROLE_HINT = "PageRenameEventListener";

    @Inject
    protected ObservationContext observationContext;

    @Inject
    protected JobContext jobContext;

    @Inject
    protected Provider<XWikiContext> contextProvider;

    @Inject
    private DiagramRenameStateManager renameStateManager;

    @Inject
    private Logger logger;

    @Inject
    private DiagramRunnableThreadsManager diagramRunnableThreadsManager;

    /**
     * Constructor.
     */
    public PageRenameListener()
    {
        // The former event is responsible for handling the actual updates, while the latter is responsible for
        // cleaning up the memory so we don't create leaks.
        super(ROLE_HINT, new DocumentRenamingEvent(), new EntitiesRenamedEvent());
    }

    @Override
    public void onEvent(Event event, Object source, Object data)
    {

        diagramRunnableThreadsManager.maybeStart();

        // When the job ends, every page has been moved and the rename map is complete: only now do we hand the
        // collected entries to the runnable threads, then mark the job finished and clean up if all entries are done.
        if (event instanceof EntitiesRenamedEvent) {
            String jobId = getCurrentJobId();
            if (jobId != null) {
                submitCollectedEntries(jobId);
                renameStateManager.markJobFinished(jobId);
            }
            return;
        }

        if (!observationContext.isIn(new JobStartedEvent("refactoring/rename"))) {
            return;
        }

        DocumentRenamingEvent renamedEvent = (DocumentRenamingEvent) event;
        DocumentReference originalDocRef = renamedEvent.getSourceReference();
        DocumentReference destinationRef = renamedEvent.getTargetReference();
        logger.info("Original document {}, destination {}", originalDocRef, destinationRef);
        String jobId = getCurrentJobId();
        // Because the event if fired in sequence, and we don't know when we might reach a page that interests us
        // we always record the new mapping so the other entires runnables can resolve it to the new reference if
        // it appears as a backlink somewhere.
        renameStateManager.recordRename(jobId, originalDocRef, destinationRef);

        XWikiContext context = contextProvider.get();
        try {
            XWikiDocument originalDoc = context.getWiki().getDocument(originalDocRef, context);
            // We check the original document because the new one doesn't have any object yet.
            List<DocumentReference> backlinks = originalDoc.getBackLinkedReferences(context);
            boolean isDiagram = originalDoc.getXObject(DiagramContentHandler.DIAGRAM_CLASS) != null;
            JobRenameState state = renameStateManager.getOrCreateState(jobId);
            DiagramQueueEntry queueEntry =
                new DiagramQueueEntry(originalDocRef, destinationRef, backlinks, jobId, state.renameMap);

            // The documents are captured here. The actual processing is deferred until the whole job is done
            // to avoid race condition.
            // We have 2 cases where we should add pages for further processing:
            // 1. When the page is a diagram, in this case we have to check there are any DiagramMacros that might
            // need refactoring.
            // 2. When the page has backlinks, in this case one of those backlinks might be a diagram and we should
            // update it's content to the new name so the links don't go stale.
            if (isDiagram) {
                logger.info("The entity is a diagram [{}]", queueEntry);
                state.collectedMacroEntries.add(queueEntry);
            }

            if (!backlinks.isEmpty()) {
                logger.info("The entity has backlinks [{}]", queueEntry);
                state.collectedLinksEntries.add(queueEntry);
            }
        } catch (XWikiException e) {
            logger.error("Error when getting backlinks of renamed document [{}]", originalDocRef, e);
        }
    }

    @Override
    public void dispose() throws ComponentLifecycleException
    {
        diagramRunnableThreadsManager.stopThreads();
    }

    /**
     * Submits every entry collected during the rename job to its runnable thread. Called once the job has finished and
     * all pages have been moved, so the runnables operate on a fully renamed hierarchy and a complete rename map.
     *
     * @param jobId the rename job ID
     */
    private void submitCollectedEntries(String jobId)
    {
        JobRenameState state = renameStateManager.getState(jobId);
        if (state == null) {
            return;
        }

        state.pendingEntries.addAndGet(state.collectedMacroEntries.size() + state.collectedLinksEntries.size());

        DiagramQueueEntry queueEntry;
        while ((queueEntry = state.collectedMacroEntries.poll()) != null) {
            diagramRunnableThreadsManager.submitDiagramMacroUpdate(queueEntry);
        }
        while ((queueEntry = state.collectedLinksEntries.poll()) != null) {
            diagramRunnableThreadsManager.submitDiagramLinksUpdate(queueEntry);
        }
    }

    private String getCurrentJobId()
    {
        if (jobContext.getCurrentJob() == null) {
            return null;
        }
        return jobContext.getCurrentJob().getStatus().getRequest().getId().toString();
    }
}
