package org.diskproject.server.repository;
import java.net.URLEncoder;
import java.util.Arrays;
import java.util.Scanner;

import com.gargoylesoftware.htmlunit.TextPage;
import com.gargoylesoftware.htmlunit.WebClient;
import com.gargoylesoftware.htmlunit.html.HtmlButton;
import com.gargoylesoftware.htmlunit.html.HtmlForm;
import com.gargoylesoftware.htmlunit.html.HtmlPage;
import com.gargoylesoftware.htmlunit.html.HtmlPasswordInput;
import com.gargoylesoftware.htmlunit.html.HtmlTextInput;
import com.gargoylesoftware.htmlunit.BrowserVersion;
import com.gargoylesoftware.htmlunit.BrowserVersion.BrowserVersionBuilder;

public class DataQuery {
	public static String [] queryFor(String args) throws Exception{
			WebClient webClient = WebClientSetup();
			return prepareQueryString("",args, webClient);

	}
    public static void main (String [] args)
    {
    	try {
			//System.out.println(Arrays.toString(queryFor(args[0])));
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
    }
	public static WebClient WebClientSetup() throws Exception {
		BrowserVersion browserVersion
        = new BrowserVersion.BrowserVersionBuilder(BrowserVersion.FIREFOX_52)
            .setApplicationName("Firefox")
            .setApplicationVersion("5.0 (Windows NT 10.0; WOW64; rv:39.0) Gecko/20100101 Firefox/39.0")
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; WOW64; rv:39.0) Gecko/20100101 Firefox/39.0")
            .build();	
	
		WebClient webClient = new WebClient(browserVersion);
		// Get the first page
		HtmlPage page1 = webClient
				.getPage("http://organicdatacuration.org/enigma_new/index.php?title=Special:UserLogin&wpRemember=1&returnto=Regina+Wang&returntoquery=");

		// Get the form that we are dealing with and within that form,
		// find the submit button and the field that we want to change.
		HtmlForm form = page1.getFormByName("userlogin");
		HtmlTextInput textField = form.getInputByName("wpName");

		// Change the value of the text field
		textField.type("regina");
		HtmlPasswordInput textField2 = (HtmlPasswordInput) page1
				.getElementById("wpPassword1");
		textField2.type("regina123");
		HtmlButton button = (HtmlButton) page1.getElementById("wpLoginAttempt");

		// Now submit the form by clicking the button and get back the
		// second page.
		HtmlPage page2 = button.click();

		return webClient;
	}

	public static String [] prepareQueryString(String str, String inputQuery,
			WebClient webClient) throws Exception{
			String saveQuery = inputQuery;
			// Make query machine readable and initialize variables
			inputQuery = inputQuery.replace("|", "/");
			int offset = 0;
			
			String query = toMachineReadableQuery(inputQuery).replace("-2F",
					"/");
			String MachineReadableQuery = "http://organicdatacuration.org/enigma_new/index.php/Special:Ask/"
					+ query
					+ "/offset%3D"
					+ offset
					+ "/limit%3D-2010/format=-20csv";
			// Open query result reader for datasets first
			
			TextPage page3 =  (TextPage)webClient.getPage(MachineReadableQuery);
			//System.out.println(MachineReadableQuery);
			//System.out.println(page3.getWebResponse().getContentAsString());
			Scanner s = new Scanner(page3.getWebResponse().getContentAsString());
			s.useDelimiter(",");

			// Set up reading through pages (first line is irrelevant)
			// (500 datasets per query max)
			s.nextLine();
			String SingleResult;
			try {
				while ((SingleResult = s.nextLine()) != null) {
					// Read through query results
					try {
						// System.out.println(SingleResult);
						while ((SingleResult = s.nextLine()) != null) {
							if (SingleResult.indexOf(",") != -1
									&& SingleResult.indexOf(",") != SingleResult
											.length() - 1) {
								SingleResult = SingleResult
										.substring(SingleResult.indexOf(",") + 1);
								String data = pageNameToFileInformation(
										SingleResult, webClient);
								// Open new file in zip and save text into entry
								str += data + "\n\",\"\n";
							}
						}
					} catch (Exception e) {
					}
					offset += 10;
					MachineReadableQuery = "http://organicdatacuration.org/enigma_new/index.php/Special:Ask/"
							+ query
							+ "/offset%3D"
							+ offset
							+ "/limit%3D-2010/format=-20csv";
					// Get query results for datasets again\
					//System.out.println(MachineReadableQuery);
					page3 = (TextPage)webClient.getPage(MachineReadableQuery);
					s = new Scanner(page3.getWebResponse().getContentAsString());
					s.nextLine();
				}
			} catch (Exception e) {
			}
			String [] output = new String [2];
			str = str.substring(0, str.length() - 3);
			saveQuery = saveQuery.replace("/", "").replace("\\", "")
					.replace(":", "").replace("*", "").replace("?", "")
					.replace("\"", "").replace("<", "").replace(">", "")
					.replace("|", "");
			output[0] = saveQuery;
			output[1] = str;
			s.close();
			return output;
			// Clean up
	}

	/**
	 * @param original
	 *            = query provided
	 * @return link to the query in a csv format
	 */
	public static String toMachineReadableQuery(String original) {
		try {
			String query = original;
			query = URLEncoder.encode(query, "UTF-8");
			return query.replace("%", "-");
		} catch (Exception e) {
		}
		return "";
	}

	public static String pageNameToFileInformation(String SingleResult,
			WebClient webClient){
			try{
			// Find dataset link and download file
			HtmlPage page3 = webClient
					.getPage("http://organicdatacuration.org/enigma_new/index.php/"
							+ SingleResult);
			Scanner s = new Scanner(page3.getWebResponse().getContentAsString());
			String file;
			while ((file = s.nextLine()).indexOf("<li><a href=\"#filelinks\">") == -1) {
			}
			s.close();
			file = file.substring(file.indexOf("a href=") + 7);
			file = file.substring(file.indexOf("a href=") + 8);
			file = file.substring(0, file.indexOf("\""));
			TextPage pageTxt = (TextPage) webClient
					.getPage("http://organicdatacuration.org" + file);

			return SingleResult.replace("/", "").replace("\\", "")
					.replace(":", "").replace("*", "").replace("?", "")
					.replace("\"", "").replace("<", "").replace(">", "")
					.replace("|", "")+ "\n\",\"\n" +pageTxt.getWebResponse().getContentAsString();
			}
			catch(Exception e)
			{}
		return "";
	}

}