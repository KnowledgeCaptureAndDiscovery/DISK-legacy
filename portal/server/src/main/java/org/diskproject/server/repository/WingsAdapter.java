package org.diskproject.server.repository;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Type;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.FileUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.CookieStore;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpHead;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.cookie.Cookie;
import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntityBuilder;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.BasicCookieStore;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.impl.cookie.BasicClientCookie;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.message.BasicHeader;
import org.apache.http.util.EntityUtils;
import org.diskproject.server.util.Config;
import org.diskproject.shared.classes.util.KBConstants;
import org.diskproject.shared.classes.workflow.Variable;
import org.diskproject.shared.classes.workflow.VariableBinding;
import org.diskproject.shared.classes.workflow.Workflow;
import org.diskproject.shared.classes.workflow.WorkflowRun;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import edu.isi.wings.ontapi.KBAPI;
import edu.isi.wings.ontapi.KBObject;
import edu.isi.wings.ontapi.KBTriple;
import edu.isi.wings.ontapi.OntFactory;
import edu.isi.wings.ontapi.OntSpec;

public class WingsAdapter {
	static WingsAdapter singleton = null;

	private Gson json;
	private String server;
	private Map<String, String> sessions;
	private String wflowns = "http://www.wings-workflows.org/ontology/workflow.owl#";
	private String execns = "http://www.wings-workflows.org/ontology/execution.owl#";

	public static WingsAdapter get() {
		if (singleton == null)
			singleton = new WingsAdapter();
		return singleton;
	}

	public WingsAdapter() {
		this.sessions = new HashMap<String, String>();
		this.server = Config.get().getProperties().getString("wings.server");
		this.json = new Gson();
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

	public String DATAID(String username, String domain, String id) {
		return this.DOMURI(username, domain) + "/data/library.owl#" + id;
	}

	public String RUNURI(String username, String domain, String id) {
		return this.DOMURI(username, domain) + "/executions/" + id + ".owl";
	}

	public String RUNID(String username, String domain, String id) {
		return this.RUNURI(username, domain, id) + "#" + id;
	}

	public List<Workflow> getWorkflowList(String username, String domain) {
		String liburi = this.WFLOWURI(username, domain) + "/library.owl";
		try {
			List<Workflow> list = new ArrayList<Workflow>();
			OntFactory fac = new OntFactory(OntFactory.JENA);
			KBAPI kb = fac.getKB(liburi, OntSpec.PLAIN);
			KBObject typeprop = kb.getProperty(KBConstants.RDFNS() + "type");
			KBObject templatecls = kb.getResource(this.wflowns
					+ "WorkflowTemplate");
			for (KBTriple triple : kb.genericTripleQuery(null, typeprop,
					templatecls)) {
				KBObject tobj = triple.getSubject();
				Workflow wflow = new Workflow();
				wflow.setName(tobj.getName());
				wflow.setLink(this.getWorkflowLink(username, domain,
						wflow.getName()));
				list.add(wflow);
			}
			return list;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public List<Variable> getWorkflowVariables(String username, String domain,
			String id) {
		String wflowuri = this.WFLOWURI(username, domain, id);
		try {
			List<Variable> list = new ArrayList<Variable>();
			Map<String, Boolean> varmap = new HashMap<String, Boolean>();
			OntFactory fac = new OntFactory(OntFactory.JENA);
			KBAPI kb = fac.getKB(wflowuri, OntSpec.PLAIN);
			KBObject linkprop = kb.getProperty(this.wflowns + "hasLink");
			KBObject origprop = kb.getProperty(this.wflowns + "hasOriginNode");
			KBObject destprop = kb.getProperty(this.wflowns
					+ "hasDestinationNode");
			KBObject varprop = kb.getProperty(this.wflowns + "hasVariable");
			KBObject paramcls = kb.getConcept(this.wflowns
					+ "ParameterVariable");

			for (KBTriple triple : kb.genericTripleQuery(null, linkprop, null)) {
				KBObject linkobj = triple.getObject();
				KBObject orignode = kb.getPropertyValue(linkobj, origprop);
				KBObject destnode = kb.getPropertyValue(linkobj, destprop);

				// Only return Input and Output variables
				if (orignode != null && destnode != null)
					continue;

				KBObject varobj = kb.getPropertyValue(linkobj, varprop);

				if (varmap.containsKey(varobj.getID()))
					continue;
				varmap.put(varobj.getID(), true);

				Variable var = new Variable();
				var.setName(varobj.getName());
				if (orignode == null)
					var.setInput(true);

				if (kb.isA(varobj, paramcls))
					var.setParam(true);

				list.add(var);
			}
			return list;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public Map<String, String> getRunVariableBindings(String username,
			String domain, String runid) {
		String runuri = runid.replace("#.*", "");
		try {
			OntFactory fac = new OntFactory(OntFactory.JENA);
			KBAPI kb = fac.getKB(runuri, OntSpec.PLAIN);
			KBObject execobj = kb.getIndividual(runid);
			KBObject prop = kb.getProperty(execns + "hasExpandedTemplate");
			KBObject xtpl = kb.getPropertyValue(execobj, prop);
			String xtpluri = xtpl.getID().replaceAll("#.*", "");

			Map<String, String> varmap = new HashMap<String, String>();
			KBAPI xkb = fac.getKB(xtpluri, OntSpec.PLAIN);
			KBObject bindprop = xkb
					.getProperty(this.wflowns + "hasDataBinding");

			for (KBTriple triple : xkb.genericTripleQuery(null, bindprop, null)) {
				KBObject varobj = triple.getSubject();
				KBObject bindobj = triple.getObject();
				varmap.put(varobj.getName(), bindobj.getID());
			}
			return varmap;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	public Map<String, Variable> getWorkflowInputs(String username,
			String domain, String id) {
		String pageid = "users/" + username + "/" + domain
				+ "/workflows/getInputsJSON";

		String wflowid = this.WFLOWID(username, domain, id);
		List<NameValuePair> data = new ArrayList<NameValuePair>();
		data.add(new BasicNameValuePair("template_id", wflowid));

		String inputsjson = this.get(username, pageid, data);

		Type type = new TypeToken<List<Map<String, Object>>>() {
		}.getType();
		List<Map<String, Object>> list = json.fromJson(inputsjson, type);

		Map<String, Variable> inputs = new HashMap<String, Variable>();
		for (Map<String, Object> inputitem : list) {
			Variable var = new Variable();
			String varid = (String) inputitem.get("id");
			var.setName(varid.replaceAll("^.*#", ""));
			if (inputitem.containsKey("dim"))
				var.setDimensionality(((Double) inputitem.get("dim"))
						.intValue());
			if (inputitem.containsKey("dtype"))
				var.setType((String) inputitem.get("dtype"));
			String vartype = (String) inputitem.get("type");
			var.setParam(vartype.equals("param"));

			inputs.put(var.getName(), var);
		}
		return inputs;
	}

	private String login(String username) {
		String password = Config.get().getProperties()
				.getString("wings.passwords." + username);

		CloseableHttpClient client = HttpClients.createDefault();
		HttpClientContext context = HttpClientContext.create();
		try {
			HttpHead securedResource = new HttpHead(this.server + "/sparql");
			HttpResponse httpResponse = client
					.execute(securedResource, context);
			HttpEntity responseEntity = httpResponse.getEntity();
			int statusCode = httpResponse.getStatusLine().getStatusCode();
			EntityUtils.consume(responseEntity);
			if (statusCode != 200)
				return null;

			// Login
			HttpPost authpost = new HttpPost(this.server + "/j_security_check");
			List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
			nameValuePairs.add(new BasicNameValuePair("j_username", username));
			nameValuePairs.add(new BasicNameValuePair("j_password", password));
			authpost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
			for (int i = 1; i < 4; i++) {
				try {
					httpResponse = client.execute(authpost);
					responseEntity = httpResponse.getEntity();
					break;
				} catch (Exception e) {
					System.out.println("Tried " + i + " times");
					e.printStackTrace();
				}
			}
			statusCode = httpResponse.getStatusLine().getStatusCode();
			EntityUtils.consume(responseEntity);
			if (statusCode != 302)
				return null;

			httpResponse = client.execute(securedResource);
			responseEntity = httpResponse.getEntity();
			EntityUtils.consume(responseEntity);
			statusCode = httpResponse.getStatusLine().getStatusCode();
			if (statusCode != 200)
				return null;

			String sessionId = null;
			for (Cookie cookie : context.getCookieStore().getCookies()) {
				if (cookie.getName().equalsIgnoreCase("JSESSIONID"))
					sessionId = cookie.getValue();
			}
			System.out.println("Got session id: " + sessionId);
			return sessionId;
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		return null;
	}

	private CookieStore getCookieStore(String sessionId) {
		CookieStore cookieStore = new BasicCookieStore();
		BasicClientCookie cookie = new BasicClientCookie("JSESSIONID",
				sessionId);
		try {
			URL url = new URL(this.server);
			cookie.setDomain(url.getHost());
			cookie.setPath(url.getPath());
			cookieStore.addCookie(cookie);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return cookieStore;
	}

	public WorkflowRun getWorkflowRunStatus(String username, String domain,
			String runid) {
		try {
			// Get data
			String execid = RUNID(username, domain, runid);
			List<NameValuePair> formdata = new ArrayList<NameValuePair>();
			formdata.add(new BasicNameValuePair("run_id", execid));
			String pageid = "users/" + username + "/" + domain
					+ "/executions/getRunDetails";
			String runjson = this.post(username, pageid, formdata);
			if (runjson == null)
				return null;
			WorkflowRun wflowstatus = new WorkflowRun();
			wflowstatus.setId(execid);

			JsonParser jsonParser = new JsonParser();
			JsonObject runobj = jsonParser.parse(runjson).getAsJsonObject();
			JsonObject expobj = runobj.get("execution").getAsJsonObject();
			String status = expobj.get("runtimeInfo").getAsJsonObject()
					.get("status").getAsString();
			
			wflowstatus.setStatus(status);

			String link = this.server + "/users/" + username + "/" + domain
					+ "/executions";
			link += "?run_id=" + URLEncoder.encode(execid, "UTF-8");
			wflowstatus.setLink(link);
			return wflowstatus;
		} catch (Exception e) {
			e.printStackTrace();
		}
		return null;
	}

	// TODO: Hackish function. Fix it !!!! *IMPORTANT*
	private String getWorkflowRunWithSameBindings(String username,
			String domain, String templateid, List<VariableBinding> vbindings) {

		// Get all successful runs for the template (and their variable
		// bindings)
		String query = "PREFIX xsd: <http://www.w3.org/2001/XMLSchema#>\n"
				+ "PREFIX exec: <http://www.wings-workflows.org/ontology/execution.owl#>\n"
				+ "PREFIX wflow: <http://www.wings-workflows.org/ontology/workflow.owl#>\n"
				+ "\n"
				+ "SELECT ?run\n"
				+ "(group_concat(concat(strafter(str(?iv), \"#\"), \"=\", str(?b));separator=\"||\") as ?bindings)  \n"
				+ "WHERE {\n" + "  ?run a exec:Execution .\n"
				+ "  ?run exec:hasTemplate <" + templateid + "> .\n"
				+ "  ?run exec:hasExecutionStatus \"SUCCESS\"^^xsd:string .\n"
				+ "\n" + "  ?run exec:hasExpandedTemplate ?xt .\n"
				+ "  ?xt wflow:hasInputRole ?ir .\n"
				+ "  ?ir wflow:mapsToVariable ?iv .\n"
				+ "  OPTIONAL { ?iv wflow:hasDataBinding ?b } .\n"
				+ "  OPTIONAL { ?iv wflow:hasParameterValue ?b } .\n" + "}\n"
				+ "GROUP BY ?run";

		String pageid = "sparql";
		List<NameValuePair> formdata = new ArrayList<NameValuePair>();
		formdata.add(new BasicNameValuePair("query", query));
		formdata.add(new BasicNameValuePair("format", "json"));
		String resultjson = get(username, pageid, formdata);
		if (resultjson == null || resultjson.equals(""))
			return null;

		// Check the variable bindings to see if this matches the values that we
		// have
		JsonParser jsonParser = new JsonParser();
		JsonObject result = jsonParser.parse(resultjson).getAsJsonObject();
		JsonArray qbindings = result.get("results").getAsJsonObject()
				.get("bindings").getAsJsonArray();

		for (JsonElement qbinding : qbindings) {
			JsonObject qb = qbinding.getAsJsonObject();
			if (qb.get("run") == null)
				continue;
			String runid = qb.get("run").getAsJsonObject().get("value")
					.getAsString();
			String bindstrs = qb.get("bindings").getAsJsonObject().get("value")
					.getAsString();
			HashMap<String, String> keyvalues = new HashMap<String, String>();
			for (String bindstr : bindstrs.split("\\|\\|")) {
				String[] keyval = bindstr.split("=", 2);
				String varid = keyval[0];
				String value = keyval[1];
				keyvalues.put(varid, value);
			}
			String[] array = keyvalues.keySet().toArray(new String[keyvalues.keySet().size()]);
				for (String key : array) {
					if (isPartOfCollection(key)) {
						String newKey = key.substring(0, key.lastIndexOf("_"));
						if (keyvalues.get(newKey) == null)
							keyvalues.put(newKey, keyvalues.get(key));
						else {
							keyvalues.put(newKey, keyvalues.get(newKey) + ","
									+ keyvalues.get(key));
						}
						keyvalues.remove(key);
					}
				}
			boolean match = true;
			for (VariableBinding vbinding : vbindings) {
				String value = keyvalues.get(vbinding.getVariable());

				if (value == null) {
					match = false;
					break;
				}
				String[] tempValues = value.split(",");
				String[] vbindingValues = vbinding.getBinding().split(",");
				for (int i = 0; i < vbindingValues.length; i++) {
					boolean singleMatch = false;
					for (int j = 0; j < tempValues.length; j++) {
						if (vbindingValues[i].equals(tempValues[j]
								.substring(tempValues[j].indexOf("#") + 1))) {
							singleMatch = true;
							break;
						}
					}
					if (!singleMatch) {
						match = false;
						break;
					}
				}
			}

			if (match)
				return runid;
		}

		return null;
	}
	
	private boolean isPartOfCollection(String key) {
		if (key.lastIndexOf("_") != key.length() - 5)
			return false;
		for (int i = key.length() - 4; i < key.length(); i++) {
			if (!Character.isDigit(key.charAt(i)))
				return false;
		}
		return true;
	}

	public String getWorkflowLink(String username, String domain, String id) {
		return this.server + "/users/" + username + "/" + domain
				+ "/workflows/" + id + ".owl";
	}

	public String runWorkflow(String username, String domain, String wflowname,
			List<VariableBinding> vbindings,
			Map<String, Variable> inputVariables) {
		try {
			wflowname = WFLOWURI(username,domain,wflowname)+"#" + wflowname;
			String toPost = toPlanAcceptableFormat(username, domain, wflowname,
					vbindings, inputVariables);
			String getData = postWithSpecifiedMediaType(username, "users/"+username+"/"+domain+"/plan/getData",
					toPost, "application/json", "application/json");
			vbindings = addDataBindings(inputVariables, vbindings, getData, false);
			toPost = toPlanAcceptableFormat(username, domain, wflowname, vbindings,
					inputVariables);
			String getParams = postWithSpecifiedMediaType(username, "users/"+username+"/"+domain+"/plan/getParameters", toPost,
					"application/json", "application/json");
			vbindings = addDataBindings(inputVariables, vbindings, getParams, true);
			toPost = toPlanAcceptableFormat(username, domain, wflowname, vbindings,
					inputVariables);

		// TODO: This should be called after getting expanded workflow.
		// - Create mapping data from expanded workflow, and then check.
		// - *NEEDED* to handle collections properly

			String runid = getWorkflowRunWithSameBindings(username, domain,
					wflowname, vbindings);
		if (runid != null) {
			System.out.println("Found existing run : " + runid);
			return runid;
		}
		String s = postWithSpecifiedMediaType(username, "users/"+username+"/"+domain+"/plan/getExpansions",
				toPost, "application/json", "application/json");
		JsonParser jsonParser = new JsonParser();

		JsonObject expobj = (JsonObject) jsonParser.parse(s);
		JsonObject dataobj = expobj.get("data").getAsJsonObject();

		JsonArray templatesobj = dataobj.get("templates").getAsJsonArray();
		if (templatesobj.size() == 0)
			return null;
		JsonObject templateobj = templatesobj.get(0).getAsJsonObject();
		JsonObject seedobj = dataobj.get("seed").getAsJsonObject();

		// Run the first Expanded workflow
		List<NameValuePair> formdata = new ArrayList<NameValuePair>();
		formdata.add(new BasicNameValuePair("template_id", wflowname));
		formdata.add(new BasicNameValuePair("json", templateobj.get("template")
				.toString()));
		formdata.add(new BasicNameValuePair("constraints_json", templateobj
				.get("constraints").toString()));
		formdata.add(new BasicNameValuePair("seed_json", seedobj
				.get("template").toString()));
		formdata.add(new BasicNameValuePair("seed_constraints_json", seedobj
				.get("constraints").toString()));
		String pageid = "users/" + username + "/" + domain
				+ "/executions/runWorkflow";
		runid = post(username, pageid, formdata);
		return runid;
	} catch (Exception e) {
		e.printStackTrace();
	}
	return null;
}

public String fetchDataFromWings(String username, String domain,
		String dataid) {
	String getpage = "users/" + username + "/" + domain + "/data/fetch";

	// Check for data already present on the server
	List<NameValuePair> formdata = new ArrayList<NameValuePair>();
	formdata.add(new BasicNameValuePair("data_id", dataid));
	return this.get(username, getpage, formdata);
}

public String addOrUpdateData(String username, String domain, String id,
		String type, String contents, boolean addServer) {
	if(addServer)
		type = this.server+type;
	String getpage = "users/" + username + "/" + domain
			+ "/data/getDataJSON";
	String postpage = "users/" + username + "/" + domain
			+ "/data/addDataForType";
	String uploadpage = "users/" + username + "/" + domain + "/upload";
	String locationpage = "users/" + username + "/" + domain + "/data/setDataLocation";

	String dataid = this.DATAID(username, domain, id);
	List<NameValuePair> formdata = new ArrayList<NameValuePair>();

	System.out.println("Upload " + id);
	String response = null;
	try {
		File dir = File.createTempFile("tmp", "");
		if (!dir.delete() || !dir.mkdirs()) {
			System.err.println("Could not create temporary directory "
					+ dir);
			return null;
		}
		File f = new File(dir.getAbsolutePath() + "/" + id);
		FileUtils.write(f, contents);
		this.upload(username, uploadpage, "data", f);
		f.delete();

		List<NameValuePair> data = new ArrayList<NameValuePair>();
		data.add(new BasicNameValuePair("data_id", dataid));
		data.add(new BasicNameValuePair("data_type", type));
		response = post(username, postpage, data);
		List<NameValuePair> location = new ArrayList<NameValuePair>();
		location.add(new BasicNameValuePair("data_id", dataid));
		location.add(new BasicNameValuePair("location", "/scratch/data/wings/storage/default/users/"+username+"/"+domain+"/data/"+dataid.substring(dataid.indexOf('#')+1)));
		response = post(username, locationpage, location);
		if(response.equals("OK"))
			System.out.println("Upload successful.");
		else 
			System.out.println("Upload failed.");
	} catch (Exception e) {
		System.out.println("Upload failed.");
		e.printStackTrace();
	}
	return response;
}

public String addDataToWings(String username, String domain, String id,
		String type, String contents) {

	String getpage = "users/" + username + "/" + domain
			+ "/data/getDataJSON";
	String postpage = "users/" + username + "/" + domain
			+ "/data/addDataForType";
	String uploadpage = "users/" + username + "/" + domain + "/upload";

	// Add unique md5 hash to id based on contents
	String md5 = DigestUtils.md5Hex(contents.getBytes());
	Pattern extensionPattern = Pattern.compile("^(.*)(\\..+)$");
	Matcher mat = extensionPattern.matcher(id);
	if (mat.find()) {
		id = mat.group(1) + "-" + md5 + mat.group(2);
	} else
		id += "-" + md5;

	// Check for data already present on the server
	String dataid = this.DATAID(username, domain, id);
	List<NameValuePair> formdata = new ArrayList<NameValuePair>();
	formdata.add(new BasicNameValuePair("data_id", dataid));
	String datajson = this.get(username, getpage, formdata);
	if (datajson != null && !datajson.trim().equals("null")) {
		// Already there
		return dataid;
	}

	System.out.println("Not found, upload " + id);
	// If not present, Create temporary file and upload
	try {
		File dir = File.createTempFile("tmp", "");
		if (!dir.delete() || !dir.mkdirs()) {
			System.err.println("Could not create temporary directory "
					+ dir);
			return null;
		}
		File f = new File(dir.getAbsolutePath() + "/" + id);
		FileUtils.write(f, contents);
		this.upload(username, uploadpage, "data", f);
		f.delete();

		List<NameValuePair> data = new ArrayList<NameValuePair>();
		data.add(new BasicNameValuePair("data_id", dataid));
		data.add(new BasicNameValuePair("data_type", type));
		String response = this.post(username, postpage, data);
		if (response != null && response.equals("OK"))
			return dataid;
	} catch (Exception e) {
		e.printStackTrace();
	}
	return null;
}

private List<NameValuePair> getBindings(String json,
		List<VariableBinding> initbindings, List<NameValuePair> formdata) {

	Map<String, Boolean> isBound = new HashMap<String, Boolean>();
	for (VariableBinding vbinding : initbindings)
		isBound.put(vbinding.getVariable(), true);

	if (json == null)
		return null;

	JsonParser jsonParser = new JsonParser();
	JsonObject expobj = jsonParser.parse(json.trim()).getAsJsonObject();

	if (!expobj.get("success").getAsBoolean())
		return null;

	JsonObject dataobj = expobj.get("data").getAsJsonObject();
	JsonArray bindingsobj = dataobj.get("bindings").getAsJsonArray();
	if (bindingsobj.size() == 0)
		return formdata;

	JsonObject bindingobj = bindingsobj.get(0).getAsJsonObject();
	for (Entry<String, JsonElement> entry : bindingobj.entrySet()) {
		if (!isBound.containsKey(entry.getKey())) {
			if (entry.getValue().isJsonArray()) {
				for (JsonElement bindingEl : entry.getValue()
						.getAsJsonArray())
					formdata.add(new BasicNameValuePair(entry.getKey(),
							this.getJsonBindingValue(bindingEl)));
			} else {
				formdata.add(new BasicNameValuePair(entry.getKey(), this
						.getJsonBindingValue(entry.getValue())));
			}
		}
	}
	return formdata;
}

private String getJsonBindingValue(JsonElement el) {
	JsonObject obj = el.getAsJsonObject();
	if (obj.get("type").getAsString().equals("uri"))
		return obj.get("id").getAsString();
	else
		return obj.get("value").getAsString();
}

private List<NameValuePair> createFormData(String username, String domain,
		String templateid, List<VariableBinding> bindings,
		Map<String, Variable> inputs) {
	List<NameValuePair> data = new ArrayList<NameValuePair>();
	HashMap<String, String> paramDTypes = new HashMap<String, String>();


	data.add(new BasicNameValuePair("templateId", templateid));

	for (VariableBinding vbinding : bindings) {
		if (paramDTypes.containsKey(vbinding.getVariable())) {
			data.add(new BasicNameValuePair(vbinding.getVariable(),
					vbinding.getBinding()));
		} else {
			data.add(new BasicNameValuePair(vbinding.getVariable(), DATAID(
					username, domain, vbinding.getBinding())));
		}
	}
	data.add(new BasicNameValuePair("__paramdtypes", json
			.toJson(paramDTypes)));
	return data;
}

private String get(String username, String pageid, List<NameValuePair> data) {
	String sessionId = this.sessions.get(username);

	if (sessionId == null) {
		sessionId = this.login(username);
		if (sessionId == null)
			return null;
		this.sessions.put(username, sessionId);
		return this.get(username, pageid, data);
	}
	for (int i = 0; i < 3; i++) {
		CloseableHttpClient client = HttpClientBuilder.create()
				.setDefaultCookieStore(this.getCookieStore(sessionId))
				.build();
		try {
			String url = this.server + "/" + pageid;
			if (data != null && data.size() > 0) {
				url += "?" + URLEncodedUtils.format(data, "UTF-8");
			}
			HttpGet securedResource = new HttpGet(url);
			CloseableHttpResponse httpResponse = client
					.execute(securedResource);

			try {
				HttpEntity responseEntity = httpResponse.getEntity();
				String strResponse = EntityUtils.toString(responseEntity);
				EntityUtils.consume(responseEntity);
				httpResponse.close();

				if (strResponse.indexOf("j_security_check") > 0) {
					sessionId = this.login(username);
					if (sessionId == null)
						return null;
					this.sessions.put(username, sessionId);
					return this.get(username, pageid, data);
				}
				return strResponse;
			} finally {
				httpResponse.close();
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			try {
				client.close();
			} catch (IOException e) {
			}
		}
	}
	return null;
}

private List<VariableBinding> addDataBindings(
		Map<String, Variable> inputVariables, List<VariableBinding> vbl,
		String data, boolean param) {

	JsonParser jsonParser = new JsonParser();
	try{
	JsonObject expobj = jsonParser.parse(data.trim()).getAsJsonObject();
	}
	catch(Exception e){
		System.out.println("Problem parsing: "+data);
		return vbl;
	}
	JsonObject expobj = jsonParser.parse(data.trim()).getAsJsonObject();
	if (!expobj.get("success").getAsBoolean())
		return vbl;

	JsonObject dataobj = expobj.get("data").getAsJsonObject();
	String bindings = dataobj.get("bindings").getAsJsonArray().toString();
	if (bindings.length() < 7)
		return vbl;

	bindings = bindings.substring(1, bindings.length() - 1);
	if (!param)
		bindings = bindings.substring(1, bindings.length() - 1)
				.replace("}]}],[{", "}],").replace("}}],[{", "},")
				.replace("}},{", "},");
	JsonObject bindingobj = jsonParser.parse(bindings.trim())
			.getAsJsonObject();

	for (String key : inputVariables.keySet()) {
		Variable v = inputVariables.get(key);
		boolean existing = false;
		if (!v.isParam() && !param) {
			for (int i = 0; i < vbl.size(); i++) {
				if (vbl.get(i).getVariable().equals(key)) {
					existing = true;
					break;
				}
			}
			if (!existing) {
				if (bindingobj.get(key).toString().charAt(0) == '[') {// is
																		// a
																		// collection
					JsonArray wingsData = bindingobj.get(key)
							.getAsJsonArray();
					String id = "";
					String temp;
					for (int i = 0; i < wingsData.size(); i++) {
						temp = wingsData.get(i).getAsJsonObject().get("id")
								.toString();
						temp = temp.substring(temp.indexOf("#") + 1,
								temp.length() - 1);
						id += temp + ",";
					}
					id = id.substring(0, id.length() - 1);
					vbl.add(new VariableBinding(key, id));
				} else {
					JsonObject wingsData = bindingobj.get(key)
							.getAsJsonObject();
					String id = wingsData.get("id").toString();
					id = id.substring(id.indexOf("#") + 1, id.length() - 1);
					vbl.add(new VariableBinding(key, id));
				}
			}
		} else if (v.isParam() && param) {
			for (int i = 0; i < vbl.size(); i++) {
				if (vbl.get(i).getVariable().equals(key)) {
					existing = true;
					break;
				}
			}
			if (!existing) {
				JsonObject wingsParam = bindingobj.get(key)
						.getAsJsonObject();
				String value = wingsParam.get("value").toString();
				value = value.substring(1, value.length() - 1);

				vbl.add(new VariableBinding(key,value));
			}
		}
	}
	return vbl;
}

private String toPlanAcceptableFormat(String username, String domain,
		String wfname, List<VariableBinding> vbl, Map<String, Variable> ivm) {
	String output = "";

	// Set Template ID first
	output += "{\"templateId\":\"" + wfname + "\",";
	wfname = wfname.substring(0, wfname.lastIndexOf("#") + 1);

	// Set Component Bindings
	output += "\"componentBindings\": {},";

	// Set Parameter Types
	String paramTypes = "\"parameterTypes\": {";
	for(String key: ivm.keySet()){
		if(ivm.get(key).isParam())
		paramTypes += "\"" + wfname + key + "\":\"" + ivm.get(key).getType()+"\",";
	}
	if(ivm.keySet().size()>0)
		paramTypes = paramTypes.substring(0,paramTypes.length()-1) + "}";
	output += paramTypes +",";
	
	// Set Inputs (Parameters and Data)
	String paramBindings = "\"parameterBindings\": {";
	boolean paramAdded = false;
	String dataBindings = "\"dataBindings\": {";
	boolean dataAdded = false;
	String dataID = server + "/export/users/" + username + "/" + domain
			+ "/data/library.owl#";
	for (String key : ivm.keySet()) {
		Variable v = ivm.get(key);
		if (v.isParam()) {
			for (int i = 0; i < vbl.size(); i++) {
				VariableBinding vb = vbl.get(i);
				if (vb.getVariable().equals(v.getName())) {
					paramBindings += "\"" + wfname + v.getName() + "\":\""
							+ vb.getBinding() + "\",";
					paramAdded = true;
				}
			}
		} else {
			for (int i = 0; i < vbl.size(); i++) {
				VariableBinding vb = vbl.get(i);
				if (vb.getVariable().equals(v.getName())) {
					dataBindings += "\"" + wfname + v.getName() + "\":[";
					String[] dBs = vb.getBinding().split(",");
					for (int j = 0; j < dBs.length; j++) {
						if (dBs[j].length() > 0)
							dataBindings += "\"" + dataID + dBs[j] + "\",";
					}
					dataBindings = dataBindings.substring(0,
							dataBindings.length() - 1);
					dataBindings += "],";
					dataAdded = true;
				}
			}
		}

	}
	if (paramAdded)
		paramBindings = paramBindings.substring(0,
				paramBindings.length() - 1);
	if (dataAdded)
		dataBindings = dataBindings.substring(0, dataBindings.length() - 1);

	output += paramBindings + "}," + dataBindings + "}}";

	return output;
}

private String postWithSpecifiedMediaType(String username, String pageid, String data,
		String type, String type2) {
	String sessionId = this.sessions.get(username);
	if (sessionId == null) {
		sessionId = this.login(username);
		if (sessionId == null)
			return null;
		this.sessions.put(username, sessionId);
		return postWithSpecifiedMediaType(username, pageid, data, type, type2);
	}

	CloseableHttpClient client = HttpClientBuilder.create()
			.setDefaultCookieStore(this.getCookieStore(sessionId)).build();
	try {
		HttpPost securedResource = new HttpPost(server + "/" + pageid);
		securedResource.setEntity(new StringEntity(data));
		securedResource.addHeader("Accept", type);
		securedResource.addHeader("Content-type", type2);
		CloseableHttpResponse httpResponse = client
				.execute(securedResource);
		try {
		HttpEntity responseEntity = httpResponse.getEntity();
		String strResponse = EntityUtils.toString(responseEntity);
		EntityUtils.consume(responseEntity);
		httpResponse.close();

		if (strResponse.indexOf("j_security_check") > 0) {
			sessionId = this.login(username);
			if (sessionId == null)
				return null;
			this.sessions.put(username, sessionId);
			return postWithSpecifiedMediaType(username, pageid, data, type, type2);
		}
		return strResponse;
	} finally {
		httpResponse.close();
	}
} catch (Exception e) {
	e.printStackTrace();
} finally {
	try {
		client.close();
	} catch (IOException e) {
	}
}
return null;
}

private String post(String username, String pageid, List<NameValuePair> data) {
	String sessionId = this.sessions.get(username);
	if (sessionId == null) {
		sessionId = this.login(username);
		if (sessionId == null)
			return null;
		this.sessions.put(username, sessionId);
		return this.post(username, pageid, data);
	}

	CloseableHttpClient client = HttpClientBuilder.create()
			.setDefaultCookieStore(this.getCookieStore(sessionId)).build();

	try {
		HttpPost securedResource = new HttpPost(this.server + "/" + pageid);
		securedResource.setEntity(new UrlEncodedFormEntity(data));
		CloseableHttpResponse httpResponse = client
				.execute(securedResource);
		try {
			HttpEntity responseEntity = httpResponse.getEntity();
			String strResponse = EntityUtils.toString(responseEntity);
			EntityUtils.consume(responseEntity);
			httpResponse.close();

			if (strResponse.indexOf("j_security_check") > 0) {
				sessionId = this.login(username);
				if (sessionId == null)
					return null;
				this.sessions.put(username, sessionId);
				return this.post(username, pageid, data);
			}
			return strResponse;
		} finally {
			httpResponse.close();
		}
	} catch (Exception e) {
		e.printStackTrace();
	} finally {
		try {
			client.close();
		} catch (IOException e) {
		}
	}
	return null;
}

private String upload(String username, String pageid, String type, File file) {

	String sessionId = this.sessions.get(username);
	if (sessionId == null) {
		sessionId = this.login(username);
		if (sessionId == null)
			return null;
		this.sessions.put(username, sessionId);
		return this.upload(username, pageid, type, file);
	}

	CloseableHttpClient client = HttpClientBuilder.create()
			.setDefaultCookieStore(this.getCookieStore(sessionId)).build();
	try {
		HttpPost post = new HttpPost(this.server + "/" + pageid);
		MultipartEntityBuilder builder = MultipartEntityBuilder.create();
		builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);
		builder.addTextBody("name", file.getName());
		builder.addTextBody("type", type);
		builder.addBinaryBody("file", file);
		//
		HttpEntity entity = builder.build();
		post.setEntity(entity);
		CloseableHttpResponse response = client.execute(post);
		try {
			HttpEntity responseEntity = response.getEntity();
			String strResponse = EntityUtils.toString(responseEntity);
			EntityUtils.consume(responseEntity);

			if (strResponse.indexOf("j_security_check") > 0) {
				sessionId = this.login(username);
				if (sessionId == null)
					return null;
				this.sessions.put(username, sessionId);
				return this.upload(username, pageid, type, file);
			}
			return strResponse;
		} finally {
			response.close();
		}
	} catch (Exception e) {
		e.printStackTrace();
	} finally {
		try {
			client.close();
		} catch (IOException e) {
		}
	}
	return null;
}
}
