import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.util.EntityUtils;
/**
 * 
 * @author reginawang
 * Make sure to change the user, domain, and server Strings to the appropriate ones
 */
public class exampleUseOfDiskAPI {

	static String user = "ravali";
	static String domain = "test";
	static String server = "http://localhost:8080/disk-server";

	public static void main(String[] args) {

		vocabularyAPIExamples(); //verified to work
		hypothesisAPIExamples(); //verified to work
		assertionsAPIExamples(); //verified to work
		lineOfInquiryAPIExamples(); //verified to work
		triggeredLineOfInquiryAPIExmaples();
		workflowAPIExamples(); //verified to work
	}

	public static void vocabularyAPIExamples() {
		// returns the vocabulary of the ontologies that have been loaded
		get("vocabulary", "application/json");

		// returns the vocabulary of the specified user and domain
		get(user + "/" + domain + "/vocabulary", "application/json");

		// reloads the vocabularies and ontologies
		get("vocabulary/reload", "application/json");
	}

	public static void hypothesisAPIExamples() {
		// returns the hypotheses of the specified user and domain
		get(user + "/" + domain + "/hypotheses", "application/json");

		// updates or adds the specified hypothesis
		String specificHypothesisId = "Hypothesis-CYdTjreAYeiM";
		String hypothesisToBeChangedTo = "{\"id\":\"Hypothesis-CYdTjreAYeiM\",\"name\":\"APOE4 associated with Hippocampal Volume\",\"description\":\"APOE4 is associated with Hippocampal Volume\",\"parentId\":null,\"graph\":{\"triples\":[{\"subject\":\"https://w3id.org/disk/ontology/neuro#APOE4\",\"predicate\":\"http://disk-project.org/ontology/hypothesis#associatedWith\",\"object\":{\"type\":\"URI\",\"value\":\"https://w3id.org/disk/ontology/neuro#HippocampalVolume\",\"datatype\":null},\"details\":null}]}}";
		put(user + "/" + domain + "/hypotheses/" + specificHypothesisId,
				hypothesisToBeChangedTo);

		// adds specified hypothesis
		String hypothesisToBeAdded = "{\"id\":\"Hypothesis-CYdTjreAYeiK\",\"name\":\"APOE4 associated with Hippocampal Volume\",\"description\":\"APOE4 is associated with Hippocampal Volume\",\"parentId\":null,\"graph\":{\"triples\":[{\"subject\":\"https://w3id.org/disk/ontology/neuro#APOE4\",\"predicate\":\"http://disk-project.org/ontology/hypothesis#associatedWith\",\"object\":{\"type\":\"URI\",\"value\":\"https://w3id.org/disk/ontology/neuro#HippocampalVolume\",\"datatype\":null},\"details\":null}]}}";
		post(user + "/" + domain + "/hypotheses", hypothesisToBeAdded);

		// returns the specified hypothesis of the specified user and domain
		get(user + "/" + domain + "/hypotheses/" + specificHypothesisId,
				"application/json");

		// deletes specified hypothesis
		delete(user + "/" + domain + "/hypotheses/" + specificHypothesisId);

		specificHypothesisId = "Hypothesis-CYdTjreAYeiM";
		// query specified hypothesis
		get(user + "/" + domain + "/hypotheses/" + specificHypothesisId
				+ "/query", "application/json");
	}

	public static void assertionsAPIExamples() {
		// returns all assertions
		get(user + "/" + domain + "/assertions", "application/json");

		// adds specified assertions
		String assertionsToBeAdded = "{\"triples\":[{\"subject\":\"http://localhost:8080/disk-server/ravali/test/assertions#dd\",\"predicate\":\"http://www.w3.org/1999/02/22-rdf-syntax-ns#type\",\"object\":{\"type\":\"URI\",\"value\":\"https://w3id.org/disk/ontology/neuro#Gene\",\"datatype\":null},\"details\":null}]}";
		post(user + "/" + domain + "/assertions", assertionsToBeAdded);

		// deletes specified assertions
		String assertionsToBeDeleted = "{\"triples\":[{\"subject\":\"http://localhost:8080/disk-server/ravali/test/assertions#dd\",\"predicate\":\"http://www.w3.org/1999/02/22-rdf-syntax-ns#type\",\"object\":{\"type\":\"URI\",\"value\":\"https://w3id.org/disk/ontology/neuro#Gene\",\"datatype\":null},\"details\":null}]}";
		deleteWithData(user + "/" + domain + "/assertions",
				assertionsToBeDeleted);

		// replaces all existing assertions with given assertions
		String newAssertions = "{\"triples\":[{\"subject\":\"http://localhost:8080/disk-server/ravali/test/assertions#dd\",\"predicate\":\"http://www.w3.org/1999/02/22-rdf-syntax-ns#type\",\"object\":{\"type\":\"URI\",\"value\":\"https://w3id.org/disk/ontology/neuro#BrainScan\",\"datatype\":null},\"details\":null}]}";
		put(user + "/" + domain + "/assertions", newAssertions);
	}

	public static void lineOfInquiryAPIExamples() {
		//list line of inquiries
		get(user+"/"+domain+"/lois", "application/json");
		
		//get specified line of inquiry
		String specificLoiId = "LOI-hRQe2Qq811tB";
		get(user+"/"+domain+"/lois/"+specificLoiId, "application/json");

		//add specified line of inquiry
		String lineOfInquiryToAdd = "{\"id\":\"LOI-hRQe2Qq811tA\",\"name\":\"xx\",\"description\":\"xxx\",\"hypothesisQuery\":\"?Gene hyp:associatedWith ?BrainChar\\n?Gene a neuro:Gene\\n?BrainChar a neuro:BrainCharacteristic\",\"dataQuery\":\"?data1 a neuro:BrainScan\\n?data2 a neuro:BrainScan\",\"workflows\":[{\"workflow\":\"test\",\"workflowLink\":\"http://www.wings-workflows.org/wings-omics-portal/users/ravali/test/workflows/test.owl\",\"bindings\":[{\"variable\":\"input\",\"binding\":\"?data1\"},{\"variable\":\"input2\",\"binding\":\"?data2\"}],\"run\":{\"id\":null,\"link\":null,\"status\":null},\"meta\":{\"hypothesis\":null,\"revisedHypothesis\":null}}],\"metaWorkflows\":[{\"workflow\":\"test\",\"workflowLink\":\"http://www.wings-workflows.org/wings-omics-portal/users/ravali/test/workflows/test.owl\",\"bindings\":[{\"variable\":\"input\",\"binding\":\"test\"}],\"run\":{\"id\":null,\"link\":null,\"status\":null},\"meta\":{\"hypothesis\":\"input2\",\"revisedHypothesis\":\"output2\"}}]}";
		post(user + "/" + domain + "/lois", lineOfInquiryToAdd);
		
		//updates specified line of inquiry
		String LoiId = "LOI-hRQe2Qq811tA";
		String lineOfInquiryToUpdate = "{\"id\":\"LOI-hRQe2Qq811tA\",\"name\":\"updatedVersion\",\"description\":\"xxx\",\"hypothesisQuery\":\"?Gene hyp:associatedWith ?BrainChar\\n?Gene a neuro:Gene\\n?BrainChar a neuro:BrainCharacteristic\",\"dataQuery\":\"?data1 a neuro:BrainScan\\n?data2 a neuro:BrainScan\",\"workflows\":[{\"workflow\":\"test\",\"workflowLink\":\"http://www.wings-workflows.org/wings-omics-portal/users/ravali/test/workflows/test.owl\",\"bindings\":[{\"variable\":\"input\",\"binding\":\"?data1\"},{\"variable\":\"input2\",\"binding\":\"?data2\"}],\"run\":{\"id\":null,\"link\":null,\"status\":null},\"meta\":{\"hypothesis\":null,\"revisedHypothesis\":null}}],\"metaWorkflows\":[{\"workflow\":\"test\",\"workflowLink\":\"http://www.wings-workflows.org/wings-omics-portal/users/ravali/test/workflows/test.owl\",\"bindings\":[{\"variable\":\"input\",\"binding\":\"test\"}],\"run\":{\"id\":null,\"link\":null,\"status\":null},\"meta\":{\"hypothesis\":\"input2\",\"revisedHypothesis\":\"output2\"}}]}";
		put(user + "/" + domain + "/lois/"+LoiId, lineOfInquiryToUpdate);
		
		//deletes specified loi
		delete(user + "/" + domain + "/lois/"+LoiId);
	}
	
	public static void triggeredLineOfInquiryAPIExmaples() {
		// addTriggeredLOI
		
		
		//listTriggeredLOIs
		
		
		//getTriggeredLOI
		
		
		//deleteTriggeredLOI
		
	}
	
	public static void workflowAPIExamples() {	
		//list workflows
		get(user+"/"+domain+"/workflows", "application/json");

		//get specified workflow
		String workflowId = "test";
		get(user+"/"+domain+"/workflows/"+workflowId, "application/json");
		
		//Check run status
		String runId = "test-1e5dd764-6fe4-4f2a-8d57-99c6b9768160";
		get(user+"/"+domain+"/runs/"+runId, "application/json");

	}

	public static void get(String pageid, String acceptType) {
		CloseableHttpClient client = HttpClientBuilder.create().build();
		try {
			String url = server + "/" + pageid;
			HttpGet securedResource = new HttpGet(url);
			securedResource.addHeader("Accept", "application/json");

			CloseableHttpResponse httpResponse = client
					.execute(securedResource);
			HttpEntity responseEntity = httpResponse.getEntity();
			String strResponse = EntityUtils.toString(responseEntity);
			EntityUtils.consume(responseEntity);
			System.out.println("get/strResponse" + strResponse);
			client.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void post(String pageid, String data) {
		try {
			CloseableHttpClient client = HttpClientBuilder.create().build();
			String url = server + "/" + pageid;
			HttpPost securedResource = new HttpPost(url);
			securedResource.setEntity(new StringEntity(data));
			// securedResource.addHeader("Accept", "application/json");
			securedResource.setHeader("Content-type", "application/json");
			securedResource.setHeader("Accept", "application/json");

			CloseableHttpResponse httpResponse = client
					.execute(securedResource);
			HttpEntity responseEntity = httpResponse.getEntity();
			try {
				String strResponse = EntityUtils.toString(responseEntity);
				System.out.println(strResponse);
				EntityUtils.consume(responseEntity);
			} catch (Exception e) {
				System.out.println("null");
			}
			httpResponse.close();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void put(String pageid, String data) {
		try {
			String urlLink = server + "/" + pageid;
			URL url = new URL(urlLink);
			HttpURLConnection httpCon = (HttpURLConnection) url
					.openConnection();
			httpCon.setDoOutput(true);
			httpCon.setRequestProperty("Content-Type", "application/json");
			httpCon.setRequestProperty("Accept", "application/json");

			httpCon.setRequestMethod("PUT");
			OutputStreamWriter out = new OutputStreamWriter(
					httpCon.getOutputStream());
			out.write(data);
			out.close();
			System.err.println(httpCon.getResponseCode());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void deleteWithData(String pageid, String data) {
		try {
			String urlLink = server + "/" + pageid;
			URL url = new URL(urlLink);
			HttpURLConnection httpCon = (HttpURLConnection) url
					.openConnection();
			httpCon.setDoOutput(true);
			httpCon.setRequestProperty("Content-Type", "application/json");
			httpCon.setRequestMethod("DELETE");
			OutputStreamWriter out = new OutputStreamWriter(
					httpCon.getOutputStream());
			out.write(data);
			out.close();
			httpCon.connect();
			System.err.println(httpCon.getResponseCode());

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public static void delete(String pageid) {
		try {
			String urlLink = server + "/" + pageid;
			URL url = new URL(urlLink);
			HttpURLConnection httpCon = (HttpURLConnection) url
					.openConnection();
			httpCon.setDoOutput(true);
			httpCon.setRequestProperty("Content-Type", "application/json");
			httpCon.setRequestMethod("DELETE");
			httpCon.connect();
			System.err.println(httpCon.getResponseCode());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}