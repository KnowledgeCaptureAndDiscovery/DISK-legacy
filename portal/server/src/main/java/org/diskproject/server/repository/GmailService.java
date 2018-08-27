package org.diskproject.server.repository;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.*;

import javax.mail.*;
import javax.mail.internet.*;
import javax.mail.search.FlagTerm;

import org.apache.commons.configuration.plist.PropertyListConfiguration;
import org.diskproject.shared.classes.common.Graph;
import org.diskproject.shared.classes.common.TreeItem;
import org.diskproject.shared.classes.common.Triple;
import org.diskproject.shared.classes.common.TripleDetails;
import org.diskproject.shared.classes.common.TripleUtil;
import org.diskproject.shared.classes.hypothesis.Hypothesis;
import org.diskproject.shared.classes.loi.TriggeredLOI;
import org.diskproject.server.util.Config;
import org.diskproject.server.util.Mail;
import org.diskproject.shared.classes.util.GUID;

public class GmailService {

	private static String USER_NAME;
	private static String PASSWORD; // GMail password
	static ScheduledExecutorService monitor;
	static MailMonitor mailThread;
	static Set<Mail> emails;
	static GmailService gmail;
	static boolean created = false;
    
	/**
	 * When using this, https://myaccount.google.com/lesssecureapps?pli=1 set
	 * Allow less secure apps: ON Otherwise, it will not connect
	 * 
	 * If reading list of emails doesn't work, go to Gmail-->Settings-->Settings
	 * -->Forwarding and POP/IMAP-->IMAP Access--> Enable IMAP-->Save Changes
	 */
	public static void main(String[] args) {
		// new GmailService();

	}

	public static GmailService get() {
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		if (!created && gmail == null)
			gmail = new GmailService();

		return gmail;
	}

	public void shutdown() {
		if (mailThread != null)
			mailThread.stop();
		if (monitor != null)
			monitor.shutdownNow();
	}

	private GmailService() {
		USER_NAME = getProperty("gmail.username");
		PASSWORD = getProperty("gmail.password");
		created = true;
		emails = new HashSet<Mail>();
		monitor = Executors.newScheduledThreadPool(1);
		mailThread = new MailMonitor();

		monitor.scheduleWithFixedDelay(mailThread, 0, 20, TimeUnit.SECONDS);
	}

	public String getProperty(String property) {
		if (Config.get() == null)
			return "";
		PropertyListConfiguration props = Config.get().getProperties();
		return props.getString(property);
	}

	public void saveMail(Message m, boolean read) throws Exception {
		try {

			Mail mail = toMail(m);
			if (!hypothesisExists(mail))
				addHypothesis(mail);
			if (!read) {
				sendEmail(
						"Re: " + mail.getSubject(),
						mail.getReplyTo(),
						"Successfully found hypothesis "
								+ (String) mail.getContent() + ".");
			}
			emails.add(mail);
		} catch (Exception e) {
			e.printStackTrace();

			String c;
			if (m.getContent() instanceof MimeMultipart)
				c = getTextFromMimeMultipart((MimeMultipart) m.getContent())
						.replace("<", "").replace(">", "");
			else
				c = m.getContent().toString();

			sendEmail(
					"Re: " + m.getSubject(),
					m.getReplyTo(),
					"Could not add "
							+ c
							+ ". Please check that the content if formatted accurately.");
			throw new Exception();
		}
	}

	public boolean hypothesisExists(Mail mail) {
		String username = getProperty("username");
		String domain = getProperty("domain");

		TripleUtil util = new TripleUtil();
		List<Triple> triples = new ArrayList<Triple>();

		String[] arr = mail.getContent().split("\n");

		for (String triple : arr) {
			Triple t = util.fromString(triple);
			t.setDetails(null);
			triples.add(t);
		}

		List<TreeItem> hypList = DiskRepository.get().listHypotheses(username,
				domain);
		for (TreeItem hypothesis : hypList) {
			boolean tripleExists = false;
			Hypothesis hyp = DiskRepository.get().getHypothesis(username,
					domain, hypothesis.getId());
			if (hyp.getGraph().getTriples().size() == mail.getContent().split(
					"\n").length)
				for (Triple t : hyp.getGraph().getTriples()) {
					tripleExists = false;
					for (Triple temp : triples) {
						System.out.println("check triple: " + temp.toString());

						if (t.toString().equals(temp.toString())) {
							tripleExists = true;
							break;
						}
					}
					if (!tripleExists)
						break;
				}
			if (tripleExists) {
				mail.setHypothesisId(hypothesis.getId());
				return true;
			}
		}
		return false;

	}

	public Mail toMail(Message m) throws Exception {
		Mail mail = new Mail();
		mail.setSubject(m.getSubject());

		if (m.getContent() instanceof MimeMultipart)
			mail.setContent(getTextFromMimeMultipart((MimeMultipart) m
					.getContent()));
		else
			mail.setContent(m.getContent().toString());
		mail.setReplyTo(m.getReplyTo());
		return mail;
	}

	public void addHypothesis(Mail mail) throws Exception {
		// Add hypothesis
		Hypothesis hypothesis = new Hypothesis();

		// Set Id
		String id = GUID.randomId("Hypothesis");
		hypothesis.setId(id);
		mail.setHypothesisId(id);

		// Set Graph
		TripleUtil util = new TripleUtil();
		Graph newgraph = new Graph();
		List<Triple> triples = new ArrayList<Triple>();

		String[] arr = mail.getContent().split("\n");

		for (String triple : arr) {
			Triple t = util.fromString(triple);
			t.setDetails(null);
			triples.add(t);
		}

		newgraph.setTriples(triples);
		hypothesis.setGraph(newgraph);

		// Set Name
		String name = "";
		String content = mail.getContent().replace("<", "").replace(">", "")
				.trim();
		arr = content.split("(#)|( )");
		for (int i = 0; i < arr.length; i++)
			if (i % 2 == 1)
				name += arr[i] + " ";
		hypothesis.setName(name.trim());

		// Set Description
		hypothesis.setDescription("Added by DISK Agent. First requested by: "
				+ Arrays.toString(mail.getReplyTo()).replace("[", "")
						.replace("]", "") + ".");

		// Set Parent Id
		hypothesis.setParentId(null);

		// Add Hypothesis
		boolean saved = DiskRepository.get().addHypothesis(
				getProperty("username"), getProperty("domain"), hypothesis);

		if (saved) {
			addTriggeredLineOfInquiry(mail);
			sendEmail("Re: " + mail.getSubject(), mail.getReplyTo(),
					"Successfully added " + (String) mail.getContent() + ".");
		} else
			throw new Exception();
	}

	public void fetchMessages(String user, String password, boolean read) {
		try {

			Properties props = System.getProperties();
			String host = "smtp.gmail.com";
			props.put("mail.smtp.starttls.required", "false");

			props.put("mail.smtp.starttls.enable", "true");
			props.put("mail.smtp.host", host);
			props.put("mail.smtp.user", USER_NAME);
			props.put("mail.smtp.password", PASSWORD);
			props.put("mail.smtp.port", "587");
			props.put("mail.smtp.auth", "true");

			props.put("spring.mail.properties.mail.smtp.starttls.enable",
					"true");
			props.put("spring.mail.properties.mail.smtp.starttls.required",
					"false");

			Session session = Session.getDefaultInstance(props);
			Store store = session.getStore("imaps");
			store.connect("imap.googlemail.com", 993, user, password);
			Folder inbox = store.getFolder("INBOX");
			inbox.open(Folder.READ_WRITE);

			// Fetch unseen messages from inbox folder
			Message[] messages;
			messages = inbox.search(new FlagTerm(new Flags(Flags.Flag.SEEN),
					read));
			for (Message message : messages) {
				// Delete messages that are not necessary
				try {
					message.setFlag(Flags.Flag.SEEN, true);
					saveMail(message, read);
				} catch (Exception e) {
					Folder trash = store.getFolder("[Gmail]/Other");
					Message[] m = { message };
					inbox.copyMessages(m, trash);
				}

			}

			inbox.close(false);
			store.close();
			System.out.println("emails: " + emails);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private void addTriggeredLineOfInquiry(Mail mail) {
		List<TriggeredLOI> tloiList = DiskRepository.get().queryHypothesis(
				getProperty("username"), getProperty("domain"),
				mail.getHypothesisId());
		System.out.println("tloiList: " + tloiList);
		for (TriggeredLOI tloi : tloiList) {
			DiskRepository.get().addTriggeredLOI(getProperty("username"),
					getProperty("domain"), tloi);
		}
	}

	private String getTextFromMimeMultipart(MimeMultipart mimeMultipart)
			throws MessagingException, IOException {
		String result = "";
		int count = mimeMultipart.getCount();
		for (int i = 0; i < count; i++) {
			BodyPart bodyPart = mimeMultipart.getBodyPart(i);
			if (bodyPart.isMimeType("text/plain")) {
				if (i != 0)
					result = result + " " + bodyPart.getContent();
				else
					result = result + bodyPart.getContent();

				break; // without break same text appears twice
			} else if (bodyPart.isMimeType("text/html")) {
				String html = (String) bodyPart.getContent();
				if (i != 0)
					result = result + " " + org.jsoup.Jsoup.parse(html).text();
				else
					result = result + org.jsoup.Jsoup.parse(html).text();
			} else if (bodyPart.getContent() instanceof MimeMultipart) {
				result = result
						+ getTextFromMimeMultipart((MimeMultipart) bodyPart
								.getContent());
			}
		}
		result = result.replace("<", " ").replace(">", " ").trim();
		String[] ar = result.split("\\s+");
		System.out.println("arraystostring: " + Arrays.toString(ar));
		String endResult = "";
		for (int i = 0; i < ar.length; i++) {
			if (i % 3 == 0 && i != 0)
				endResult += "\n";
			endResult += "<" + ar[i] + "> ";
		}
		System.out.println("endResult: " + endResult);
		return endResult.trim();
	}

	private void sendEmail(String subject, Address[] recipients, String body) {
		String from = USER_NAME;
		String pass = PASSWORD;

		sendFromGMail(from, pass, recipients, subject, body);
	}

	private void checkForNewResults() {
		String username = getProperty("username");
		String domain = getProperty("domain");

		TripleUtil util = new TripleUtil();
		for (Mail mail : emails) {
			try {
				String header = "Revised Hypotheses: Results of the Hypothesis request ("
						+ mail.getContent() + ")\n";
				String results = "";

				if (mail.getHypothesisId() != null) {
					List<TreeItem> hypList = DiskRepository.get()
							.listHypotheses(username, domain);
					for (TreeItem hypothesis : hypList) {
						try {
							Hypothesis hyp = DiskRepository.get().getHypothesis(
									username, domain, hypothesis.getId());
							if (hyp.getParentId() != null
									&& hyp.getParentId().equals(
											mail.getHypothesisId())) {
								System.out.println(hyp);
								for (Triple t : hyp.getGraph().getTriples()) {
									results += "\n" + util.toString(t)
											+ " with Confidence Value: "
											+ t.getDetails().getConfidenceValue();
								}
							}
						} catch (Exception e) {
							e.printStackTrace();
						}
					}
					System.out.println(results);
					if ((mail.getResults() == null && results.length() > 10)
							|| (mail.getResults()!= null && !mail.getResults().equals(results))) {
						mail.setResults(results);
						sendEmail("Re: " + mail.getSubject(),
								mail.getReplyTo(), header + results);
					}
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}

	private void sendFromGMail(String from, String pass, Address[] to,
			String subject, String body) {
		Properties props = System.getProperties();
		String host = "smtp.gmail.com";
		props.put("mail.smtp.starttls.enable", "true");
		props.put("mail.smtp.host", host);
		props.put("mail.smtp.user", from);
		props.put("mail.smtp.password", pass);
		props.put("mail.smtp.port", "587");
		props.put("mail.smtp.auth", "true");

		Session session = Session.getDefaultInstance(props);
		MimeMessage message = new MimeMessage(session);

		try {
			message.setFrom(new InternetAddress(from));

			message.setSubject(subject);
			message.setText(body);
			Transport transport = session.getTransport("smtp");
			transport.connect(host, from, pass);
			transport.sendMessage(message, to);
			transport.close();
		} catch (AddressException ae) {
			ae.printStackTrace();
		} catch (MessagingException me) {
			me.printStackTrace();
		}
	}

	class MailMonitor implements Runnable {

		boolean stop;
		boolean firstLoad;

		public MailMonitor() {
			stop = false;
			firstLoad = true;
		}

		public void run() {
			try {
				if (stop) {
					while (!Thread.currentThread().isInterrupted()) {
						Thread.currentThread().interrupt();
					}
				} else {
					if (!this.equals(mailThread))
						stop();
					if(!firstLoad){
					if (emails.size() == 0)
						fetchMessages(USER_NAME, PASSWORD, true);
					checkForNewResults();
					fetchMessages(USER_NAME, PASSWORD, false);
					}
					else 
						firstLoad = false;
				}
			} catch (IllegalStateException e) {
				while (!Thread.interrupted()) {
					stop = true;
					Thread.currentThread().interrupt();
				}
			}
		}

		public void stop() {
			while (!Thread.interrupted()) {
				System.out.println("Shutting Down");
				stop = true;
				Thread.currentThread().interrupt();
			}
		}
	}
}
