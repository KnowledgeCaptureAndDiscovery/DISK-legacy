package org.diskproject.server.repository;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.common.Triple;
import org.diskproject.shared.classes.common.Value;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.util.GUID;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.vocabulary.Individual;
import org.diskproject.shared.classes.vocabulary.Property;
import org.diskproject.shared.classes.vocabulary.Vocabulary;
import org.diskproject.shared.classes.vocabulary.Type;

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

  public static DiskRepository get() {
    if(singleton == null)
      singleton = new DiskRepository();
    return singleton;
  }

  public DiskRepository() {
    setConfiguration(KBConstants.DISKURI());
    initializeKB();
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
      if(clsid.startsWith(vocabulary.getNamespace()))
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
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBObject hypitem = kb.getIndividual(fullid);
      Graph graph = this.getKBGraph(fullid);
      if(hypitem == null || graph == null)
        return null;
      
      Hypothesis hypothesis = new Hypothesis();
      hypothesis.setId(id);
      hypothesis.setName(kb.getLabel(hypitem));
      hypothesis.setDescription(kb.getComment(hypitem));
      hypothesis.setTriples(graph.getTriples());
      
      KBObject parentobj = kb.getPropertyValue(hypitem, 
          pmap.get("hasParentHypothesis"));
      if(parentobj != null)
        hypothesis.setParentId(parentobj.getName());
      
      return hypothesis;

    } catch (Exception e) {
      e.printStackTrace();
    }    
    return null;
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
    
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, false);
      KBAPI hypkb = this.fac.getKB(fullid, OntSpec.PLAIN, false);
      if(kb != null && hypkb != null) {
        KBObject hypitem = kb.getIndividual(fullid);
        kb.deleteObject(hypitem, true, true);
        return kb.save() && hypkb.delete();
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
    
    try {
      KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
      KBAPI hypkb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
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
      
      for(Triple triple : hypothesis.getTriples()) {
        KBTriple t = this.getKBTriple(triple, hypkb);
        if(t != null)
          hypkb.addTriple(t);
      }
      return kb.save() && hypkb.save();
    } catch (Exception e) {
      e.printStackTrace();
    }        
    return true;
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
            "PREFIX xsd: <"+KBConstants.XSDNS()+">\n\n" +
            "SELECT *\n" +
            "WHERE { \n" + where + "}\n";
        
        for(ArrayList<SparqlQuerySolution> solutions : kb.sparqlQuery(sparqlQuery)) {
          TriggeredLOI tloi = new TriggeredLOI(loi, id);          
          Map<String, String> varbindings = new HashMap<String, String>();
          for(SparqlQuerySolution solution : solutions) {
            String value;
            if(solution.getObject().isLiteral())
              value = solution.getObject().getValueAsString();
            else
              value = solution.getObject().getName();
            varbindings.put("?" + solution.getVariable(), value);
          }
          
          for(WorkflowBindings bindings : tloi.getWorkflows()) {
            for(String varid : bindings.getBindings().keySet()) {
              String bindingstr = bindings.getBindings().get(varid);
              String newval;
              if(bindingstr.startsWith("?") && varbindings.containsKey(bindingstr))
                newval = varbindings.get(bindingstr);
              else
                newval = bindingstr;
              bindings.getBindings().put(varid, newval);
            }
          }
          tlois.add(tloi);
        }
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
        Triple triple = new Triple(
            t.getSubject().getID(), 
            t.getPredicate().getID(), 
            value);
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
      for(String varid : bindings.getBindings().keySet()) {
        String binding = bindings.getBindings().get(varid);
        Value bindingValue = new Value(binding, KBConstants.XSDNS()+"string");
        KBObject varbindingobj = kb.createObjectOfClass(null, 
            cmap.get("VariableBinding"));
        kb.setPropertyValue(varbindingobj, pmap.get("hasVariable"), 
            kb.getResource(workflowuri + "#" + varid));
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
      
      loi.setWorkflows(this.getWorkflowBindingsFromKB(loikb, floiitem, 
          pmap.get("hasWorkflowBindings")));
      loi.setMetaWorkflows(this.getWorkflowBindingsFromKB(loikb, floiitem, 
          pmap.get("hasMetaWorkflowBindings")));      
      
      return loi;
    } catch (Exception e) {
      e.printStackTrace();
    }    
    return null;
  }
  
  private List<WorkflowBindings> getWorkflowBindingsFromKB(KBAPI kb, 
      KBObject loiitem, KBObject bindingprop) { 
    List<WorkflowBindings> list = new ArrayList<WorkflowBindings>();
    for(KBTriple t : kb.genericTripleQuery(loiitem, bindingprop, null)) {
      KBObject wbobj = t.getObject();
      WorkflowBindings bindings = new WorkflowBindings();
      bindings.setWorkflow(wbobj.getName());
      for(KBObject vbobj : kb.getPropertyValues(wbobj, pmap.get("hasVariableBinding"))) {
        KBObject varobj = kb.getPropertyValue(vbobj, pmap.get("hasVariable"));
        KBObject bindobj = kb.getPropertyValue(vbobj, pmap.get("hasBinding"));
        bindings.getBindings().put(varobj.getName(), bindobj.getValueAsString());
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
  
  public void addTriggeredLOI(String username, String domain, TriggeredLOI tloi) {
    Hypothesis parentHypothesis = 
        this.getHypothesis(username, domain, tloi.getParentHypothesisId());
    Hypothesis newHypothesis = new Hypothesis();
    newHypothesis.setId(GUID.randomId("Hypothesis"));
    newHypothesis.setName("[Modified] " +parentHypothesis.getName());
    String description = "Followed the Triggered line of inquiry \""+tloi.getName()+"\" to run workflows "
        + "and generate a hypothesis. (This is a Dummy hypothesis for now).";
    newHypothesis.setDescription(description);
    newHypothesis.setParentId(parentHypothesis.getId());
    
    List<Triple> triples = parentHypothesis.getTriples();
    triples.add(new Triple(
          KBConstants.OMICSNS()+"DummyProtein",
          KBConstants.HYPNS()+" associatedWith",
          new Value(KBConstants.OMICSNS()+"ColonCancerDummySubtype")
        ));
    newHypothesis.setTriples(triples);
    
    this.addHypothesis(username, domain, newHypothesis);
  }
}
