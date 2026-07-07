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
package com.xwiki.diagram.test.ui;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.test.docker.junit5.TestReference;
import org.xwiki.test.docker.junit5.UITest;
import org.xwiki.test.ui.TestUtils;
import org.xwiki.test.ui.po.InlinePage;
import org.xwiki.test.ui.po.RenamePage;
import org.xwiki.test.ui.po.ViewPage;
import org.xwiki.test.ui.po.editor.ObjectEditPage;

import com.xwiki.diagram.test.po.DiagramMacro;
import com.xwiki.diagram.test.po.DiagramMacroPage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * UI tests for the Diagram Macro.
 *
 * @version $Id$
 * @since 1.22.8
 */
@UITest
public class DiagramIT
{
    private final DocumentReference diagramReference1 = new DocumentReference("xwiki", "Main", "DiagramTest");

    private final DocumentReference diagramReference2 = new DocumentReference("xwiki", "Main", "DiagramTestRenamed");

    @BeforeAll
    void setup(TestUtils testUtils) {
        testUtils.createAdminUser();
    }

    @BeforeEach
    void beforeEach(TestUtils testUtils)
    {
        testUtils.loginAsSuperAdmin();
        testUtils.deletePage(diagramReference1);
        testUtils.deletePage(diagramReference2);
        createDiagram(testUtils);
    }

    @Test
    @Order(1)
    void cachedDiagramMacroTest(TestUtils setup, TestReference testReference)
    {
        setup.createPage(testReference, getMacroContent("cachedDiagrams.vm"), "CachedDiagramTest");
        DiagramMacroPage page = new DiagramMacroPage();

        // Cached diagram macro with a correct reference.
        DiagramMacro d0 = page.getDiagram(0);
        assertTrue(d0.hasThumbnail());
        assertTrue(d0.hasSvg());
        assertTrue(d0.getEditLink().contains("DiagramTest"));
        assertEquals("DiagramTest", d0.getCaption());
        assertTrue(d0.getLink().contains("DiagramTest"));

        // Non-cached diagram macro with a correct reference.
        DiagramMacro d1 = page.getDiagram(1);
        assertTrue(d1.getReference().contains("DiagramTest"));
        assertTrue(d1.isInteractiveSvg());
        assertTrue(d1.hasToolbar());
        assertTrue(d1.hasDataModel());
    }

    @Test
    @Order(2)
    void nonExistingReferenceTest(TestUtils setup, TestReference testReference)
    {
        setup.createPage(testReference, getMacroContent("referenceDiagrams.vm"), "ExistingReferenceDiagramTest");
        DiagramMacroPage page = new DiagramMacroPage();

        // Cached diagram macro with no specified reference.
        DiagramMacro d0 = page.getDiagram(0);
        assertTrue(d0.isCreateButton());
        assertTrue(d0.getCreateButtonTemplateType().contains("template=Diagram"));

        // Non-cached diagram macro with a non-existing reference specified (its same as cached in this case).
        DiagramMacro d1 = page.getDiagram(1);
        assertTrue(d1.isCreateButton());
        assertTrue(d1.getCreateButtonTemplateType().contains("template=Diagram"));
    }

    @Test
    @Order(3)
    void invalidTemplateReferenceTest(TestUtils setup, TestReference testReference)
    {
        setup.createPage("Main", "PageWithNoDiagram", "normal page with no diagrams", "PageWithNoDiagram");

        setup.createPage(testReference, getMacroContent("templateDiagrams.vm"), "InvalidReferenceDiagramTest");
        DiagramMacroPage page = new DiagramMacroPage();

        // Cached diagram macro with a reference that is not a diagram.
        DiagramMacro d0 = page.getDiagram(0);
        assertEquals("PageWithNoDiagram", d0.getCaption());
        assertTrue(d0.getLink().contains("PageWithNoDiagram"));
        assertTrue(d0.hasThumbnail());
        assertTrue(d0.hasWarningMessage());
        assertEquals("The specified page is not a diagram.", d0.getWarningMessage());

        // Non-cached diagram macro with a reference that is not a diagram (its same as cached in this case).
        DiagramMacro d1 = page.getDiagram(1);
        assertEquals("PageWithNoDiagram", d1.getCaption());
        assertTrue(d1.getLink().contains("PageWithNoDiagram"));
        assertTrue(d1.hasThumbnail());
        assertTrue(d1.hasWarningMessage());
        assertEquals("The specified page is not a diagram.", d1.getWarningMessage());
    }

    @Test
    @Order(4)
    void renamedDiagramTest(TestUtils setup)
    {
        DocumentReference docRef = new DocumentReference("xwiki", "Main", "PageWithDiagramsTestRenamed");
        setup.createPage(docRef, getMacroContent("renamedDiagramPage.vm"), "PageWithDiagramsTestRenamed");

        DiagramMacroPage page = new DiagramMacroPage();

        DiagramMacro d0 = page.getDiagram(0);
        assertTrue(d0.getEditLink().contains("DiagramTest"));
        assertEquals("DiagramTest", d0.getCaption());
        assertTrue(d0.getLink().contains("DiagramTest"));

        DiagramMacro d1 = page.getDiagram(1);
        assertTrue(d1.getReference().contains("DiagramTest"));

        renamePage(setup, diagramReference1, "DiagramTestRenamed");

        setup.gotoPage(docRef);
        DiagramMacroPage renamedPage = new DiagramMacroPage();

        // Checks that after renaming the linked diagram page the references are updated.
        DiagramMacro d2 = renamedPage.getDiagram(0);
        assertTrue(d2.getEditLink().contains("DiagramTestRenamed"));
        assertEquals("DiagramTestRenamed", d2.getCaption());
        assertTrue(d2.getLink().contains("DiagramTestRenamed"));

        DiagramMacro d3 = renamedPage.getDiagram(1);
        assertTrue(d3.getReference().contains("xwiki:Main.DiagramTestRenamed"));
    }

    private void renamePage(TestUtils setup, DocumentReference documentReference, String newName)
    {
        setup.gotoPage(documentReference);
        ViewPage viewPage = setup.gotoPage(documentReference);
        RenamePage renamePage = viewPage.rename();
        renamePage.getDocumentPicker().setTitle(newName);
        renamePage.clickRenameButton();
    }

    private void createDiagram(TestUtils setup)
    {
        ViewPage vp = setup.createPage("Main", "DiagramTest", getMacroContent("diagram.xml"), "DiagramTest");
        ObjectEditPage objectEditPage = vp.editObjects();
        objectEditPage.addObject("Diagram.DiagramClass");
        objectEditPage.clickSaveAndView();

        InlinePage inlinePage = vp.editInline();
        inlinePage.clickSaveAndView();
    }

    private String getMacroContent(String filename)
    {
        try (InputStream inputStream = getClass().getResourceAsStream("/diagramContent/" + filename)) {
            if (inputStream == null) {
                throw new RuntimeException("Failed to load " + filename + " from resources.");
            }

            return new BufferedReader(new InputStreamReader(inputStream)).lines()
                .filter(line -> !line.trim().startsWith("##")).collect(Collectors.joining("\n"));
        } catch (IOException e) {
            throw new RuntimeException("Failed to read macro file: " + filename, e);
        }
    }
}
