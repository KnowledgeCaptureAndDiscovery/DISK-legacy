package org.diskproject.server;

import java.util.List;
import java.util.Map;

import java.io.StringWriter;
import java.io.PrintWriter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.SecurityContext;

import org.diskproject.server.api.impl.DiskResource;
import org.diskproject.server.api.impl.UserResource;

import org.diskproject.server.repository.DiskRepository;
import org.diskproject.server.repository.WingsAdapter;
import org.diskproject.shared.api.DiskService1;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.vocabulary.Vocabulary;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.Workflow;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import org.codehaus.jackson.map.ObjectMapper;

import com.fasterxml.jackson.annotation.JsonProperty;

@Path("/test")
public class DiskAPI {

	DiskResource disk;
	UserResource user;
	ObjectMapper toJson;
	StringWriter sw;
	PrintWriter pw;
	DiskRepository repo;

	public DiskAPI() {
		disk = new DiskResource();
		user = new UserResource();
		toJson = new ObjectMapper();
		sw = new StringWriter();
		pw = new PrintWriter(sw);
		repo = DiskRepository.get(); //Here

	}

	@GET
	@Produces(MediaType.TEXT_XML)
	public String sayHello() {
		String response = "<?xml version='1.0'?> <hello>Hello</hello>";
		return response;
	}

	/**
	 * Vocabulary
	 */
	@GET
	@Path("/vocabulary")
	@Produces(MediaType.APPLICATION_JSON)
	public String getVocabularies() {
		try {
			return toJson.writeValueAsString(disk.getVocabularies());
		} catch (Exception e) {
			e.printStackTrace(pw);
			String stackTrace = sw.toString();
			return "System Error\n" + stackTrace;
		}
	}

	@GET
	@Path("/{username}/{domain}/vocabulary")
	@Produces(MediaType.APPLICATION_JSON)
	public String getUserVocabulary(@PathParam("username") String username,
			@PathParam("domain") String domain) {
		try {
			return toJson.writeValueAsString(disk.getUserVocabulary(username, domain));
		} catch (Exception e) {
			e.printStackTrace(pw);
			String stackTrace = sw.toString();
			return "System Error\n" + stackTrace;
		}
	}
	
	  @GET
	  @Path("/vocabulary/reload")
	  @Produces(MediaType.TEXT_PLAIN)
	  public String reloadVocabularies() {
	    try {
	    	disk.reloadVocabularies();
	    	return "OK";
	    } catch (Exception e) {
			e.printStackTrace(pw);
			String stackTrace = sw.toString();
			return "System Error\n" + stackTrace;
	    }
	  }
	  
	  /**
	   * Hypothesis
	   */
	  
	  @POST
	  @Consumes(MediaType.APPLICATION_JSON)
	  @Produces(MediaType.APPLICATION_JSON)
	  @Path("{username}/{domain}/hypotheses")
	  public String addHypothesis(
	      @PathParam("username") String username, 
	      @PathParam("domain") String domain,
	      Hypothesis hypothesis) {
	      if(repo.addHypothesis(username, domain, hypothesis))
	    	  return "Hypothesis Added";
	      return "Operation Failed";
	  }

}
