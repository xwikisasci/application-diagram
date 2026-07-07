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
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.Block;
import org.xwiki.rendering.block.Block.Axes;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.rendering.block.match.MacroBlockMatcher;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiDocument;

/**
 * Updates diagram macro reference parameter after the rename of a diagram.
 *
 * @version $Id$
 * @since 1.13.1
 */
@Component(roles = DiagramMacroRunnable.class)
@Singleton
public class DiagramMacroRunnable extends AbstractDiagramRunnable
{
    private static final String MACRO_REFERENCE_PARAMETER = "reference";

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Inject
    @Named("compact")
    private EntityReferenceSerializer<String> compactEntityReferenceSerializer;

    @Inject
    @Named("explicit")
    private DocumentReferenceResolver<String> resolver;

    @Inject
    private Logger logger;

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

            XWikiContext xcontext = contextProvider.get();
            DocumentReference originalDocRef = queueEntry.originalDocRef;
            DocumentReference currentDocRef = queueEntry.currentDocRef;

            try {
                // We need to take backlinks from the original document because at this step they are not loaded on
                // the new document.
                List<DocumentReference> backlinks = queueEntry.backlinks;

                for (DocumentReference backlinkDocRef : backlinks) {
                    XWikiDocument backlinkDoc = xcontext.getWiki().getDocument(backlinkDocRef, xcontext).clone();

                    updateDiagramMacrosReferences(backlinkDoc, currentDocRef, originalDocRef, xcontext);
                }
            } catch (XWikiException e) {
                logger.warn("Update diagram macro reference parameter thread interrupted", e);
            } finally {
                // Always decrement so the map is never kept alive by a stuck counter, and trigger cleanup in case
                // the job already finished.
                renameStateManager.decrementAndCleanup(queueEntry);
            }
        }
    }

    /**
     * Update for a page diagram macros old references with new ones.
     *
     * @param document document that need to be updated with the new reference
     * @param newReference new diagram reference
     * @param oldReference old diagram reference of a macro
     * @param xcontext the XWikiContext
     * @throws XWikiException if updating the document fails
     */
    public void updateDiagramMacrosReferences(XWikiDocument document, DocumentReference newReference,
        DocumentReference oldReference, XWikiContext xcontext) throws XWikiException
    {
        XDOM backlinkDocXDOM = document.getXDOM();
        List<Block> macroBlocks = backlinkDocXDOM.getBlocks(new MacroBlockMatcher("diagram"), Axes.CHILD);

        String newReferenceString =
            compactEntityReferenceSerializer.serialize(newReference, document.getDocumentReference());
        String oldReferenceString =
            compactEntityReferenceSerializer.serialize(oldReference, document.getDocumentReference());

        Boolean modified = false;
        for (Block macroBlock : macroBlocks) {
            String rawReference = macroBlock.getParameter(MACRO_REFERENCE_PARAMETER);
            String macroReference = compactEntityReferenceSerializer.serialize(resolver.resolve(rawReference,
                document.getDocumentReference()), document.getDocumentReference());

            if (!macroReference.equals(newReferenceString) && macroReference.equals(oldReferenceString)) {
                macroBlock.setParameter(MACRO_REFERENCE_PARAMETER, newReferenceString);
                modified = true;
            }
        }

        if (modified) {
            document.setContent(backlinkDocXDOM);
            xcontext.getWiki().saveDocument(document, "Updated diagram macros references after diagram page rename",
                xcontext);
        }
    }
}
