package org.diskproject.server.repository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.common.Triple;
import org.diskproject.shared.classes.common.TripleDetails;
import org.diskproject.shared.classes.common.Value;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.TriggeredLOI.Status;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.util.GUID;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.vocabulary.Individual;
import org.diskproject.shared.classes.vocabulary.Property;
import org.diskproject.shared.classes.vocabulary.Vocabulary;
import org.diskproject.shared.classes.vocabulary.Type;
import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import edu.isi.wings.ontapi.KBAPI;
import edu.isi.wings.ontapi.KBObject;
import edu.isi.wings.ontapi.KBTriple;
import edu.isi.wings.ontapi.OntSpec;
import edu.isi.wings.ontapi.SparqlQuerySolution;

public class DiskRepository extends KBRepository {
  static DiskRepository singleton = null;
  
  private static SimpleDateFormat dateformatter = 
      new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");
  
  protected KBAPI hypontkb;
  protected KBAPI omicsontkb;
  
  Map<String, Vocabulary> vocabularies;
  ScheduledExecutorService monitor;
  ExecutorService executor;

  public static DiskRepository get() {
    if(singleton == null)
      singleton = new DiskRepository();
    return singleton;
  }

  public DiskRepository() {
    setConfiguration(KBConstants.DISKURI());
    initializeKB();
    monitor = Executors.newScheduledThreadPool(2);
    executor = Executors.newFixedThreadPool(2);
  }
  
  public String DOMURI(String username, String domain) {
    return this.server + "/" + username + "/" + domain;
  }
  
  public String ASSERTIONSURI(String username, String domain) {
    return this.DOMURI(username, domain) + "/assertions";
  }
  
  public String HYPURI(String username, String domain) {
    return this.DOMURI(username, domain) + "/hypotheses";
  }
  
  public String LOIURI(String username, String domain) {
    return this.DOMURI(username, domain) + "/lois";
  }

  public String TLOIURI(String username, String domain) {
    return this.DOMURI(username, domain) + "/tlois";
  }
  
  /**
   * KB Initialization
   */
  
  public void reloadKBCaches() {
    if(this.ontkb != null)
      this.ontkb.delete();
    if(this.hypontkb != null)
      this.hypontkb.delete();
    if(this.omicsontkb != null)
      this.omicsontkb.delete();
    
    this.initializeKB();
  }
  
  public void initializeKB() {
    super.initializeKB();
    try {
      this.hypontkb = fac.getKB(KBConstants.HYPURI(), OntSpec.PELLET, false, true);
      this.omicsontkb = fac.getKB(KBConstants.OMICSURI(), OntSpec.PELLET, false, true);
      
      this.vocabularies = new HashMap<String, Vocabulary>();
      this.vocabularies.put(KBConstants.DISKURI(),
          this.initializeVocabularyFromKB(this.ontkb, KBConstants.DISKNS()));      
      this.vocabularies.put(KBConstants.OMICSURI(), 
          this.initializeVocabularyFromKB(this.omicsontkb, KBConstants.OMICSNS()));
      this.vocabularies.put(KBConstants.HYPURI(),
          this.initializeVocabularyFromKB(this.hypontkb, KBConstants.HYPNS()));      
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  
  /**
   * Vocabulary Initialization
   */
  
  public Map<String, Vocabulary> getVocabularies() {
    return this.vocabularies;
  }
  
  public Vocabulary getVocabulary(String uri) {
    return this.vocabularies.get(uri);
  }
  
  public Vocabulary getUserVocabulary(String username, String domain) {
    String url = this.ASSERTIONSURI(username, domain);
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PELLET, true);
      return this.initializeVocabularyFromKB(kb, url + "#");
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
  
  public Vocabulary initializeVocabularyFromKB(KBAPI kb, String ns) {
    Vocabulary vocabulary = new Vocabulary(ns);
    this.fetchPropertiesFromKB(kb, vocabulary);
    this.fetchTypesFromKB(kb, vocabulary);
    this.fetchIndividualsFromKB(kb, vocabulary);
    return vocabulary;
  }
  
  private void fetchPropertiesFromKB(KBAPI kb, Vocabulary vocabulary) {
    for(KBObject prop : kb.getAllProperties()) {
      if(!prop.getID().startsWith(vocabulary.getNamespace()))
        continue;
      
      KBObject domcls = kb.getPropertyDomain(prop);
      KBObject rangecls = kb.getPropertyRange(prop);
      
      Property mprop = new Property();
      mprop.setId(prop.getID());
      mprop.setName(prop.getName());
      
      String label = this.createPropertyLabel(prop.getName());
      mprop.setLabel(label);
      
      if(domcls != null)
        mprop.setDomain(domcls.getID());
      
      if(rangecls != null)
        mprop.setRange(rangecls.getID());
      
      vocabulary.addProperty(mprop);
    }
  }
  
  private void fetchTypesFromKB(KBAPI kb, Vocabulary vocabulary) {
    try {
      KBObject topcls = kb.getConcept(owlns + "Thing");
      this.fetchTypesFromKB(topcls, kb, vocabulary);
    }
    catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private Type fetchTypesFromKB(KBObject cls, KBAPI kb, Vocabulary vocabulary) {
    String clsid = cls.getID();
    Type type = new Type();
    type.setId(clsid);
    type.setName(cls.getName());
    type.setLabel(kb.getLabel(cls));
    for(KBObject subcls : kb.getSubClasses(cls, true)) {
      Type subtype = this.fetchTypesFromKB(subcls, kb, vocabulary);
      if(subtype == null)
        continue;
      //if(clsid.startsWith(vocabulary.getNamespace()))
      if(!clsid.startsWith(KBConstants.OWLNS()))
        subtype.setParent(clsid);
      if(subtype.getId().startsWith(vocabulary.getNamespace())) {
        type.addChild(subtype.getId());
        vocabulary.addType(subtype);
      }
    }

    return type;
  }
  
  private void fetchIndividualsFromKB(KBAPI kb, Vocabulary vocabulary) {
    try {
      KBObject typeprop = kb.getProperty(KBConstants.RDFNS()+"type");
      for(KBTriple t : kb.genericTripleQuery(null, typeprop, null)) {
        KBObject inst = t.getSubject();
        KBObject typeobj = t.getObject();        
        if(!inst.getID().startsWith(vocabulary.getNamespace()))
          continue;
        if(typeobj.getNamespace().equals(KBConstants.OWLNS()))
          continue;
        Individual ind = new Individual();
        ind.setId(inst.getID());
        ind.setName(inst.getName());
        ind.setType(typeobj.getID());
        String label = kb.getLabel(inst);
        if(label == null)
          label = inst.getName();
        ind.setLabel(label);
        vocabulary.addIndividual(ind);        
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
  
  private String createPropertyLabel(String pname) {
    // Remove starting "has"
    pname = pname.replaceAll("^has", "");
    // Convert camel case to spaced human readable string
    pname = pname.replaceAll(String.format("%s|%s|%s",
        "(?<=[A-Z])(?=[A-Z][a-z])", "(?<=[^A-Z])(?=[A-Z])",
        "(?<=[A-Za-z])(?=[^A-Za-z])"), " ");
    // Make first letter upper case
    return pname.substring(0,1).toUpperCase() + pname.substring(1);
  }
  
  /*
   * Hypotheses
   */
  
  public List<TreeItem> listHypotheses(String username, String domain) {
    List<TreeItem> list = new ArrayList<TreeItem>();
    String url = this.HYPURI(username, domain);
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBObject hypcls = this.cmap.get("Hypothesis");
      KBObject typeprop = kb.getProperty(KBConstants.RDFNS()+"type");
      for(KBTriple t : kb.genericTripleQuery(null, typeprop, hypcls)) {
        KBObject hypobj = t.getSubject();
        String name = kb.getLabel(hypobj);
        String description = kb.getComment(hypobj);
        
        String parentid = null;
        KBObject parentobj = kb.getPropertyValue(hypobj, 
            pmap.get("hasParentHypothesis"));
        if(parentobj != null)
          parentid = parentobj.getName();
        
        TreeItem item = new TreeItem(hypobj.getName(), name, description, parentid);
        list.add(item);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return list;
  }
  
  public Hypothesis getHypothesis(String username, String domain, String id) {
    String url = this.HYPURI(username, domain);
    String fullid = url + "/" + id;
    String provid = fullid + "/provenance";
    
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBAPI provkb = this.fac.getKB(provid, OntSpec.PLAIN, true);
      
      KBObject hypitem = kb.getIndividual(fullid);
      Graph graph = this.getKBGraph(fullid);
      if(hypitem == null || graph == null)
        return null;
      
      Hypothesis hypothesis = new Hypothesis();
      hypothesis.setId(id);
      hypothesis.setName(kb.getLabel(hypitem));
      hypothesis.setDescription(kb.getComment(hypitem));
      hypothesis.setGraph(graph);
      
      KBObject parentobj = kb.getPropertyValue(hypitem, 
          pmap.get("hasParentHypothesis"));
      if(parentobj != null)
        hypothesis.setParentId(parentobj.getName());
      
      this.updateTripleDetails(graph, provkb);
      
      return hypothesis;

    } catch (Exception e) {
      e.printStackTrace();
    }    
    return null;
  }
  
  private Graph updateTripleDetails(Graph graph, KBAPI provkb) {
    HashMap<String, Triple> tripleMap = new HashMap<String, Triple>();
    for(Triple t : graph.getTriples())
      tripleMap.put(t.toString(), t);
    
    KBObject subprop = provkb.getProperty(KBConstants.RDFNS() + "subject");
    KBObject predprop = provkb.getProperty(KBConstants.RDFNS() + "predicate");
    KBObject objprop = provkb.getProperty(KBConstants.RDFNS() + "object");

    for(KBTriple kbt : provkb.genericTripleQuery(null, subprop, null)) {
      KBObject stobj = kbt.getSubject();
      KBObject subjobj = kbt.getObject();
      KBObject predobj = provkb.getPropertyValue(stobj, predprop);
      KBObject objobj = provkb.getPropertyValue(stobj, objprop);      
      
      Value value = this.getObjectValue(objobj);
      Triple triple = new Triple();
      triple.setSubject(subjobj.getID());
      triple.setPredicate(predobj.getID());
      triple.setObject(value);

      String triplestr = triple.toString();
      if(tripleMap.containsKey(triplestr)) {
        Triple t = tripleMap.get(triplestr);
        
        KBObject conf = provkb.getPropertyValue(stobj, pmap.get("hasConfidenceValue"));
        KBObject tloi = provkb.getPropertyValue(stobj, pmap.get("hasTriggeredLOI"));
        
        TripleDetails details = new TripleDetails();
        if(conf != null && conf.getValue() != null)
          details.setConfidenceValue((Double) conf.getValue());
        if(tloi != null)
          details.setTriggeredLOI(tloi.getID());
        
        t.setDetails(details);
      }
    }
    return graph;
  }

  
  public Hypothesis updateHypothesis(String username, String domain, String id,
      Hypothesis hypothesis) {
    if(hypothesis.getId() == null)
      return null;
    
    if(this.deleteHypothesis(username, domain, id) && 
        this.addHypothesis(username, domain, hypothesis))
      return hypothesis;
    return null;
  }
  
  public boolean deleteHypothesis(String username, String domain, String id) {
    if(id == null)
      return false;
    
    String url = this.HYPURI(username, domain);
    String fullid = url + "/" + id;
    String provid = fullid + "/provenance";
    
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, false);
      KBAPI hypkb = this.fac.getKB(fullid, OntSpec.PLAIN, false);
      KBAPI provkb = this.fac.getKB(provid, OntSpec.PLAIN, false);
      
      if(kb != null && hypkb != null && provkb != null) {
        KBObject hypitem = kb.getIndividual(fullid);
        if(hypitem != null) {
          for(KBTriple t : 
            kb.genericTripleQuery(null, pmap.get("hasParentHypothesis"), hypitem)) {
            this.deleteHypothesis(username, domain, t.getSubject().getName());
          }
          kb.deleteObject(hypitem, true, true);
        }
        return kb.save() && hypkb.delete() && provkb.delete();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;
  }
  
  public boolean addHypothesis(String username, String domain, Hypothesis hypothesis) {
    if(hypothesis.getId() == null)
      return false;
    
    String url = this.HYPURI(username, domain);
    String fullid = url + "/" + hypothesis.getId();
    String provid = fullid + "/provenance";
    
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBAPI hypkb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
      KBAPI provkb = this.fac.getKB(provid, OntSpec.PLAIN, true);
      
      KBObject hypitem = kb.createObjectOfClass(fullid, this.cmap.get("Hypothesis"));
      if(hypothesis.getName() != null)
        kb.setLabel(hypitem, hypothesis.getName());
      if(hypothesis.getDescription() != null)
        kb.setComment(hypitem, hypothesis.getDescription());
      if(hypothesis.getParentId() != null) {
        String fullparentid = url + "/" + hypothesis.getParentId();
        kb.setPropertyValue(hypitem, pmap.get("hasParentHypothesis"), 
            kb.getResource(fullparentid));
      }
      
      for(Triple triple : hypothesis.getGraph().getTriples()) {
        KBTriple t = this.getKBTriple(triple, hypkb);
        if(t != null)
          hypkb.addTriple(t);
        
        // Add triple details (confidence value, provenance, etc)
        this.storeTripleDetails(triple, provid, provkb);
      }
      return kb.save() && hypkb.save() && provkb.save();
    } catch (Exception e) {
      e.printStackTrace();
    }        
    return true;
  }
  
  private void storeTripleDetails(Triple triple, String provid, KBAPI provkb) {
    TripleDetails details = triple.getDetails();
    if(details != null) {
      KBObject stobj = provkb.getResource(provid +"#"+GUID.randomId("Statement"));
      this.setKBStatement(triple, provkb, stobj);
      
      if(details.getConfidenceValue() > 0)
        provkb.setPropertyValue(stobj, pmap.get("hasConfidenceValue"), 
            provkb.createLiteral(triple.getDetails().getConfidenceValue()));
      if(details.getTriggeredLOI() != null)
        provkb.setPropertyValue(stobj, pmap.get("hasTriggeredLOI"), 
            provkb.getResource(triple.getDetails().getTriggeredLOI()));          
    }
  }
  
  public List<TriggeredLOI> queryHypothesis(String username, String domain, 
      String id) {
    String hypuri = this.HYPURI(username, domain) + "/" + id;
    String omicsont = KBConstants.OMICSURI();
    String hypont = KBConstants.HYPURI();    
    String assertions = this.ASSERTIONSURI(username, domain);
    
    List<TriggeredLOI> tlois = new ArrayList<TriggeredLOI>();
    
    try {
      KBAPI kb = this.fac.getKB(OntSpec.PLAIN);
      kb.importFrom(this.fac.getKB(omicsont, OntSpec.PLAIN));
      kb.importFrom(this.fac.getKB(hypont, OntSpec.PLAIN));
      kb.importFrom(this.fac.getKB(assertions, OntSpec.PLAIN));
      kb.importFrom(this.fac.getKB(hypuri, OntSpec.PLAIN));
      
      for(TreeItem item : this.listLOIs(username, domain)) {
        LineOfInquiry loi = this.getLOI(username, domain, item.getId());

        String loiquery = loi.getQuery();
        if(loiquery == null || loiquery.equals(""))
          continue;
        
        String where = "";
        for(String line : loiquery.split("\\n")) {
          line = line.trim();
          if(line.equals(""))
            continue;
          where += line + " .\n";
        }
        
        String sparqlQuery = "PREFIX bio: <"+KBConstants.OMICSNS()+">\n" +
            "PREFIX hyp: <"+KBConstants.HYPNS()+">\n" +
            "PREFIX xsd: <"+KBConstants.XSDNS()+">\n" +
            "PREFIX user: <"+assertions+"#>\n\n" +
            "SELECT *\n" +
            "WHERE { \n" + where + "}\n";
        
        TriggeredLOI tloi = null; 
        for(ArrayList<SparqlQuerySolution> solutions : kb.sparqlQuery(sparqlQuery)) {
          if(tloi == null) {
            tloi = new TriggeredLOI(loi, id);
            tloi.copyWorkflowBindings(loi.getMetaWorkflows(), tloi.getMetaWorkflows());            
          }
          
          Map<String, String> varbindings = new HashMap<String, String>();
          for(SparqlQuerySolution solution : solutions) {
            String value;
            if(solution.getObject().isLiteral())
              value = solution.getObject().getValueAsString();
            else
              value = solution.getObject().getName();
            varbindings.put("?" + solution.getVariable(), value);
          }
          
          for(WorkflowBindings bindings : loi.getWorkflows()) {
            WorkflowBindings newbindings = new WorkflowBindings();
            newbindings.setWorkflow(bindings.getWorkflow());
            ArrayList<VariableBinding> vbindings = 
                new ArrayList<VariableBinding>(bindings.getBindings());
            
            for(VariableBinding vbinding : vbindings) {
              String bindingstr = vbinding.getBinding();
              String newval;
              if(bindingstr.startsWith("?") && varbindings.containsKey(bindingstr))
                newval = varbindings.get(bindingstr);
              else
                newval = bindingstr;
              vbinding.setBinding(newval);
            }
            newbindings.setBindings(vbindings);
            tloi.getWorkflows().add(newbindings);
          }
        }
        if(tloi != null)
          tlois.add(tloi);
      }
    }
    catch (Exception e) {
      e.printStackTrace();
    }
    return tlois;
  }

  /**
   * Assertions
   */
  
  private KBObject getKBValue(Value v, KBAPI kb) {
    if(v.getType() == Value.Type.LITERAL) {
      if(v.getDatatype() != null)
        return kb.createXSDLiteral(v.getValue().toString(), v.getDatatype());
      else
        return kb.createLiteral(v.getValue());
    }
    else {
      return kb.getResource(v.getValue().toString());
    }
  }
  
  private KBTriple getKBTriple(Triple triple, KBAPI kb) {
    KBObject subj = kb.getResource(triple.getSubject());
    KBObject pred = kb.getResource(triple.getPredicate());
    KBObject obj = getKBValue(triple.getObject(), kb);
    
    if(subj != null && pred != null && obj != null)
      return this.fac.getTriple(subj, pred, obj);
    return null;
  }
  
  private void setKBStatement(Triple triple, KBAPI kb, KBObject st) {
    KBObject subj = kb.getResource(triple.getSubject());
    KBObject pred = kb.getResource(triple.getPredicate());
    KBObject obj = getKBValue(triple.getObject(), kb);
    KBObject subprop = kb.getProperty(KBConstants.RDFNS() + "subject");
    KBObject predprop = kb.getProperty(KBConstants.RDFNS() + "predicate");
    KBObject objprop = kb.getProperty(KBConstants.RDFNS() + "object");
    kb.addTriple(st, subprop, subj);
    kb.addTriple(st, predprop, pred);
    kb.addTriple(st, objprop, obj);
  }
  
  private Value getObjectValue(KBObject obj) {
    Value v = new Value();
    if(obj.isLiteral()) {
      Object valobj = obj.getValue();
      if(valobj instanceof Date) {
        valobj = dateformatter.format((Date)valobj);
      }
      v.setType(Value.Type.LITERAL);
      v.setValue(valobj);
      v.setDatatype(obj.getDataType());
    }
    else {
      v.setType(Value.Type.URI);
      v.setValue(obj.getID());
    }
    return v;
  }
  
  private Graph getKBGraph(String url) {
    try {
      Graph graph = new Graph();
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, false);
      if(kb == null)
        return null;
      for(KBTriple t : kb.genericTripleQuery(null, null, null)) {
        Value value = this.getObjectValue(t.getObject());
        Triple triple = new Triple();
        triple.setSubject(t.getSubject().getID());
        triple.setPredicate(t.getPredicate().getID());
        triple.setObject(value);
        graph.addTriple(triple);
      }
      return graph;
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
  
  public void addAssertion(String username, String domain, Graph assertion) {
    String url = this.ASSERTIONSURI(username, domain);
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      for(Triple triple : assertion.getTriples()) {
        KBTriple t = this.getKBTriple(triple, kb);
        if(t != null)
          kb.addTriple(t);
      }
      kb.save();
    } catch (Exception e) {
      e.printStackTrace();
    }   
  }

  public Graph listAssertions(String username, String domain) {
    String url = this.ASSERTIONSURI(username, domain);
    return this.getKBGraph(url);
  }
  
  public void updateAssertions(String username, String domain, Graph assertions) {
    String url = this.ASSERTIONSURI(username, domain);
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      kb.delete(); 
      this.addAssertion(username, domain, assertions);
    } catch (Exception e) {
      e.printStackTrace();
    }   
  }
  
  public void deleteAssertion(String username, String domain, Graph assertion) {
    String url = this.ASSERTIONSURI(username, domain);
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      for(Triple triple : assertion.getTriples()) {
        KBTriple t = this.getKBTriple(triple, kb);
        if(t != null)
          kb.removeTriple(t);
      }
      kb.save();
    } catch (Exception e) {
      e.printStackTrace();
    }       
  }

  
  /**
   * Lines of Inquiry
   */
  public boolean addLOI(String username, String domain, LineOfInquiry loi) {
    if(loi.getId() == null)
      return false;
    
    String url = this.LOIURI(username, domain);
    String fullid = url + "/" + loi.getId();
    
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBAPI loikb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
      KBObject loiitem = kb.createObjectOfClass(fullid, this.cmap.get("LineOfInquiry"));
      KBObject floiitem = loikb.createObjectOfClass(fullid, this.cmap.get("LineOfInquiry"));
      if(loi.getName() != null)
        kb.setLabel(loiitem, loi.getName());
      if(loi.getDescription() != null)
        kb.setComment(loiitem, loi.getDescription());
      if(loi.getQuery() != null) {
        KBObject valobj = loikb.createLiteral(loi.getQuery());
        loikb.setPropertyValue(floiitem, pmap.get("hasPatternQuery"), valobj);
      }
      this.storeWorkflowBindingsInKB(loikb, floiitem, 
          pmap.get("hasWorkflowBindings"),
          loi.getWorkflows(), username, domain);
      this.storeWorkflowBindingsInKB(loikb, floiitem, 
          pmap.get("hasMetaWorkflowBindings"),
          loi.getMetaWorkflows(), username, domain);          

      return kb.save() && loikb.save();
    } catch (Exception e) {
      e.printStackTrace();
    }        
    return true;    
  }
  
  private void storeWorkflowBindingsInKB(KBAPI kb, KBObject loiitem, 
      KBObject bindingprop, List<WorkflowBindings> bindingslist, 
      String username, String domain) {    
    if(bindingslist == null)
      return;
    
    for(WorkflowBindings bindings : bindingslist) {  
      String workflowid = WingsAdapter.get().WFLOWID(username, domain, 
          bindings.getWorkflow());
      String workflowuri = WingsAdapter.get().WFLOWURI(username, domain, 
          bindings.getWorkflow());      
      KBObject bindingobj = kb.createObjectOfClass(workflowid, 
          cmap.get("WorkflowBindings"));
      kb.addPropertyValue(loiitem, bindingprop, bindingobj);
      
      // Get Run details
      if(bindings.getRun() != null) {
        if(bindings.getRun().getId() != null) {
          kb.setPropertyValue(bindingobj, pmap.get("hasRunId"), 
              kb.createLiteral(bindings.getRun().getId()));
        }
        if(bindings.getRun().getStatus() != null)
          kb.setPropertyValue(bindingobj, pmap.get("hasRunStatus"), 
              kb.createLiteral(bindings.getRun().getStatus()));
        if(bindings.getRun().getLink() != null)
          kb.setPropertyValue(bindingobj, pmap.get("hasRunLink"), 
              kb.createLiteral(bindings.getRun().getLink()));
      }
      
      for(VariableBinding vbinding : bindings.getBindings()) {
        String varid = vbinding.getVariable();
        String binding = vbinding.getBinding();
        Value bindingValue = new Value(binding, KBConstants.XSDNS()+"string");
        KBObject varbindingobj = kb.createObjectOfClass(null, 
            cmap.get("VariableBinding"));
        kb.setPropertyValue(varbindingobj, pmap.get("hasVariable"), 
            kb.getResource(workflowuri + "#" + varid));
        if(bindingValue != null)
          kb.setPropertyValue(varbindingobj, pmap.get("hasBinding"), 
              this.getKBValue(bindingValue, kb));  
        
        kb.addPropertyValue(bindingobj, pmap.get("hasVariableBinding"), varbindingobj);
      }
    }
  }

  public List<TreeItem> listLOIs(String username, String domain) {
    List<TreeItem> list = new ArrayList<TreeItem>();
    String url = this.LOIURI(username, domain);
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBObject hypcls = this.cmap.get("LineOfInquiry");
      KBObject typeprop = kb.getProperty(KBConstants.RDFNS()+"type");
      for(KBTriple t : kb.genericTripleQuery(null, typeprop, hypcls)) {
        KBObject hypobj = t.getSubject();
        String name = kb.getLabel(hypobj);
        String description = kb.getComment(hypobj);
        TreeItem item = new TreeItem(hypobj.getName(), name, description, null);
        list.add(item);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }    
    return list;
  }

  public LineOfInquiry getLOI(String username, String domain, String id) {
    String url = this.LOIURI(username, domain);
    String fullid = url + "/" + id;
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBAPI loikb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
      KBObject loiitem = kb.getIndividual(fullid);
      if(loiitem == null)
        return null;
      
      LineOfInquiry loi = new LineOfInquiry();
      loi.setId(id);
      loi.setName(kb.getLabel(loiitem));
      loi.setDescription(kb.getComment(loiitem));
      
      KBObject floiitem = loikb.getIndividual(fullid);
      KBObject queryobj = loikb.getPropertyValue(floiitem, pmap.get("hasPatternQuery"));
      if(queryobj != null)
        loi.setQuery(queryobj.getValueAsString());
      
      loi.setWorkflows(this.getWorkflowBindingsFromKB(username, domain, 
          loikb, floiitem, pmap.get("hasWorkflowBindings")));
      loi.setMetaWorkflows(this.getWorkflowBindingsFromKB(username, domain, 
          loikb, floiitem, pmap.get("hasMetaWorkflowBindings")));      
      
      return loi;
    } catch (Exception e) {
      e.printStackTrace();
    }    
    return null;
  }
  
  private List<WorkflowBindings> getWorkflowBindingsFromKB(String username,
      String domain, KBAPI kb, KBObject loiitem, KBObject bindingprop) { 
    List<WorkflowBindings> list = new ArrayList<WorkflowBindings>();
    for(KBTriple t : kb.genericTripleQuery(loiitem, bindingprop, null)) {
      KBObject wbobj = t.getObject();
      WorkflowBindings bindings = new WorkflowBindings();

      // Workflow Run details
      WorkflowRun run = new WorkflowRun();
      KBObject robj = kb.getPropertyValue(wbobj, pmap.get("hasRunId"));
      if(robj != null) 
        run.setId(robj.getValue().toString());
      KBObject statusobj = kb.getPropertyValue(wbobj, pmap.get("hasRunStatus"));
      if(statusobj != null) 
        run.setStatus(statusobj.getValue().toString());
      KBObject linkobj = kb.getPropertyValue(wbobj, pmap.get("hasRunLink"));
      if(linkobj != null) 
        run.setLink(linkobj.getValue().toString());
      bindings.setRun(run);
      
      // Workflow details
      bindings.setWorkflow(wbobj.getName());
      bindings.setWorkflowLink(
          WingsAdapter.get().getWorkflowLink(username, domain, wbobj.getName()));
      
      // Variable binding details
      for(KBObject vbobj : kb.getPropertyValues(wbobj, pmap.get("hasVariableBinding"))) {
        KBObject varobj = kb.getPropertyValue(vbobj, pmap.get("hasVariable"));
        KBObject bindobj = kb.getPropertyValue(vbobj, pmap.get("hasBinding"));
        VariableBinding vbinding = new VariableBinding();
        vbinding.setVariable(varobj.getName());
        vbinding.setBinding(bindobj.getValueAsString());
        bindings.getBindings().add(vbinding);
      }
      list.add(bindings);
    }
    return list;
  }
  
  public LineOfInquiry updateLOI(String username, String domain, String id,
      LineOfInquiry loi) {
    if(loi.getId() == null)
      return null;
    
    if(this.deleteLOI(username, domain, id) && 
        this.addLOI(username, domain, loi))
      return loi;
    return null;
  }
  
  public boolean deleteLOI(String username, String domain, String id) {
    if(id == null)
      return false;
    
    String url = this.LOIURI(username, domain);
    String fullid = url + "/" + id;
    
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, false);
      KBAPI loikb = this.fac.getKB(fullid, OntSpec.PLAIN, false);
      if(kb != null && loikb != null) {
        KBObject hypitem = kb.getIndividual(fullid);
        kb.deleteObject(hypitem, true, true);
        return kb.save() && loikb.delete();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;    
  }
  
  
  /**
   * Triggered Lines of Inquiries (LOIs)
   */
  
  public List<TriggeredLOI> listTriggeredLOIs(String username, String domain) {
    List<TriggeredLOI> list = new ArrayList<TriggeredLOI>();
    String url = this.TLOIURI(username, domain);
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBObject cls = this.cmap.get("TriggeredLOI");
      KBObject typeprop = kb.getProperty(KBConstants.RDFNS()+"type");
      
      for(KBTriple t : kb.genericTripleQuery(null, typeprop, cls)) {
        KBObject obj = t.getSubject();

        TriggeredLOI tloi = this.getTriggeredLOI(username, domain, 
            obj.getID(), kb, null);
        
        list.add(tloi);
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return list;
  }
  
  public TriggeredLOI getTriggeredLOI(String username, String domain, String id) {
    String url = this.TLOIURI(username, domain);
    String fullid = url + "/" + id;
    
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBAPI tloikb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
      return this.getTriggeredLOI(username, domain, fullid, kb, tloikb);
    } catch (Exception e) {
      e.printStackTrace();
    }
    return null;
  }
  
  public void addTriggeredLOI(String username, String domain, TriggeredLOI tloi) {
    tloi.setStatus(Status.QUEUED);
    this.saveTriggeredLOI(username, domain, tloi);
    
    TLOIExecutionThread wflowThread = 
        new TLOIExecutionThread(username, domain, tloi, false);
    executor.execute(wflowThread);
  }
  
  public boolean deleteTriggeredLOI(String username, String domain, String id) {
    if(id == null)
      return false;
    
    String url = this.TLOIURI(username, domain);
    String fullid = url + "/" + id;
    
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, false);
      KBAPI tloikb = this.fac.getKB(fullid, OntSpec.PLAIN, false);
      if(kb != null && tloikb != null) {
        KBObject item = kb.getIndividual(fullid);
        KBObject hypobj = kb.getPropertyValue(item, pmap.get("hasResultingHypothesis"));
        if(hypobj != null) {
          for(KBTriple t : 
            kb.genericTripleQuery(null, pmap.get("hasParentHypothesis"), hypobj)) {
            this.deleteTriggeredLOI(username, domain, t.getSubject().getName());
          }
          this.deleteHypothesis(username, domain, hypobj.getName());
        }
        kb.deleteObject(item, true, true);
        return kb.save() && tloikb.delete();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    return false;    
  }
  
  private TriggeredLOI getTriggeredLOI(String username, String domain, 
      String id, KBAPI kb, KBAPI tloikb) {  
    TriggeredLOI tloi = new TriggeredLOI();
    KBObject obj = kb.getIndividual(id);
    
    tloi.setId(obj.getName());
    tloi.setName(kb.getLabel(obj));
    tloi.setDescription(kb.getComment(obj));

    KBObject lobj = kb.getPropertyValue(obj, pmap.get("hasLOI"));
    if(lobj != null) 
      tloi.setLoiId(lobj.getName());
    
    KBObject pobj = kb.getPropertyValue(obj, pmap.get("hasParentHypothesis"));
    if(pobj != null) 
      tloi.setParentHypothesisId(pobj.getName());
    
    KBObject robj = kb.getPropertyValue(obj, pmap.get("hasResultingHypothesis"));
    if(robj != null) 
      tloi.setResultingHypothesisId(robj.getName());
    
    KBObject stobj = kb.getPropertyValue(obj, pmap.get("hasTriggeredLOIStatus"));
    if(stobj != null) 
      tloi.setStatus(Status.valueOf(stobj.getValue().toString()));
    
    if(tloikb != null) {
      KBObject floiitem = tloikb.getIndividual(id);
      tloi.setWorkflows(this.getWorkflowBindingsFromKB(username, domain, 
          tloikb, floiitem, pmap.get("hasWorkflowBindings")));
      tloi.setMetaWorkflows(this.getWorkflowBindingsFromKB(username, domain, 
          tloikb, floiitem, pmap.get("hasMetaWorkflowBindings")));          
    }
    return tloi;
  }
  
  
  private void updateTriggeredLOI(String username, String domain, String id,
      TriggeredLOI tloi) {
    if(tloi.getId() == null)
      return;
    
    this.deleteTriggeredLOI(username, domain, id);
    this.saveTriggeredLOI(username, domain, tloi);
  }
 
  private boolean saveTriggeredLOI(String username, String domain, TriggeredLOI tloi) {
    if(tloi.getId() == null)
      return false;
    
    String url = this.TLOIURI(username, domain);
    String fullid = url + "/" + tloi.getId();
    String hypns = this.HYPURI(username, domain) + "/";
    String loins = this.LOIURI(username, domain) + "/";
    
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBAPI tloikb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
      KBObject tloiitem = kb.createObjectOfClass(fullid, this.cmap.get("TriggeredLOI"));
      KBObject ftloiitem = tloikb.createObjectOfClass(fullid, this.cmap.get("TriggeredLOI"));
      if(tloi.getName() != null) {
        kb.setLabel(tloiitem, tloi.getName());
      }
      if(tloi.getDescription() != null) {
        kb.setComment(tloiitem, tloi.getDescription());
      }
      
      if(tloi.getLoiId() != null) {
        KBObject lobj = kb.getResource(loins + tloi.getLoiId());
        kb.setPropertyValue(tloiitem, pmap.get("hasLOI"), lobj);
      }
      if(tloi.getParentHypothesisId() != null) {
        KBObject hobj = kb.getResource(hypns + tloi.getParentHypothesisId());
        kb.setPropertyValue(tloiitem, pmap.get("hasParentHypothesis"), hobj);
      }
      if(tloi.getResultingHypothesisId() != null) {
        KBObject hobj = kb.getResource(hypns + tloi.getResultingHypothesisId());
        kb.setPropertyValue(tloiitem, pmap.get("hasResultingHypothesis"), hobj);
      }
      if(tloi.getStatus() != null) {
        KBObject stobj = kb.createLiteral(tloi.getStatus().toString());
        kb.setPropertyValue(tloiitem, pmap.get("hasTriggeredLOIStatus"), stobj);
      }
      this.storeWorkflowBindingsInKB(tloikb, ftloiitem, 
          pmap.get("hasWorkflowBindings"),
          tloi.getWorkflows(), username, domain);
      this.storeWorkflowBindingsInKB(tloikb, ftloiitem, 
          pmap.get("hasMetaWorkflowBindings"),
          tloi.getMetaWorkflows(), username, domain);          

      return kb.save() && tloikb.save();
    } catch (Exception e) {
      e.printStackTrace();
    }        
    return true;     
  }
  
  private String getWorkflowExecutionRun(TriggeredLOI tloi, String workflow) {
    for(WorkflowBindings bindings : tloi.getWorkflows()) {
      if(bindings.getWorkflow().equals(workflow))
        return bindings.getRun().getId();
    }
    return null;
  }
  
  private String createDummyHypothesis(String username, String domain, TriggeredLOI tloi) {
    Hypothesis parentHypothesis = 
        this.getHypothesis(username, domain, tloi.getParentHypothesisId());
    Hypothesis newHypothesis = new Hypothesis();
    newHypothesis.setId(GUID.randomId("Hypothesis"));
    newHypothesis.setName("[Revision] " +parentHypothesis.getName());
    String description = "Followed the line of inquiry: \""+tloi.getName()+"\" to run workflows "
        + "and generate a hypothesis.";
    newHypothesis.setDescription(description);
    newHypothesis.setParentId(parentHypothesis.getId());
    
    List<Triple> triples = 
        new ArrayList<Triple>(parentHypothesis.getGraph().getTriples());
    for(Triple t : triples) {
      TripleDetails details = new TripleDetails();
      details.setConfidenceValue(0.97);
      details.setTriggeredLOI(tloi.getId());
      t.setDetails(details);
    }
    Graph newgraph = new Graph();
    newgraph.setTriples(triples);
    newHypothesis.setGraph(newgraph);
    
    this.addHypothesis(username, domain, newHypothesis);
    return newHypothesis.getId();
  }
  
  class TLOIExecutionThread implements Runnable {
    String username;
    String domain;
    boolean metamode;
    TriggeredLOI tloi;
    
    public TLOIExecutionThread(String username, String domain, 
        TriggeredLOI tloi, boolean metamode) {
      this.username = username;
      this.domain = domain;
      this.tloi = tloi;
      this.metamode = metamode;
    }
    
    @Override
    public void run() {
      try {
        System.out.println("Running execution thread");
        
        List<WorkflowBindings> wflowBindings = tloi.getWorkflows();
        if(this.metamode)
          wflowBindings = tloi.getMetaWorkflows();
        
        // Start off workflows from tloi
        for(WorkflowBindings bindings : wflowBindings) {
          List<VariableBinding> vbindings = bindings.getBindings();
          if(this.metamode) {
            for(VariableBinding vbinding : vbindings) {
              String runid = getWorkflowExecutionRun(tloi, vbinding.getBinding());
              vbinding.setBinding(runid);
            }
          }
          System.out.println("Executing "+bindings.getWorkflow()+" with:\n"+vbindings);
          String runid = WingsAdapter.get().runWorkflow(username, domain, 
              bindings.getWorkflow(), vbindings);
          if(runid != null) {
            bindings.getRun().setId(runid.replaceAll("^.*#", ""));
          }
        }
        tloi.setStatus(Status.RUNNING);
        updateTriggeredLOI(username, domain, tloi.getId(), tloi);
        
        // Start monitoring
        TLOIMonitoringThread monitorThread = 
            new TLOIMonitoringThread(username, domain, tloi, metamode);
        monitor.schedule(monitorThread, 2, TimeUnit.SECONDS);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
  class TLOIMonitoringThread implements Runnable {
    String username;
    String domain;
    boolean metamode;
    TriggeredLOI tloi;
    
    public TLOIMonitoringThread(String username, String domain, 
        TriggeredLOI tloi, boolean metamode) {
      this.username = username;
      this.domain = domain;
      this.tloi = tloi;
      this.metamode = metamode;
    }
    
    @Override
    public void run() {
      try {
        System.out.println("Running monitoring thread");

        // Check workflow runs from tloi
        List<WorkflowBindings> wflowBindings = tloi.getWorkflows();
        if(this.metamode)
          wflowBindings = tloi.getMetaWorkflows();
        
        Status overallStatus = tloi.getStatus();
        int numSuccessful = 0;
        int numFinished = 0;
        for(WorkflowBindings bindings : wflowBindings) {
          String runid = bindings.getRun().getId();
          if(runid == null) {
            overallStatus = Status.FAILED;
            numFinished++;
            continue;
          }
          WorkflowRun wstatus = 
              WingsAdapter.get().getWorkflowRunStatus(this.username, this.domain, runid);
          bindings.setRun(wstatus);
          
          if(wstatus.getStatus().equals("FAILURE")) {
            overallStatus = Status.FAILED;
            numFinished++;
            continue;
          }
          if(wstatus.getStatus().equals("RUNNING")) {
            if(overallStatus != Status.FAILED)
              overallStatus = Status.RUNNING;
            continue; 
          }
          if(wstatus.getStatus().equals("SUCCESS")) {
            numFinished++;
            numSuccessful++;
          }
        }
        // If all the workflows are successfully finished
        if(numSuccessful == wflowBindings.size()) {
          if(metamode) {
            overallStatus = Status.SUCCESSFUL;
            // TODO: Fetch the output hypothesis, and create a new one
            // TODO: Creating a dummy hypothesis for now
            String hypId = createDummyHypothesis(username, domain, tloi);
            tloi.setResultingHypothesisId(hypId);
          }
          else {
            overallStatus = Status.RUNNING;
            
            // Start meta workflows
            TLOIExecutionThread wflowThread = 
                new TLOIExecutionThread(username, domain, tloi, true);
            executor.execute(wflowThread);
          }
        }
        else if(numFinished < wflowBindings.size()) {
          monitor.schedule(this, 2, TimeUnit.SECONDS);
        }
        tloi.setStatus(overallStatus);
        updateTriggeredLOI(username, domain, tloi.getId(), tloi);
      }
      catch (Exception e) {
        e.printStackTrace();
      }
    }
  }
  
}
