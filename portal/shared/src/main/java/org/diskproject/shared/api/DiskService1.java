package org.diskproject.shared.api;

import java.util.List;
import java.util.Map;

import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;

import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.vocabulary.Vocabulary;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.Workflow;
import org.diskproject.shared.classes.workflow.WorkflowRun;
import org.fusesource.restygwt.client.DirectRestService;

import com.fasterxml.jackson.annotation.JsonProperty;

@Path("")
@Produces("application/json")
@Consumes("application/json")
public interface DiskService1 extends DirectRestService {
   /*
   * Vocabulary
   */
  @GET
  @Path("vocabulary")
  public Map<String, Vocabulary> getVocabularies();
  
  @GET
  @Path("{username}/{domain}/vocabulary")
  public Vocabulary getUserVocabulary(
      @PathParam("username") String username, 
      @PathParam("domain") String domain);

  @GET
  @Path("vocabulary/reload")
  @Produces("text/html")
  public String reloadVocabularies();
  
  /*
   * Hypothesis
   */
  @POST
  @Path("{username}/{domain}/hypotheses")
  public void addHypothesis(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @JsonProperty("hypothesis") Hypothesis hypothesis);
  
  @GET
  @Path("{username}/{domain}/hypotheses")
  public List<TreeItem> listHypotheses(
      @PathParam("username") String username, 
      @PathParam("domain") String domain);
  
  @GET
  @Path("{username}/{domain}/hypotheses/{id}")
  public Hypothesis getHypothesis(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id);
  
  @PUT
  @Path("{username}/{domain}/hypotheses/{id}")
  public void updateHypothesis(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id,
      @JsonProperty("hypothesis") Hypothesis hypothesis);
  
  @DELETE
  @Path("{username}/{domain}/hypotheses/{id}")
  public void deleteHypothesis(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id);
  
  @GET
  @Path("{username}/{domain}/hypotheses/{id}/query")
  public List<TriggeredLOI> queryHypothesis(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id);
  
  /*
   * Assertions
   */
  @POST
  @Path("{username}/{domain}/assertions")
  public void addAssertion(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @JsonProperty("assertions") Graph assertions);
  
  @GET
  @Path("{username}/{domain}/assertions")
  public Graph listAssertions(
      @PathParam("username") String username, 
      @PathParam("domain") String domain);
  
  @DELETE
  @Path("{username}/{domain}/assertions")
  public void deleteAssertion(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @JsonProperty("assertions") Graph assertions);

  @PUT
  @Path("{username}/{domain}/assertions")
  public void updateAssertions (
      @PathParam("username") String username, 
      @PathParam("domain") String domain, 
      @JsonProperty("assertions") Graph assertions);
  
  /*
   * Lines of Inquiry
   */
  @POST
  @Path("{username}/{domain}/lois")  
  public void addLOI(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @JsonProperty("loi") LineOfInquiry loi);
  
  @GET
  @Path("{username}/{domain}/lois")  
  public List<TreeItem> listLOIs(
      @PathParam("username") String username, 
      @PathParam("domain") String domain);
  
  @GET
  @Path("{username}/{domain}/lois/{id}")  
  public LineOfInquiry getLOI(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id);
  
  @PUT
  @Path("{username}/{domain}/lois/{id}")  
  public void updateLOI(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id,
      @JsonProperty("loi") LineOfInquiry loi);
  
  @DELETE
  @Path("{username}/{domain}/lois/{id}")  
  public void deleteLOI(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id);
  
  /*
   * Triggered LOIs
   */
  @POST
  @Path("{username}/{domain}/tlois")
  public void addTriggeredLOI(@PathParam("username") String username, 
      @PathParam("domain") String domain,
      @JsonProperty("tloi") TriggeredLOI tloi);
  
  @GET
  @Path("{username}/{domain}/tlois")
  public List<TriggeredLOI> listTriggeredLOIs(@PathParam("username") String username, 
      @PathParam("domain") String domain);

  @GET
  @Path("{username}/{domain}/tlois/{id}")
  public TriggeredLOI getTriggeredLOI(@PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id);

  @DELETE
  @Path("{username}/{domain}/tlois/{id}")
  public void deleteTriggeredLOI(@PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id);
  
  /*
   * Workflows
   */
  @GET
  @Path("{username}/{domain}/workflows")
  public List<Workflow> listWorkflows(
      @PathParam("username") String username, 
      @PathParam("domain") String domain);

  @GET
  @Path("{username}/{domain}/workflows/{id}")
  public List<Variable> getWorkflowVariables(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id);

  @GET
  @Path("{username}/{domain}/runs/{id}")
  public WorkflowRun monitorWorkflow(
      @PathParam("username") String username, 
      @PathParam("domain") String domain,
      @PathParam("id") String id);  
}
