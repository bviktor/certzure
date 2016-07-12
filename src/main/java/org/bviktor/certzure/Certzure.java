package org.bviktor.certzure;

import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import java.security.cert.*;

import org.apache.commons.mail.*;

import org.apache.http.*;
import org.apache.http.client.*;
import org.apache.http.client.methods.*;
import org.apache.http.impl.client.*;

import com.microsoft.aad.adal4j.*;

import com.microsoft.windowsazure.*;
import com.microsoft.windowsazure.core.*;
import com.microsoft.windowsazure.management.configuration.*;

import com.microsoft.azure.management.dns.*;
import com.microsoft.azure.management.dns.models.*;

public class Certzure
{
	/*
	 * This ugly hack prevents the Maven Shade plugin to remove the specified classes with minimizeJar.
	 * This is basically trial-n-error so when changing functionality you gotta run tests to make sure
	 * nothing's missing at runtime.
	 */
	static
	{
		@SuppressWarnings("unused")
		Class<?>[] classes = new Class<?>[] { org.apache.commons.logging.impl.LogFactoryImpl.class, org.apache.commons.logging.impl.SimpleLog.class,
				com.microsoft.azure.management.dns.Exports.class, com.microsoft.windowsazure.core.pipeline.apache.Exports.class,
				com.microsoft.windowsazure.core.pipeline.jersey.Exports.class, com.microsoft.windowsazure.core.utils.Exports.class,
				com.microsoft.windowsazure.credentials.Exports.class, com.microsoft.windowsazure.management.configuration.Exports.class, com.sun.mail.smtp.SMTPTransport.class };
	}

	static final String settingsFileName = "certzure.properties";
	static final String challengeString = "_acme-challenge";
	static final String[] supportedOperations = { "deploy_challenge", "clean_challenge", "deploy_cert" };

	/*
	 * TODO: figure out URIs:
	 * https://login.microsoftonline.com/common/
	 * https://login.microsoftonline.com/TENANT.COM/
	 * https://login.windows.net/common/
	 * https://login.windows.net/TENANT.com/
	 * https://management.core.windows.net/
	 * https://management.azure.com/
	 */

	static final String accessUri = "https://management.azure.com/";
	static final String loginUri = "https://login.microsoftonline.com/common/";

	public enum extractMode
	{
		HOST,
		ZONE
	}

	public static void printHelp()
	{
		System.out.println("certzure operationName domainName challengeToken domainToken\n");
		System.out.println("Supported operationName values:");
		System.out.println("\tdeploy_challenge");
		System.out.println("\tclean_challenge");
		System.out.println("\tdeploy_cert");
	}

	/*
	 * Get the TenantID for a given Azure subscription
	 */
	public static String getSubscriptionTenantId(String subscriptionId) throws Exception
	{
		String tenantId = "";
		String url = accessUri + "subscriptions/" + subscriptionId + "?api-version=2016-01-01";

		HttpClient client = HttpClientBuilder.create().build();
		HttpGet request = new HttpGet(url);
		HttpResponse response = client.execute(request);

		Header[] headers = response.getAllHeaders();
		for (Header header : headers)
		{
			if (header.getName().equals("WWW-Authenticate"))
			{
				/* split by '"' to get the URL, split the URL by '/' to get the ID */
				tenantId = header.getValue().split("\"")[1].split("/")[3];
			}
		}

		return tenantId;
	}

	private static Configuration createConfiguration(String subscriptionId, String clientId, String username, String password) throws Exception
	{
		AuthenticationContext context = null;
		AuthenticationResult result = null;
		ExecutorService service = null;
		Future<AuthenticationResult> future = null;

		try
		{
			service = Executors.newFixedThreadPool(1);
			context = new AuthenticationContext(loginUri, false, service);

			/* 
			 * TODO: cert auth doesn't work with native apps: http://stackoverflow.com/questions/32616569/how-can-i-use-the-azure-ad-without-single-sign-on
			 */

			// FileInputStream fis = new FileInputStream(keyStoreLocation);
			// AsymmetricKeyCredential cred =
			// AsymmetricKeyCredential.create(clientID, fis, keyStorePassword);
			// future = context.acquireToken(accessURI, cred, null);

			future = context.acquireToken(accessUri, clientId, username, password, null);
			result = future.get();
		}
		finally
		{
			service.shutdown();
		}

		if (result == null)
		{
			throw new Exception("authentication result was null");
		}

		return ManagementConfiguration.configure(null, new URI(accessUri), subscriptionId, result.getAccessToken());

		/*
		 * Doesn't work
		 */

		// HttpClient client = HttpClientBuilder.create().build();
		// HttpGet request = new HttpGet(accessURI);
		// request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " +
		// result.getAccessToken());
		//
		// System.out.println(request.toString());
		// Header[] headers = request.getAllHeaders();
		// for (Header header : headers)
		// {
		// System.out.println(header.toString());
		// }
		//
		// System.out.println(client.execute(request));
		// return ManagementConfiguration.configure (new URI(accessURI),
		// subscriptionID, keyStoreLocation, keyStorePassword,
		// KeyStoreType.jks); */
	}

	private static String parseProperty(Properties prop, String key)
	{
		String trimmed = prop.getProperty(key, "").trim();
		return trimmed.substring(1, trimmed.length() - 1);
	}

	public static String extractFqdn(String domainName, extractMode mode)
	{
		String[] parts = domainName.split("\\.");
		String rootDomain = parts[parts.length - 2] + "." + parts[parts.length - 1];
		String subDomain = "";

		for (int i = 0; i < parts.length - 2; i++)
		{
			subDomain += parts[i] + ".";
		}

		switch (mode)
		{
			case HOST:
				/* if host is not empty, cut the trailing dot */
				return subDomain.isEmpty() ? "" : subDomain.substring(0, subDomain.length() - 1);
			case ZONE:
				return rootDomain;
			default:
				return null;
		}
	}

	public static Zone getZone(DnsManagementClient dnsClient, String resourceGroupName, String zoneName)
	{
		try
		{
			return dnsClient.getZonesOperations().get(resourceGroupName, zoneName).getZone();
		}
		catch (Exception e)
		{
			return new Zone();
		}
	}

	public static ArrayList<Zone> getAllZones(DnsManagementClient dnsClient, String resourceGroupName) throws Exception
	{
		/* you have to iterate over it so it's not a problem if it's null */
		ZoneListResponse zones = dnsClient.getZonesOperations().list(resourceGroupName, null);
		return zones.getZones();
	}

	public static RecordSet getTxtRecord(DnsManagementClient dnsClient, String resourceGroupName, String domainName) throws Exception
	{
		try
		{
			String host = extractFqdn(domainName, extractMode.HOST);

			if (host.isEmpty())
			{
				host = "@";
			}

			return dnsClient.getRecordSetsOperations().get(resourceGroupName, extractFqdn(domainName, extractMode.ZONE), host, RecordType.TXT).getRecordSet();
		}
		catch (Exception e)
		{
			return new RecordSet();
		}
	}

	public static ArrayList<RecordSet> getAllTxtRecords(DnsManagementClient dnsClient, String resourceGroupName, String domainName) throws Exception
	{
		RecordSetListResponse records = dnsClient.getRecordSetsOperations().list(resourceGroupName, extractFqdn(domainName, extractMode.ZONE), RecordType.TXT, null);
		return records.getRecordSets();
	}

	public static String getTxtRecordValue(RecordSet record) throws Exception
	{
		try
		{
			return record.getProperties().getTxtRecords().get(0).getValue();
		}
		catch (Exception e)
		{
			return null;
		}
	}

	public static boolean httpSuccess(int code)
	{
		if (code == HttpStatus.SC_OK || code == HttpStatus.SC_CREATED || code == HttpStatus.SC_ACCEPTED)
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public static String checkCert(String certFile)
	{
		InputStream inStream = null;
		String body = "";

		try
		{
			inStream = new FileInputStream(certFile);
			CertificateFactory cf = CertificateFactory.getInstance("X.509");
			X509Certificate cert = (X509Certificate) cf.generateCertificate(inStream);

			body += "Subject DN:\t" + cert.getSubjectDN() + "\n";
			body += "Issuer DN:\t" + cert.getIssuerDN() + "\n";
			body += "Valid from:\t" + cert.getNotBefore() + "\n";
			body += "Valid until:\t" + cert.getNotAfter() + "\n";

			inStream.close();
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return body;
	}

	public static boolean deployChallenge(DnsManagementClient dnsClient, String resourceGroupName, String domainName, String token, boolean verifyRecord) throws Exception
	{
		String zone = extractFqdn(domainName, extractMode.ZONE);
		String host = extractFqdn(domainName, extractMode.HOST);
		String recordSuffix = "";

		/*
		 * If we add the challenge to the root, there must not be a dot.
		 * If we add it to a subdomain, there must be a dot.
		 */
		if (!host.isEmpty())
		{
			recordSuffix = ".";
		}

		TxtRecord myTxt = new TxtRecord(token);
		ArrayList<TxtRecord> myTxtList = new ArrayList<TxtRecord>();
		myTxtList.add(myTxt);

		RecordSetProperties myProps = new RecordSetProperties();
		myProps.setTxtRecords(myTxtList);
		myProps.setTtl(60);

		RecordSet mySet = new RecordSet("global");
		mySet.setProperties(myProps);

		RecordSetCreateOrUpdateParameters myParams = new RecordSetCreateOrUpdateParameters(mySet);
		RecordSetCreateOrUpdateResponse myResponse = dnsClient.getRecordSetsOperations().createOrUpdate(resourceGroupName, zone, challengeString + recordSuffix + host,
				RecordType.TXT, myParams);

		int ret = myResponse.getStatusCode();
		if (!httpSuccess(ret))
		{
			return false;
		}

		if (!verifyRecord)
		{
			return true;
		}

		RecordSet check = getTxtRecord(dnsClient, resourceGroupName, challengeString + "." + domainName);
		if (getTxtRecordValue(check).equals(token))
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public static boolean cleanChallenge(DnsManagementClient dnsClient, String resourceGroupName, String domainName) throws Exception
	{
		String zone = extractFqdn(domainName, extractMode.ZONE);
		String host = extractFqdn(domainName, extractMode.HOST);
		String recordSuffix = "";

		/*
		 * If we add the challenge to the root, there must not be a dot.
		 * If we add it to a subdomain, there must be a dot.
		 */
		if (!host.isEmpty())
		{
			recordSuffix = ".";
		}

		RecordSetDeleteParameters myParams = new RecordSetDeleteParameters();
		OperationResponse myResponse = dnsClient.getRecordSetsOperations().delete(resourceGroupName, zone, challengeString + recordSuffix + host, RecordType.TXT, myParams);

		int ret = myResponse.getStatusCode();
		if (httpSuccess(ret))
		{
			return true;
		}
		else
		{
			return false;
		}
	}

	public static boolean deployCert(String certFile, String domainName, String smtpHost, int smtpPort, String smtpSender, String smtpRcpt, String smtpUser, String smtpPassword,
			boolean smtpSsl, boolean smtpStartTls)
	{
		Email email = new SimpleEmail();
		email.setHostName(smtpHost);
		email.setSmtpPort(smtpPort);
		email.setAuthentication(smtpUser, smtpPassword);
		email.setSubject("Certzure Renewal Report @ " + domainName);

		if (smtpStartTls)
		{
			email.setStartTLSRequired(true);
		}

		if (smtpSsl)
		{
			email.setSSLOnConnect(true);
		}

		try
		{
			email.setFrom(smtpSender);
			email.addTo(smtpRcpt);
			email.setMsg(checkCert(certFile));
			email.send();
		}
		catch (EmailException e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return false;
	}

	public static void main(String[] args)
	{
		String operationName = null;
		String domainName = null;
		// String challengeToken = null;
		String domainToken = null;

		if (args.length != 4)
		{
			printHelp();
			return;
		}
		else
		{
			operationName = args[0];
			domainName = args[1];
			// challengeToken = args[2];
			domainToken = args[3];
		}

		if (!Arrays.asList(supportedOperations).contains(operationName))
		{
			printHelp();
			return;
		}

		/* parse the properties file */
		Properties properties = new Properties();

		try
		{
			InputStream iStream = Certzure.class.getClassLoader().getResourceAsStream(settingsFileName);
			properties.load(iStream);
		}
		catch (Exception e)
		{
			System.out.println("Make sure you have your configured \"certzure.properties\" file in place. Example:\n");
			System.out.println("subscriptionId = \"2a4da06c-ff07-410d-af8a-542a512f5092\"");
			System.out.println("clientId = \"1950a258-227b-4e31-a9cf-717495945fc2\"");
			System.out.println("username = \"dns@foobar.com\"");
			System.out.println("password = \"whatever\"");
			// System.out.println("keyStoreLocation = \"c:\\azure.pfx\"");
			// System.out.println("keyStorePassword = \"whatnever\"");
			System.out.println("resourceGroupName = \"DNSGroup\"");
			//System.out.println("certDir = \"/opt/letsencrypt.sh/certs\"");
			System.out.println("smtpHost = \"smtp.office365.com\"");
			System.out.println("smtpPort = \"587\"");
			System.out.println("smtpSender = \"certzure@foobar.com\"");
			System.out.println("smtpRcpt = \"admin@foobar.com\"");
			System.out.println("smtpUser = \"certzure@foobar.com\"");
			System.out.println("smtpPassword = \"whatforever\"");
			System.out.println("smtpSsl = \"false\"");
			System.out.println("smtpStartTls = \"true\"\n");
			System.out.println("Also make sure to secure this file with chmod and/or chown.");
			System.exit(1);
		}

		String subscriptionId = parseProperty(properties, "subscriptionId");
		String clientId = parseProperty(properties, "clientId");
		String username = parseProperty(properties, "username");
		String password = parseProperty(properties, "password");
		String resourceGroupName = parseProperty(properties, "resourceGroupName");
		//String certDir = parseProperty(properties, "certDir");
		String smtpHost = parseProperty(properties, "smtpHost");
		String smtpPortString = parseProperty(properties, "smtpPort");
		String smtpSender = parseProperty(properties, "smtpSender");
		String smtpRcpt = parseProperty(properties, "smtpRcpt");
		String smtpUser = parseProperty(properties, "smtpUser");
		String smtpPassword = parseProperty(properties, "smtpPassword");
		String smtpSslString = parseProperty(properties, "smtpSsl");
		String smtpStartTlsString = parseProperty(properties, "smtpStartTls");

		Configuration config;
		try
		{
			if (operationName.equals("deploy_challenge"))
			{
				config = createConfiguration(subscriptionId, clientId, username, password);
				DnsManagementClient dnsClient = DnsManagementService.create(config);
				deployChallenge(dnsClient, resourceGroupName, domainName, domainToken, true);
			}
			else if (operationName.equals("clean_challenge"))
			{
				config = createConfiguration(subscriptionId, clientId, username, password);
				DnsManagementClient dnsClient = DnsManagementService.create(config);
				cleanChallenge(dnsClient, resourceGroupName, domainName);
			}
			else if (operationName.equals("deploy_cert"))
			{
				/* For deploy_cert the 3rd argument is the issued certificate's path */
				String certFile = domainToken;

				deployCert(certFile, domainName, smtpHost, Integer.parseInt(smtpPortString), smtpSender, smtpRcpt, smtpUser, smtpPassword,
						smtpSslString.equals("true") ? true : false, smtpStartTlsString.equals("true") ? true : false);
			}
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
