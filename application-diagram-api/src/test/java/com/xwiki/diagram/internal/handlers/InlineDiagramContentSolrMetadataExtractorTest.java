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
package com.xwiki.diagram.internal.handlers;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import javax.inject.Named;
import javax.inject.Provider;

import org.apache.solr.common.SolrInputDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.model.validation.EntityNameValidation;
import org.xwiki.rendering.block.XDOM;
import org.xwiki.test.junit5.mockito.ComponentTest;
import org.xwiki.test.junit5.mockito.InjectMockComponents;
import org.xwiki.test.junit5.mockito.MockComponent;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.XWikiException;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit for {@link InlineDiagramContentSolrMetadataExtractor}
 *
 * @version $Id$
 * @since 2.0
 */
@ComponentTest
public class InlineDiagramContentSolrMetadataExtractorTest
{
    @InjectMockComponents
    private InlineDiagramContentSolrMetadataExtractor inlineDiagramContentSolrMetadataExtractor;

    @MockComponent
    private DiagramContentHandler diagramContentHandler;

    @MockComponent
    private LinkRegistry linkRegistry;

    @MockComponent
    @Named("explicit")
    private DocumentReferenceResolver<String> explicitDocumentReferenceResolver;

    @MockComponent
    private Provider<XWikiContext> contextProvider;

    @Mock
    private XWiki wiki;

    @Mock
    private XWikiDocument xwikiDocument;

    @Mock
    private XDOM xdom;

    @Mock
    private XWikiContext xwikiContext;

    @Mock
    private EntityNameValidation entityNameValidation;

    @Mock
    private DocumentReference documentReference;

    @Mock
    private XWikiDocument document;

    @Mock
    private SolrInputDocument solrDocument;

    @Mock
    private XWikiAttachment attachment;

    @Mock
    private EntityReference entityReference;

    @BeforeEach
    void setup()
    {
        when(this.xwikiDocument.getXDOM()).thenReturn(xdom);
        when(this.contextProvider.get()).thenReturn(this.xwikiContext);
        when(this.xwikiContext.getWiki()).thenReturn(this.wiki);
    }

    @Test
    void extractIgnoresOldAttachments()
    {
        Date oldDate = new Date(System.currentTimeMillis() - (20 * 60 * 1000));

        when(attachment.getFilename()).thenReturn("test.diagram.xml");
        when(attachment.getDate()).thenReturn(oldDate);

        when(document.getAttachmentList()).thenReturn(Collections.singletonList(attachment));

        boolean result = inlineDiagramContentSolrMetadataExtractor.extract(document, solrDocument);

        assertFalse(result);

        verifyNoInteractions(diagramContentHandler);
        verifyNoInteractions(linkRegistry);
    }

    @Test
    void extractIgnoresNonDiagramAttachments()
    {
        Date now = new Date();

        when(attachment.getFilename()).thenReturn("test.png");
        when(attachment.getDate()).thenReturn(now);

        when(document.getAttachmentList()).thenReturn(Collections.singletonList(attachment));
        boolean result = inlineDiagramContentSolrMetadataExtractor.extract(document, solrDocument);

        assertFalse(result);

        verifyNoInteractions(diagramContentHandler);
        verifyNoInteractions(linkRegistry);
    }

    @Test
    void extractWithValidDiagramAttachment() throws XWikiException
    {
        Date now = new Date();

        when(attachment.getFilename()).thenReturn("test.diagram.xml");
        when(attachment.getDate()).thenReturn(now);
        when(attachment.getContentInputStream(xwikiContext)).thenReturn(
            new ByteArrayInputStream("<diagram/>".getBytes(StandardCharsets.UTF_8)));

        when(document.getAttachmentList()).thenReturn(Collections.singletonList(attachment));
        when(document.getDocumentReference()).thenReturn(documentReference);

        List<EntityReference> references = List.of(entityReference);

        when(diagramContentHandler.getLinkedPages("<diagram/>", documentReference)).thenReturn(references);

        when(linkRegistry.registerBacklinks(solrDocument, references)).thenReturn(false);

        boolean result = inlineDiagramContentSolrMetadataExtractor.extract(document, solrDocument);

        assertFalse(result);
        verify(diagramContentHandler).getLinkedPages("<diagram/>", documentReference);
        verify(linkRegistry).registerBacklinks(solrDocument, references);
    }
}

