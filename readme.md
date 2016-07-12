# About

**[Let's Encrypt](https://letsencrypt.org/)** is a free **[ACME](https://datatracker.ietf.org/wg/acme/documents/)** certificate authority. ACME allows for automated certificate provisioning and renewal, which requires certain verification methods to be in place. One of those is **DNS-01** in which case the domain in question is verified by setting up temporary TXT records for your domain. **[Certbot](https://certbot.eff.org/)**, the official Let's Encrypt client [doesn't support DNS-01 yet](https://github.com/certbot/certbot/pull/2061), so you need a 3rd party client, such as **[letsencrypt.sh](https://github.com/lukas2511/letsencrypt.sh)**. DNS-01 has important advantages over other methods:

- It doesn't require additional webserver configuration
- It doesn't require additional firewall configuration
- It doesn't make you stop your webserver
- Most importantly, it doesn't require the webserver to be publicly accessible thus you can obtain certificates for Intranet sites as well

**Certzure** is a DNS-01 hook for letsencrypt.sh. With Certzure, you can obtain and renew certificates for domains managed by **[Azure DNS](https://azure.microsoft.com/en-us/services/dns/)**.

You **need** to perform a few tasks by hand to prepare your environment correctly - this is not an _it just works_ application, so please follow this guide before attempting to use it, otherwise the attempt **will** fail.

# DNS Zones

Azure DNS is available via the **[Azure Resource Manager](https://azure.microsoft.com/en-us/documentation/articles/resource-group-overview/)** (ARM). This means that all DNS zones you host on Azure are part of a **[Resource Group](https://azure.microsoft.com/en-us/documentation/articles/resource-group-overview/#resource-groups)**. If you haven't done so yet, create a Resource Group for your DNS zones in Azure, then copy over the records from your previous DNS server. For more details on how to do this, please refer to [Manage DNS records and record sets using the Azure portal](https://azure.microsoft.com/en-us/documentation/articles/dns-operations-recordsets-portal/). 

Once done, modify your NS records to point to the Azure DNS servers. This is usually done via the web interface of your DNS registrar. When finished, verify with **nslookup** that your records are indeed served by Azure. Example:

~~~
C:\Users\user>nslookup
Standardserver:  resolver1.opendns.com
Address:  208.67.222.222

> set type=ns
> foobar.com
Server:  resolver1.opendns.com
Address:  208.67.222.222

Nicht autorisierende Antwort:
foobar.com      nameserver = ns1-01.azure-dns.com
foobar.com      nameserver = ns2-01.azure-dns.net
foobar.com      nameserver = ns3-01.azure-dns.org
foobar.com      nameserver = ns4-01.azure-dns.info
~~~

# Azure App

You cannot access Azure resources directly so you need to add an Azure AD (AAD) application to your directory. You'll need **both** the classic and the new Azure portal to do this.

## Classic Portal

On the **[classic portal](https://manage.windowsazure.com/)**, open your directory, select the **Applications** tab and click **Add**:

- **Add an application my organization is developing**
- Name: **certzure**
- **Native Client Application**
- Redirect URI: unused, thus can be anything e.g. company website address

Take note of the **Client ID**.

Permissions to other applications:

 - add **Windows Azure Service Management API / Access Azure Service Management as organization**

## New Portal

Now open the **[new portal](https://portal.azure.com/)**:

 - Select the resource group which holds your DNS zones
 - **Users / Add**
 - Select a role: **DNS Zone Contributor**
 - Select **certzure**

**Note:** If you want to avoid managing your DNS records by an Azure co-admin, repeat this for the user account as well.

# Client

## Prerequisites

Certzure needs **JRE 1.5** or later to work. Please note that this only applies to Certzure itself, I cannot guarantee that all other dependencies also support this version.

## Installation

Tell your environment what the latest version of Certzure is and also your [domain name(s)](https://github.com/lukas2511/letsencrypt.sh/blob/master/docs/domains_txt.md):

~~~
CERTZURE_VERSION='0.0.0'
MY_DOMAIN='your.domain.name'
~~~

Now install letsencrypt.sh and Certzure:

~~~
wget https://github.com/bviktor/certzure/releases/download/v${CERTZURE_VERSION}/certzure-${CERTZURE_VERSION}.zip
unzip certzure-${CERTZURE_VERSION}.zip -d /opt
chmod +x /opt/certzure/certzure.sh
git clone https://github.com/lukas2511/letsencrypt.sh.git /opt/letsencrypt.sh
echo ${MY_DOMAIN} > /opt/letsencrypt.sh/domains.txt
touch /opt/certzure/certzure.properties
chown root.root /opt/certzure/certzure.properties
chmod 0400 /opt/certzure/certzure.properties
~~~

## Configuration

### letsencrypt.sh

It's recommended to test the staging Let's Encrypt CA first due to [their limits](https://letsencrypt.org/docs/rate-limits/).
You can configure this via letsencrypt.sh's **config** file:

~~~
echo 'CA="https://acme-staging.api.letsencrypt.org/directory"' >> /opt/letsencrypt.sh/config
~~~

Once everything's working fine, just commment this line out with a number sign (#).
For more configuration options, refer to the [example config](https://github.com/lukas2511/letsencrypt.sh/blob/master/docs/examples/config).

### Certzure

Set up the Certzure config file, **/opt/certzure/certzure.properties**:

~~~
subscriptionId = "2a4da06c-ff07-410d-af8a-542a512f5092"
clientId = "1950a258-227b-4e31-a9cf-717495945fc2"
username = "dns@foobar.com"
password = "whatever"
resourceGroupName = "DNSGroup"
smtpHost = "smtp.office365.com"
smtpPort = "587"
smtpSender = "certzure@foobar.com"
smtpRcpt = "admin@foobar.com"
smtpUser = "certzure@foobar.com"
smtpPassword = "whatforever"
smtpSsl = "false"
smtpStartTls = "true"
~~~

The properties should be self-explanatory:

- **subscriptionId**: the ID of your Azure subscription
- **clientId**: the ID of the AAD app you just set up in a previous step
- **username**: either an Azure co-admin or a user account with granted permissions as explained above - **Note**: this account must not have 2FA enabled
- **password**: the account's password
- **resourceGroupName**: the resource group that holds your DNS zones
- **smtpHost**: SMTP server address
- **smtpPort**: SMTP server port
- **smtpSender**: sender of the notification emails (**From:** field)
- **smtpRcpt**: recipient of the notification emails (**To:** field)
- **smtpUser**: SMTP user name
- **smtpPassword**: SMTP password
- **smtpSsl**: use SSL for SMTP (true / false)
- **smtpStartTls**: use STARTTLS for SMTP (true / false)

## Usage

Once everything's in place, you can obtain a certificate with the following command:

~~~
./letsencrypt.sh --cron --hook /opt/certzure/certzure.sh --challenge dns-01
~~~

To make your system renew certs every month:

~~~
echo '00 07 1 * * root /opt/letsencrypt.sh/letsencrypt.sh "--cron" "--hook" "/opt/certzure/certzure.sh" "--challenge" "dns-01" "--force" >> /var/log/certzure.log 2>&1' > /etc/cron.d/certzure
systemctl restart crond.service
~~~

# FAQ

- Why don't you use the REST API?

Because there's no REST API for Azure DNS yet. Remember, even the service is in preview, let alone the API.

- Why can't I use a certificate for authentication?

That's a very good question, you might as well ask the Azure API developers and let me know the reason!

- Why Java?

Because that's the only available option on Linux. Since most webservers run on Linux, that should be a priority. The Python API only supports REST and as mentioned above, that lacks DNS support. Finally, Mono doesn't have Azure support at all.

- Why is the JAR so big?

Actually, it's already way smaller than it used to be, thanks to some JAR cleanup methods. Either way, it's because of the dependencies. Certzure depends on AAD and Azure libraries, those in turn also rely on Apache libraries, and so on and so forth. Cerzure itself only adds about 20kB to the total size.

- Why are there so many unhandled exceptions and other caveats in the code?

Because I had to implement this in a hurry, with totally awful, and often nonexistent documentation, without good examples, at a time when the Azure folks moved their repos so I even had to dig out some of the example code from Google Cache. Also, I'm not even a Java programmer, so the fact that this program even works is already quite an accomplishment on its own. Patches are very-very welcome!

- Do you plan to support Certbot once they add DNS-01 support?

Most definitely.

- FUUUUU Azure, Microsoft sucks, etc.

Take a deep breath.
