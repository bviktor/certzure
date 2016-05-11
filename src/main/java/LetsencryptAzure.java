import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

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

public class LetsencryptAzure
{
	static final String settingsFileName = "app.properties";
	static final String challengeString = "_acme-challenge";
	static final String[] supportedOperations = { "deploy_challenge", "clean_challenge" };

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

	public static void printHelp()
	{
		System.out.println("letsencrypt-azure operationName domainName challengeToken domainToken\n");
		System.out.println("Supported operationName values:");
		System.out.println("\tdeploy_challenge");
		System.out.println("\tclean_challenge");
	}

	/*
	 * Get the TenantID for a given Azure subscription
	 */
	public static String getSubscriptionTenantId(String subscriptionId) throws Exception
	{
		String tenantId = null;
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
		String trimmed = prop.getProperty(key).trim();
		return trimmed.substring(1, trimmed.length() - 1);
	}

	public static String extractFqdn(String domainName, String mode)
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
			case "host":
				/* if host is not empty, cut the trailing dot */
				return subDomain.isEmpty() ? "" : subDomain.substring(0, subDomain.length() - 1);
			case "zone":
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
			String host = extractFqdn(domainName, "host");

			if (host.isEmpty())
			{
				host = "@";
			}

			return dnsClient.getRecordSetsOperations().get(resourceGroupName, extractFqdn(domainName, "zone"), host, RecordType.TXT).getRecordSet();
		}
		catch (Exception e)
		{
			return new RecordSet();
		}
	}

	public static ArrayList<RecordSet> getAllTxtRecords(DnsManagementClient dnsClient, String resourceGroupName, String domainName) throws Exception
	{
		RecordSetListResponse records = dnsClient.getRecordSetsOperations().list(resourceGroupName, extractFqdn(domainName, "zone"), RecordType.TXT, null);
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

	public static boolean deployChallenge(DnsManagementClient dnsClient, String resourceGroupName, String domainName, String token, boolean verifyRecord) throws Exception
	{
		String zone = extractFqdn(domainName, "zone");
		String host = extractFqdn(domainName, "host");
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
		if (httpSuccess(ret))
		{
			if (verifyRecord)
			{
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
			else
			{
				return true;
			}
		}
		else
		{
			return false;
		}
	}

	public static boolean cleanChallenge(DnsManagementClient dnsClient, String resourceGroupName, String domainName) throws Exception
	{
		String zone = extractFqdn(domainName, "zone");
		String host = extractFqdn(domainName, "host");
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

	public static void main(String[] args)
	{
		String operationName = null;
		String domainName = null;
		String challengeToken = null;
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
			challengeToken = args[2];
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
			InputStream iStream = LetsencryptAzure.class.getClassLoader().getResourceAsStream(settingsFileName);
			properties.load(iStream);
		}
		catch (IOException e)
		{
			System.out.println("Make sure you have your configured app.properties file in place.");
			System.exit(1);
		}

		String subscriptionId = parseProperty(properties, "subscriptionId");
		String clientId = parseProperty(properties, "clientId");
		String username = parseProperty(properties, "username");
		String password = parseProperty(properties, "password");
		String resourceGroupName = parseProperty(properties, "resourceGroupName");

		Configuration config;
		try
		{
			config = createConfiguration(subscriptionId, clientId, username, password);
			DnsManagementClient dnsClient = DnsManagementService.create(config);

			switch (operationName)
			{
				case "deploy_challenge":
					deployChallenge(dnsClient, resourceGroupName, domainName, domainToken, true);
					break;
				case "clean_challenge":
					cleanChallenge(dnsClient, resourceGroupName, domainName);
					break;
				default:
					break;
			}
		}
		catch (Exception e)
		{
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
