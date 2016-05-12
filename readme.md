# About

[Let's Encrypt](https://letsencrypt.org/) is a free [ACME](https://datatracker.ietf.org/wg/acme/documents/) certificate authority. ACME allows for automated certificate provisioning and renewal, which requires certain verification methods to be in place. One of those is DNS-01 in which case the domain in question is verified by setting up temporary TXT records for your domain.

**Certzure** is a DNS-01 hook for the 3rd party [letsencrypt.sh](https://github.com/lukas2511/letsencrypt.sh) client. It allows for obtaining and/or renewing certificates for domains managed by [Azure DNS](https://azure.microsoft.com/en-us/services/dns/).

You **need** to perform a few tasks by hand to prepare your environment correctly - this is not an _it just works_ application, so please follow this guide before attempting to use it, otherwise the attempt **will** fail.

# DNS Zones

Azure DNS is available via the [Azure Resource Manager](https://azure.microsoft.com/en-us/documentation/articles/resource-group-overview/) (ARM). This means that all DNS zones you host on Azure are part of a [Resource Group](https://azure.microsoft.com/en-us/documentation/articles/resource-group-overview/#resource-groups). If you haven't done so yet, create a Resource Group for your DNS zones in Azure, then copy over the records from your previous DNS server. For more details on how to do this, please refer to [Manage DNS records and record sets using the Azure portal](https://azure.microsoft.com/en-us/documentation/articles/dns-operations-recordsets-portal/). 

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

# Client

Certzure needs **JRE 1.5** or later to work. Please note that this only applies to Certzure itself, I cannot guarantee that all other dependencies also support this version.

To download letsencrypt.sh and Certzure (make sure to update **VERSION** to the latest one):

~~~
VERSION=1.0.0
wget https://github.com/bviktor/certzure/releases/download/v${VERSION}/certzure-${VERSION}.zip
unzip certzure-${VERSION}.zip -d /opt
git clone https://github.com/lukas2511/letsencrypt.sh.git
cd letsencrypt.sh
~~~

Tell letsencrypt.sh what your domain name is:

~~~
echo 'your.domain.name' > domains.txt
~~~

Now set up the Certzure config file, **/opt/certzure/certzure.properties**:

~~~
subscriptionId = "2a4da06c-ff07-410d-af8a-542a512f5092"
clientId = "1950a258-227b-4e31-a9cf-717495945fc2"
username = "dns@foobar.com"
password = "whatever"
resourceGroupName = "DNSGroup"
~~~

The properties should be self-explanatory:

- **subscriptionId**: the ID of your Azure subscription
- **clientId**: the ID of the AAD app you just set up in a previous step
- **username**: an Azure admin account - **Note**: this account must not have 2FA enabled
- **password**: the account's password
- **resourceGroupName**: the resource group that holds your DNS zones

After this, you **need** to restrict access to this file, **otherwise other users may gain access to your Azure admin account**. Example:

~~~
chown root.root /opt/certzure/certzure.properties
chmod 0400 /opt/certzure/certzure.properties
~~~

# Usage

Once everything's in place, you can obtain a certificate with the following command:

~~~
./letsencrypt.sh --cron --hook /opt/certzure/certzure.sh --challenge dns-01
~~~
