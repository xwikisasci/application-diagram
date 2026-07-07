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
package com.xwiki.diagram.internal.macroRefactoring;

import java.util.Optional;

import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroRefactoringException;
import org.xwiki.stability.Unstable;

/**
 * Responsible for updating the diagram content when a back reference is moved. The code will be executed on move/rename
 * actions and updates the diagrams create by the Inline Diagram Macro.
 *
 * @version $Id$
 * @since 1.22.11
 */
@Component
@Named("inlineDiagram")
@Singleton
@Unstable
public class InlineDiagramMacroRefactoring extends AbstractInlineDiagramMacroRefactoring
{
    @Override
    public Optional<MacroBlock> replaceReference(MacroBlock macroBlock, DocumentReference currentDocumentReference,
        DocumentReference sourceReference, DocumentReference targetReference, boolean relative)
        throws MacroRefactoringException
    {
        return Optional.empty();
    }

    @Override
    public Optional<MacroBlock> replaceReference(MacroBlock macroBlock, DocumentReference currentDocumentReference,
        AttachmentReference sourceReference, AttachmentReference targetReference, boolean relative)
    {
        String diagramName = String.format(FORMAT_NAME, macroBlock.getParameter(DIAGRAM_NAME), ATTACHMENT_SUFFIX);

        // We should refactor the macro block only if the old name of the moved attachment matches the
        // diagramName parameter of the macro, and if the attachment is moved on the same page.
        boolean isTheDiagramAttachment = sourceReference.getName().equals(diagramName);
        boolean isMovedToTheSameParent = sourceReference.getParent().equals(targetReference.getParent());
        if (!isTheDiagramAttachment) {
            return Optional.empty();
        }
        if (isMovedToTheSameParent) {
            String referenceName = targetReference.getName();
            String newName = referenceName.substring(0, referenceName.length() - ATTACHMENT_SUFFIX.length());
            macroBlock.setParameter(DIAGRAM_NAME, newName);
            return Optional.of(macroBlock);
        }
        logger.warn("Failed to update the diagram attachment name parameter after the attachment was moved "
            + "because the attachment was moved to another document.");
        return Optional.empty();
    }

}
