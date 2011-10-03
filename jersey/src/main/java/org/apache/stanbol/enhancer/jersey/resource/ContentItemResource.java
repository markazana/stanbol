/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.apache.stanbol.enhancer.jersey.resource;

import static javax.ws.rs.core.MediaType.TEXT_HTML;
import static org.apache.stanbol.commons.web.base.CorsHelper.addCORSOrigin;
import static org.apache.stanbol.enhancer.servicesapi.rdf.OntologicalClasses.DBPEDIA_ORGANISATION;
import static org.apache.stanbol.enhancer.servicesapi.rdf.OntologicalClasses.DBPEDIA_PERSON;
import static org.apache.stanbol.enhancer.servicesapi.rdf.OntologicalClasses.DBPEDIA_PLACE;
import static org.apache.stanbol.enhancer.servicesapi.rdf.OntologicalClasses.SKOS_CONCEPT;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.GEO_LAT;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.GEO_LONG;
import static org.apache.stanbol.enhancer.servicesapi.rdf.Properties.NIE_PLAINTEXTCONTENT;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import javax.servlet.ServletContext;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.ResponseBuilder;

import org.apache.clerezza.rdf.core.Graph;
import org.apache.clerezza.rdf.core.Language;
import org.apache.clerezza.rdf.core.Literal;
import org.apache.clerezza.rdf.core.LiteralFactory;
import org.apache.clerezza.rdf.core.MGraph;
import org.apache.clerezza.rdf.core.PlainLiteral;
import org.apache.clerezza.rdf.core.Resource;
import org.apache.clerezza.rdf.core.Triple;
import org.apache.clerezza.rdf.core.TripleCollection;
import org.apache.clerezza.rdf.core.TypedLiteral;
import org.apache.clerezza.rdf.core.UriRef;
import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.clerezza.rdf.core.impl.SimpleMGraph;
import org.apache.clerezza.rdf.core.impl.TripleImpl;
import org.apache.clerezza.rdf.core.serializedform.Serializer;
import org.apache.clerezza.rdf.core.serializedform.SupportedFormat;
import org.apache.clerezza.rdf.core.sparql.ParseException;
import org.apache.clerezza.rdf.core.sparql.QueryParser;
import org.apache.clerezza.rdf.core.sparql.ResultSet;
import org.apache.clerezza.rdf.core.sparql.SolutionMapping;
import org.apache.clerezza.rdf.core.sparql.query.SelectQuery;
import org.apache.clerezza.rdf.utils.GraphNode;
import org.apache.commons.io.IOUtils;
import org.apache.stanbol.commons.web.base.resource.BaseStanbolResource;
import org.apache.stanbol.enhancer.servicesapi.ContentItem;
import org.apache.stanbol.enhancer.servicesapi.rdf.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.jersey.api.view.Viewable;

public class ContentItemResource extends BaseStanbolResource {

    @SuppressWarnings("unused")
    private final Logger log = LoggerFactory.getLogger(getClass());

    // TODO make this configurable trough a property
    public static final UriRef SUMMARY = new UriRef("http://www.w3.org/2000/01/rdf-schema#comment");

    // TODO make this configurable trough a property
    public static final UriRef THUMBNAIL = new UriRef("http://dbpedia.org/ontology/thumbnail");

    public final Map<UriRef,String> defaultThumbnails = new HashMap<UriRef,String>();

    protected ContentItem contentItem;

    protected String localId;

    protected String textContent;

    protected URI imageSrc;

    protected URI downloadHref;

    protected URI metadataHref;

    protected final TcManager tcManager;

    protected final Serializer serializer;

    protected String serializationFormat = SupportedFormat.RDF_XML;

    protected Collection<EntityExtractionSummary> people;

    protected Collection<EntityExtractionSummary> organizations;

    protected Collection<EntityExtractionSummary> places;

    protected Collection<EntityExtractionSummary> concepts;
    
    protected Collection<EntityExtractionSummary> others;

    public ContentItemResource(String localId,
                               ContentItem ci,
                               UriInfo uriInfo,
                               TcManager tcManager,
                               Serializer serializer,
                               ServletContext servletContext) throws IOException {
        this.contentItem = ci;
        this.localId = localId;
        this.uriInfo = uriInfo;
        this.tcManager = tcManager;
        this.serializer = serializer;
        this.servletContext = servletContext;

        if (localId != null) {
            URI rawURI = uriInfo.getBaseUriBuilder().path("/store/raw").path(localId).build();
            if (ci.getMimeType().equals("text/plain")) {
                this.textContent = IOUtils.toString(ci.getStream(), "UTF-8");
            } else if (ci.getMimeType().startsWith("image/")) {
                this.imageSrc = rawURI;
            }
            else {
              Iterator<Triple> it = ci.getMetadata().filter(new UriRef(ci.getId()), NIE_PLAINTEXTCONTENT, null);
              if (it.hasNext()) {
                this.textContent = ((Literal)it.next().getObject()).getLexicalForm();
              }
            }
            this.downloadHref = rawURI;
            this.metadataHref = uriInfo.getBaseUriBuilder().path("/store/metadata").path(localId).build();
        }
        defaultThumbnails.put(DBPEDIA_PERSON, getStaticRootUrl() + "/home/images/user_48.png");
        defaultThumbnails.put(DBPEDIA_ORGANISATION, getStaticRootUrl() + "/home/images/organization_48.png");
        defaultThumbnails.put(DBPEDIA_PLACE, getStaticRootUrl() + "/home/images/compass_48.png");
        defaultThumbnails.put(SKOS_CONCEPT, getStaticRootUrl() + "/home/images/black_gear_48.png");
        defaultThumbnails.put(null, getStaticRootUrl() + "/home/images/unknown_48.png");
    }

    public String getRdfMetadata(String mediatype) throws UnsupportedEncodingException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializer.serialize(out, contentItem.getMetadata(), mediatype);
        return out.toString("utf-8");
    }

    public String getRdfMetadata() throws UnsupportedEncodingException {
        return getRdfMetadata(serializationFormat);
    }

    public ContentItem getContentItem() {
        return contentItem;
    }

    public String getLocalId() {
        return localId;
    }

    public String getTextContent() {
        return textContent;
    }

    public URI getImageSrc() {
        return imageSrc;
    }

    public URI getDownloadHref() {
        return downloadHref;
    }

    public URI getMetadataHref() {
        return metadataHref;
    }

    public Collection<EntityExtractionSummary> getPersonOccurrences() throws ParseException {
        if (people == null) {
            people = getOccurrences(DBPEDIA_PERSON);
        }
        return people;
    }
    public Collection<EntityExtractionSummary> getOtherOccurrences() throws ParseException {
        if(others == null){
            others = getOccurrences(null);
        }
        return others;
    }

    public Collection<EntityExtractionSummary> getOrganizationOccurrences() throws ParseException {
        if (organizations == null) {
            organizations = getOccurrences(DBPEDIA_ORGANISATION);
        }
        return organizations;
    }

    public Collection<EntityExtractionSummary> getPlaceOccurrences() throws ParseException {
        if (places == null) {
            places = getOccurrences(DBPEDIA_PLACE);
        }
        return places;
    }
    public Collection<EntityExtractionSummary> getConceptOccurrences() throws ParseException {
        if (concepts == null) {
            concepts = getOccurrences(SKOS_CONCEPT);
        }
        return concepts;
    }

    public Collection<EntityExtractionSummary> getOccurrences(UriRef type) throws ParseException {
        MGraph graph = contentItem.getMetadata();
        StringBuilder queryBuilder = new StringBuilder();
        queryBuilder.append("PREFIX enhancer: <http://fise.iks-project.eu/ontology/> ");
        queryBuilder.append("PREFIX dc:   <http://purl.org/dc/terms/> ");
        queryBuilder.append("SELECT ?textAnnotation ?text ?entity ?entity_label ?confidence WHERE { ");
        queryBuilder.append("  ?textAnnotation a enhancer:TextAnnotation ." );
        if(type != null){
            queryBuilder.append("  ?textAnnotation dc:type ").append(type).append(" . ");
        } else {
            //append a filter that this value needs to be non existent
            queryBuilder.append(" OPTIONAL { ?textAnnotation dc:type ?type } . ");
            queryBuilder.append(" FILTER(!bound(?type)) ");
        }
        queryBuilder.append("  ?textAnnotation enhancer:selected-text ?text ." );
        queryBuilder.append(" OPTIONAL {");
        queryBuilder.append("   ?entityAnnotation dc:relation ?textAnnotation .");
        queryBuilder.append("   ?entityAnnotation a enhancer:EntityAnnotation . ");
        queryBuilder.append("   ?entityAnnotation enhancer:entity-reference ?entity .");
        queryBuilder.append("   ?entityAnnotation enhancer:entity-label ?entity_label .");
        queryBuilder.append("   ?entityAnnotation enhancer:confidence ?confidence . }" );
        queryBuilder.append("} ORDER BY ?text ");
//        String queryString = String.format(queryBuilder.toString(), type);

        SelectQuery query = (SelectQuery) QueryParser.getInstance().parse(queryBuilder.toString());
        ResultSet result = tcManager.executeSparqlQuery(query, graph);
        Map<String,EntityExtractionSummary> occurrenceMap = new TreeMap<String,EntityExtractionSummary>();
        LiteralFactory lf = LiteralFactory.getInstance();
        while (result.hasNext()) {
            SolutionMapping mapping = result.next();

            UriRef textAnnotationUri = (UriRef) mapping.get("textAnnotation");
            if (graph.filter(textAnnotationUri, Properties.DC_RELATION, null).hasNext()) {
                // this is not the most specific occurrence of this name: skip
                continue;
            }
            // TODO: collect the selected text and contexts of subsumed
            // annotations

            Literal textLiteral = (Literal) mapping.get("text");
            String text = textLiteral.getLexicalForm();

            EntityExtractionSummary entity = occurrenceMap.get(text);
            if (entity == null) {
                entity = new EntityExtractionSummary(text, type, defaultThumbnails);
                occurrenceMap.put(text, entity);
            }
            UriRef entityUri = (UriRef) mapping.get("entity");
            if (entityUri != null) {
                String label = ((Literal) mapping.get("entity_label")).getLexicalForm();
                Double confidence = lf.createObject(Double.class, (TypedLiteral) mapping.get("confidence"));
                Graph properties = new GraphNode(entityUri, contentItem.getMetadata()).getNodeContext();
                entity.addSuggestion(entityUri, label, confidence, properties);
            }
        }
        return occurrenceMap.values();
    }

    public static class EntityExtractionSummary implements Comparable<EntityExtractionSummary> {

        protected final String name;

        protected final UriRef type;

        protected List<EntitySuggestion> suggestions = new ArrayList<EntitySuggestion>();

        protected List<String> mentions = new ArrayList<String>();

        public final Map<UriRef,String> defaultThumbnails;

        public EntityExtractionSummary(String name, UriRef type, Map<UriRef,String> defaultThumbnails) {
            this.name = name;
            this.type = type;
            mentions.add(name);
            this.defaultThumbnails = defaultThumbnails;
        }

        public void addSuggestion(UriRef uri, String label, Double confidence, TripleCollection properties) {
            EntitySuggestion suggestion = new EntitySuggestion(uri, type, label, confidence, properties,
                    defaultThumbnails);
            if (!suggestions.contains(suggestion)) {
                suggestions.add(suggestion);
                Collections.sort(suggestions);
            }
        }

        public String getName() {
            EntitySuggestion bestGuess = getBestGuess();
            if (bestGuess != null) {
                return bestGuess.getLabel();
            }
            return name;
        }

        public String getUri() {
            EntitySuggestion bestGuess = getBestGuess();
            if (bestGuess != null) {
                return bestGuess.getUri();
            }
            return null;
        }

        public String getSummary() {
            if (suggestions.isEmpty()) {
                return "";
            }
            return suggestions.get(0).getSummary();
        }

        public String getThumbnailSrc() {
            if (suggestions.isEmpty()) {
                return defaultThumbnails.get(type);
            }
            return suggestions.get(0).getThumbnailSrc();
        }

        public String getMissingThumbnailSrc() {
            return defaultThumbnails.get(type);
        }

        public EntitySuggestion getBestGuess() {
            if (suggestions.isEmpty()) {
                return null;
            }
            return suggestions.get(0);
        }

        public List<EntitySuggestion> getSuggestions() {
            return suggestions;
        }

        public List<String> getMentions() {
            return mentions;
        }

        @Override
        public int compareTo(EntityExtractionSummary o) {
            return getName().compareTo(o.getName());
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            EntityExtractionSummary that = (EntityExtractionSummary) o;

            return !(name != null ? !name.equals(that.name) : that.name != null);
        }

        @Override
        public int hashCode() {
            return name != null ? name.hashCode() : 0;
        }
    }

    public static class EntitySuggestion implements Comparable<EntitySuggestion> {

        protected final UriRef uri;

        protected final UriRef type;

        protected final String label;

        protected final Double confidence;

        protected TripleCollection entityProperties;

        protected final Map<UriRef,String> defaultThumbnails;

        public EntitySuggestion(UriRef uri,
                                UriRef type,
                                String label,
                                Double confidence,
                                TripleCollection entityProperties,
                                Map<UriRef,String> defaultThumbnails) {
            this.uri = uri;
            this.label = label;
            this.type = type;
            this.confidence = confidence;
            this.entityProperties = entityProperties;
            this.defaultThumbnails = defaultThumbnails;
        }

        @Override
        public int compareTo(EntitySuggestion o) {
            // order suggestions by decreasing confidence
            return -confidence.compareTo(o.confidence);
        }

        public String getUri() {
            return uri.getUnicodeString();
        }

        public Double getConfidence() {
            return confidence;
        }

        public String getLabel() {
            return label;
        }

        public String getThumbnailSrc() {
            Iterator<Triple> abstracts = entityProperties.filter(uri, THUMBNAIL, null);
            while (abstracts.hasNext()) {
                Resource object = abstracts.next().getObject();
                if (object instanceof UriRef) {
                    return ((UriRef) object).getUnicodeString();
                }
            }
            return defaultThumbnails.get(type);
        }

        public String getMissingThumbnailSrc() {
            return defaultThumbnails.get(type);
        }

        public String getSummary() {
            Iterator<Triple> abstracts = entityProperties.filter(uri, SUMMARY, null);
            while (abstracts.hasNext()) {
                Resource object = abstracts.next().getObject();
                if (object instanceof PlainLiteral) {
                    PlainLiteral abstract_ = (PlainLiteral) object;
                    if (new Language("en").equals(abstract_.getLanguage())) {
                        return abstract_.getLexicalForm();
                    }
                }
            }
            return "";
        }

        // consider entities with same URI as equal even if we have alternate
        // label values
        @Override
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((uri == null) ? 0 : uri.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) return true;
            if (obj == null) return false;
            if (getClass() != obj.getClass()) return false;
            EntitySuggestion other = (EntitySuggestion) obj;
            if (uri == null) {
                if (other.uri != null) return false;
            } else if (!uri.equals(other.uri)) return false;
            return true;
        }

    }

    public void setRdfSerializationFormat(String format) {
        serializationFormat = format;
    }

    /**
     * @return an RDF/JSON descriptions of places for the word map widget
     */
    public String getPlacesAsJSON() throws ParseException, UnsupportedEncodingException {
        MGraph g = new SimpleMGraph();
        LiteralFactory lf = LiteralFactory.getInstance();
        MGraph metadata = contentItem.getMetadata();
        for (EntityExtractionSummary p : getPlaceOccurrences()) {
            EntitySuggestion bestGuess = p.getBestGuess();
            if (bestGuess == null) {
                continue;
            }
            UriRef uri = new UriRef(bestGuess.getUri());
            Iterator<Triple> latitudes = metadata.filter(uri, GEO_LAT, null);
            if (latitudes.hasNext()) {
                g.add(latitudes.next());
            }
            Iterator<Triple> longitutes = metadata.filter(uri, GEO_LONG, null);
            if (longitutes.hasNext()) {
                g.add(longitutes.next());
                g.add(new TripleImpl(uri, Properties.RDFS_LABEL, lf.createTypedLiteral(bestGuess.getLabel())));
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serializer.serialize(out, g, SupportedFormat.RDF_JSON);
        
        String rdfString = out.toString("utf-8");
        return rdfString;
    }

    @GET
    @Produces(TEXT_HTML)
    public Response get(@Context HttpHeaders headers) {
        ResponseBuilder rb = Response.ok(new Viewable("index", this));
        rb.header(HttpHeaders.CONTENT_TYPE, TEXT_HTML+"; charset=utf-8");
        addCORSOrigin(servletContext,rb, headers);
        return rb.build();
    }

}
