package org.diskproject.server.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.diskproject.server.util.Config;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.Workflow;

import edu.isi.wings.ontapi.KBAPI;
import edu.isi.wings.ontapi.KBObject;
import edu.isi.wings.ontapi.KBTriple;
import edu.isi.wings.ontapi.OntFactory;
import edu.isi.wings.ontapi.OntSpec;

public class WingsAdapter {
  static WingsAdapter singleton = null;
  
  private OntFactory fac;
  private String server;
  private String wflowns = "http://www.wings-workflows.org/ontology/workflow.owl#";
  
  public static WingsAdapter get() {
    if(singleton == null)
      singleton = new WingsAdapter();
    return singleton;
  }

  public WingsAdapter() {
    this.fac = new OntFactory(OntFactory.JENA);
    this.server = Config.get().getProperties().getString("wings-server");
  }
  
  public String DOMURI(String username, String domain) {
    return this.server + "/export/users/" + username + "/" + domain;
  }
  
  public String WFLOWURI(String username, String domain) {
    return this.DOMURI(username, domain) + "/workflows";
  }
  
  public String WFLOWURI(String username, String domain, String id) {
    return this.DOMURI(username, domain) + "/workflows/" + id + ".owl";
  }  
  
  public String WFLOWID(String username, String domain, String id) {
    return this.WFLOWURI(username, domain, id) + "#" + id;
  }
  
  public List<Workflow> getWorkflowList(String username, String domain) {
    String liburi = this.WFLOWURI(username, domain) + "/library.owl";
    try {
      List<Workflow> list = new ArrayList<Workflow>();
      KBAPI kb = fac.getKB(liburi, OntSpec.PLAIN);
      KBObject typeprop = kb.getProperty(KBConstants.RDFNS()+"type");
      KBObject templatecls = kb.getResource(this.wflowns + "WorkflowTemplate");
      for(KBTriple triple : kb.genericTripleQuery(null, typeprop, templatecls)) {
        KBObject tobj = triple.getSubject();
        Workflow wflow = new Workflow();
        wflow.setName(tobj.getName());
        list.add(wflow);
      }
      return list;
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
  
  public List<Variable> getWorkflowVariables(String username, String domain, String id) {
    String wflowuri = this.WFLOWURI(username, domain, id);
    try {
      List<Variable> list = new ArrayList<Variable>();
      Map<String, Boolean> varmap = new HashMap<String, Boolean>(); 
      KBAPI kb = fac.getKB(wflowuri, OntSpec.PELLET);
      KBObject linkprop = kb.getProperty(this.wflowns+"hasLink");
      KBObject origprop = kb.getProperty(this.wflowns+"hasOriginNode");
      KBObject destprop = kb.getProperty(this.wflowns+"hasDestinationNode");
      KBObject varprop = kb.getProperty(this.wflowns+"hasVariable");
      KBObject paramcls = kb.getConcept(this.wflowns+"ParameterVariable");
      
      for(KBTriple triple : kb.genericTripleQuery(null, linkprop, null)) {
        KBObject linkobj = triple.getObject();
        KBObject orignode = kb.getPropertyValue(linkobj, origprop);
        KBObject destnode = kb.getPropertyValue(linkobj, destprop);

        // Only return Input and Output variables
        if(orignode != null && destnode != null)
          continue;

        KBObject varobj = kb.getPropertyValue(linkobj, varprop);
        
        if(varmap.containsKey(varobj.getID()))
          continue;
        varmap.put(varobj.getID(), true);
        
        Variable var = new Variable();
        var.setName(varobj.getName());
        if(orignode == null)
          var.setInput(true);
        
        if(kb.isA(varobj, paramcls))
          var.setParam(true);
        
        list.add(var);
      }
      return list;      
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return null;    
  }
}
