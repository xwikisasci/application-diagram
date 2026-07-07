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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.xwiki.component.util.ReflectionUtils;
import org.xwiki.model.reference.AttachmentReference;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.rendering.block.MacroBlock;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InlineDiagramMacroRefactoring}
 *
 * @version $Id$
 */
@ComponentTest
class InlineDiagramMacroRefactoringTest
{
    private static final String DIAGRAM_NAME = "diagramName";

    @InjectMockComponents
    private InlineDiagramMacroRefactoring refactoring;

    @MockComponent
    private Logger logger;

    @Mock
    private MacroBlock macroBlock;

    @BeforeEach
    void setUp()
    {
        ReflectionUtils.setFieldValue(refactoring, "logger", this.logger);
    }

    @Test
    void testReplaceDocumentReferenceAlwaysReturnsEmpty() throws Exception
    {
        DocumentReference currentDocRef = new DocumentReference("wiki", "Space", "Page");
        DocumentReference sourceRef = new DocumentReference("wiki", "Space", "OldPage");
        DocumentReference targetRef = new DocumentReference("wiki", "Space", "NewPage");

        Optional<MacroBlock> result =
            refactoring.replaceReference(macroBlock, currentDocRef, sourceRef, targetRef, true);

        assertTrue(result.isEmpty());
    }

    @Test
    void testReplaceAttachmentReferenceNotMatchingDiagram()
    {
        DocumentReference parentRef = new DocumentReference("wiki", "Space", "Page");
        when(macroBlock.getParameter(DIAGRAM_NAME)).thenReturn("mydiagram");

        AttachmentReference source = new AttachmentReference("somename.diagram.xml", parentRef);
        AttachmentReference target = new AttachmentReference("other.diagram.xml", parentRef);

        Optional<MacroBlock> result =
            refactoring.replaceReference(macroBlock, parentRef, source, target, false);

        assertTrue(result.isEmpty());
        verify(macroBlock, never()).setParameter(DIAGRAM_NAME, "other");
    }

    @Test
    void testReplaceAttachmentReferenceMatchingDiagramSameParent()
    {
        DocumentReference parentRef = new DocumentReference("wiki", "Space", "Page");
        when(macroBlock.getParameter(DIAGRAM_NAME)).thenReturn("mydiagram");

        AttachmentReference source = new AttachmentReference("mydiagram.diagram.xml", parentRef);
        AttachmentReference target = new AttachmentReference("newname.diagram.xml", parentRef);

        Optional<MacroBlock> result =
            refactoring.replaceReference(macroBlock, parentRef, source, target, false);

        assertTrue(result.isPresent());
        verify(macroBlock).setParameter(DIAGRAM_NAME, "newname");
    }

    @Test
    void testReplaceAttachmentReferenceMatchingDiagramDifferentParent()
    {
        DocumentReference sourceParent = new DocumentReference("wiki", "Space", "OldPage");
        DocumentReference targetParent = new DocumentReference("wiki", "Space", "NewPage");
        when(macroBlock.getParameter(DIAGRAM_NAME)).thenReturn("mydiagram");

        AttachmentReference source = new AttachmentReference("mydiagram.diagram.xml", sourceParent);
        AttachmentReference target = new AttachmentReference("mydiagram.diagram.xml", targetParent);

        Optional<MacroBlock> result =
            refactoring.replaceReference(macroBlock, sourceParent, source, target, false);

        assertTrue(result.isEmpty());
        verify(macroBlock, never()).setParameter(DIAGRAM_NAME, "mydiagram");
    }
}
