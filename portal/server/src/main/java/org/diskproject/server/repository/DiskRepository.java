package org.diskproject.server.repository;

import java.io.InputStream;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.ConcurrentModificationException;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.apache.commons.lang.SerializationUtils;
//import org.diskproject.server.repository.GmailService.MailMonitor;
import org.diskproject.server.util.Config;
import org.diskproject.server.util.DataQuery;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.common.Triple;
import org.diskproject.shared.classes.common.TripleDetails;
import org.diskproject.shared.classes.common.TripleUtil;
import org.diskproject.shared.classes.common.Value;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.LineOfInquiry;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.TriggeredLOI.Status;
import org.diskproject.shared.classes.question.Question;
import org.diskproject.shared.classes.question.QuestionVariable;
import org.diskproject.shared.classes.util.GUID;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.vocabulary.Individual;
import org.diskproject.shared.classes.vocabulary.Property;
import org.diskproject.shared.classes.vocabulary.Vocabulary;
import org.diskproject.shared.classes.vocabulary.Type;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import edu.isi.kcap.ontapi.KBAPI;
import edu.isi.kcap.ontapi.KBObject;
import edu.isi.kcap.ontapi.KBTriple;
import edu.isi.kcap.ontapi.OntSpec;
import edu.isi.kcap.ontapi.SparqlQuerySolution;

public class DiskRepository extends KBRepository {
	static DiskRepository singleton;
	private static boolean creatingKB = false;

	private static SimpleDateFormat dateformatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

	protected KBAPI hypontkb;
	protected KBAPI omicsontkb;
	protected KBAPI neuroontkb;
	protected KBAPI questionkb;

	Map<String, Vocabulary> vocabularies;
	ScheduledExecutorService monitor;
	ScheduledExecutorService monitorData;
	ExecutorService executor;
	//static GmailService gmail;
	static DataMonitor dataThread;
	
  Pattern varPattern = Pattern.compile("\\?(.+?)\\b");
  Pattern varCollPattern = Pattern.compile("\\[\\s*\\?(.+?)\\s*\\]");

	public static void main(String[] args) {
		get();
		get().shutdownExecutors();
	}

	public static DiskRepository get() {
		if (!creatingKB && singleton == null) {
			creatingKB = true;
			singleton = new DiskRepository(); // Here
			creatingKB = false;
		}
		return singleton;
	}

	public DiskRepository() {
		/*if (gmail == null) {
			gmail = GmailService.get();
		}*/
		setConfiguration(KBConstants.DISKURI(), KBConstants.DISKNS());
		initializeKB(); // Here
		monitor = Executors.newScheduledThreadPool(0);
		executor = Executors.newFixedThreadPool(2);
		dataThread = new DataMonitor();
	}

	public void shutdownExecutors() {
		if (monitor != null)
			monitor.shutdownNow();
		if (executor != null)
			executor.shutdownNow();
		if (dataThread != null)
			dataThread.stop();
		if (monitorData != null)
			monitorData.shutdownNow();
		/*if (gmail != null)
			gmail.shutdown();*/
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

	public void initializeKB() {
		super.initializeKB();
		if (fac == null)
			return;
		try {
			this.neuroontkb = fac.getKB(KBConstants.NEUROURI(), OntSpec.PLAIN, false, true);
			System.out.println("GET KB: " + KBConstants.NEUROURI());
			this.hypontkb = fac.getKB(KBConstants.HYPURI(), OntSpec.PLAIN, false, true);
			System.out.println("GET KB: " + KBConstants.HYPURI());
			this.omicsontkb = fac.getKB(KBConstants.OMICSURI(), OntSpec.PLAIN, false, true);
			System.out.println("GET KB: " + KBConstants.OMICSURI());
			this.questionkb = fac.getKB(KBConstants.QUESTIONSURI(), OntSpec.PLAIN, false, true);
			System.out.println("GET KB: " + KBConstants.QUESTIONSURI());
			
			this.vocabularies = new HashMap<String, Vocabulary>();
			
			this.start_read();			
			
			this.vocabularies.put(KBConstants.NEUROURI(),
					this.initializeVocabularyFromKB(this.neuroontkb, KBConstants.NEURONS()));
			this.vocabularies.put(KBConstants.HYPURI(),
					this.initializeVocabularyFromKB(this.hypontkb, KBConstants.HYPNS()));
			this.vocabularies.put(KBConstants.OMICSURI(),
					this.initializeVocabularyFromKB(this.omicsontkb, KBConstants.OMICSNS()));
			this.vocabularies.put(KBConstants.DISKURI(),
					this.initializeVocabularyFromKB(this.ontkb, KBConstants.DISKNS()));
			this.vocabularies.put(KBConstants.QUESTIONSURI(),
					this.initializeVocabularyFromKB(this.questionkb, KBConstants.QUESTIONSNS()));

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			this.end();
		}
	}

	public void reloadKBCaches () {
		KBAPI[] kbs = {this.ontkb, this.hypontkb, this.omicsontkb, this.neuroontkb, this.hypontkb};

		try {
			this.start_write();
			for (KBAPI kb: kbs) if (kb != null) {
				System.out.println("Reloading " + kb.getURI());
				kb.removeAllTriples();
				kb.delete();
				this.save(kb);
				this.end(); this.start_write();
				
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			this.end();
		}
		
		this.initializeKB();
	}
	
	public List<Question> listHypothesesQuestions () {
		List<Question> all = new ArrayList<Question>();
		KBAPI kb = this.questionkb;
		if (kb != null)
		try {
			this.start_read();
			KBObject hypcls = this.cmap.get("Question");
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");
			KBObject labelprop = kb.getProperty(KBConstants.RDFNS() + "label");
			for (KBTriple t : kb.genericTripleQuery(null, typeprop, hypcls)) {
				KBObject question = t.getSubject();
				KBObject name = kb.getPropertyValue(question, labelprop);
				KBObject template = kb.getPropertyValue(question, pmap.get("hasQuestionTemplate"));
				KBObject pattern = kb.getPropertyValue(question, pmap.get("hasQuestionPattern"));
				ArrayList<KBObject> variables = kb.getPropertyValues(question, pmap.get("hasQuestionVariable"));

				if (name != null && template != null && pattern != null) {
					List<QuestionVariable> vars = null;
					
					if (variables != null && variables.size() > 0) {
						vars = new ArrayList<QuestionVariable>();
						for (KBObject var : variables) {
							KBObject vname = kb.getPropertyValue(var, pmap.get("hasVariableName"));
							KBObject vconstraints = kb.getPropertyValue(var, pmap.get("hasConstraints"));
							KBObject vfixedOptions = kb.getPropertyValue(var, pmap.get("hasFixedOptions"));
							if (vname != null) {
								QuestionVariable q = new QuestionVariable(var.getID(), vname.getValueAsString(), 
										vconstraints == null ? null : vconstraints.getValueAsString());
								if (vfixedOptions != null) {
									q.setFixedOptions(vfixedOptions.getValueAsString().split(","));
								}
								vars.add(q);
							}
						}
					}

					Question q = new Question(question.getID(), name.getValueAsString(), template.getValueAsString(), pattern.getValueAsString(), vars);
					all.add(q);
					//System.out.println(q.toString());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			this.end();
		}
		return all;
	}

	private Map<String, List<List<String>>> optionsCache = new WeakHashMap<String, List<List<String>>>();
	public List<List<String>> listVariableOptions (String sid) {
		if (!optionsCache.containsKey(sid)) {
			optionsCache.put(sid, this.loadVariableOptions(sid));
		}
		return optionsCache.get(sid);
	}

	private List<List<String>> loadVariableOptions (String sid) {
		String id = "http://disk-project.org/resources/question/" + sid;
		List<List<String>> options = new ArrayList<List<String>>();
		String query = null;
		KBAPI kb = this.questionkb;
		if (kb != null) try {
			this.start_read();
			KBObject qvar = kb.getIndividual(id);
			KBObject qname = qvar == null ? null : kb.getPropertyValue(qvar, pmap.get("hasVariableName"));
			if (qvar != null && qname != null) {
				String varname = qname.getValueAsString();
				String name = varname.substring(1);
				KBObject constraints = kb.getPropertyValue(qvar, pmap.get("hasConstraints"));
				KBObject fixedOptions = kb.getPropertyValue(qvar, pmap.get("hasFixedOptions"));
				if (fixedOptions != null) {
					String[] fixedoptions = fixedOptions.getValueAsString().split(",");
					for (String val: fixedoptions) {
						List<String> opt = new ArrayList<String>();
						opt.add(val);
						opt.add(val);
						options.add(opt);
					}
				} else if (constraints != null) {
					query = this.getAllPrefixes() + 
							"SELECT DISTINCT ?label " + varname + " WHERE {\n  " +
							constraints.getValueAsString() + "\n  " +
							"OPTIONAL { " + varname + " rdfs:label ?label . }\n  }";
					System.out.println(query);
					List<List<SparqlQuerySolution>> solutions = queryAllEndpoints(kb, query);
					for (List<SparqlQuerySolution> dataSolutions : solutions) {
						String url = null;
						String label = null;
						for (SparqlQuerySolution solution : dataSolutions) {
							String var = solution.getVariable();
							KBObject obj = solution.getObject();
							if (obj != null) {
								String val = obj.isLiteral() ? obj.getValueAsString() : obj.getID();
								if (var.equals(name)) {
									url = val;
								} else if (var.equals("?label")) {
									label = val;
								}
							}
						}
						if (url != null) {
							if (label == null) label = url;
							List<String> opt = new ArrayList<String>();
							opt.add(url);
							opt.add(label);
							options.add(opt);
						}
					}
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			this.end();
		}
		System.out.println("options: " + options.size());
		return options;
	}
	
	private Map<String, Map<String,String>> getEndpoints () {
		PropertyListConfiguration cfg = this.getConfig();
		Map<String, Map<String,String>> endpoints = new HashMap<String, Map<String,String>>();
		Iterator<String> a = cfg.getKeys("endpoints");
		while (a.hasNext()) {
			String key = a.next();
			String sp[] = key.split("\\.");
			if (sp != null && sp.length == 3) {
				Map<String, String> map;
				if (endpoints.containsKey(sp[1]))
					map = endpoints.get(sp[1]);
				else {
					map = new HashMap<String, String>();
					endpoints.put(sp[1], map);
				}
				map.put(sp[2], cfg.getProperty(key).toString());
			}
		}
		return endpoints;
	}
	
	private List<List<SparqlQuerySolution>> queryAllEndpoints (KBAPI kb, String query) {
		List<List<SparqlQuerySolution>> allSolutions = new ArrayList<List<SparqlQuerySolution>>();
		
		Map<String, Map<String, String>> allEndpoints = this.getEndpoints();
		
		for (String key: allEndpoints.keySet()) {
			String uri = allEndpoints.get(key).get("URI");
			String user = allEndpoints.get(key).get("username");
			String pass = allEndpoints.get(key).get("password");
			try {
			ArrayList<ArrayList<SparqlQuerySolution>> solutions = 
					kb.sparqlQueryRemote(query, uri, user, pass);
			if (solutions != null) {
				allSolutions.addAll(solutions);
			}
				
			} catch (Exception e) {
				System.out.println("ERROR querying " + uri);
				e.printStackTrace(); 
			}
		}
		return allSolutions;
	}

	/**
	 * Method that will download an ontology given its URI, doing content
	 * negotiation The ontology will be downloaded in the first serialization
	 * available (see Constants.POSSIBLE_VOCAB_SERIALIZATIONS)
	 * 
	 * @param uri          the URI of the ontology
	 * @param downloadPath path where the ontology will be saved locally.
	 * 
	 *                     We must use this for the neuro ontology because KBAPIJena
	 *                     is not able to read the given neuro ontology
	 * 
	 */
	public static void downloadOntology(String uri, String downloadPath) {
		String[] POSSIBLE_VOCAB_SERIALIZATIONS = new String[] { "application/rdf+xml", "text/turtle", "text/n3" };
		for (String serialization : POSSIBLE_VOCAB_SERIALIZATIONS) {
			System.out.println("Attempting to download vocabulary in " + serialization);
			try {
				URL url = new URL(uri);
				HttpURLConnection connection = (HttpURLConnection) url.openConnection();
				connection.setRequestMethod("GET");
				connection.setInstanceFollowRedirects(true);
				connection.setRequestProperty("Accept", serialization);
				int status = connection.getResponseCode();
				boolean redirect = false;
				if (status != HttpURLConnection.HTTP_OK) {
					if (status == HttpURLConnection.HTTP_MOVED_TEMP || status == HttpURLConnection.HTTP_MOVED_PERM
							|| status == HttpURLConnection.HTTP_SEE_OTHER)
						redirect = true;
				}
				// there are some vocabularies with multiple redirections:
				// 301 -> 303 -> owl
				while (redirect) {
					String newUrl = connection.getHeaderField("Location");
					connection = (HttpURLConnection) new URL(newUrl).openConnection();
					connection.setRequestProperty("Accept", serialization);
					status = connection.getResponseCode();
					if (status != HttpURLConnection.HTTP_MOVED_TEMP && status != HttpURLConnection.HTTP_MOVED_PERM
							&& status != HttpURLConnection.HTTP_SEE_OTHER)
						redirect = false;
				}
				InputStream in = (InputStream) connection.getInputStream();
				Files.copy(in, Paths.get(downloadPath), StandardCopyOption.REPLACE_EXISTING);
				in.close();
				break; // if the vocabulary is downloaded, then we don't
						// download it for the other serializations
			} catch (Exception e) {
				e.printStackTrace();
				System.err.println("Failed to download vocabulary in " + serialization);
			}
		}
	}

	public PropertyListConfiguration getConfig() {
	    return Config.get().getProperties();
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
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			this.start_read();
			return this.initializeVocabularyFromKB(kb, url + "#");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
      this.end();
		}
		return null;
	}

	public Vocabulary initializeVocabularyFromKB(KBAPI kb, String ns) {
		Vocabulary vocabulary = new Vocabulary(ns);
		try {
			this.fetchTypesAndIndividualsFromKB(kb, vocabulary); // Here
			this.fetchPropertiesFromKB(kb, vocabulary);
			return vocabulary;
		} catch (Exception e) {
		  //FIXME: this is weird.
			e.printStackTrace();
			vocabulary = new Vocabulary(ns);
			this.fetchTypesAndIndividualsFromKB(kb, vocabulary); // Here
			this.fetchPropertiesFromKB(kb, vocabulary);
			return vocabulary;
		}
	}

	private void fetchPropertiesFromKB(KBAPI kb, Vocabulary vocabulary) {
		for (KBObject prop : kb.getAllProperties()) {
			if (!prop.getID().startsWith(vocabulary.getNamespace()))
				continue;

			KBObject domcls = kb.getPropertyDomain(prop);
			KBObject rangecls = kb.getPropertyRange(prop);

			Property mprop = new Property();
			mprop.setId(prop.getID());
			mprop.setName(prop.getName());

			String label = this.createPropertyLabel(prop.getName());
			mprop.setLabel(label);

			if (domcls != null)
				mprop.setDomain(domcls.getID());

			if (rangecls != null)
				mprop.setRange(rangecls.getID());

			vocabulary.addProperty(mprop);
		}
	}

	private void fetchTypesAndIndividualsFromKB(KBAPI kb, Vocabulary vocabulary) {
		try {
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");
			for (KBTriple t : kb.genericTripleQuery(null, typeprop, null)) {
				KBObject inst = t.getSubject();
				KBObject typeobj = t.getObject();
				try {
					if (!inst.getID().startsWith(vocabulary.getNamespace())) // Null
			// Pointer
																				// Exception
						continue;
					if (typeobj.getNamespace().equals(KBConstants.OWLNS()))
						continue;
					// Add individual
					//FIXME: this does not ADD individuals without type.
					Individual ind = new Individual();
					ind.setId(inst.getID());
					ind.setName(inst.getName());
					ind.setType(typeobj.getID());
					String label = kb.getLabel(inst);
					if (label == null)
						label = inst.getName();
					ind.setLabel(label);
					vocabulary.addIndividual(ind);

					// Add asserted types
					if (!typeobj.getID().startsWith(vocabulary.getNamespace()))
						continue;
					String clsid = typeobj.getID();
					Type type = new Type();
					type.setId(clsid);
					type.setName(typeobj.getName());
					type.setLabel(kb.getLabel(typeobj));
					vocabulary.addType(type);
				} catch (Exception e) {
					// Verified that exception is not abnormal
					// e.printStackTrace();
				}
			}
			// Add types not asserted
			KBObject clsobj = kb.getProperty(KBConstants.OWLNS() + "Class");

			for (KBTriple t : kb.genericTripleQuery(null, typeprop, clsobj)) {
				KBObject cls = t.getSubject();
				try {
					if (!cls.getID().startsWith(vocabulary.getNamespace()))
						continue;
					if (cls.getNamespace().equals(KBConstants.OWLNS()))
						continue;

					String clsid = cls.getID();
					Type type = vocabulary.getType(clsid);
					if (type == null) {
						type = new Type();
						type.setId(clsid);
						type.setName(cls.getName());
						type.setLabel(kb.getLabel(cls));
						vocabulary.addType(type);
					}
				} catch (Exception e) {
					if (vocabulary.getNamespace().indexOf("neuro") != -1) {
						e.printStackTrace();
					}
				}
			}

			// Add type hierarchy
			KBObject subclsprop = kb.getProperty(KBConstants.RDFSNS() + "subClassOf");
			for (KBTriple t : kb.genericTripleQuery(null, subclsprop, null)) {
				KBObject subcls = t.getSubject();
				KBObject cls = t.getObject();
				String clsid = cls.getID();

				Type subtype = vocabulary.getType(subcls.getID());
				if (subtype == null)
					continue;

				if (!clsid.startsWith(KBConstants.OWLNS()))
					subtype.setParent(clsid);

				Type type = vocabulary.getType(cls.getID());
				if (type != null && subtype.getId().startsWith(vocabulary.getNamespace())) {
					type.addChild(subtype.getId());
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private String createPropertyLabel(String pname) {
		// Remove starting "has"
		pname = pname.replaceAll("^has", "");
		// Convert camel case to spaced human readable string
		pname = pname.replaceAll(String.format("%s|%s|%s", "(?<=[A-Z])(?=[A-Z][a-z])", "(?<=[^A-Z])(?=[A-Z])",
				"(?<=[A-Za-z])(?=[^A-Za-z])"), " ");
		// Make first letter upper case
		return pname.substring(0, 1).toUpperCase() + pname.substring(1);
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
			
			this.start_read();
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");
			for (KBTriple t : kb.genericTripleQuery(null, typeprop, hypcls)) {
				KBObject hypobj = t.getSubject();
				String name = kb.getLabel(hypobj);
				String description = kb.getComment(hypobj);

				String parentid = null;
				KBObject parentobj = kb.getPropertyValue(hypobj, pmap.get("hasParentHypothesis"));
				if (parentobj != null)
					parentid = parentobj.getName();
				String DateCreated = null;
				KBObject dateobj = kb.getPropertyValue(hypobj, pmap.get("dateCreated"));
				if (dateobj != null)
					DateCreated = dateobj.getValueAsString();

				String dateModified = null;
				KBObject dateModifiedObj = kb.getPropertyValue(hypobj, pmap.get("dateModified"));
				if (dateModifiedObj != null)
					dateModified = dateModifiedObj.getValueAsString();

				String author = null;
				KBObject authorobj = kb.getPropertyValue(hypobj, pmap.get("hasAuthor"));
				if (authorobj != null)
					author = authorobj.getValueAsString();
        
				TreeItem item = new TreeItem(hypobj.getName(), name, description, parentid, DateCreated, author); //TODO
				if (dateModified != null) {
					item.setDateModified(dateModified);
				}
				list.add(item);
			}
		} catch (ConcurrentModificationException e) {
		   System.out.println("ERROR: Concurrent modification exception on listHypothesis");
		   //e.printStackTrace();
		   return null;
		} catch (Exception e) {
			e.printStackTrace();
			
		} finally {
		  this.end();
		}
		return list;
	}

	public Hypothesis getHypothesis(String username, String domain, String id) {
		String url = this.HYPURI(username, domain);
		String fullid = url + "/" + id;
		String provid = fullid + "/provenance";
		System.out.println("GET Hypothesis: " + fullid);

		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBAPI provkb = this.fac.getKB(provid, OntSpec.PLAIN, true);

			this.start_read();
			
			KBObject hypitem = kb.getIndividual(fullid);
			Graph graph = this.getKBGraph(fullid);
			if (hypitem == null || graph == null)
				return null;

			Hypothesis hypothesis = new Hypothesis();
			hypothesis.setId(id);
			hypothesis.setName(kb.getLabel(hypitem));
			hypothesis.setDescription(kb.getComment(hypitem));
			hypothesis.setGraph(graph);

			KBObject parentobj = kb.getPropertyValue(hypitem, pmap.get("hasParentHypothesis"));
			if (parentobj != null)
				hypothesis.setParentId(parentobj.getName());

			KBObject dateobj = kb.getPropertyValue(hypitem, pmap.get("dateCreated"));
			if (dateobj != null)
				hypothesis.setDateCreated(dateobj.getValueAsString());

			KBObject dateModifiedObj = kb.getPropertyValue(hypitem, pmap.get("dateModified"));
			if (dateModifiedObj != null)
				hypothesis.setDateModified(dateModifiedObj.getValueAsString());

			KBObject authorobj = kb.getPropertyValue(hypitem, pmap.get("hasAuthor"));
			if (authorobj != null)
				hypothesis.setAuthor(authorobj.getValueAsString());

			KBObject notesobj = kb.getPropertyValue(hypitem, pmap.get("hasUsageNotes"));
			if (notesobj != null)
				hypothesis.setNotes(notesobj.getValueAsString());

			KBObject questionobj = kb.getPropertyValue(hypitem, pmap.get("hasQuestion"));
			if (questionobj != null)
				hypothesis.setQuestion(questionobj.getValueAsString());

			ArrayList<KBObject> questionBindings = kb.getPropertyValues(hypitem, pmap.get("hasVariableBinding"));
			if (questionBindings != null) {
				List<VariableBinding> variableBindings = new ArrayList<VariableBinding>();
				for (KBObject binding: questionBindings) {
					KBObject kbvar = kb.getPropertyValue(binding, pmap.get("hasVariable"));
					KBObject kbval = kb.getPropertyValue(binding, pmap.get("hasBindingValue"));
					if (kbvar != null && kbval != null) {
						String var = kbvar.getValueAsString();
						String val = kbval.getValueAsString();
						variableBindings.add( new VariableBinding(var, val));
					}
				}
				if (variableBindings.size() > 0) hypothesis.setQuestionBindings(variableBindings);
			}

			this.updateTripleDetails(graph, provkb); //WHY?

			return hypothesis;

		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return null;
	}

	// For provenance on revised hypotheses
	private Graph updateTripleDetails(Graph graph, KBAPI provkb) {
		HashMap<String, Triple> tripleMap = new HashMap<String, Triple>();
		for (Triple t : graph.getTriples())
			tripleMap.put(t.toString(), t);

		KBObject subprop = provkb.getProperty(KBConstants.RDFNS() + "subject");
		KBObject predprop = provkb.getProperty(KBConstants.RDFNS() + "predicate");
		KBObject objprop = provkb.getProperty(KBConstants.RDFNS() + "object");

		for (KBTriple kbt : provkb.genericTripleQuery(null, subprop, null)) {
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
			if (tripleMap.containsKey(triplestr)) {
				Triple t = tripleMap.get(triplestr);

				KBObject conf = provkb.getPropertyValue(stobj, pmap.get("hasConfidenceValue"));
				KBObject tloi = provkb.getPropertyValue(stobj, pmap.get("hasTriggeredLineOfInquiry"));

				TripleDetails details = new TripleDetails();
				if (conf != null && conf.getValue() != null)
					details.setConfidenceValue((Double) conf.getValue());
				if (tloi != null)
					details.setTriggeredLOI(tloi.getID());

				t.setDetails(details);
			}
		}
		return graph;
	}

	public Hypothesis updateHypothesis(String username, String domain, String id, Hypothesis hypothesis) {
		if (hypothesis.getId() == null)
			return null;

		if (this.deleteHypothesis(username, domain, id) && this.addHypothesis(username, domain, hypothesis))
			return hypothesis;
		return null;
	}

	public boolean deleteHypothesis(String username, String domain, String id) {
		if (id == null)
			return false;

		String url = this.HYPURI(username, domain);
		String fullid = url + "/" + id;
		String provid = fullid + "/provenance";

		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, false);
			KBAPI hypkb = this.fac.getKB(fullid, OntSpec.PLAIN, false);
			KBAPI provkb = this.fac.getKB(provid, OntSpec.PLAIN, false);

			if (kb != null && hypkb != null && provkb != null) {
				this.start_read();
	      
				KBObject hypitem = kb.getIndividual(fullid);
				if (hypitem != null) {
					for (KBTriple t : kb.genericTripleQuery(null, pmap.get("hasParentHypothesis"), hypitem)) {
						this.deleteHypothesis(username, domain, t.getSubject().getName());
					}
					this.end();
					this.start_write();
					
					ArrayList<KBObject> questionBindings = kb.getPropertyValues(hypitem, pmap.get("hasVariableBinding"));
					if (questionBindings != null) for (KBObject binding: questionBindings) {
						kb.deleteObject(binding, true, true);
					}
					
					kb.deleteObject(hypitem, true, true);
					
					this.save(kb);
					this.end();
				}
				return this.start_write() && hypkb.delete() && this.save(hypkb) && this.end() && 
				    this.start_write() && provkb.delete() && this.save(provkb) && this.end();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return false;
	}

	public boolean addHypothesis(String username, String domain, Hypothesis hypothesis) {
		if (hypothesis.getId() == null)
			return false;

		String url = this.HYPURI(username, domain);
		String fullid = url + "/" + hypothesis.getId();
		String provid = fullid + "/provenance";

		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBAPI hypkb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
			KBAPI provkb = this.fac.getKB(provid, OntSpec.PLAIN, true);

			this.start_write();
			
			KBObject hypitem = kb.createObjectOfClass(fullid, this.cmap.get("Hypothesis"));
			if (hypothesis.getName() != null)
				kb.setLabel(hypitem, hypothesis.getName());
			if (hypothesis.getDescription() != null)
				kb.setComment(hypitem, hypothesis.getDescription());
			if (hypothesis.getParentId() != null) {
				String fullparentid = url + "/" + hypothesis.getParentId();
				kb.setPropertyValue(hypitem, pmap.get("hasParentHypothesis"), kb.getResource(fullparentid));
			}
			if (hypothesis.getDateCreated() != null) {
				kb.setPropertyValue(hypitem, pmap.get("dateCreated"), provkb.createLiteral(hypothesis.getDateCreated()));
			}
			if (hypothesis.getDateModified() != null) {
				kb.setPropertyValue(hypitem, pmap.get("dateModified"), provkb.createLiteral(hypothesis.getDateModified()));
			}
			if (hypothesis.getAuthor() != null) {
				kb.setPropertyValue(hypitem, pmap.get("hasAuthor"), provkb.createLiteral(hypothesis.getAuthor()));
			}
			if (hypothesis.getNotes() != null) {
				kb.setPropertyValue(hypitem, pmap.get("hasUsageNotes"), hypkb.createLiteral(hypothesis.getNotes()));
			}
			if (hypothesis.getQuestion() != null) {
				kb.setPropertyValue(hypitem, pmap.get("hasQuestion"), hypkb.createLiteral(hypothesis.getQuestion()));
			}
			List<VariableBinding> questionBindings = hypothesis.getQuestionBindings();
			if (questionBindings != null) {
				for (VariableBinding vb: questionBindings) {
					String ID = fullid + "/bindings/";
					String[] sp = vb.getVariable().split("/");
					ID += sp[sp.length-1];
					System.out.println("varBindingId: " + ID);
					KBObject binding = kb.createObjectOfClass(ID, this.cmap.get("VariableBinding"));
					kb.setPropertyValue(binding, pmap.get("hasVariable"), hypkb.createLiteral(vb.getVariable()));
					kb.setPropertyValue(binding, pmap.get("hasBindingValue"), hypkb.createLiteral(vb.getBinding()));
					kb.addPropertyValue(hypitem, pmap.get("hasVariableBinding"), binding);
				}
			}

			this.save(kb);
			this.end();

			this.start_write();
			for (Triple triple : hypothesis.getGraph().getTriples()) {
				KBTriple t = this.getKBTriple(triple, hypkb);
				if (t != null)
					hypkb.addTriple(t);
			}
			this.save(hypkb);
			this.end();

			this.start_write();
			for (Triple triple : hypothesis.getGraph().getTriples()) {
				// Add triple details (confidence value, provenance, etc)
				this.storeTripleDetails(triple, provid, provkb);
			}
			this.save(provkb);
      this.end();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return true;
	}

	private void storeTripleDetails(Triple triple, String provid, KBAPI provkb) {
		TripleDetails details = triple.getDetails();
		if (details != null) {
			KBObject stobj = provkb.getResource(provid + "#" + GUID.randomId("Statement"));
			this.setKBStatement(triple, provkb, stobj);

			if (details.getConfidenceValue() > 0)
				provkb.setPropertyValue(stobj, pmap.get("hasConfidenceValue"),
						provkb.createLiteral(triple.getDetails().getConfidenceValue()));
			if (details.getTriggeredLOI() != null)
				provkb.setPropertyValue(stobj, pmap.get("hasTriggeredLineOfInquiry"),
						provkb.getResource(triple.getDetails().getTriggeredLOI()));
		}
	}

	private String getQueryBindings(String queryPattern, Pattern variablePattern,
			Map<String, String> variableBindings) {
		String pattern = "";
		for (String line : queryPattern.split("\\n")) {
			line = line.trim();
			if (line.equals(""))
				continue;
			if (variableBindings != null) {
				Matcher mat = variablePattern.matcher(line);
				int diff = 0;
				while (mat.find()) {
					if (variableBindings.containsKey(mat.group(1))) {
						String varbinding = variableBindings.get(mat.group(1));
						int st = mat.start(1);
						int end = mat.end(1);
						line = line.substring(0, st - 1 + diff) + varbinding + line.substring(end + diff);
						diff += varbinding.length() - (end - st) - 1;
					}
				}
			}
			if (line.contains("?")) {
				// Only have relevant lines in query containing variables
				pattern += line;
				if (!line.matches(".+\\.\\s*$"))
					pattern += " .";
				pattern += "\n";
			}
		}
		return pattern;
	}

	private String filterQueryBindings(String queryPattern, String namespace) {
		String pattern = "";
		Map<String, Boolean> lineExists = new HashMap<String, Boolean>();

		for (String line : queryPattern.split("\\n")) {
			line = line.trim();
			if (lineExists.containsKey(line))
				continue;
			if (line.equals(""))
				continue;
			if (line.matches(".*\\s+" + namespace + ".*"))
				continue;
			if (line.matches("^" + namespace + ".*"))
				continue;
			lineExists.put(line, true);
			pattern += line + "\n";
		}
		return pattern;
	}

	private String addQueryAssertions (String queryPattern, String assertionUri) {
	  Pattern ASSERTION_PATTERN = Pattern.compile("(user:[^\\s]+)");
  	  String extra = "";
	  try {
  		this.start_read();
  		Graph graph = this.getKBGraph(assertionUri);
  		Matcher m = ASSERTION_PATTERN.matcher(queryPattern);
		String aURL = assertionUri + "#";
  		while (m.find()) {
  		    String s = m.group(1);
  		    String id = s.replace("user:", aURL);
  			for (Triple t: graph.getTriplesForSubject(id)) {
  			  String o = t.getObject().toString();
  			  String p = t.getPredicate().toString();
  			  if (o != null && p != null && !p.contains("rdf-syntax-ns#type")) { //TODO: do not add types.
  				extra += s + " <" + p + "> " + o + " .\n";
  				System.out.println("+ " +s + " " + p + " " + o);
  			  }
  			}
  		}
	  }
	  finally {
	    this.end();
	  }
	  return queryPattern + extra;
	}
	
	private String getAllPrefixes () {
		return "PREFIX bio: <" + KBConstants.OMICSNS() + ">\n" +
				"PREFIX neuro: <" + KBConstants.NEURONS() + ">\n" +
				"PREFIX hyp: <" + KBConstants.HYPNS() + ">\n" +
				"PREFIX xsd: <" + KBConstants.XSDNS() + ">\n" +
				"PREFIX rdfs: <" + KBConstants.RDFSNS() + ">\n" +
				"PREFIX rdf: <" + KBConstants.RDFNS() + ">\n" +
				"PREFIX disk: <" + KBConstants.DISKNS() + ">\n";
	}
	
	private String getSparqlQuery(String queryPattern, String assertionsUri) {

		return "PREFIX bio: <" + KBConstants.OMICSNS() + ">\n" + "PREFIX neuro: <" + KBConstants.NEURONS() + ">\n"
				+ "PREFIX hyp: <" + KBConstants.HYPNS() + ">\n" + "PREFIX xsd: <" + KBConstants.XSDNS() + ">\n"
				+ "PREFIX rdfs: <" + KBConstants.RDFSNS() + ">\n" + "PREFIX rdf: <" + KBConstants.RDFNS() + ">\n"				
				+ "PREFIX disk: <" + KBConstants.DISKNS() + ">\n"
				+ "PREFIX user: <" + assertionsUri + "#>\n\n" + "SELECT *\n" + "WHERE { \n" + queryPattern + "}\n";
	}

	private String getDistinctSparqlQuery(String queryPattern, String assertionsUri, List<String> variables) {
		return "PREFIX bio: <" + KBConstants.OMICSNS() + ">\n" + "PREFIX neuro: <" + KBConstants.NEURONS() + ">\n"
				+ "PREFIX hyp: <" + KBConstants.HYPNS() + ">\n" + "PREFIX xsd: <" + KBConstants.XSDNS() + ">\n"
				+ "PREFIX rdfs: <" + KBConstants.RDFSNS() + ">\n" + "PREFIX rdf: <" + KBConstants.RDFNS() + ">\n"				
				+ "PREFIX disk: <" + KBConstants.DISKNS() + ">\n" + "PREFIX user: <" + assertionsUri + "#>\n\n"
				+ "SELECT DISTINCT " + String.join(" ", variables)
				+ "\nWHERE { \n" + queryPattern + "}\n";
	}

	private String getDistinctSparqlQuery(String queryPattern, String assertionsUri) {
		return "PREFIX bio: <" + KBConstants.OMICSNS() + ">\n" + "PREFIX neuro: <" + KBConstants.NEURONS() + ">\n"
				+ "PREFIX hyp: <" + KBConstants.HYPNS() + ">\n" + "PREFIX xsd: <" + KBConstants.XSDNS() + ">\n"
				+ "PREFIX rdfs: <" + KBConstants.RDFSNS() + ">\n" + "PREFIX rdf: <" + KBConstants.RDFNS() + ">\n"				
				+ "PREFIX disk: <" + KBConstants.DISKNS() + ">\n"
				+ "PREFIX user: <" + assertionsUri + "#>\n\n" + "SELECT DISTINCT * \nWHERE { \n" + queryPattern + "}\n";
	}

	public Map<String, List<String>> queryExternalStore(String username, String domain, String sparqlQuery, String variables) {
		//TODO: This should have and endpoint.
		//List<List<List<String>>> result = new ArrayList<List<List<String>>>();
		String endpoint = null;
		Map<String, List<String>> dataVarBindings = new HashMap<String, List<String>>();
		
		//TODO, should check the variables.
		String v = (variables == null || variables.contentEquals("")) ? "*" : variables;
		
		String dataQuery = "PREFIX bio: <http://disk-project.org/ontology/omics#>\n" + 
				"PREFIX neuro: <https://w3id.org/disk/ontology/enigma_hypothesis#>\n" + 
				"PREFIX hyp: <http://disk-project.org/ontology/hypothesis#>\n" + 
				"PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n" + 
				"PREFIX rdfs: <http://www.w3.org/2000/01/rdf-schema#>\n" + 
				"PREFIX rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>\n" +
				"SELECT DISTINCT " + v + " WHERE {\n" +
				sparqlQuery + "\n} LIMIT 30";
		
		//System.out.println(dataQuery);

		try {
			this.start_read();
			KBAPI queryKb = this.fac.getKB(OntSpec.PLAIN);
			queryKb.importFrom(this.omicsontkb);
			queryKb.importFrom(this.neuroontkb);
			queryKb.importFrom(this.hypontkb);
			this.end();
			
			ArrayList<ArrayList<SparqlQuerySolution>> allDataSolutions = null;
			boolean wikiStore = Config.get().getProperties().containsKey("data-store");
			if(wikiStore) {
			  String externalStore = endpoint != null ? endpoint :
					  Config.get().getProperties().getString("data-store");
			  String dataUser = Config.get().getProperties().getString("ENIGMA.username");
			  String dataPass = Config.get().getProperties().getString("ENIGMA.password");
			  if (dataUser != null && dataPass != null) {
				  //Data store is password protected.
				  allDataSolutions = queryKb.sparqlQueryRemote(dataQuery, externalStore, dataUser, dataPass);
			  } else {
				  allDataSolutions = queryKb.sparqlQueryRemote(dataQuery, externalStore);
			  }
			} else {
			  allDataSolutions = queryKb.sparqlQuery(dataQuery);
			}

			// Store solutions in dataVarBindings
			for (ArrayList<SparqlQuerySolution> dataSolutions : allDataSolutions) {
			  for (SparqlQuerySolution solution : dataSolutions) {
			    String var = solution.getVariable();
			    List<String> curValues = dataVarBindings.containsKey(var) ?
			        dataVarBindings.get(var) : new ArrayList<String>();
			    if (solution.getObject().isLiteral()) {
			      curValues.add(solution.getObject().getValueAsString());
			    } else {
			      curValues.add(wikiStore ? solution.getObject().getID() : solution.getObject().getName());
			    }
			    dataVarBindings.put(var, curValues);
			  }
			}

			System.out.println("DONE - " + allDataSolutions.size() + " solutions found.");
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		
		return dataVarBindings;
	}

  public Set<String> interceptVariables (String queryA, String queryB) {
    Set<String> A = new HashSet<String>();
    Matcher a = varPattern.matcher(queryA);
    while (a.find()) A.add(a.group());
    
    Set<String> B = new HashSet<String>();
    Matcher b = varPattern.matcher(queryB);
    while (b.find()) {
      String v = b.group();
      for (String v2: A) {
        if (v.equals(v2)) {
          B.add(v);
        }
      }
    }
    return B;
  }

  public Map<LineOfInquiry, List<Map<String, String>>> getHypothesisMatchingLOIs (String username, String domain, String hypId) {
    Map<LineOfInquiry, List<Map<String, String>>> matches = new HashMap<LineOfInquiry, List<Map<String,String>>>();
    
    String hypuri = this.HYPURI(username, domain) + "/" + hypId;
    String assertions = this.ASSERTIONSURI(username, domain);

    //Hypothesis hypothesis = this.getHypothesis(username, domain, hypId);

    Map<String, String> nsmap = new HashMap<String, String>();
    nsmap.put(KBConstants.OMICSNS(), "bio:");
    nsmap.put(KBConstants.NEURONS(), "neuro:");
    nsmap.put(KBConstants.HYPNS(), "hyp:");
    nsmap.put(KBConstants.XSDNS(), "xsd:");
    nsmap.put(assertions + "#", "user:");
    nsmap.put(hypuri + "#", "?");

    try {
      // Union of kbs
      this.start_read();
      KBAPI queryKb = this.fac.getKB(OntSpec.PLAIN);
      KBAPI hypkb = this.fac.getKB(hypuri, OntSpec.PLAIN);
      KBAPI userkb = this.fac.getKB(assertions, OntSpec.PLAIN);
      queryKb.importFrom(this.omicsontkb);
      queryKb.importFrom(this.neuroontkb);
      queryKb.importFrom(this.hypontkb);
      queryKb.importFrom(hypkb);
      queryKb.importFrom(userkb);
      this.end();

      for (TreeItem item : this.listLOIs(username, domain)) {
        //System.out.println("\nChecking LOI "+ item.getId());
        LineOfInquiry loi = this.getLOI(username, domain, item.getId());
        //Check hypothesis and data query
        String hypothesisQuery = loi.getHypothesisQuery();
        String dataQuery = loi.getDataQuery();

        if (hypothesisQuery == null || hypothesisQuery.equals("") || dataQuery == null || dataQuery.equals(""))
          continue;
        
        //Check variable bindings and parameters.
        Set<String> variables = loi.getAllWorkflowVariables();
        Set<String> parameters = loi.getAllWorkflowParameters();
        
        if (variables.size() == 0)
          continue;

        // Variable bindings must be present on the data query
        boolean ok = true;
        for (String var: variables) {
          if (!dataQuery.contains(var)) {
            ok = false;
            System.out.println(var + " is not in the query.");
            break;
          }
        }

        if (!ok) continue;
        
        // Check that there are results for the bindings.
        // This removes all lines that does not contains a variable.
        hypothesisQuery = this.getQueryBindings(hypothesisQuery, null, null);
        
        // Get all variables used on the data query that are assigned on the hypothesis.
        Set<String> setV = this.interceptVariables(hypothesisQuery, dataQuery);
        // Add all parameters
        for (String param: parameters) setV.add(param);
        if (setV.size() == 0) continue;
        List<String> usedVariables = new ArrayList<String>(setV);

        // Creating the query.
        String hypSparqlQuery = this.getDistinctSparqlQuery(hypothesisQuery, assertions, usedVariables);

        this.start_read();
        ArrayList<ArrayList<SparqlQuerySolution>> allSolutions = queryKb.sparqlQuery(hypSparqlQuery);
        if (allSolutions.size() == 0) continue;

        for (ArrayList<SparqlQuerySolution> hypothesisSolutions : allSolutions) {
          Map<String, String> hypVarBindings = new HashMap<String, String>();
          for (SparqlQuerySolution solution : hypothesisSolutions) {
            String value;
            if (solution == null) continue;
            String var = solution.getVariable();
            KBObject obj = solution.getObject();
            if (var == null || obj == null) continue;
            if (obj.isLiteral())
              value = '"' + obj.getValueAsString() + '"';
            else {
              String valns = obj.getNamespace();
              if (nsmap.containsKey(valns))
                value = nsmap.get(valns) + obj.getName();
              else
                value = "<" + obj.getID() + ">";
            }
            hypVarBindings.put(var, value);
          }
          // Check that all parameters are set.
          ok = true;
          for (String p: parameters) {
            if (p.charAt(0) == '?') p = p.substring(1);
            if (hypVarBindings.get(p) == null) {
              System.out.println(p + " has no value!");
              ok = false;
              break;
            }
          }
          if (!ok) continue;
          List<Map<String, String>> l = matches.get(loi);
          if (l == null) {
            l = new ArrayList<Map<String,String>>();
            matches.put(loi, l);
          }
          l.add(hypVarBindings);
        }
      }
    } catch (Exception e) {
       e.printStackTrace();
    } finally {
      this.end();
    }
    return matches;
  }

  public List<TriggeredLOI> queryHypothesis(String username, String domain, String id) {
		List<TriggeredLOI> tlois = new ArrayList<TriggeredLOI>();
		String hypuri = this.HYPURI(username, domain) + "/" + id;
		String assertions = this.ASSERTIONSURI(username, domain);

		Map<LineOfInquiry, List<Map<String, String>>> matches = this.getHypothesisMatchingLOIs(username, domain, id);
		if (matches.size() == 0) return tlois;

		try {
			this.start_read();
			KBAPI queryKb = this.fac.getKB(OntSpec.PLAIN);
			queryKb.importFrom(this.omicsontkb);
			queryKb.importFrom(this.neuroontkb);
			queryKb.importFrom(this.hypontkb);
			queryKb.importFrom(this.fac.getKB(hypuri, OntSpec.PLAIN));
			queryKb.importFrom(this.fac.getKB(assertions, OntSpec.PLAIN));
			
            //System.out.println("MATCHES:");
            for (LineOfInquiry loi: matches.keySet()) {
              List<Map<String, String>> results = matches.get(loi);
              //System.out.println(loi.getName() + ": " + results.size());
              for (Map<String, String> hypBinds: results) {
                // At least one binding must be non variable
                boolean ok = false;
                for (String key: hypBinds.keySet()) {
                  String val = hypBinds.get(key);
                  //System.out.println("R:" + key + " -> " + val);
                  if (val.charAt(0) != '?') ok = true;
                }
                if (!ok) continue;
                
                //Creating 1 data query for hypothesis binding solution;
                String dq = getQueryBindings(loi.getDataQuery(), varPattern, hypBinds);
                dq = this.addQueryAssertions(dq, assertions);
                dq = dq.replace("user:", "?");
                String dataQuery = this.getDistinctSparqlQuery(dq, assertions, new ArrayList<String>(loi.getAllWorkflowVariables()) );
                if (dataQuery.charAt(dataQuery.length()-1) == '\n') dataQuery = dataQuery.substring(0, dataQuery.length()-1);
                //System.out.println("binded: \n" + dataQuery + "\n");

                ArrayList<ArrayList<SparqlQuerySolution>> allDataSolutions = null;
                
                String dataSource = loi.getDataSource();
                if (dataSource != null && !dataSource.equals("")) {
                	Map<String, Map<String, String>> endpoints = this.getEndpoints();

                	String user = null;
                	String pass = null;

                	for (String key: endpoints.keySet()) {
                		if (endpoints.get(key).get("URI").equals(dataSource)) {
                			user = endpoints.get(key).get("username");
                			pass = endpoints.get(key).get("password");
                			break;
                		}
                	}
                	
                	if (user != null && pass != null) {
                		allDataSolutions = queryKb.sparqlQueryRemote(dataQuery, dataSource, user, pass);
                	} else {
                		allDataSolutions = queryKb.sparqlQueryRemote(dataQuery, dataSource);
                	}
                } else {
                  allDataSolutions = queryKb.sparqlQuery(dataQuery);
                }
                
                //System.out.println("Query done!! " + allDataSolutions.size());
                if (allDataSolutions.size() == 0) {
                	//System.out.println("No results on the external store for " + loi.getId());
                	continue;
                }

                // Store solutions in dataVarBindings
                Map<String, List<String>> dataVarBindings = new HashMap<String, List<String>>();
                for (ArrayList<SparqlQuerySolution> dataSolutions : allDataSolutions) {
                  for (SparqlQuerySolution solution : dataSolutions) {
                    String var = solution.getVariable();
                    List<String> curValues = dataVarBindings.containsKey(var) ? dataVarBindings.get(var) 
                        : new ArrayList<String>();

                    if (solution.getObject().isLiteral()) {
                      curValues.add(solution.getObject().getValueAsString());
                    }
                    else
                      curValues.add(dataSource != null && !dataSource.equals("") ? solution.getObject().getID() : solution.getObject().getName());
                    dataVarBindings.put(var, curValues);
                  }
                }

                //System.out.println("Results proceced");
                // Add the parameters
                for (String param: loi.getAllWorkflowParameters()) {
                   if (param.charAt(0) == '?') param = param.substring(1);
                   String bind = hypBinds.get(param);
                   if (bind != null) {
                     List<String> abind = new ArrayList<String>();
                     abind.add(bind);
                     dataVarBindings.put(param, abind);
                   }
                }
                
                // check collections
                Set<String> varNonCollection = loi.getAllWorkflowNonCollectionVariables();

                //System.out.println("dataBindings:");
                for (String key: dataVarBindings.keySet()) {
                  //System.out.println(" " + key + ":");
                  String var = (key.charAt(0) != '?') ? '?' + key : key;
                  if (varNonCollection.contains(var)) {
                    //System.out.println("  Is not a collection");
                    Set<String> fixed = new HashSet<String>(dataVarBindings.get(key));
                    dataVarBindings.put(key, new ArrayList<String>(fixed));
                  }
                  for (String r: dataVarBindings.get(key)) {
                    //System.out.println("  -  " + r);
                  }
                }

                TriggeredLOI tloi = new TriggeredLOI(loi, id);
                tloi.setWorkflows(
                    this.getTLOIBindings(username, domain, loi.getWorkflows(), dataVarBindings) //FIXME: the problem is here!
                    );
                tloi.setMetaWorkflows(
                    this.getTLOIBindings(username, domain, loi.getMetaWorkflows(), dataVarBindings));
                tloi.setDataQuery(dq);
                tloi.setRelevantVariables(loi.getRelevantVariables());
                tloi.setExplanation(loi.getExplanation());
                tlois.add(tloi);

                System.out.println(loi.getId() + " has created " + tloi.getId());
              }
            }
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		
		//return tlois;
		return checkExistingTLOIs(username, domain, tlois);
  }

  	// This replaces all triggered lines of inquiry already executed. tlois should be from the same hypothesis.
	private List<TriggeredLOI> checkExistingTLOIs(String username, String domain, List<TriggeredLOI> tlois) {
		List<TriggeredLOI> checked = new ArrayList<TriggeredLOI>();
		Map<String, List<TriggeredLOI>> cache = new HashMap<String, List<TriggeredLOI>>();
		for (TriggeredLOI tloi: tlois) {
			//System.out.println("Checking " + tloi.getId() + " (" + tloi.getLoiId() + ")");
			if (!cache.containsKey(tloi.getLoiId())) {
				cache.put(tloi.getLoiId(), getTLOIsForHypothesisAndLOI(username, domain, tloi.getParentHypothesisId(), tloi.getLoiId()));
			}
			List<TriggeredLOI> candidates = cache.get(tloi.getLoiId());
			TriggeredLOI real = tloi;
			for (TriggeredLOI cand: candidates) {
				if (cand.toString().equals(tloi.toString()) ) {
					//TODO: compare the hash of the input files
					System.out.println("Replaced " + tloi.getId() + " with " + cand.getId());
					real = cand;
					break;
				}
			}
			checked.add(real);
		}
		return checked;
	}

	@SuppressWarnings("unchecked")
  private List<WorkflowBindings> getTLOIBindings(
	    String username, String domain,
	    List<WorkflowBindings> wflowBindings, Map<String, List<String>> dataVarBindings) {
	  
	  List<WorkflowBindings> tloiBindings = new ArrayList<WorkflowBindings>();
	  
      for (WorkflowBindings bindings : wflowBindings) {

        // For each loi Workflow binding, create an empty tloi Binding
        WorkflowBindings tloiBinding = new WorkflowBindings(
             bindings.getWorkflow(),
             bindings.getWorkflowLink());
        tloiBinding.setMeta(bindings.getMeta());
        tloiBindings.add(tloiBinding);
        
        //System.out.println("adding parameters for " + bindings.getWorkflow());
        // Add parameters
        for (VariableBinding param: bindings.getParameters()) {
          String binding = param.getBinding();
          if (binding.charAt(0)=='?') binding = binding.substring(1);
          //System.out.println("param binding: " + binding);
          if (dataVarBindings.containsKey(binding)) {
        	  List<String> possibleBindings = dataVarBindings.get(binding);
        	  if (possibleBindings.size() == 1) {
        		  param.setBinding(possibleBindings.get(0));
        	  } else {
        		  System.out.println("more than one value for " + binding);
        	  }
          } else {
        	  System.out.println("dict does not contain " + binding);
          }
        }

        // Add optional parameters
        for (VariableBinding param: bindings.getOptionalParameters()) {
          String binding = param.getBinding();
          if (binding.charAt(0)=='?') binding = binding.substring(1);
          //System.out.println("param binding: " + binding);
          if (dataVarBindings.containsKey(binding)) {
        	  List<String> possibleBindings = dataVarBindings.get(binding);
        	  if (possibleBindings.size() == 1) {
        		  param.setBinding(possibleBindings.get(0));
        	  } else {
        		  System.out.println("more than one value for optional " + binding);
        	  }
          } else {
        	  System.out.println("dict does not contain optional " + binding);
          }
        }
        // CHECK ^
      
      for (VariableBinding vbinding : bindings.getBindings()) {
        // For each Variable binding, check :
        // - If this variable expects a collection or single values
        // - Check the binding values from the data store
        String binding = vbinding.getBinding();
        
        Matcher collmat = varCollPattern.matcher(binding);
        Matcher mat = varPattern.matcher(binding);
        
        // Check if this binding is meant for a collection
        // Also get the sparql variable
        boolean isCollection = false;
        String sparqlvar = null;
        if(collmat.find() && dataVarBindings.containsKey(collmat.group(1))) {
          sparqlvar = collmat.group(1);
          isCollection = true;
        }
        else if(mat.find() && dataVarBindings.containsKey(mat.group(1))) {
          sparqlvar = mat.group(1);
        }
        
        if(sparqlvar != null) {
          // Get the data bindings for the sparql variable
          List<String> dsurls = dataVarBindings.get(sparqlvar);
          
          // Register all datasets with Wings & get dataset names
          List<String> dsnames = new ArrayList<String>();
          for(String dsurl : dsurls) {
            String dsname = dsurl.replaceAll("^.*\\/", "");
            /*System.out.println("tick");
            WingsAdapter.get().addRemoteDataToWings(username, domain, dsurl);
            System.out.println("tock");*/
            dsnames.add(dsname);
          }
          
          // If Collection, all datasets go to same workflow
          if(isCollection) {
            // This variable expects a collection. Modify the existing tloiBinding values
            for(WorkflowBindings tmpBinding : tloiBindings) {
              tmpBinding.addBinding(new VariableBinding(
                  vbinding.getVariable(),
                  dsnames.toString()
              ));
            }
          }
          else {
            // This variable expects a single file. Add new tloi bindings for each dataset
            List<WorkflowBindings> newTloiBindings = new ArrayList<WorkflowBindings>();
            for(WorkflowBindings tmpBinding : tloiBindings) {
              for(String dsname: dsnames) {
                ArrayList<VariableBinding> newBindings = (ArrayList<VariableBinding>) SerializationUtils
                    .clone((Serializable) tmpBinding.getBindings());
                WorkflowBindings newWorkflowBindings = new WorkflowBindings(
                    bindings.getWorkflow(), 
                    bindings.getWorkflowLink(), 
                    newBindings,
                    bindings.getParameters(),
                    bindings.getOptionalParameters());
                newWorkflowBindings.addBinding(new VariableBinding(
                    vbinding.getVariable(),
                    dsname
                ));
                newWorkflowBindings.setMeta(bindings.getMeta());
                newTloiBindings.add(newWorkflowBindings);
              }
            }
            tloiBindings = newTloiBindings;
          }
        }
      }

      }
    return tloiBindings;
	}

	public Map<String, List<TriggeredLOI>> getHypothesisTLOIs (String username, String domain, String id) {
		Map<String, List<TriggeredLOI>> map = new HashMap<String, List<TriggeredLOI>>();

		String TLOIURI = this.TLOIURI(username, domain);
		String hypPrefix = this.HYPURI(username, domain);
		String hypURI = hypPrefix + "/" + id;
		try {
			this.start_read();
			KBAPI TLOIKB = this.fac.getKB(TLOIURI, OntSpec.PLAIN, true);
			KBObject hyp = TLOIKB.getResource(hypURI);

			for (KBTriple t : TLOIKB.genericTripleQuery(null, pmap.get("hasParentHypothesis"), hyp)) {
				KBObject obj = t.getSubject();
				String tloiid = obj.getID();
				String[] sp = tloiid.split("/");
				KBAPI tloiGraph = this.fac.getKB(TLOIURI + '/' + sp[sp.length-1], OntSpec.PLAIN, true);

				TriggeredLOI tloi = this.getTriggeredLOI(username, domain, tloiid, TLOIKB, tloiGraph);
				String loiId = tloi.getLoiId();
				if (!map.containsKey(loiId)) {
					map.put(loiId, new ArrayList<TriggeredLOI>());
				}
				map.get(loiId).add(tloi);
			}
		} catch (ConcurrentModificationException e) {
		   System.out.println("ERROR: Concurrent modification exception on listHypothesisTLOIs");
		   return null;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return map;
	}
	
	
	
	public Boolean runAllHypotheses (String username, String domain) {
		List<String> hlist = new ArrayList<String>();
		String url = this.HYPURI(username, domain);
		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBObject hypcls = this.cmap.get("Hypothesis");
			
			this.start_read();
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");
			for (KBTriple t : kb.genericTripleQuery(null, typeprop, hypcls)) {
				KBObject hypobj = t.getSubject();
				String uri = hypobj.getID();
				String[] sp = uri.split("/");
				hlist.add(sp[sp.length-1]);
				System.out.println("Hyp ID: " + sp[sp.length-1]);
			}
		} catch (ConcurrentModificationException e) {
		   System.out.println("Error trying to run all hypotheses. Could not read KB.");
			e.printStackTrace();
			return false;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		
		List<TriggeredLOI> tlist = new ArrayList<TriggeredLOI>();
		
		for (String hid: hlist) {
			tlist.addAll(queryHypothesis(username, domain, hid));
		}

		//Only hypotheses with status == null are new
		for (TriggeredLOI tloi: tlist) {
			if (tloi.getStatus() == null) {
				System.out.println("TLOI " + tloi.getId() + " will be trigger");
				addTriggeredLOI(username, domain, tloi);
			}
		}

		return true;
	}

	/**
	 * Assertions
	 */

	private KBObject getKBValue(Value v, KBAPI kb) {
		if (v.getType() == Value.Type.LITERAL) {
			if (v.getDatatype() != null)
				return kb.createXSDLiteral(v.getValue().toString(), v.getDatatype());
			else
				return kb.createLiteral(v.getValue());
		} else {
			return kb.getResource(v.getValue().toString());
		}
	}

	private KBTriple getKBTriple(Triple triple, KBAPI kb) {
		KBObject subj = kb.getResource(triple.getSubject());
		KBObject pred = kb.getResource(triple.getPredicate());
		KBObject obj = getKBValue(triple.getObject(), kb);

		if (subj != null && pred != null && obj != null)
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
		if (obj.isLiteral()) {
			Object valobj = obj.getValue();
			if (valobj instanceof Date) {
				valobj = dateformatter.format((Date) valobj);
			}
			if (valobj instanceof String) {
				//Fix quotes and \n
				valobj = ((String) valobj).replace("\"", "\\\"").replace("\n", "\\n");
			}
			v.setType(Value.Type.LITERAL);
			v.setValue(valobj);
			v.setDatatype(obj.getDataType());
		} else {
			v.setType(Value.Type.URI);
			v.setValue(obj.getID());
		}
		return v;
	}

	private Graph getKBGraph(String url) {
		try {
			Graph graph = new Graph();
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, false);
			if (kb == null)
				return null;
			for (KBTriple t : kb.genericTripleQuery(null, null, null)) {
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

	public void addQueries(String username, String domain, List<String> ToBeQueried) {
		try {

			String[] temp;
			for (int i = 0; i < ToBeQueried.size(); i++) {

				temp = DataQuery.queryFor(ToBeQueried.get(i).substring(ToBeQueried.get(i).indexOf(" ") + 1))[1]
						.split("\n\",\"\n");
				for (int j = 0; j < temp.length - 1; j += 2) {
					WingsAdapter.get().addOrUpdateData(username, domain, temp[j].substring(4),
							"/export/users/" + username + "/" + domain + "/data/ontology.owl#File", temp[j + 1], true);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public String[] addQuery(String username, String domain, String query, String type, boolean upload) {
		try {

			String[] temp = DataQuery.queryFor(query.substring(query.indexOf(" ") + 1))[1].split("\n\",\"\n");
			if (upload)
				for (int j = 0; j < temp.length - 1; j += 2) {
					WingsAdapter.get().addOrUpdateData(username, domain, temp[j].substring(4), type, temp[j + 1],
							false);
				}
			return temp;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public void addAssertion(String username, String domain, Graph assertion) {
		String url = this.ASSERTIONSURI(username, domain);
		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			this.start_write();
			for (Triple triple : assertion.getTriples()) {
				KBTriple t = this.getKBTriple(triple, kb);
				if (t != null)
					kb.addTriple(t);
			}
			this.save(kb); 
			this.end();

			// Re-run hypotheses if needed TODO
			//this.requeryHypotheses(username, domain);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Graph listAssertions(String username, String domain) {
	  try {
  		String url = this.ASSERTIONSURI(username, domain);
  		this.start_read();
  		return this.getKBGraph(url);
	  }
	  finally {
	    this.end();
	  }
	}

	public List<String> getQueriesToBeRun(Graph assertions) {
		List<Triple> UITriples = assertions.getTriples();
		HashSet<String> toQuery = new HashSet<String>();
		String temp;
		//String[] temp2;
		String file;
		String query;
		//Triple tri;
		for (int i = 0; i < UITriples.size(); i++) {
			temp = UITriples.get(i).getPredicate(); // check if asking for query
			if (temp.equals(KBConstants.NEURONS() + "hasEnigmaQueryLiteral")) {
				temp = UITriples.get(i).getSubject().toString();
				file = temp.substring(temp.indexOf("#") + 1);
				temp = UITriples.get(i).getObject().getValue().toString();
				query = temp.replace("|", "/");
				toQuery.add(file + " " + query);

			}
		}
		List<String> ToBeQueried = new ArrayList<String>();
		for (String strTemp : toQuery)
			ToBeQueried.add(strTemp);
		return ToBeQueried;
	}

	public void updateAssertions(String username, String domain, Graph assertions) {
		String url = this.ASSERTIONSURI(username, domain);
		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			this.start_write();
			if(kb.delete() && this.save(kb) && this.end()) {
			  this.addAssertion(username, domain, assertions);
			}
		} catch (Exception e) {
			e.printStackTrace();
			this.end();
		}
	}

	private void requeryHypotheses(String username, String domain) {
		// Cache already run/queries hypotheses (check tlois)
		HashMap<String, Boolean> tloikeys = new HashMap<String, Boolean>();
		List<String> bindkeys = new ArrayList<String>();
		HashMap<String, TriggeredLOI> tloimap = new HashMap<String, TriggeredLOI>();
		for (TriggeredLOI tloi : this.listTriggeredLOIs(username, domain)) {
			if (!tloi.getStatus().equals(TriggeredLOI.Status.FAILED)) {
				TriggeredLOI fulltloi = this.getTriggeredLOI(username, domain, tloi.getId());
				String key = fulltloi.toString();
				tloikeys.put(key, true);

				if (tloi.getStatus().equals(TriggeredLOI.Status.SUCCESSFUL)
						&& fulltloi.getResultingHypothesisIds().size() > 0) {
					String partkey = "";
					for (WorkflowBindings wb : fulltloi.getWorkflows())
						partkey += wb.toString();
					bindkeys.add(partkey);
					tloimap.put(partkey, fulltloi);
				}
			}
		}
		Collections.sort(bindkeys);
		// Get all Hypotheses
		for (TreeItem hypitem : this.listHypotheses(username, domain)) {
			// Run/Query only top-level hypotheses
			if (hypitem.getParentId() == null) {
				List<TriggeredLOI> tlois = this.queryHypothesis(username, domain, hypitem.getId());
				Collections.sort(tlois);
				for (TriggeredLOI tloi : tlois) {
					List<WorkflowBindings> wfbindings = tloi.getWorkflows(); //this.addEnimgaFiles(username, domain, tloi, false, false);
					tloi.setWorkflows(wfbindings);
					String key = tloi.toString();
					if (tloikeys.containsKey(key))
						continue;
					for (String partkey : bindkeys) {
						if (key.contains(partkey)) {
							TriggeredLOI curtloi = tloimap.get(partkey);
							if (curtloi.getLoiId().equals(tloi.getLoiId()))
								tloi.setParentHypothesisId(tloimap.get(partkey).getResultingHypothesisIds().get(0));
						}
					}
					this.addTriggeredLOI(username, domain, tloi);
				}
			}
		}
	}

	public void deleteAssertion(String username, String domain, Graph assertion) {
		String url = this.ASSERTIONSURI(username, domain);
		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			for (Triple triple : assertion.getTriples()) {
				KBTriple t = this.getKBTriple(triple, kb);
				if (t != null)
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
		if (loi.getId() == null)
			return false;

		String url = this.LOIURI(username, domain);
		String fullid = url + "/" + loi.getId();

		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBAPI loikb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
			this.start_write();
			
			KBObject loiitem = kb.createObjectOfClass(fullid, this.cmap.get("LineOfInquiry"));
			if (loi.getName() != null)
				kb.setLabel(loiitem, loi.getName());
			if (loi.getDescription() != null)
				kb.setComment(loiitem, loi.getDescription());
			if (loi.getDateCreated() != null)
				kb.setPropertyValue(loiitem, pmap.get("dateCreated"), loikb.createLiteral(loi.getDateCreated()));
			if (loi.getDateModified() != null)
				kb.setPropertyValue(loiitem, pmap.get("dateModified"), loikb.createLiteral(loi.getDateModified()));
			if (loi.getAuthor() != null)
				kb.setPropertyValue(loiitem, pmap.get("hasAuthor"), loikb.createLiteral(loi.getAuthor()));
			this.save(kb);
			this.end();
			
			this.start_write();
			KBObject floiitem = loikb.createObjectOfClass(fullid, this.cmap.get("LineOfInquiry"));
			if (loi.getHypothesisQuery() != null) {
				KBObject valobj = loikb.createLiteral(loi.getHypothesisQuery());
				loikb.setPropertyValue(floiitem, pmap.get("hasHypothesisQuery"), valobj);
			}
			if (loi.getDataQuery() != null) {
				KBObject valobj = loikb.createLiteral(loi.getDataQuery());
				loikb.setPropertyValue(floiitem, pmap.get("hasDataQuery"), valobj);
			}
			if (loi.getDataSource() != null) {
				KBObject valobj = loikb.createLiteral(loi.getDataSource());
				loikb.setPropertyValue(floiitem, pmap.get("hasDataSource"), valobj);
			}
			if (loi.getNotes() != null) {
				KBObject valobj = loikb.createLiteral(loi.getNotes());
				loikb.setPropertyValue(floiitem, pmap.get("hasUsageNotes"), valobj);
			}
			if (loi.getRelevantVariables() != null) {
				KBObject valobj = loikb.createLiteral(loi.getRelevantVariables());
				loikb.setPropertyValue(floiitem, pmap.get("hasRelevantVariables"), valobj);
			}
			if (loi.getQuestion() != null) {
				KBObject valobj = loikb.createLiteral(loi.getQuestion());
				loikb.setPropertyValue(floiitem, pmap.get("hasQuestion"), valobj);
			}
			if (loi.getExplanation() != null) {
				KBObject valobj = loikb.createLiteral(loi.getExplanation());
				loikb.setPropertyValue(floiitem, pmap.get("dataQueryDescription"), valobj);
			}

			this.storeWorkflowBindingsInKB(loikb, floiitem, pmap.get("hasWorkflowBinding"), loi.getWorkflows(),
					username, domain);
			this.storeWorkflowBindingsInKB(loikb, floiitem, pmap.get("hasMetaWorkflowBinding"), loi.getMetaWorkflows(),
					username, domain);

			return this.save(loikb) && this.end();
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return true;
	}

	private void storeWorkflowBindingsInKB(KBAPI kb, KBObject loiitem, KBObject bindingprop,
			List<WorkflowBindings> bindingslist, String username, String domain) {
		if (bindingslist == null)
			return;

		for (WorkflowBindings bindings : bindingslist) {
			String workflowid = WingsAdapter.get().WFLOWID(username, domain, bindings.getWorkflow());
			String workflowuri = WingsAdapter.get().WFLOWURI(username, domain, bindings.getWorkflow());
			KBObject bindingobj = kb.createObjectOfClass(null, cmap.get("WorkflowBinding"));
			kb.addPropertyValue(loiitem, bindingprop, bindingobj);

			kb.setPropertyValue(bindingobj, pmap.get("hasWorkflow"), kb.getResource(workflowid));

			// Get Run details
			if (bindings.getRun() != null) {
				if (bindings.getRun().getId() != null) {
					kb.setPropertyValue(bindingobj, pmap.get("hasId"), kb.createLiteral(bindings.getRun().getId()));
				}
				if (bindings.getRun().getStatus() != null)
					kb.setPropertyValue(bindingobj, pmap.get("hasStatus"),
							kb.createLiteral(bindings.getRun().getStatus()));
				if (bindings.getRun().getLink() != null)
					kb.setPropertyValue(bindingobj, pmap.get("hasRunLink"),
							kb.createLiteral(bindings.getRun().getLink()));
			}

			// Creating workflow data bindings
			for (VariableBinding vbinding : bindings.getBindings()) {
				String varid = vbinding.getVariable();
				String binding = vbinding.getBinding();
				Value bindingValue = new Value(binding, KBConstants.XSDNS() + "string");
				KBObject varbindingobj = kb.createObjectOfClass(null, cmap.get("VariableBinding"));
				kb.setPropertyValue(varbindingobj, pmap.get("hasVariable"), kb.getResource(workflowuri + "#" + varid));
				if (bindingValue != null)
					kb.setPropertyValue(varbindingobj, pmap.get("hasBindingValue"), this.getKBValue(bindingValue, kb));

				kb.addPropertyValue(bindingobj, pmap.get("hasVariableBinding"), varbindingobj);
			}
			
			// Creating parameters
			for (VariableBinding param : bindings.getParameters()) {
				String varid = param.getVariable();
				String binding = param.getBinding();
				Value bindingValue = new Value(binding, KBConstants.XSDNS() + "string");
				KBObject paramobj = kb.createObjectOfClass(null, cmap.get("VariableBinding"));
				kb.setPropertyValue(paramobj, pmap.get("hasVariable"), kb.getResource(workflowuri + "#" + varid));
				if (bindingValue != null)
					kb.setPropertyValue(paramobj, pmap.get("hasBindingValue"), this.getKBValue(bindingValue, kb));

				kb.addPropertyValue(bindingobj, pmap.get("hasParameter"), paramobj);
			}

			// Creating optional parameters
			for (VariableBinding param : bindings.getOptionalParameters()) {
				String varid = param.getVariable();
				String binding = param.getBinding();
				Value bindingValue = new Value(binding, KBConstants.XSDNS() + "string");
				KBObject paramobj = kb.createObjectOfClass(null, cmap.get("VariableBinding"));
				kb.setPropertyValue(paramobj, pmap.get("hasVariable"), kb.getResource(workflowuri + "#" + varid));
				if (bindingValue != null)
					kb.setPropertyValue(paramobj, pmap.get("hasBindingValue"), this.getKBValue(bindingValue, kb));

				kb.addPropertyValue(bindingobj, pmap.get("hasOptionalParameter"), paramobj);
			}

			String hypid = bindings.getMeta().getHypothesis();
			String revhypid = bindings.getMeta().getRevisedHypothesis();
			if (hypid != null)
				kb.setPropertyValue(bindingobj, pmap.get("hasHypothesisVariable"),
						kb.getResource(workflowuri + "#" + hypid));
			if (revhypid != null)
				kb.setPropertyValue(bindingobj, pmap.get("hasRevisedHypothesisVariable"),
						kb.getResource(workflowuri + "#" + revhypid));
		}
	}

	public List<TreeItem> listLOIs(String username, String domain) {
	    System.out.println("GET LOI LIST ");
		List<TreeItem> list = new ArrayList<TreeItem>();
		String url = this.LOIURI(username, domain);
		try {
		    this.start_read();
		  
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBObject hypcls = this.cmap.get("LineOfInquiry");
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");
			for (KBTriple t : kb.genericTripleQuery(null, typeprop, hypcls)) {
				KBObject hypobj = t.getSubject();
				String name = kb.getLabel(hypobj);
				String description = kb.getComment(hypobj);

				KBObject dateobj = kb.getPropertyValue(hypobj, pmap.get("dateCreated"));
				String date = null;
				if (dateobj != null)
					date = dateobj.getValueAsString();

				KBObject dateModifiedObj = kb.getPropertyValue(hypobj, pmap.get("dateModified"));
				String dateModified = null;
				if (dateModifiedObj != null)
					dateModified = dateModifiedObj.getValueAsString();

				KBObject authorobj = kb.getPropertyValue(hypobj, pmap.get("hasAuthor"));
				String author = null;
				if (authorobj != null)
					author = authorobj.getValueAsString();
			
				TreeItem item = new TreeItem(hypobj.getName(), name, description, null, date, author);
				if (dateModified != null) {
					item.setDateModified(dateModified);
				}
				list.add(item);
			}
		} catch (ConcurrentModificationException e) {
		   System.out.println("ERROR: Concurrent modification exception on listLOIs");
		   return null;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return list;
	}

	public LineOfInquiry getLOI(String username, String domain, String id) {
		String url = this.LOIURI(username, domain);
		String fullid = url + "/" + id;
		try {
		    this.start_read();
		  
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBAPI loikb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
			KBObject loiitem = kb.getIndividual(fullid);
			if (loiitem == null)
				return null;

			LineOfInquiry loi = new LineOfInquiry();
			loi.setId(id);
			loi.setName(kb.getLabel(loiitem));
			loi.setDescription(kb.getComment(loiitem));

			KBObject floiitem = loikb.getIndividual(fullid);
			KBObject dateobj = kb.getPropertyValue(floiitem, pmap.get("dateCreated"));
			if (dateobj != null)
				loi.setDateCreated(dateobj.getValueAsString());

			KBObject datasourceobj = kb.getPropertyValue(floiitem, pmap.get("hasDataSource"));
			if (datasourceobj != null)
				loi.setDataSource(datasourceobj.getValueAsString());

			KBObject dateModifiedObj = kb.getPropertyValue(floiitem, pmap.get("dateModified"));
			if (dateModifiedObj != null)
				loi.setDateModified(dateModifiedObj.getValueAsString());

			KBObject authorobj = kb.getPropertyValue(floiitem, pmap.get("hasAuthor"));
			if (authorobj != null)
				loi.setAuthor(authorobj.getValueAsString());

			KBObject notesobj = loikb.getPropertyValue(floiitem, pmap.get("hasUsageNotes"));
			if (notesobj != null) {
				loi.setNotes(notesobj.getValueAsString());
			}

			KBObject rvarobj = loikb.getPropertyValue(floiitem, pmap.get("hasRelevantVariables"));
			if (rvarobj != null)
				loi.setRelevantVariables(rvarobj.getValueAsString());

			KBObject hqueryobj = loikb.getPropertyValue(floiitem, pmap.get("hasHypothesisQuery"));
			if (hqueryobj != null)
				loi.setHypothesisQuery(hqueryobj.getValueAsString());
			
			KBObject dqueryobj = loikb.getPropertyValue(floiitem, pmap.get("hasDataQuery"));
			if (dqueryobj != null)
				loi.setDataQuery(dqueryobj.getValueAsString());

			KBObject questionobj = loikb.getPropertyValue(floiitem, pmap.get("hasQuestion"));
			if (questionobj != null)
				loi.setQuestion(questionobj.getValueAsString());

			KBObject explobj = loikb.getPropertyValue(floiitem, pmap.get("dataQueryDescription"));
			if (explobj != null) {
				loi.setExplanation(explobj.getValueAsString());
			}

			loi.setWorkflows(
					this.getWorkflowBindingsFromKB(username, domain, loikb, floiitem, pmap.get("hasWorkflowBinding")));
			loi.setMetaWorkflows(this.getWorkflowBindingsFromKB(username, domain, loikb, floiitem,
					pmap.get("hasMetaWorkflowBinding")));

			return loi;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return null;
	}

	private List<WorkflowBindings> getWorkflowBindingsFromKB(String username, String domain, KBAPI kb, KBObject loiitem,
			KBObject bindingprop) {
		List<WorkflowBindings> list = new ArrayList<WorkflowBindings>();
		for (KBTriple t : kb.genericTripleQuery(loiitem, bindingprop, null)) {
			KBObject wbobj = t.getObject();
			WorkflowBindings bindings = new WorkflowBindings();

			// Workflow Run details
			WorkflowRun run = new WorkflowRun();
			KBObject robj = kb.getPropertyValue(wbobj, pmap.get("hasId"));
			if (robj != null)
				run.setId(robj.getValue().toString());
			KBObject statusobj = kb.getPropertyValue(wbobj, pmap.get("hasStatus"));
			if (statusobj != null)
				run.setStatus(statusobj.getValue().toString());
			KBObject linkobj = kb.getPropertyValue(wbobj, pmap.get("hasRunLink"));
			if (linkobj != null)
				run.setLink(linkobj.getValue().toString());
			bindings.setRun(run);

			// Workflow details
			KBObject workflowobj = kb.getPropertyValue(wbobj, pmap.get("hasWorkflow"));
			if (workflowobj != null) {
			  bindings.setWorkflow(workflowobj.getName());
              String link = WingsAdapter.get().getWorkflowLink(username, domain, workflowobj.getName());
              if (link != null)
                  bindings.setWorkflowLink(link);
			}

			// Variable binding details
			for (KBObject vbobj : kb.getPropertyValues(wbobj, pmap.get("hasVariableBinding"))) {
				KBObject varobj = kb.getPropertyValue(vbobj, pmap.get("hasVariable"));
				KBObject bindobj = kb.getPropertyValue(vbobj, pmap.get("hasBindingValue"));
				VariableBinding vbinding = new VariableBinding();
				vbinding.setVariable(varobj.getName());
				vbinding.setBinding(bindobj.getValueAsString());
				bindings.getBindings().add(vbinding);
			}

			// Parameters details
			for (KBObject vbobj : kb.getPropertyValues(wbobj, pmap.get("hasParameter"))) {
				KBObject varobj = kb.getPropertyValue(vbobj, pmap.get("hasVariable"));
				KBObject bindobj = kb.getPropertyValue(vbobj, pmap.get("hasBindingValue"));
				VariableBinding param = new VariableBinding(varobj.getName(),bindobj.getValueAsString());
				bindings.addParameter(param);
			}

			// Optional parameters details
			for (KBObject vbobj : kb.getPropertyValues(wbobj, pmap.get("hasOptionalParameter"))) {
				KBObject varobj = kb.getPropertyValue(vbobj, pmap.get("hasVariable"));
				KBObject bindobj = kb.getPropertyValue(vbobj, pmap.get("hasBindingValue"));
				VariableBinding optionalParam = new VariableBinding(varobj.getName(),bindobj.getValueAsString());
				bindings.addOptionalParameter(optionalParam);
			}

			KBObject hypobj = kb.getPropertyValue(wbobj, pmap.get("hasHypothesisVariable"));
			if (hypobj != null)
				bindings.getMeta().setHypothesis(hypobj.getName());
			KBObject revhypobj = kb.getPropertyValue(wbobj, pmap.get("hasRevisedHypothesisVariable"));
			if (revhypobj != null)
				bindings.getMeta().setRevisedHypothesis(revhypobj.getName());

			list.add(bindings);
		}
		return list;
	}

	public LineOfInquiry updateLOI(String username, String domain, String id, LineOfInquiry loi) {
		if (loi.getId() == null)
			return null;

		if (this.deleteLOI(username, domain, id) && this.addLOI(username, domain, loi))
			return loi;
		return null;
	}

	public boolean deleteLOI(String username, String domain, String id) {
		if (id == null)
			return false;

		String url = this.LOIURI(username, domain);
		String fullid = url + "/" + id;

		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, false);
			KBAPI loikb = this.fac.getKB(fullid, OntSpec.PLAIN, false);
			if (kb != null && loikb != null) {
			  this.start_write();
				KBObject hypitem = kb.getIndividual(fullid);
				kb.deleteObject(hypitem, true, true);
				
				if(this.save(kb) && this.end()) {
				  this.start_write();
				  loikb.delete();
				  return this.save(loikb);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
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
		  this.start_read();
		  
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBObject cls = this.cmap.get("TriggeredLineOfInquiry");
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");

			for (KBTriple t : kb.genericTripleQuery(null, typeprop, cls)) {
				KBObject obj = t.getSubject();

				TriggeredLOI tloi = this.getTriggeredLOI(username, domain, obj.getID(), kb, null);

				list.add(tloi);
			}
		} catch (ConcurrentModificationException e) {
		   System.out.println("ERROR: Concurrent modification exception on listTriggeredLOIs");
		   return null;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return list;
	}

	public List<TriggeredLOI> getTLOIsForHypothesisAndLOI (String username, String domain, String hypid, String loiid) {
		List<TriggeredLOI> list = new ArrayList<TriggeredLOI>();

		String TLOIURI = this.TLOIURI(username, domain);
		String hypURI = this.HYPURI(username, domain) + "/" + hypid;
		String loiURI = this.LOIURI(username, domain) + "/" + loiid;
		try {
			this.start_read();
			KBAPI TLOIKB = this.fac.getKB(TLOIURI, OntSpec.PLAIN, true);
			KBObject hyp = TLOIKB.getResource(hypURI);
			KBObject loi = TLOIKB.getResource(loiURI);
			
			Set<String> hypSet = new HashSet<String>();
			Set<String> finalSet = new HashSet<String>();

			for (KBTriple t : TLOIKB.genericTripleQuery(null, pmap.get("hasParentHypothesis"), hyp)) {
				KBObject obj = t.getSubject();
				String tloiid = obj.getID();
				hypSet.add(tloiid);
			}
			for (KBTriple t : TLOIKB.genericTripleQuery(null, pmap.get("hasLineOfInquiry"), loi)) {
				KBObject obj = t.getSubject();
				String tloiid = obj.getID();
				if (hypSet.contains(tloiid)) finalSet.add(tloiid);
			}
			for (String tloiid: finalSet) {	
				String[] sp = tloiid.split("/");
				KBAPI tloiGraph = this.fac.getKB(TLOIURI + '/' + sp[sp.length-1], OntSpec.PLAIN, true);

				TriggeredLOI tloi = this.getTriggeredLOI(username, domain, tloiid, TLOIKB, tloiGraph);
				list.add(tloi);
			}
		} catch (ConcurrentModificationException e) {
		   System.out.println("ERROR: Concurrent modification exception on listHypothesisTLOIs");
		   return null;
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return list;
	}

	public TriggeredLOI getTriggeredLOI(String username, String domain, String id) {
		String url = this.TLOIURI(username, domain);
		String fullid = url + "/" + id;
		TriggeredLOI tloi = null;

		try {
		  this.start_read();
		  
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBAPI tloikb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
			tloi = this.getTriggeredLOI(username, domain, fullid, kb, tloikb);
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return tloi;
	}

	public void addTriggeredLOI(String username, String domain, TriggeredLOI tloi) {
		tloi.setStatus(Status.QUEUED);
		this.saveTriggeredLOI(username, domain, tloi);

		TLOIExecutionThread wflowThread = new TLOIExecutionThread(username, domain, tloi, false);
		executor.execute(wflowThread);
	}

	public boolean deleteTriggeredLOI(String username, String domain, String id) {
		if (id == null)
			return false;

		String url = this.TLOIURI(username, domain);
		String fullid = url + "/" + id;

		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, false);
			KBAPI tloikb = this.fac.getKB(fullid, OntSpec.PLAIN, false);
			if (kb != null && tloikb != null) {
			  this.start_read();
				KBObject item = kb.getIndividual(fullid);
				KBObject hypobj = kb.getPropertyValue(item, pmap.get("hasResultingHypothesis"));
				if (hypobj != null) {
					List<KBTriple> alltlois = kb.genericTripleQuery(null, pmap.get("hasParentHypothesis"), hypobj);
					this.end();
					if (alltlois != null && alltlois.size() == 1) {
						this.deleteHypothesis(username, domain, hypobj.getName());
					} else {
						System.out.println("Resulting hypotesis cannot be deleted as is being used for other tloi.");
					}
					/*
					for (KBTriple t : kb.genericTripleQuery(null, pmap.get("hasParentHypothesis"), hypobj)) {
						this.deleteTriggeredLOI(username, domain, t.getSubject().getName());
					}
					this.end();
					
					this.deleteHypothesis(username, domain, hypobj.getName());*/
				}
				else {
				  this.end();
				}
				
				this.start_write();
				kb.deleteObject(item, true, true);
				return this.save(kb) && this.end() && 
				    this.start_write() && tloikb.delete() && this.save(tloikb) && this.end();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return false;
	}

	private TriggeredLOI getTriggeredLOI(String username, String domain, String id, KBAPI kb, KBAPI tloikb) {
		TriggeredLOI tloi = new TriggeredLOI();
		KBObject obj = kb.getIndividual(id);

		try{
			tloi.setId(obj.getName());
		} catch (Exception e) {
			System.out.println("ERROR trying to get " + id);
			return null;
		}
		tloi.setName(kb.getLabel(obj));
		tloi.setDescription(kb.getComment(obj));

		KBObject lobj = kb.getPropertyValue(obj, pmap.get("hasLineOfInquiry"));
		if (lobj != null)
			tloi.setLoiId(lobj.getName());

		KBObject pobj = kb.getPropertyValue(obj, pmap.get("hasParentHypothesis"));
		if (pobj != null)
			tloi.setParentHypothesisId(pobj.getName());

		String hypPrefix = this.HYPURI(username, domain);
		for (KBObject robj : kb.getPropertyValues(obj, pmap.get("hasResultingHypothesis"))) {
			String resHypId = robj.getName();
			tloi.addResultingHypothesisId(resHypId);
		
			//ADDs confidence value:
			String resHypURI = hypPrefix + "/" + resHypId;
			String prov = resHypURI + "/provenance";
			Graph g = this.getKBGraph(resHypURI);
			try {
				KBAPI provkb = this.fac.getKB(prov, OntSpec.PLAIN, false);
				this.updateTripleDetails(g, provkb);
			} catch (Exception e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			for (Triple t: g.getTriples()) {
				if (t.getDetails() != null) {
					tloi.setConfidenceValue(t.getDetails().getConfidenceValue());
					break;
				}
			}
		}

		KBObject stobj = kb.getPropertyValue(obj, pmap.get("hasTriggeredLineOfInquiryStatus"));
		if (stobj != null)
			tloi.setStatus(Status.valueOf(stobj.getValue().toString()));

		KBObject dateobj = kb.getPropertyValue(obj, pmap.get("dateCreated"));
		if (dateobj != null)
			tloi.setDateCreated(dateobj.getValueAsString());
		
		KBObject dateModifiedObj = kb.getPropertyValue(obj, pmap.get("dateModified"));
		if (dateModifiedObj != null)
			tloi.setDateModified(dateModifiedObj.getValueAsString());
		
		KBObject authorobj = kb.getPropertyValue(obj, pmap.get("hasAuthor"));
		if (authorobj != null)
			tloi.setAuthor(authorobj.getValueAsString());

		KBObject dqobj = kb.getPropertyValue(obj, pmap.get("hasDataQuery"));
		if (dqobj != null)
			tloi.setDataQuery(dqobj.getValueAsString());
		
		KBObject dataSourceObj = kb.getPropertyValue(obj, pmap.get("hasDataSource"));
		if (dataSourceObj != null)
			tloi.setDataSource(dataSourceObj.getValueAsString());

		KBObject rvobj = kb.getPropertyValue(obj, pmap.get("hasRelevantVariables"));
		if (rvobj != null)
			tloi.setRelevantVariables(rvobj.getValueAsString());

		KBObject explobj = kb.getPropertyValue(obj, pmap.get("dataQueryDescription"));
		if (explobj != null)
			tloi.setExplanation(explobj.getValueAsString());

		if (tloikb != null) {
			KBObject floiitem = tloikb.getIndividual(id);
			tloi.setWorkflows(
					this.getWorkflowBindingsFromKB(username, domain, tloikb, floiitem, pmap.get("hasWorkflowBinding")));
			tloi.setMetaWorkflows(this.getWorkflowBindingsFromKB(username, domain, tloikb, floiitem,
					pmap.get("hasMetaWorkflowBinding")));
		}
		return tloi;
	}

	private void addAllTriplesToList (List<Triple> list, KBAPI api, KBObject s, KBObject p, KBObject o) {
		for (KBTriple t: api.genericTripleQuery(s, p, o)) {
			KBObject sub = t.getSubject();
			String subject = (sub.isAnonymous()) ? sub.shortForm() : sub.getValueAsString();
			KBObject obj = t.getObject();
			Value objvalue;
			if (obj.isAnonymous()) {
				objvalue = new Value();
				objvalue.setValue(obj.shortForm());
				objvalue.setType(Value.Type.URI);
			} else {
				objvalue = this.getObjectValue(obj);
			}
			list.add(new Triple(subject, t.getPredicate().getValueAsString(), objvalue, null));
		}
	}

	public List<Triple> getTriggeredLOITriples(String username, String domain, String id) {
		List<Triple> all = new ArrayList<Triple>();
		try {
			this.start_read();
			String kburi, fulluri;
			KBAPI api;
			KBObject tloi, loi, hyp;
			ArrayList<KBObject> phyp;

			// Get TLOI and triples
			kburi = this.TLOIURI(username, domain);
			api = this.fac.getKB(kburi, OntSpec.PLAIN, true);
			fulluri = kburi + "/" + id;
			// Get all subjects related to TLOI
			tloi = api.getIndividual(fulluri);
			if (tloi == null) return all;
			loi = api.getPropertyValue(tloi, pmap.get("hasLineOfInquiry"));
			hyp = api.getPropertyValue(tloi, pmap.get("hasParentHypothesis"));
			phyp = api.getPropertyValues(tloi, pmap.get("hasResultingHypothesis"));
			

			addAllTriplesToList(all, api, tloi, null, null);
			
			api = this.fac.getKB(tloi.getID(), OntSpec.PLAIN, true);
			addAllTriplesToList(all, api, api.getIndividual(tloi.getName()), null, null);

			// LOI triples
			api = this.fac.getKB(this.LOIURI(username, domain), OntSpec.PLAIN, true);
			addAllTriplesToList(all, api, loi, null, null);
			api = this.fac.getKB(loi.getID(), OntSpec.PLAIN, true);
			addAllTriplesToList(all, api, api.getIndividual(loi.getName()), null, null);
			
			// Hyp triples
			api = this.fac.getKB( this.HYPURI(username, domain) , OntSpec.PLAIN, true);
			addAllTriplesToList(all, api, hyp, null, null);

			api = this.fac.getKB(hyp.getID(), OntSpec.PLAIN, true);
			addAllTriplesToList(all, api, api.getIndividual(hyp.getName()), null, null);
			//prov
			String prov = hyp.getID() + "/provenance";
			api = this.fac.getKB(prov, OntSpec.PLAIN, true);
			Graph graph = this.getKBGraph(prov);
			this.updateTripleDetails(graph, api);
			all.addAll( graph.getTriples() );

			// RHyp triples
			for (KBObject robj : phyp) {
				prov = robj.getID() + "/provenance";
				api = this.fac.getKB(prov, OntSpec.PLAIN, true);
				Graph graph2 = this.getKBGraph(prov);
				this.updateTripleDetails(graph2, api);
				all.addAll( graph2.getTriples() );
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return all;
	}

	private void updateTriggeredLOI(String username, String domain, String id, TriggeredLOI tloi) {
		if (tloi.getId() == null)
			return;

		this.deleteTriggeredLOI(username, domain, id);
		this.saveTriggeredLOI(username, domain, tloi);
	}

	private boolean saveTriggeredLOI(String username, String domain, TriggeredLOI tloi) {
		if (tloi.getId() == null)
			return false;
		
		System.out.println("- SAVE TLOI -");

		String url = this.TLOIURI(username, domain);
		String fullid = url + "/" + tloi.getId();
		String hypns = this.HYPURI(username, domain) + "/";
		String loins = this.LOIURI(username, domain) + "/";

		try {
			// TODO: We store stuff on two graphs, the general one and one created for LOI/TLOI...
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBAPI tloikb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
			this.start_write();
			KBObject tloiitem = kb.createObjectOfClass(fullid, this.cmap.get("TriggeredLineOfInquiry"));
			if (tloi.getName() != null) {
				kb.setLabel(tloiitem, tloi.getName());
			}
			if (tloi.getDescription() != null) {
				kb.setComment(tloiitem, tloi.getDescription());
			}

			if (tloi.getDataSource() != null) {
				kb.setPropertyValue(tloiitem, pmap.get("hasDataSource"), tloikb.createLiteral(tloi.getDataSource()));
			}

			if (tloi.getDateCreated() != null) {
				kb.setPropertyValue(tloiitem, pmap.get("dateCreated"), tloikb.createLiteral(tloi.getDateCreated()));
			}

			if (tloi.getDateModified() != null) {
				kb.setPropertyValue(tloiitem, pmap.get("dateModified"), tloikb.createLiteral(tloi.getDateModified()));
			}

			if (tloi.getAuthor() != null) {
				kb.setPropertyValue(tloiitem, pmap.get("hasAuthor"), tloikb.createLiteral(tloi.getAuthor()));
			}

			if (tloi.getDataQuery() != null) {
				kb.setPropertyValue(tloiitem, pmap.get("hasDataQuery"), tloikb.createLiteral(tloi.getDataQuery()));
			}

			if (tloi.getRelevantVariables() != null) {
				kb.setPropertyValue(tloiitem, pmap.get("hasRelevantVariables"), tloikb.createLiteral(tloi.getRelevantVariables()));
			}

			if (tloi.getExplanation() != null) {
				kb.setPropertyValue(tloiitem, pmap.get("dataQueryDescription"), tloikb.createLiteral(tloi.getExplanation()));
			}

			if (tloi.getLoiId() != null) {
				KBObject lobj = kb.getResource(loins + tloi.getLoiId());
				kb.setPropertyValue(tloiitem, pmap.get("hasLineOfInquiry"), lobj);
			}
			if (tloi.getParentHypothesisId() != null) {
				KBObject hobj = kb.getResource(hypns + tloi.getParentHypothesisId());
				kb.setPropertyValue(tloiitem, pmap.get("hasParentHypothesis"), hobj);
			}
			for (String hypid : tloi.getResultingHypothesisIds()) {
				KBObject hobj = kb.getResource(hypns + hypid);
				kb.addPropertyValue(tloiitem, pmap.get("hasResultingHypothesis"), hobj);
			}
			if (tloi.getStatus() != null) {
				KBObject stobj = kb.createLiteral(tloi.getStatus().toString());
				kb.setPropertyValue(tloiitem, pmap.get("hasTriggeredLineOfInquiryStatus"), stobj);
			}
			if (this.save(kb) && this.end()) {
				this.start_write();
				KBObject ftloiitem = tloikb.createObjectOfClass(fullid, this.cmap.get("TriggeredLineOfInquiry"));
				this.storeWorkflowBindingsInKB(
						tloikb, ftloiitem, pmap.get("hasWorkflowBinding"), tloi.getWorkflows(), username, domain);
				this.storeWorkflowBindingsInKB(
						tloikb, ftloiitem, pmap.get("hasMetaWorkflowBinding"), tloi.getMetaWorkflows(), username, domain);
				return this.save(tloikb) && this.end();
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return true;
	}
	
	private String getWorkflowExecutionRunIds(TriggeredLOI tloi, String workflow) {
		String runids = null;
		for (WorkflowBindings bindings : tloi.getWorkflows()) {
			if (bindings.getWorkflow().equals(workflow)) {
				runids = (runids == null) ? "" : runids + ", ";
				runids += bindings.getRun().getId();
			}
		}
		return runids;
	}
	
	public String getDataFromWings (String username, String domain, String id) {
		WingsAdapter wings = WingsAdapter.get();
		String dataid = wings.DOMURI(username, domain) + "/data/library.owl#" + id;
		return wings.fetchDataFromWings(username, domain, dataid);
	}

	private String fetchOutputHypothesis(String username, String domain, WorkflowBindings bindings, TriggeredLOI tloi) {
		String varname = bindings.getMeta().getRevisedHypothesis();
		Map<String, String> varmap = WingsAdapter.get().getRunVariableBindings(username, domain,
				bindings.getRun().getId());
		if (varmap.containsKey(varname)) {
			String dataid = varmap.get(varname);
			String dataname = dataid.replaceAll(".*#", "");
			System.out.println("LOADING DATAID: " + dataid);
			String content = WingsAdapter.get().fetchDataFromWings(username, domain, dataid);

			HashMap<String, Integer> workflows = new HashMap<String, Integer>();
			for (WorkflowBindings wb : tloi.getWorkflows()) {
				String wid = wb.getWorkflow();
				if (workflows.containsKey(wid))
					workflows.put(wid, workflows.get(wid) + 1);
				else
					workflows.put(wid, 1);
			}
			String wflows = "";
			for (String wid : workflows.keySet()) {
				if (!wflows.equals(""))
					wflows += ", ";
				int num = workflows.get(wid);
				wflows += (num > 1 ? num : "a") + " " + wid + " workflow" + (num > 1 ? "s" : "");
			}
			String meta = bindings.getWorkflow();

			Hypothesis parentHypothesis = this.getHypothesis(username, domain, tloi.getParentHypothesisId());
			Hypothesis newHypothesis = new Hypothesis();
			newHypothesis.setId(dataname);
			newHypothesis.setName("[Revision] " + parentHypothesis.getName());
			String description = "Followed the line of inquiry: \"" + tloi.getName() + "\" to run " + wflows
					+ ", then run " + meta + " meta-workflow, to create a revised hypothesis.";
			newHypothesis.setDescription(description);
			newHypothesis.setParentId(parentHypothesis.getId());

			List<Triple> triples = new ArrayList<Triple>();
			TripleUtil util = new TripleUtil();
			for (String line : content.split("\\n")) {
				String[] parts = line.split("\\s+", 4);
				TripleDetails details = new TripleDetails();
				if(parts.length > 3)
				  details.setConfidenceValue(Double.parseDouble(parts[3]));
				details.setTriggeredLOI(tloi.getId());
				Triple t = util.fromString(parts[0] + " " + parts[1] + " " + parts[2]);
				t.setDetails(details);
				triples.add(t);
			}
			Graph newgraph = new Graph();
			newgraph.setTriples(triples);
			newHypothesis.setGraph(newgraph);

			this.addHypothesis(username, domain, newHypothesis);
			return newHypothesis.getId();
		}
		return null;
	}


	/* Narratives */
	public Map<String, String> getNarratives (String username, String domain, String tloid) {
		Map<String,String> narratives = new HashMap<String, String>();
		TriggeredLOI tloi = this.getTriggeredLOI(username, domain, tloid);
		if (tloi != null) {
			String hypId = tloi.getParentHypothesisId();
			String loiId = tloi.getLoiId();
			Hypothesis hyp = this.getHypothesis(username, domain, hypId);
			LineOfInquiry loi = this.getLOI(username, domain, loiId);
			
			// Assuming each tloi only has a workflow or metaworkdflow:
			WorkflowBindings wf = null;
			List<WorkflowBindings> wfs = tloi.getWorkflows();
			List<WorkflowBindings> mwfs = tloi.getMetaWorkflows();

			if (mwfs != null && mwfs.size() > 0) {
				wf = mwfs.get(0);
			} else if (wfs != null && wfs.size() > 0){
				wf = wfs.get(0);
			} else {
				System.out.println("TLOID: " + tloid + " has does not have any workflow.");
			}
			
			String dataset = "";
			for (VariableBinding ds: wf.getBindings()) {
				String binding = ds.getBinding();
				if (binding.startsWith("[")) {
					for (String datas: ds.getBindingAsArray()) {
						dataset += "<li>" + datas + "</li>";
					}
				}
				System.out.println("binding: " + binding);
			}
			
			String pval = Double.toString(tloi.getConfidenceValue());
			int pvallen = pval.length();
			if (pval != null && pvallen > 3) {
				int decimals = 0;
				String newval = "";
				for (int i = 0; i < pvallen; i++) {
					if (pval.charAt(i) != '0' && pval.charAt(i) != '.' && decimals < 2) {
						decimals += 1;
					}
					newval += pval.charAt(i);
					if (decimals >= 2) break;
				}
				pval = newval;
			}
			
			//Execution narratives
			String execution = "<b>Execution Narrative:</b><br/>"
							 + "The Hypothesis with title: <b>" + hyp.getName()
							 + "</b> was runned <span class=\"" + tloi.getStatus() + "\">" 
							 + tloi.getStatus() + "</span>"
							 + " with the Line of Inquiry: <b>" + loi.getName()
							 + "</b>. The LOI triggered the <a target=\"_blank\" href=\"" + wf.getWorkflowLink() 
							 + "\">workflow on WINGS</a>"
							 + " where it was tested with the following datasets:<ul>" + dataset
							 + "</ul>The resulting p-value is " + pval + ".";
			narratives.put("execution", execution);

			System.out.println("EXECUTION NARRATIVE: " + execution);
			
			String dataQuery = "<b>Data Query Narrative:</b><br/>" + this.dataQueryNarrative(loi.getDataQuery());

			System.out.println("DATA Query NARRATIVE: " + dataQuery);
			
			narratives.put("dataquery", dataQuery);
		}
		return narratives;
	}
	
	private String dataQueryNarrative(String dataQuery) {
      String dataQuery1 = dataQuery.replaceAll("^(//)n${1}",""); //this is necessary to replace the new line characters in query
      String[] querylist = dataQuery1.split("\\.");
      String rdfs_label = "rdfs:label";
      //storing properties
      Map<String, String> properties = new HashMap<String, String>();
      for(int i = 0; i < querylist.length; i++) {
//          System.out.println("\n"+ querylist[i]);
          if(querylist[i].contains(rdfs_label)){
              String[] line = querylist[i].replace("\\","").split(" ");
              properties.put(line[2],line[4].replace('"',' '));
          }
      }

      //We map all the objects to the properties they were identified with, by using the objects dictionary
      Map<String, List<List<String>>> inputs = new HashMap<>();
      Map<List<String>, String> outputs = new HashMap<>();
      for(int i = 0; i < querylist.length; i++) {
          if(!querylist[i].contains(rdfs_label)){
              String[] line = querylist[i].split(" ");
              String schema = "Schema";
              if(!inputs.containsKey(line[2])&!line[2].contains(schema)){
                  List<List<String>> list = new ArrayList<List<String>>();
                  List<String> item = new ArrayList<String>();
                  item.add(line[2]);
                  item.add(properties.get(line[3]));
//                  item.add(line[4]);
                  list.add(item);
                  inputs.put(line[2],list);
                  outputs.put(item,line[4]);
              } else if(inputs.containsKey(line[2])&!line[2].contains(schema)){
                  List<List<String>> list2 = inputs.get(line[2]);
                  List<String> item = new ArrayList<String>();
                  item.add(line[2]);
                  item.add(properties.get(line[3]));
                  list2.add(item);
                  inputs.put(line[2],list2);
                  List<String> list = new ArrayList<String>();
                  list.add(line[2]);
                  list.add(properties.get(line[3]));
                  outputs.put(item,line[4]);
              }
          }
      }

      //Now we traverse the path
      String path = "";
      for (String key : inputs.keySet()) {
    	  List<List<String>> value = inputs.get(key);
          for(int j=0;j<value.size();j++){
              //p = v
              List<String> p = value.get(j);
              try {
                  path = path+key.replace("?","")+"->"+ p.get(1).toString().trim().replace("?","") +"->"+outputs.get(p).toString().replace("?","")+"<br/>";
              } catch (NullPointerException e){

              }
              }
          }
//      System.out.println("Narrative"+path);
      return path;
  }

	/*
	private List<WorkflowBindings> addEnimgaFiles(String username, String domain, TriggeredLOI tloi, boolean metamode, boolean upload) {
		WingsAdapter wings = WingsAdapter.get();

		List<WorkflowBindings> wflowBindings = tloi.getWorkflows();
		if (metamode)
			wflowBindings = tloi.getMetaWorkflows();
		// Query for assertions pertaining to hasEnigmaQueryLiteral
		ArrayList<KBTriple> equeries = new ArrayList<KBTriple>();
		try {
			String url = ASSERTIONSURI(username, domain);
			this.start_read();
			KBAPI kb = fac.getKB(url, OntSpec.PLAIN, false);
			KBObject typeprop = kb.getProperty(KBConstants.NEURONS() + "hasEnigmaQueryLiteral");
			equeries = kb.genericTripleQuery(null, typeprop, null);
			this.end();
		} catch (Exception e) {
			e.printStackTrace();
		}

		// Iterate through data and add enigma query files if necessary
		for (int i = 0; i < wflowBindings.size(); i++) {
			List<VariableBinding> bindings = wflowBindings.get(i).getBindings();

			Map<String, Variable> inputs = wings.getWorkflowInputs(username, domain,
					wflowBindings.get(i).getWorkflow());
			String[] files = null;
			for (VariableBinding var : bindings) {
				try {
					String url = ASSERTIONSURI(username, domain);
					String fullid = url + "#" + var.getBinding();
					if (!inputs.get(var.getVariable()).isParam())
						for (KBTriple kbt : equeries) {
							if (kbt.getSubject().getValueAsString().equals(fullid)) {
								files = addQuery(username, domain, kbt.getObject().getValueAsString(),
										inputs.get(var.getVariable()).getType(), upload);
								break;
							}
						}
					if (files != null) {
						for (int f = 0; f < files.length; f += 2) {
							List<VariableBinding> newvb = new ArrayList<VariableBinding>();
							for (VariableBinding newvar : bindings) {
								if (!newvar.equals(var))
									newvb.add(new VariableBinding(newvar.getVariable(), newvar.getBinding()));
								else
									newvb.add(new VariableBinding(newvar.getVariable(), files[f].substring(4)));

							}
							WorkflowBindings newwfb = new WorkflowBindings(wflowBindings.get(i).getWorkflow(),
									wflowBindings.get(i).getWorkflowLink(), newvb);
							newwfb.setRun(new WorkflowRun(wflowBindings.get(i).getRun().getId(),
									wflowBindings.get(i).getRun().getLink(),
									wflowBindings.get(i).getRun().getStatus()));
							newwfb.setMeta(new MetaWorkflowDetails(wflowBindings.get(i).getMeta().getHypothesis(),
									wflowBindings.get(i).getMeta().getRevisedHypothesis()));
							wflowBindings.add(newwfb);
						}
						break;
					}
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			if (files != null) {
				wflowBindings.remove(wflowBindings.get(i));
				i--;
			}
		}
		return wflowBindings;
	}
	*/

	/*
	 * private String createDummyHypothesis(String username, String domain,
	 * WorkflowBindings bindings, TriggeredLOI tloi) { Hypothesis parentHypothesis =
	 * this.getHypothesis(username, domain, tloi.getParentHypothesisId());
	 * Hypothesis newHypothesis = new Hypothesis();
	 * newHypothesis.setId(GUID.randomId("Hypothesis"));
	 * newHypothesis.setName("[Revision] " +parentHypothesis.getName()); String
	 * description =
	 * "Followed the line of inquiry: \""+tloi.getName()+"\" to run workflows " +
	 * "and generate a hypothesis."; newHypothesis.setDescription(description);
	 * newHypothesis.setParentId(parentHypothesis.getId());
	 * 
	 * List<Triple> triples = new
	 * ArrayList<Triple>(parentHypothesis.getGraph().getTriples()); for(Triple t :
	 * triples) { TripleDetails details = new TripleDetails();
	 * details.setConfidenceValue(0.97); details.setTriggeredLOI(tloi.getId());
	 * t.setDetails(details); } Graph newgraph = new Graph();
	 * newgraph.setTriples(triples); newHypothesis.setGraph(newgraph);
	 * 
	 * this.addHypothesis(username, domain, newHypothesis); return
	 * newHypothesis.getId(); }
	 */

	public String directQuery(String username, String domain, String query) {
		//List<List<SparqlQuerySolution>> result = null;
		String url = this.LOIURI(username, domain);
		String query2 = "SELECT * WHERE { ?a ?b ?c }";
		try {
			this.start_read();
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			ArrayList<ArrayList<SparqlQuerySolution>> result2 = kb.sparqlQuery(query2);
			System.out.println("DOM " + url);
			System.out.println("<< " + query2);
			System.out.println(">> " + result2.toString());
			
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
		  this.end();
		}
		return "";
	}
	

	class TLOIExecutionThread implements Runnable {
		String username;
		String domain;
		boolean metamode;
		TriggeredLOI tloi;

		public TLOIExecutionThread(String username, String domain, TriggeredLOI tloi, boolean metamode) {
			this.username = username;
			this.domain = domain;
			this.tloi = tloi;
			this.metamode = metamode;
		}

		@Override
		public void run() {
			try {
				System.out.println("Running execution thread");
				if (this.metamode)
					System.out.println("METAMODE enabled!");

				WingsAdapter wings = WingsAdapter.get();

				List<WorkflowBindings> wflowBindings = tloi.getWorkflows();
				if (this.metamode)
					wflowBindings = tloi.getMetaWorkflows();
        
				// Start off workflows from tloi
				for (WorkflowBindings bindings : wflowBindings) {
					// Get workflow input details
					Map<String, Variable> inputs = wings.getWorkflowInputs(username, domain, bindings.getWorkflow());
					List<VariableBinding> vbindings = bindings.getBindings();
					List<VariableBinding> params = bindings.getParameters();
					List<VariableBinding> optionalparams = bindings.getOptionalParameters();
					List<VariableBinding> sendbindings = new ArrayList<VariableBinding>(vbindings);
					
					//Remove " on parameters
					for (VariableBinding p: params) {
						String bind = p.getBinding();
						if (bind.charAt(0) == '"') {
							bind = bind.substring(1, bind.length() - 1);
						}
						sendbindings.add(new VariableBinding(p.getVariable(), bind));
					}
					//Same for optional parameters
					for (VariableBinding p: optionalparams) {
						String bind = p.getBinding();
						if (!(bind == null || bind.equals("") || bind.equals("\"\"") || bind.charAt(0) == '?')) {
							if (bind.charAt(0) == '"') {
								bind = bind.substring(1, bind.length() - 1);
							}
							sendbindings.add(new VariableBinding(p.getVariable(), bind));
						}
					}
					
					System.out.println("Bindings to be send on run:");
					for (VariableBinding b: sendbindings) {
						System.out.println(b.getVariable()+  " = " + b.getBinding());
					}
					
					
					// Special processing for Meta Workflows
					if (this.metamode) {
						// Replace workflow ids with workflow run ids in
						// Variable Bindings
						for (VariableBinding vbinding : vbindings) {
							String runids = getWorkflowExecutionRunIds(tloi, vbinding.getBinding());
							if( runids != null && runids.length() > 0 )
								vbinding.setBinding(runids);
						}
						// Upload hypothesis to Wings as a file, and add to
						// Variable Bindings
						String hypVarId = bindings.getMeta().getHypothesis();
						System.out.println("Hypothesis Variable ID: " + hypVarId);
						if (hypVarId != null && inputs.containsKey(hypVarId)) {
							Variable hypVar = inputs.get(hypVarId);
							String hypId = tloi.getParentHypothesisId();
							Hypothesis hypothesis = getHypothesis(username, domain, tloi.getParentHypothesisId());
							String contents = "";
							for (Triple t : hypothesis.getGraph().getTriples())
								contents += t.toString() + "\n";

							if (hypVar.getType() == null) {
								System.err.println("Couldn't retrieve hypothesis type information");
								continue;
							}
							String dataid = wings.addDataToWings(username, domain, hypId, hypVar.getType(), contents);
							if (dataid == null) {
								System.err.println("Couldn't add hypothesis to wings");
								continue;
							}
							VariableBinding hypBinding = new VariableBinding();
							hypBinding.setVariable(hypVarId);
							hypBinding.setBinding(dataid.replaceAll("^.*#", ""));
							sendbindings.add(hypBinding);
						} else {
							System.err.println("Workflow doesn't have hypothesis information");
							continue;
						}
					}
					// Execute workflow
					System.out.println("Executing " + bindings.getWorkflow() + " with:\n" + vbindings);
					String runid = wings.runWorkflow(username, domain, bindings.getWorkflow(), sendbindings, inputs);
					if (runid != null)
						bindings.getRun().setId(runid);// .replaceAll("^.*#",
														// ""));
				}
				tloi.setStatus(Status.RUNNING);
				updateTriggeredLOI(username, domain, tloi.getId(), tloi);

				// Start monitoring
				TLOIMonitoringThread monitorThread = new TLOIMonitoringThread(username, domain, tloi, metamode);
				monitor.schedule(monitorThread, 5, TimeUnit.SECONDS);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	class TLOIMonitoringThread implements Runnable {
		String username;
		String domain;
		boolean metamode;
		TriggeredLOI tloi;

		public TLOIMonitoringThread(String username, String domain, TriggeredLOI tloi, boolean metamode) {
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
				if (this.metamode)
					wflowBindings = tloi.getMetaWorkflows();

				Status overallStatus = tloi.getStatus();
				int numSuccessful = 0;
				int numFinished = 0;
				for (WorkflowBindings bindings : wflowBindings) {
					String runid = bindings.getRun().getId();
					if (runid == null) {
						overallStatus = Status.FAILED;
						numFinished++;
						continue;
					}
					String rname = runid.replaceAll("^.*#", "");
					WorkflowRun wstatus = WingsAdapter.get().getWorkflowRunStatus(this.username, this.domain, rname);
					bindings.setRun(wstatus);

					if (wstatus.getStatus().equals("FAILURE")) {
						overallStatus = Status.FAILED;
						numFinished++;
						continue;
					}
					if (wstatus.getStatus().equals("RUNNING")) {
						if (overallStatus != Status.FAILED)
							overallStatus = Status.RUNNING;
						continue;
					}
					if (wstatus.getStatus().equals("SUCCESS")) {
						numFinished++;
						numSuccessful++;

						if (metamode) {
							// Fetch the output hypothesis file, and create a
							// new hypothesis
							String hypId = fetchOutputHypothesis(username, domain, bindings, tloi);
							// String hypId = createDummyHypothesis(username,
							// domain, bindings, tloi);
							if (hypId != null)
								tloi.addResultingHypothesisId(hypId);
						}
					}
				}
				// If all the workflows are successfully finished
				if (numSuccessful == wflowBindings.size()) {
					if (metamode) {
						overallStatus = Status.SUCCESSFUL;
					} else {
						overallStatus = Status.RUNNING;

						// Start meta workflows
						TLOIExecutionThread wflowThread = new TLOIExecutionThread(username, domain, tloi, true);
						executor.execute(wflowThread);
					}
				} else if (numFinished < wflowBindings.size()) {
					monitor.schedule(this, 2, TimeUnit.MINUTES);
				}
				tloi.setStatus(overallStatus);
				updateTriggeredLOI(username, domain, tloi.getId(), tloi);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	public class DataMonitor implements Runnable {
		boolean stop;
		ScheduledFuture<?> scheduledFuture;
		String defaultUsername;
		String defaultDomain;

		public DataMonitor() {
			stop = false;
			scheduledFuture = monitor.scheduleWithFixedDelay(this, 0, 1, TimeUnit.DAYS);
		}

		public void run() {
			try {
				Thread.sleep(5000);
				if (stop) {
					scheduledFuture.cancel(false);
					while (!Thread.currentThread().isInterrupted()) {
						Thread.currentThread().interrupt();
					}
				} else if (!this.equals(dataThread)) {
					stop();
					return;
				} else {
					defaultUsername = Config.get().getProperties().getString("username");
					defaultDomain = Config.get().getProperties().getString("domain");
					String url = ASSERTIONSURI(defaultUsername, defaultDomain);
					KBAPI kb = fac.getKB(url, OntSpec.PLAIN, false);
					KBObject typeprop = kb.getProperty(KBConstants.NEURONS() + "hasEnigmaQueryLiteral");
					List<KBTriple> equeries = kb.genericTripleQuery(null, typeprop, null);
					for (KBTriple kbt : equeries)
						if (DataQuery.wasUpdatedInLastDay(kbt.getObject().getValueAsString())) {
							requeryHypotheses(defaultUsername, defaultDomain);
							break;
						}

				}
			} catch (Exception e) {
				scheduledFuture.cancel(false);
				while (!Thread.interrupted()) {
					stop = true;
					Thread.currentThread().interrupt();
				}
			}

		}

		public void stop() {
			while (!Thread.interrupted()) {
				stop = true;
				scheduledFuture.cancel(false);
				Thread.currentThread().interrupt();
			}
		}
	}
}
