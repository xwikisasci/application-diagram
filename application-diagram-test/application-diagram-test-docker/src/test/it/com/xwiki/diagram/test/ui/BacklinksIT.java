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

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

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
import org.xwiki.user.test.po.PreferencesEditPage;
import org.xwiki.user.test.po.PreferencesUserProfilePage;
import org.xwiki.user.test.po.ProfileUserProfilePage;

import com.xwiki.diagram.test.po.BackreferenceViewPage;
import com.xwiki.diagram.test.po.DiagramViewPage;
import com.xwiki.diagram.test.util.DockerDiagramTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * UI tests for making sure that the backreferences are updated.
 *
 * @version $Id$
 * @since 2.0
 */
@UITest(properties = { "xwikiPropertiesAdditionalProperties=test.prchecker.excludePattern=.*:XWiki\\"
    + ".EntityNameValidation\\.AdministrationJSON" })
public class BacklinksIT
{
    private final DocumentReference backLinksTestSpace =
        new DocumentReference("xwiki", List.of("Main", "BackreferencesTests"), "WebHome");

    private final DocumentReference backlinksWebHome =
        new DocumentReference("xwiki", List.of("Main", "BackreferencesTests", "Backlinks"), "WebHome");

    private final DocumentReference diagramSpaceWebHome =
        new DocumentReference("xwiki", List.of("Main", "BackreferencesTests", "Backlinks", "DiagramSpace"), "WebHome");

    private final DocumentReference diagramWebHome =
        new DocumentReference("xwiki", List.of("Main", "BackreferencesTests", "Backlinks", "DiagramSpace", "Diagram"),
            "WebHome");
    private final DocumentReference diagramInlineWebHome =
        new DocumentReference("xwiki", List.of("Main", "BackreferencesTests", "Backlinks", "DiagramSpace",
            "DiagramInline"),
            "WebHome");

    private final DocumentReference anotherSpaceWebHome =
        new DocumentReference("xwiki", List.of("Main", "BackreferencesTests", "Backlinks", "AnotherSpace"), "WebHome");

    // Actual content pages
    private final DocumentReference diagramSibling =
        new DocumentReference("xwiki", List.of("Main", "BackreferencesTests", "Backlinks", "DiagramSpace"),
            "DiagramSibling");

    private final DocumentReference diagramChild =
        new DocumentReference("xwiki", List.of("Main", "BackreferencesTests", "Backlinks", "DiagramSpace", "Diagram"),
            "DiagramChild");

    private final DocumentReference anotherSpaceNested =
        new DocumentReference("xwiki", List.of("Main", "BackreferencesTests", "Backlinks", "AnotherSpace"),
            "AnotherSpaceNestedLinked");

    private final String linkedPageContent =
        DockerDiagramTestUtils.getResourceContent(List.of("backlinks", "linkedPagesContent"));

    private final String diagramContent =
        DockerDiagramTestUtils.getResourceContent(List.of("backlinks", "diagramContent"));

    private final String diagramInlinePageContent =
        DockerDiagramTestUtils.getResourceContent(List.of("backlinks", "inlineDiagramPageContent"));

    private final DiagramViewPage diagramPage = null;

    // Cases:
    // 1. The diagram references a page from another space. This document is moved alone
    // 2. The diagram references a page the same space AKA they are siblings.
    // 3. The diagram references a page that is a child.
    // 4. The diagram references a page from another space and the root of that space is moved.
    // 5. The diagram references a page from the same space and the root space is moved.
    // 4. The diagram references multiple pages with cases from all the above.

    // Tree structure to encapsulate all cases
    // - BackreferencesTests
    // - Backlinks This is for case 5.
    // --- DiagramSpace
    // ---- DiagramSibling -> This is for case 2
    // ---- Diagram
    // -----Diagram Child -> This is for case 3
    // --- AnotherSpace
    // ---- AnotherSpaceNestedLinked -> This is for case 1 | 4
    // ----
    //

    @BeforeAll
    public void setup(TestUtils testUtils) throws Exception
    {

        // We make sure that the admin account is created and we are logged in as it.
        testUtils.createAdminUser();
        // We log as the admin because the superadmin doesn't have all the rights, and we want to make the admin an
        // advanced user
        testUtils.loginAsSuperAdmin();
        testUtils.setGlobalRights("XWiki.XWikiAdminGroup", "", "admin", true);

        // Make the user advanced
        ProfileUserProfilePage userProfilePage = ProfileUserProfilePage.gotoPage("Admin");
        PreferencesUserProfilePage preferencesPage = userProfilePage.switchToPreferences();
        PreferencesEditPage preferencesEditPage = preferencesPage.editPreferences();
        preferencesEditPage.setAdvancedUserType();
        preferencesEditPage.clickSaveAndView();

        // Log bag as the admin and start the creation of the tree.
        testUtils.loginAsAdmin();

        testUtils.createPage(backLinksTestSpace, "", "BacklinksTestsSpace");
        testUtils.createPage(backlinksWebHome, "", "BacklinksCommonRoot");
        testUtils.createPage(diagramSpaceWebHome, "", "DiagramSpace");

        testUtils.createPage(anotherSpaceWebHome, "", "AnotherSpace");

        testUtils.createPage(diagramSibling, linkedPageContent, "DiagramSibling");
        testUtils.createPage(diagramChild, linkedPageContent, "DiagramChild");
        testUtils.createPage(anotherSpaceNested, linkedPageContent, "AnotherSpaceNestedLinked");

        ViewPage viewPage = testUtils.createPageWithAttachment(diagramInlineWebHome, diagramInlinePageContent,
        "DiagramInline",
        "test.diagram.xml",
            new ByteArrayInputStream(diagramContent.getBytes(
            StandardCharsets.UTF_8)));

        viewPage.editWYSIWYG().clickSaveAndView();


        // Creat the page that has the diagram
        ViewPage plainPage = testUtils.createPage(diagramWebHome, diagramContent, "diagram");
        ObjectEditPage objectEditPage = plainPage.editObjects();
        objectEditPage.addObject("Diagram.DiagramClass");
        objectEditPage.clickSaveAndView();
        // We need to register the backlinks.
        InlinePage inlinePage = plainPage.editInline();
        inlinePage.clickSaveAndView();
        testUtils.loginAsAdmin();
    }

    @BeforeEach
    void beforeEach(TestUtils testUtils)
    {
        testUtils.loginAsAdmin();
    }

    /**
     * In this test we aim to make sure that if a diagram references a page, when that page is moved, the diagram
     * updates the backreferences.
     */
    @Test
    @Order(1)
    void moveExactBackLinkTest(TestUtils setup, TestReference testReference)
    {
        ViewPage movedPage = movePage(setup, anotherSpaceNested, "AnotherSpaceNestedLinked - Moved");
        assertPageMovedAndBackrefUpdated(movedPage,
            "xwiki:Main.BackreferencesTests.Backlinks.AnotherSpace.AnotherSpaceNestedLinked - Moved",
            List.of("xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.Diagram.WebHome", "xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.DiagramInline.WebHome"));

        // Move the page back so we don't mess the tree.
        ViewPage restoredPage = movePage(setup, movedPage, "AnotherSpaceNestedLinked");
        assertPageMovedAndBackrefUpdated(restoredPage,
            "xwiki:Main.BackreferencesTests.Backlinks.AnotherSpace.AnotherSpaceNestedLinked",
            List.of("xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.Diagram.WebHome", "xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.DiagramInline.WebHome"));
    }

    /**
     * In this test we aim to test that when a digram references a child of itself and when that child is moved, the
     * diagram updates the backlinks.
     */
    @Test
    @Order(2)
    void moveSiblingPageOfDiagramTest(TestUtils setup, TestReference testReference) {
        ViewPage movedPage = movePage(setup, diagramSibling, "DiagramSibling - Moved");
        assertPageMovedAndBackrefUpdated(movedPage,
            "xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.DiagramSibling - Moved",
            List.of("xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.Diagram.WebHome", "xwiki:Main"
                + ".BackreferencesTests.Backlinks.DiagramSpace.DiagramInline.WebHome"));

        // Move the page back so we don't mess the tree.
        ViewPage restoredPage = movePage(setup, movedPage, "DiagramSibling");
        assertPageMovedAndBackrefUpdated(restoredPage,
            "xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.DiagramSibling",
            List.of("xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.Diagram.WebHome", "xwiki:Main"
                + ".BackreferencesTests.Backlinks.DiagramSpace.DiagramInline.WebHome"));

    }

    /**
     * In this test we aim to test that when a digram references a sibing and that sibling is moved, the diagram
     * updates the backlinks.
     */
    @Test
    @Order(3)
    void moveChildPageOfDiagramTest (TestUtils setup, TestReference testReference) {
        ViewPage movedPage = movePage(setup, diagramChild, "DiagramChild - Moved");
        assertPageMovedAndBackrefUpdated(movedPage,
            "xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.Diagram.DiagramChild - Moved",
            List.of("xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.Diagram.WebHome", "xwiki:Main"
                + ".BackreferencesTests.Backlinks.DiagramSpace.DiagramInline.WebHome"));

        // Move the page back so we don't mess the tree.
        ViewPage restoredPage = movePage(setup, movedPage, "DiagramChild");
        assertPageMovedAndBackrefUpdated(restoredPage,
            "xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.Diagram.DiagramChild",
            List.of("xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.Diagram.WebHome", "xwiki:Main"
                + ".BackreferencesTests.Backlinks.DiagramSpace.DiagramInline.WebHome"));

    }

    @Test
    @Order(4)
    void moveParentOfReferencedPageInDiagramTest(TestUtils setup, TestReference testReference)
    {
        ViewPage movedPage = movePage(setup, anotherSpaceWebHome, "AnotherSpace - Moved");
        // Because we moved the parent we have to use the new reference.
        DocumentReference newAnotherSpaceNestedLinked =
            new DocumentReference("xwiki", List.of("Main", "BackreferencesTests", "Backlinks", "AnotherSpace - Moved"),
                "AnotherSpaceNestedLinked");
        setup.gotoPage(newAnotherSpaceNestedLinked);
        assertPageMovedAndBackrefUpdated(movedPage,
            "xwiki:Main.BackreferencesTests.Backlinks.AnotherSpace - Moved.AnotherSpaceNestedLinked",
            List.of("xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.Diagram.WebHome", "xwiki:Main"
                + ".BackreferencesTests.Backlinks.DiagramSpace.DiagramInline.WebHome"));
        DocumentReference newAnotherSpaceWebHome =
            new DocumentReference("xwiki", List.of("Main", "BackreferencesTests", "Backlinks", "AnotherSpace - Moved"), "WebHome");
        setup.gotoPage(newAnotherSpaceWebHome);
        ViewPage oldAnotherSpace = movePage(setup, newAnotherSpaceWebHome, "AnotherSpace");

        ViewPage restoredPage = setup.gotoPage(anotherSpaceNested);
        assertPageMovedAndBackrefUpdated(restoredPage,
            "xwiki:Main.BackreferencesTests.Backlinks.AnotherSpace.AnotherSpaceNestedLinked",
            List.of("xwiki:Main.BackreferencesTests.Backlinks.DiagramSpace.Diagram.WebHome", "xwiki:Main"
                + ".BackreferencesTests.Backlinks.DiagramSpace.DiagramInline.WebHome"));
    }

    @Test
    @Order(5)
    void moveRootParentOfDiagramAndReferencedPagesTest(TestUtils setup, TestReference testReference)
        throws InterruptedException
    {

        // Move the common root of the diagram and all referenced pages.
        ViewPage movedPage = movePage(setup, backlinksWebHome, "Common Root - Moved");
        Thread.sleep(Duration.ofSeconds(10).toMillis());
        DocumentReference newDiagramWebHome =
            new DocumentReference("xwiki",
                List.of("Main", "BackreferencesTests", "Common Root - Moved", "DiagramSpace", "Diagram"),
                "WebHome");

        DocumentReference newDiagramSibling =
            new DocumentReference("xwiki",
                List.of("Main", "BackreferencesTests", "Common Root - Moved", "DiagramSpace"),
                "DiagramSibling");

        DocumentReference newDiagramChild =
            new DocumentReference("xwiki",
                List.of("Main", "BackreferencesTests", "Common Root - Moved", "DiagramSpace", "Diagram"),
                "DiagramChild");

        DocumentReference newAnotherSpaceNested =
            new DocumentReference("xwiki",
                List.of("Main", "BackreferencesTests", "Common Root - Moved", "AnotherSpace"),
                "AnotherSpaceNestedLinked");

        String newDiagramReference =
            "xwiki:Main.BackreferencesTests.Common Root - Moved.DiagramSpace.Diagram.WebHome";
        String newInlineDiagramReference =
            "xwiki:Main.BackreferencesTests.Common Root - Moved.DiagramSpace.DiagramInline.WebHome";

        // Assert that each referenced page now points back to the diagram at its new location.
        ViewPage diagramSiblingPage = setup.gotoPage(newDiagramSibling);
        assertPageMovedAndBackrefUpdated(diagramSiblingPage,
            "xwiki:Main.BackreferencesTests.Common Root - Moved.DiagramSpace.DiagramSibling",
            List.of(newDiagramReference, newInlineDiagramReference));

        ViewPage diagramChildPage = setup.gotoPage(newDiagramChild);
        assertPageMovedAndBackrefUpdated(diagramChildPage,
            "xwiki:Main.BackreferencesTests.Common Root - Moved.DiagramSpace.Diagram.DiagramChild",
            List.of(newDiagramReference, newInlineDiagramReference));

        ViewPage anotherSpaceNestedPage = setup.gotoPage(newAnotherSpaceNested);
        assertPageMovedAndBackrefUpdated(anotherSpaceNestedPage,
            "xwiki:Main.BackreferencesTests.Common Root - Moved.AnotherSpace.AnotherSpaceNestedLinked",
            List.of(newDiagramReference, newInlineDiagramReference));
        // Since this is the last test we don't need to move them back.
    }


    private ViewPage movePage(TestUtils setup, DocumentReference reference, String newTitle)
    {
        return movePage(setup, setup.gotoPage(reference), newTitle);
    }

    private ViewPage movePage(TestUtils setup, ViewPage sourcePage, String newTitle)
    {
        RenamePage renamePage = sourcePage.rename();
        renamePage.getDocumentPicker().setTitle(newTitle);
        return renamePage.clickRenameButton().gotoNewPage();
    }

    private void assertPageMovedAndBackrefUpdated(ViewPage page, String expectedReference,
        List<String> diagramPageReferences)
    {
        assertEquals(expectedReference, page.getMetaDataValue("reference"));
        assertTrue(BackreferenceViewPage.getInstance().containsPageReferences(diagramPageReferences));
    }
}