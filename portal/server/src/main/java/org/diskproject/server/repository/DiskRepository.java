package org.diskproject.server.repository;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Scanner;

import org.apache.commons.lang.SerializationUtils;
import org.diskproject.server.repository.GmailService.MailMonitor;
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
import org.diskproject.shared.classes.loi.MetaWorkflowDetails;
import org.diskproject.shared.classes.loi.WorkflowBindings;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.shared.classes.loi.TriggeredLOI.Status;
import org.diskproject.shared.classes.util.GUID;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.vocabulary.Individual;
import org.diskproject.shared.classes.vocabulary.Property;
import org.diskproject.shared.classes.vocabulary.Vocabulary;
import org.diskproject.shared.classes.vocabulary.Type;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import edu.isi.wings.ontapi.KBAPI;
import edu.isi.wings.ontapi.KBObject;
import edu.isi.wings.ontapi.KBTriple;
import edu.isi.wings.ontapi.OntSpec;
import edu.isi.wings.ontapi.SparqlQuerySolution;

public class DiskRepository extends KBRepository {
	static DiskRepository singleton;

	private static SimpleDateFormat dateformatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssX");

	protected KBAPI hypontkb;
	protected KBAPI omicsontkb;
	protected KBAPI neuroontkb;

	Map<String, Vocabulary> vocabularies;
	ScheduledExecutorService monitor;
	ScheduledExecutorService monitorData;
	ExecutorService executor;
	static GmailService gmail;
	static DataMonitor dataThread;

	public static void main(String[] args) {
		get();
		get().shutdownExecutors();
	}

	public static DiskRepository get() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
		if (singleton == null)
			singleton = new DiskRepository(); // Here
		return singleton;
	}

	public DiskRepository() {
		if (gmail == null) {
			gmail = GmailService.get();
		}
		setConfiguration(KBConstants.DISKURI(), KBConstants.DISKNS());
		initializeKB(); // Here
		monitor = Executors.newScheduledThreadPool(0);
		executor = Executors.newFixedThreadPool(2);
		dataThread = new DataMonitor();
		dataThread.run();
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
		if (gmail != null)
			gmail.shutdown();
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
		if (this.ontkb != null)
			this.ontkb.delete();
		if (this.hypontkb != null)
			this.hypontkb.delete();
		if (this.omicsontkb != null)
			this.omicsontkb.delete();
		if (this.neuroontkb != null)
			this.neuroontkb.delete();

		this.initializeKB();
	}

	public void initializeKB() {
		super.initializeKB();
		if (fac == null)
			return;
		try {

			// String DownloadPath = "C:/Users/rrreg/neuroOnt.ttl";
			// downloadOntology(KBConstants.NEUROURI(), DownloadPath);
			// InputStream is = new FileInputStream(new File(DownloadPath));
			this.neuroontkb = fac.getKB(KBConstants.NEUROURI(), OntSpec.PLAIN, false, true);
			this.vocabularies = new HashMap<String, Vocabulary>();
			this.vocabularies.put(KBConstants.NEUROURI(),
					this.initializeVocabularyFromKB(this.neuroontkb, KBConstants.NEURONS()));
			// System.out.println("KBConstants.DISKNS(): "+KBConstants.DISKNS());
			// System.out.println("ontkb: "+this.ontkb);
			this.hypontkb = fac.getKB(KBConstants.HYPURI(), OntSpec.PLAIN, false, true);
			this.vocabularies.put(KBConstants.HYPURI(),
					this.initializeVocabularyFromKB(this.hypontkb, KBConstants.HYPNS()));
			this.omicsontkb = fac.getKB(KBConstants.OMICSURI(), OntSpec.PLAIN, false, true);
			this.vocabularies.put(KBConstants.OMICSURI(),
					this.initializeVocabularyFromKB(this.omicsontkb, KBConstants.OMICSNS()));
			this.vocabularies.put(KBConstants.DISKURI(),
					this.initializeVocabularyFromKB(this.ontkb, KBConstants.DISKNS()));
		} catch (Exception e) {
			e.printStackTrace();
		}
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
			return this.initializeVocabularyFromKB(kb, url + "#");
		} catch (Exception e) {
			e.printStackTrace();
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
					// System.out.println(t);
					// System.out.println(inst);
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
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");
			for (KBTriple t : kb.genericTripleQuery(null, typeprop, hypcls)) {
				KBObject hypobj = t.getSubject();
				String name = kb.getLabel(hypobj);
				String description = kb.getComment(hypobj);

				String parentid = null;
				KBObject parentobj = kb.getPropertyValue(hypobj, pmap.get("hasParentHypothesis"));
				if (parentobj != null)
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

			this.updateTripleDetails(graph, provkb);

			return hypothesis;

		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

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
				KBObject hypitem = kb.getIndividual(fullid);
				if (hypitem != null) {
					for (KBTriple t : kb.genericTripleQuery(null, pmap.get("hasParentHypothesis"), hypitem)) {
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
		if (hypothesis.getId() == null)
			return false;

		String url = this.HYPURI(username, domain);
		String fullid = url + "/" + hypothesis.getId();
		String provid = fullid + "/provenance";

		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBAPI hypkb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
			KBAPI provkb = this.fac.getKB(provid, OntSpec.PLAIN, true);

			KBObject hypitem = kb.createObjectOfClass(fullid, this.cmap.get("Hypothesis"));
			if (hypothesis.getName() != null)
				kb.setLabel(hypitem, hypothesis.getName());
			if (hypothesis.getDescription() != null)
				kb.setComment(hypitem, hypothesis.getDescription());
			if (hypothesis.getParentId() != null) {
				String fullparentid = url + "/" + hypothesis.getParentId();
				kb.setPropertyValue(hypitem, pmap.get("hasParentHypothesis"), kb.getResource(fullparentid));
			}

			for (Triple triple : hypothesis.getGraph().getTriples()) {
				KBTriple t = this.getKBTriple(triple, hypkb);
				if (t != null)
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

	private String getSparqlQuery(String queryPattern, String assertionsUri) {

		return "PREFIX bio: <" + KBConstants.OMICSNS() + ">\n" + "PREFIX neuro: <" + KBConstants.NEURONS() + ">\n"
				+ "PREFIX hyp: <" + KBConstants.HYPNS() + ">\n" + "PREFIX xsd: <" + KBConstants.XSDNS() + ">\n"
				+ "PREFIX user: <" + assertionsUri + "#>\n\n" + "SELECT *\n" + "WHERE { \n" + queryPattern + "}\n";
	}

	private String getPrefixedItem(KBObject item, Map<String, String> nsmap) {
		if (item.isLiteral())
			return item.getValueAsString();
		else {
			if (item.getID().equals(KBConstants.RDFNS() + "type"))
				return "a";
			String valns = item.getNamespace();
			if (nsmap.containsKey(valns))
				return nsmap.get(valns) + item.getName();
			else
				return "<" + item.getID() + ">";
		}
	}

	public List<TriggeredLOI> queryHypothesis(String username, String domain, String id) {
		String hypuri = this.HYPURI(username, domain) + "/" + id;
		String assertions = this.ASSERTIONSURI(username, domain);

		Map<String, String> nsmap = new HashMap<String, String>();
		nsmap.put(KBConstants.OMICSNS(), "bio:");
		nsmap.put(KBConstants.NEURONS(), "neuro:");
		nsmap.put(KBConstants.HYPNS(), "hyp:");
		nsmap.put(KBConstants.XSDNS(), "xsd:");
		nsmap.put(assertions + "#", "user:");
		nsmap.put(hypuri + "#", "?");

		List<TriggeredLOI> tlois = new ArrayList<TriggeredLOI>();
		try {
			KBAPI queryKb = this.fac.getKB(OntSpec.PLAIN);
			KBAPI hypkb = this.fac.getKB(hypuri, OntSpec.PLAIN);
			queryKb.importFrom(this.omicsontkb);
			queryKb.importFrom(this.neuroontkb);
			queryKb.importFrom(this.hypontkb);
			queryKb.importFrom(hypkb);
			queryKb.importFrom(this.fac.getKB(assertions, OntSpec.PLAIN));

			Pattern varPattern = Pattern.compile("\\?(.+?)\\b");
			// get according loi
			String hypPattern = "";
			for (KBTriple t : hypkb.getAllTriples()) {
				String subject = this.getPrefixedItem(t.getSubject(), nsmap);
				String predicate = this.getPrefixedItem(t.getPredicate(), nsmap);
				String object = this.getPrefixedItem(t.getObject(), nsmap);
				hypPattern += subject + " " + predicate + " " + object + " .\n";
			}

			for (TreeItem item : this.listLOIs(username, domain)) {
				LineOfInquiry loi = this.getLOI(username, domain, item.getId());
				String hypothesisQuery = loi.getHypothesisQuery();

				String dataQuery = loi.getDataQuery();
				if (hypothesisQuery == null || hypothesisQuery.equals("") || dataQuery == null || dataQuery.equals(""))
					continue;

				hypothesisQuery = this.getQueryBindings(hypothesisQuery, null, null);

				String hypSparqlQuery = this.getSparqlQuery(hypothesisQuery, assertions);
				for (ArrayList<SparqlQuerySolution> hypothesisSolutions : queryKb.sparqlQuery(hypSparqlQuery)) {
					Map<String, String> hypVarBindings = new HashMap<String, String>();
					for (SparqlQuerySolution solution : hypothesisSolutions) {
						String value;
						if (solution.getObject().isLiteral())
							value = solution.getObject().getValueAsString();
						else {
							String valns = solution.getObject().getNamespace();
							if (nsmap.containsKey(valns))
								value = nsmap.get(valns) + solution.getObject().getName();
							else
								value = "<" + solution.getObject().getID() + ">";
						}
						hypVarBindings.put(solution.getVariable(), value);
					}

					String boundHypothesisQuery = this.getQueryBindings(hypothesisQuery, varPattern, hypVarBindings);
					String boundDataQuery = this.getQueryBindings(dataQuery, varPattern, hypVarBindings);

					boundDataQuery = hypPattern + boundHypothesisQuery + boundDataQuery;
					boundDataQuery = this.filterQueryBindings(boundDataQuery, "hyp:");
					String dataSparqlQuery = this.getSparqlQuery(boundDataQuery, assertions);
					TriggeredLOI tloi = null;

					for (ArrayList<SparqlQuerySolution> dataSolutions : queryKb.sparqlQuery(dataSparqlQuery)) {// kbapijena
																												// sparqlquery
						// TODO: What to do with multiple data solutions ?
						// - Separate triggered line of inquiries for each one ?
						// - Multiple workflows in the same triggered line of
						// inquiry ?
						// - What if workflows take collections as input ?
						if (tloi == null) {
							tloi = new TriggeredLOI(loi, id);
							tloi.copyWorkflowBindings(loi.getMetaWorkflows(), tloi.getMetaWorkflows());
						}

						Map<String, String> dataVarBindings = new HashMap<String, String>();
						for (SparqlQuerySolution solution : dataSolutions) {
							String value;
							if (solution.getObject().isLiteral())
								value = solution.getObject().getValueAsString();
							else
								value = solution.getObject().getName();
							dataVarBindings.put(solution.getVariable(), value);
						}

						for (WorkflowBindings bindings : loi.getWorkflows()) {
							WorkflowBindings newbindings = new WorkflowBindings();
							newbindings.setWorkflow(bindings.getWorkflow());

							@SuppressWarnings("unchecked")
							ArrayList<VariableBinding> vbindings = (ArrayList<VariableBinding>) SerializationUtils
									.clone((Serializable) bindings.getBindings());

							for (VariableBinding vbinding : vbindings) {
								String binding = vbinding.getBinding();
								Matcher mat = varPattern.matcher(binding);
								if (mat.find() && dataVarBindings.containsKey(mat.group(1))) {
									binding = mat.replaceAll(dataVarBindings.get(mat.group(1)));
								}
								vbinding.setBinding(binding);
							}
							newbindings.setBindings(vbindings);
							tloi.getWorkflows().add(newbindings);
						}
					}
					if (tloi != null)
						tlois.add(tloi);
				}
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
		return tlois;
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

	public String[] addQuery(String username, String domain, String query, String type) {
		try {

			String[] temp = DataQuery.queryFor(query.substring(query.indexOf(" ") + 1))[1].split("\n\",\"\n");
			for (int j = 0; j < temp.length - 1; j += 2) {
				WingsAdapter.get().addOrUpdateData(username, domain, temp[j].substring(4), type, temp[j + 1], false);
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
			for (Triple triple : assertion.getTriples()) {
				KBTriple t = this.getKBTriple(triple, kb);
				if (t != null)
					kb.addTriple(t);
			}
			kb.save();
			// Re-run hypotheses if needed
			this.requeryHypotheses(username, domain);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public Graph listAssertions(String username, String domain) {
		String url = this.ASSERTIONSURI(username, domain);
		return this.getKBGraph(url);
	}

	public List<String> getQueriesToBeRun(Graph assertions) {
		List<Triple> UITriples = assertions.getTriples();
		HashSet<String> toQuery = new HashSet<String>();
		String temp;
		String[] temp2;
		String file;
		String query;
		Triple tri;
		for (int i = 0; i < UITriples.size(); i++) {
			// System.out.println("UITiples.get(i).getSubject():
			// "+UITriples.get(i).getSubject());
			// System.out.println("UITiples.get(i).getPredicate():
			// "+UITriples.get(i).getPredicate());
			// System.out.println("UITiples.get(i).getObject():
			// "+UITriples.get(i).getObject());
			temp = UITriples.get(i).getPredicate(); // check if asking for query
			if (temp.equals(KBConstants.NEURONS() + "hasEnigmaQueryLiteral")) {
				temp = UITriples.get(i).getSubject().toString();
				file = temp.substring(temp.indexOf("#") + 1);
				temp = UITriples.get(i).getObject().getValue().toString();
				query = temp.replace("|", "/");
				// query = DataQuery.toMachineReadableQuery(query);
				toQuery.add(file + " " + query);
				// temp2 = DataQuery.queryFor(temp);
				// System.out.println("here we are:" + temp2[1]);
				// temp2 = temp2[1].split("\",\"");
				// tri = new Triple();
				// tri.setSubject(this.ASSERTIONSURI(username,
				// domain)+"#"+temp2.length);
				// tri.setPredicate(UITriples.get(i).getPredicate());
				// tri.setObject(UITriples.get(i).getObject());
				// UITriples.add(tri);

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
			kb.delete();
			this.addAssertion(username, domain, assertions);

		} catch (Exception e) {
			e.printStackTrace();
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
			KBObject loiitem = kb.createObjectOfClass(fullid, this.cmap.get("LineOfInquiry"));
			KBObject floiitem = loikb.createObjectOfClass(fullid, this.cmap.get("LineOfInquiry"));
			if (loi.getName() != null)
				kb.setLabel(loiitem, loi.getName());
			if (loi.getDescription() != null)
				kb.setComment(loiitem, loi.getDescription());
			if (loi.getHypothesisQuery() != null) {
				KBObject valobj = loikb.createLiteral(loi.getHypothesisQuery());
				loikb.setPropertyValue(floiitem, pmap.get("hasHypothesisQuery"), valobj);
			}
			if (loi.getDataQuery() != null) {
				KBObject valobj = loikb.createLiteral(loi.getDataQuery());
				loikb.setPropertyValue(floiitem, pmap.get("hasDataQuery"), valobj);
			}
			this.storeWorkflowBindingsInKB(loikb, floiitem, pmap.get("hasWorkflowBinding"), loi.getWorkflows(),
					username, domain);
			this.storeWorkflowBindingsInKB(loikb, floiitem, pmap.get("hasMetaWorkflowBinding"), loi.getMetaWorkflows(),
					username, domain);

			return kb.save() && loikb.save();
		} catch (Exception e) {
			e.printStackTrace();
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
		List<TreeItem> list = new ArrayList<TreeItem>();
		String url = this.LOIURI(username, domain);
		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBObject hypcls = this.cmap.get("LineOfInquiry");
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");
			for (KBTriple t : kb.genericTripleQuery(null, typeprop, hypcls)) {
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
			if (loiitem == null)
				return null;

			LineOfInquiry loi = new LineOfInquiry();
			loi.setId(id);
			loi.setName(kb.getLabel(loiitem));
			loi.setDescription(kb.getComment(loiitem));

			KBObject floiitem = loikb.getIndividual(fullid);
			KBObject hqueryobj = loikb.getPropertyValue(floiitem, pmap.get("hasHypothesisQuery"));
			if (hqueryobj != null)
				loi.setHypothesisQuery(hqueryobj.getValueAsString());

			KBObject dqueryobj = loikb.getPropertyValue(floiitem, pmap.get("hasDataQuery"));
			if (dqueryobj != null)
				loi.setDataQuery(dqueryobj.getValueAsString());

			loi.setWorkflows(
					this.getWorkflowBindingsFromKB(username, domain, loikb, floiitem, pmap.get("hasWorkflowBinding")));
			loi.setMetaWorkflows(this.getWorkflowBindingsFromKB(username, domain, loikb, floiitem,
					pmap.get("hasMetaWorkflowBinding")));

			return loi;
		} catch (Exception e) {
			e.printStackTrace();
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
			if (workflowobj != null)
				bindings.setWorkflow(workflowobj.getName());
			bindings.setWorkflowLink(WingsAdapter.get().getWorkflowLink(username, domain, workflowobj.getName()));

			// Variable binding details
			for (KBObject vbobj : kb.getPropertyValues(wbobj, pmap.get("hasVariableBinding"))) {
				KBObject varobj = kb.getPropertyValue(vbobj, pmap.get("hasVariable"));
				KBObject bindobj = kb.getPropertyValue(vbobj, pmap.get("hasBindingValue"));
				VariableBinding vbinding = new VariableBinding();
				vbinding.setVariable(varobj.getName());
				vbinding.setBinding(bindobj.getValueAsString());
				bindings.getBindings().add(vbinding);
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
			KBObject cls = this.cmap.get("TriggeredLineOfInquiry");
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");

			for (KBTriple t : kb.genericTripleQuery(null, typeprop, cls)) {
				KBObject obj = t.getSubject();

				TriggeredLOI tloi = this.getTriggeredLOI(username, domain, obj.getID(), kb, null);

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
				KBObject item = kb.getIndividual(fullid);
				KBObject hypobj = kb.getPropertyValue(item, pmap.get("hasResultingHypothesis"));
				if (hypobj != null) {
					for (KBTriple t : kb.genericTripleQuery(null, pmap.get("hasParentHypothesis"), hypobj)) {
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

	private TriggeredLOI getTriggeredLOI(String username, String domain, String id, KBAPI kb, KBAPI tloikb) {
		TriggeredLOI tloi = new TriggeredLOI();
		KBObject obj = kb.getIndividual(id);

		tloi.setId(obj.getName());
		tloi.setName(kb.getLabel(obj));
		tloi.setDescription(kb.getComment(obj));

		KBObject lobj = kb.getPropertyValue(obj, pmap.get("hasLineOfInquiry"));
		if (lobj != null)
			tloi.setLoiId(lobj.getName());

		KBObject pobj = kb.getPropertyValue(obj, pmap.get("hasParentHypothesis"));
		if (pobj != null)
			tloi.setParentHypothesisId(pobj.getName());

		for (KBObject robj : kb.getPropertyValues(obj, pmap.get("hasResultingHypothesis")))
			tloi.addResultingHypothesisId(robj.getName());

		KBObject stobj = kb.getPropertyValue(obj, pmap.get("hasTriggeredLineOfInquiryStatus"));
		if (stobj != null)
			tloi.setStatus(Status.valueOf(stobj.getValue().toString()));

		if (tloikb != null) {
			KBObject floiitem = tloikb.getIndividual(id);
			tloi.setWorkflows(
					this.getWorkflowBindingsFromKB(username, domain, tloikb, floiitem, pmap.get("hasWorkflowBinding")));
			tloi.setMetaWorkflows(this.getWorkflowBindingsFromKB(username, domain, tloikb, floiitem,
					pmap.get("hasMetaWorkflowBinding")));
		}
		return tloi;
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

		String url = this.TLOIURI(username, domain);
		String fullid = url + "/" + tloi.getId();
		String hypns = this.HYPURI(username, domain) + "/";
		String loins = this.LOIURI(username, domain) + "/";

		try {
			KBAPI kb = this.fac.getKB(url, OntSpec.PLAIN, true);
			KBAPI tloikb = this.fac.getKB(fullid, OntSpec.PLAIN, true);
			KBObject tloiitem = kb.createObjectOfClass(fullid, this.cmap.get("TriggeredLineOfInquiry"));
			KBObject ftloiitem = tloikb.createObjectOfClass(fullid, this.cmap.get("TriggeredLineOfInquiry"));
			if (tloi.getName() != null) {
				kb.setLabel(tloiitem, tloi.getName());
			}
			if (tloi.getDescription() != null) {
				kb.setComment(tloiitem, tloi.getDescription());
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
			this.storeWorkflowBindingsInKB(tloikb, ftloiitem, pmap.get("hasWorkflowBinding"), tloi.getWorkflows(),
					username, domain);
			this.storeWorkflowBindingsInKB(tloikb, ftloiitem, pmap.get("hasMetaWorkflowBinding"),
					tloi.getMetaWorkflows(), username, domain);

			return kb.save() && tloikb.save();
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

	private String fetchOutputHypothesis(String username, String domain, WorkflowBindings bindings, TriggeredLOI tloi) {
		String varname = bindings.getMeta().getRevisedHypothesis();
		Map<String, String> varmap = WingsAdapter.get().getRunVariableBindings(username, domain,
				bindings.getRun().getId());
		if (varmap.containsKey(varname)) {
			String dataid = varmap.get(varname);
			String dataname = dataid.replaceAll(".*#", "");
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

				WingsAdapter wings = WingsAdapter.get();

				List<WorkflowBindings> wflowBindings = tloi.getWorkflows();
				if (this.metamode)
					wflowBindings = tloi.getMetaWorkflows();

				ArrayList<KBTriple> equeries = new ArrayList<KBTriple>();
				try {
					String url = ASSERTIONSURI(username, domain);
					KBAPI kb = fac.getKB(url, OntSpec.PLAIN, false);
					KBObject typeprop = kb.getProperty(KBConstants.NEURONS() + "hasEnigmaQueryLiteral");
					equeries = kb.genericTripleQuery(null, typeprop, null);
				} catch (Exception e) {
					e.printStackTrace();
				}

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
												inputs.get(var.getVariable()).getType());
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
									newwfb.setMeta(
											new MetaWorkflowDetails(wflowBindings.get(i).getMeta().getHypothesis(),
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
				// Start off workflows from tloi
				for (WorkflowBindings bindings : wflowBindings) {
					// Get workflow input details
					Map<String, Variable> inputs = wings.getWorkflowInputs(username, domain, bindings.getWorkflow());
					List<VariableBinding> vbindings = bindings.getBindings();
					List<VariableBinding> sendbindings = new ArrayList<VariableBinding>(vbindings);

					// Special processing for Meta Workflows
					if (this.metamode) {
						// Replace workflow ids with workflow run ids in
						// Variable Bindings
						for (VariableBinding vbinding : vbindings) {
							String runids = getWorkflowExecutionRunIds(tloi, vbinding.getBinding());
							vbinding.setBinding(runids);
						}
						// Upload hypothesis to Wings as a file, and add to
						// Variable Bindings
						String hypVarId = bindings.getMeta().getHypothesis();
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
				monitor.schedule(monitorThread, 2, TimeUnit.SECONDS);
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
					monitor.schedule(this, 2, TimeUnit.SECONDS);
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
			scheduledFuture = monitor.scheduleWithFixedDelay(this, 5, 24, TimeUnit.SECONDS);
		}

		public void run() {
			try {
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
					 KBObject typeprop = kb.getProperty(KBConstants.NEURONS() +
					 "hasEnigmaQueryLiteral");
					 List<KBTriple> equeries = kb.genericTripleQuery(null, typeprop, null);
					 for (KBTriple kbt : equeries)
					 if (DataQuery.wasUpdatedInLastDay(kbt.getObject().getValueAsString())) {
					 requeryHypotheses(defaultUsername, defaultDomain);
					 System.out.println(this.toString());
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
