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

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;

import org.apache.solr.common.SolrInputDocument;
import org.xwiki.component.annotation.Component;
import org.xwiki.model.reference.DocumentReferenceResolver;
import org.xwiki.model.reference.EntityReference;
import org.xwiki.search.solr.SolrEntityMetadataExtractor;

import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiAttachment;
import com.xpn.xwiki.doc.XWikiDocument;

import static com.xwiki.diagram.internal.AttachmentUtils.getContentAsString;

/**
 * Handles the registration of backlinks for the inline diagrams.
 * <p>
 *
 * @version $Id$
 * @since 2.0
 */
@Component
@Named("macroinlinediagram")
@Singleton
public class InlineDiagramContentSolrMetadataExtractor implements SolrEntityMetadataExtractor<XWikiDocument>
{
    @Inject
    private DiagramContentHandler diagramContentHandler;

    @Inject
    private LinkRegistry linkRegistry;

    @Inject
    @Named("explicit")
    private DocumentReferenceResolver<String> explicitDocumentReferenceResolver;

    @Inject
    private Provider<XWikiContext> contextProvider;

    @Override
    public boolean extract(XWikiDocument document, SolrInputDocument solrDocument)
    {
        long tenMinutesMillis = 10L * 60L * 1000L;
        Date tenMinutesAgo = new Date(System.currentTimeMillis() - tenMinutesMillis);
        // The XWikiDocument API doesn't have a method to get all attachments with a specific file extension so we
        // get all and filter them.
        // We also filter the attachments to index only the new ones (no older than 10 minutes), because checking
        // every diagram attachment whenever someone updates a single one can cause performance issues. This is not a
        // foolproof time window, but if there are no huge indexing jobs running on the instance, the document
        // should be indexed immediately after it is saved.
        List<XWikiAttachment> attachments =
            document.getAttachmentList().stream().filter(attachment -> attachment.getFilename().endsWith("diagram"
                + ".xml") && attachment.getDate().after(tenMinutesAgo)).collect(Collectors.toList());
        boolean pageUpdated = false;
        for (XWikiAttachment attachment : attachments) {
            String content = getContentAsString(attachment, contextProvider.get());
            List<EntityReference> references =
                diagramContentHandler.getLinkedPages(content, document.getDocumentReference());
            pageUpdated |= linkRegistry.registerBacklinks(solrDocument, references);
        }

        return pageUpdated;
    }
}
