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

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.xwiki.component.annotation.Component;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.rendering.macro.MacroRefactoringException;
import org.xwiki.stability.Unstable;

/**
 * Handles the update of the embedded diagram reference when the source is moved.
 *
 * @version $Id$
 * @since 2.0
 */
@Component
@Named("embedDiagram")
@Singleton
@Unstable
public class EmbedDiagramMacroRefactoring extends AbstractInlineDiagramMacroRefactoring
{
    private static final String SOURCE_DOCUMENT = "diagramSource";

    @Inject
    @Named("compact")
    private EntityReferenceSerializer<String> serializer;

    @Inject
    @Named("current")
    private EntityReferenceResolver<String> resolver;

    @Override
    public Optional<MacroBlock> replaceReference(MacroBlock macroBlock, DocumentReference currentDocumentReference,
        DocumentReference sourceReference, DocumentReference targetReference, boolean relative)
        throws MacroRefactoringException
    {
        String serializedReference = macroBlock.getParameter(SOURCE_DOCUMENT);
        if (serializedReference == null) {
            return Optional.empty();
        }

        if (serializedReference.endsWith(".diagram.xml")) {
            return handleInlineDiagramMoved(serializedReference, sourceReference, targetReference, macroBlock);
        } else {
            return replaceSerializedReference(serializedReference, macroBlock, sourceReference, targetReference);
        }
    }

    @Override
    public Optional<MacroBlock> replaceReference(MacroBlock macroBlock, DocumentReference currentDocumentReference,
        AttachmentReference sourceReference, AttachmentReference targetReference, boolean relative)
        throws MacroRefactoringException
    {
        String serializedReference = macroBlock.getParameter(SOURCE_DOCUMENT);
        if (serializedReference == null) {
            return Optional.empty();
        }
        return replaceSerializedReference(serializedReference, macroBlock, sourceReference, targetReference);
    }

    private Optional<MacroBlock> handleInlineDiagramMoved(String serializedReference, DocumentReference sourceReference,
        DocumentReference targetReference, MacroBlock macroBlock)
    {
        EntityReference attachmentReference = resolver.resolve(serializedReference, EntityType.ATTACHMENT);
        if (attachmentReference != null && attachmentReference.getParent().equals(sourceReference)) {
            EntityReference newAttachmentReference =
                new AttachmentReference(attachmentReference.getName(), targetReference);
            macroBlock.setParameter(SOURCE_DOCUMENT, serializer.serialize(newAttachmentReference));
            return Optional.of(macroBlock);
        }
        return Optional.empty();
    }

    private Optional<MacroBlock> replaceSerializedReference(String serializedReference, MacroBlock macroBlock,
        EntityReference sourceReference, EntityReference targetReference)
    {

        if (serializedReference.equals(serializer.serialize(sourceReference))) {
            macroBlock.setParameter(SOURCE_DOCUMENT, serializer.serialize(targetReference));
            return Optional.of(macroBlock);
        }
        return Optional.empty();
    }
}
