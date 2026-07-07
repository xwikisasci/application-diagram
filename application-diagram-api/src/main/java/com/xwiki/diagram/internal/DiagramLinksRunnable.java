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
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.slf4j.Logger;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReference;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xwiki.diagram.internal.handlers.DiagramContentHandler;

/**
 * Updates content and attachment of a diagram after the rename of backlinked pages.
 *
 * @version $Id$
 * @since 1.13
 */
@Component(roles = DiagramLinksRunnable.class)
@Singleton
public class DiagramLinksRunnable extends AbstractDiagramRunnable
{
    @Inject
    private Logger logger;

    @Inject
    private DiagramContentHandler contentHandler;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    private DiagramRenameStateManager renameStateManager;

    /**
     * @see com.xpn.xwiki.util.AbstractXWikiRunnable#runInternal()
     */
    @Override
    public void runInternal()
    {
        while (!Thread.interrupted()) {
            DiagramQueueEntry queueEntry = getNextDiagramQueueEntry();

            if (queueEntry == STOP_RUNNABLE_ENTRY) {
                break;
            }
            logger.info("Processing queue entry: {}", queueEntry);
            try {
                processEntry(queueEntry);
            } finally {
                // Always decrement so the map is never kept alive by a stuck counter, and trigger cleanup in case
                // the job already finished.
                renameStateManager.decrementAndCleanup(queueEntry);
            }
        }
    }

    private void processEntry(DiagramQueueEntry queueEntry)
    {

        XWikiContext context = contextProvider.get();
        DocumentReference originalDocRef = queueEntry.originalDocRef;
        DocumentReference currentDocRef = queueEntry.currentDocRef;
        List<DocumentReference> backlinks = queueEntry.backlinks;
        for (DocumentReference backlinkRef : backlinks) {
            logger.info("Handling backlink [{}]", backlinkRef);
            try {
                // If this backlink was also part of the same rename job, it may have already been moved. Resolve it to
                // its new location, falling back to the original reference if it was not part of the job yet.
                DocumentReference resolvedRef = queueEntry.renameMap.getOrDefault(backlinkRef, backlinkRef);

                if (!resolvedRef.equals(backlinkRef)) {
                    logger.info("Backlink [{}] was also renamed, resolving to [{}]", backlinkRef, resolvedRef);
                }

                XWikiDocument backlinkDoc = context.getWiki().getDocument(resolvedRef, context).clone();

                if (backlinkDoc.isNew()) {
                    logger.warn("Could not load backlink document [{}], skipping", resolvedRef);
                    continue;
                }

                if (backlinkDoc.getXObject(DiagramContentHandler.DIAGRAM_CLASS) != null) {
                    logger.info("The backlink was a standalone diagram.");
                    contentHandler.updateDiagramContent(backlinkDoc, originalDocRef, currentDocRef, context);
                    contentHandler.updateAttachment(backlinkDoc, originalDocRef, currentDocRef);
                }

                List<XWikiAttachment> attachments = backlinkDoc.getAttachmentList().stream()
                    .filter(attachment -> attachment.getFilename().endsWith("diagram" + ".xml"))
                    .collect(Collectors.toList());
                if (!attachments.isEmpty()) {
                    logger.info("The backlink was an inline diagram.");
                    contentHandler.updateDiagramContent(attachments, backlinkDoc, originalDocRef, currentDocRef,
                        context);
                }
            } catch (Exception e) {
                logger.warn("Error processing diagram links for entry [{}]", queueEntry, e);
            }
        }
    }
}
