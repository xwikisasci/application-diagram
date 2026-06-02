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

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.xwiki.model.EntityType;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.reference.EntityReferenceResolver;
import org.xwiki.model.reference.EntityReferenceSerializer;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EmbedDiagramMacroRefactoring}
 */
@ComponentTest
public class EmbedDiagramMacroRefactoringTest
{
    private static final String SOURCE_DOCUMENT = "diagramSource";

    @InjectMockComponents
    private EmbedDiagramMacroRefactoring refactoring;

    @Mock
    private MacroBlock macroBlock;

    @MockComponent
    @Named("compact")
    private EntityReferenceSerializer<String> serializer;

    @MockComponent
    @Named("current")
    private EntityReferenceResolver<String> resolver;

 @Test
void testReplaceReferenceUpdateWithSuccess() throws Exception
{
    DocumentReference currentDocumentReference = mock(DocumentReference.class);
    DocumentReference sourceReference = mock(DocumentReference.class);
    DocumentReference targetReference = mock(DocumentReference.class);

    EntityReference attachmentReference = mock(EntityReference.class);

    when(macroBlock.getParameter(SOURCE_DOCUMENT)).thenReturn("test.diagram.xml");
    when(resolver.resolve("test.diagram.xml", EntityType.ATTACHMENT)).thenReturn(attachmentReference);

    when(attachmentReference.getParent()).thenReturn(sourceReference);
    when(attachmentReference.getName()).thenReturn("file.diagram.xml");

    ArgumentCaptor<AttachmentReference> captor = ArgumentCaptor.forClass(AttachmentReference.class);
    when(serializer.serialize(captor.capture())).thenReturn("Space.TargetPage@file.diagram.xml");

    Optional<MacroBlock> optional =
        refactoring.replaceReference(macroBlock, currentDocumentReference, sourceReference, targetReference, false);

    assertTrue(optional.isPresent());

    AttachmentReference captured = captor.getValue();
    assertEquals("file.diagram.xml", captured.getName());
    assertEquals(targetReference, captured.getParent());

    verify(macroBlock).setParameter(SOURCE_DOCUMENT, "Space.TargetPage@file.diagram.xml");
}

    @Test
    void testReplaceReferenceUpdateWithoutSuccess() throws Exception
    {
        DocumentReference documentReference = mock(DocumentReference.class);
        when(macroBlock.getParameter(SOURCE_DOCUMENT)).thenReturn("test");
        when(serializer.serialize(documentReference)).thenReturn("test!");

        Optional<MacroBlock> optional =
            refactoring.replaceReference(macroBlock, documentReference, mock(DocumentReference.class),
                documentReference, false);
        assertTrue(optional.isEmpty());
    }
}
