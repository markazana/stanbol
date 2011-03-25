package org.apache.stanbol.ontologymanager.web;

import java.util.Hashtable;
import java.util.Set;

import javax.servlet.ServletContext;
import javax.ws.rs.DELETE;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.Response;

import org.apache.clerezza.rdf.core.access.TcManager;
import org.apache.stanbol.ontologymanager.ontonet.api.ONManager;
import org.apache.stanbol.ontologymanager.ontonet.api.ontology.OntologyScope;
import org.apache.stanbol.ontologymanager.ontonet.api.ontology.ScopeRegistry;
import org.apache.stanbol.ontologymanager.ontonet.impl.ONManagerImpl;
import org.apache.stanbol.ontologymanager.ontonet.impl.io.ClerezzaOntologyStorage;
import org.apache.stanbol.ontologymanager.ontonet.impl.renderers.ScopeSetRenderer;
import org.semanticweb.owlapi.model.OWLOntology;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.stanbol.kres.jersey.format.KReSFormat;
import org.apache.stanbol.kres.jersey.resource.NavigationMixin;

/**
 * The main Web resource of the KReS ontology manager. All the scopes, sessions and ontologies are accessible
 * as subresources of ONMRootResource.<br>
 * <br>
 * This resource allows a GET method for obtaining an RDF representation of the set of registered scopes and a
 * DELETE method for clearing the scope set and ontology store accordingly.
 * 
 * @author alessandro
 * 
 */
@Path("/ontology")
public class ONMRootResource extends NavigationMixin {

    private Logger log = LoggerFactory.getLogger(getClass());

    /*
     * Placeholder for the ONManager to be fetched from the servlet context.
     */
    protected ONManager onm;
    protected ClerezzaOntologyStorage storage;

    protected ServletContext servletContext;

    public ONMRootResource(@Context ServletContext servletContext) {
        this.servletContext = servletContext;
        this.onm = (ONManager) servletContext.getAttribute(ONManager.class.getName());
//      this.storage = (OntologyStorage) servletContext
//      .getAttribute(OntologyStorage.class.getName());
// Contingency code for missing components follows.
/*
 * FIXME! The following code is required only for the tests. This should
 * be removed and the test should work without this code.
 */
if (onm == null) {
    log
            .warn("No KReSONManager in servlet context. Instantiating manually...");
    onm = new ONManagerImpl(new TcManager(), null,
            new Hashtable<String, Object>());
}
this.storage = onm.getOntologyStore();
if (storage == null) {
    log.warn("No OntologyStorage in servlet context. Instantiating manually...");
    storage = new ClerezzaOntologyStorage(new TcManager(),null);
}
    }

    /**
     * RESTful DELETE method that clears the entire scope registry and managed ontology store.
     */
    @DELETE
    public void clearOntologies() {
        // First clear the registry...
        ScopeRegistry reg = onm.getScopeRegistry();
        for (OntologyScope scope : reg.getRegisteredScopes())
            reg.deregisterScope(scope);
        // ...then clear the store.
        // TODO : the other way around?
        onm.getOntologyStore().clear();
    }

    @GET
    @Path("/{param:.+}")
    public Response echo(@PathParam("param") String s) {
        return Response.ok(s).build();
    }

    /**
     * Default GET method for obtaining the set of (both active and, optionally, inactive) ontology scopes
     * currently registered with this instance of KReS.
     * 
     * @param inactive
     *            if true, both active and inactive scopes will be included. Default is false.
     * @param headers
     *            the HTTP headers, supplied by the REST call.
     * @param servletContext
     *            the servlet context, supplied by the REST call.
     * @return a string representation of the requested scope set, in a format acceptable by the client.
     */
    @GET
    @Produces(value = {KReSFormat.RDF_XML, KReSFormat.OWL_XML, KReSFormat.TURTLE, KReSFormat.FUNCTIONAL_OWL,
                       KReSFormat.MANCHESTER_OWL, KReSFormat.RDF_JSON})
    public Response getScopes(@DefaultValue("false") @QueryParam("with-inactive") boolean inactive,
                              @Context HttpHeaders headers,
                              @Context ServletContext servletContext) {

        ScopeRegistry reg = onm.getScopeRegistry();

        Set<OntologyScope> scopes = inactive ? reg.getRegisteredScopes() : reg.getActiveScopes();

        OWLOntology ontology = ScopeSetRenderer.getScopes(scopes);

        return Response.ok(ontology).build();
    }

    // @Path("upload")
    // @Consumes(MediaType.MULTIPART_FORM_DATA)
    // @POST
    // public void uploadDumb(@FormParam("file") InputStream is) {
    // Writer writer = new StringWriter();
    //
    // char[] buffer = new char[1024];
    //
    // try {
    //
    // Reader reader = new BufferedReader(
    //
    // new InputStreamReader(is, "UTF-8"));
    //
    // int n;
    //
    // while ((n = reader.read(buffer)) != -1) {
    //
    // writer.write(buffer, 0, n);
    //
    // }
    // } catch (IOException ex) {
    // throw new WebApplicationException(ex);
    // } finally {
    //
    // try {
    // is.close();
    // } catch (IOException e) {
    // throw new WebApplicationException(e);
    // }
    //
    // }
    // System.out.println(writer.toString());
    // }
    //
    // @Path("formdata")
    // @Consumes(MediaType.MULTIPART_FORM_DATA)
    // @POST
    // public void uploadUrlFormData(
    // @FormDataParam("file") List<FormDataBodyPart> parts,
    // @FormDataParam("submit") FormDataBodyPart submit)
    // throws IOException, ParseException {
    //
    // System.out.println("XXXX: " + submit.getMediaType());
    // System.out.println("XXXX: "
    // + submit.getHeaders().getFirst("Content-Type"));
    //
    // for (FormDataBodyPart bp : parts) {
    // System.out.println(bp.getMediaType());
    // System.out.println(bp.getHeaders().get("Content-Disposition"));
    // System.out.println(bp.getParameterizedHeaders().getFirst(
    // "Content-Disposition").getParameters().get("name"));
    // bp.cleanup();
    // }
    // }

}
