# Azure

## Classic Portal

Create a new AAD application on the **classic** portal.

Open your directory, select the **Applications** tab and click **Add**:

 - **Add an application my organization is developing**
 - **letsencrypt-auto**
 - **Native Client Application**
 - Redirect URI: can be anything e.g. company site

Get the **Client ID**.

Permissions to other applications:

 - add **Windows Azure Service Management API / Access Azure Service Management as organization**

## New Portal

Open the **new** portal:

 - Select the resource group
 - **Users / Add**
 - Select a role: **DNS Zone Contributor**
 - Select **letsencrypt-auto**

# Eclipse

Download and install:

 - [Eclipse IDE for Java EE Developers 64 bit](http://www.eclipse.org/downloads/)
 - [Java SE JDK 64 bit](http://www.oracle.com/technetwork/indexes/downloads/index.html?ssSourceSiteId=ocomen)

Window / Preferences / Maven:

 - Download Artifact Sources
 - Download Artifact JavaDoc
 - Download repository index updates on startup

Help / Install New Software:

 - http://dl.microsoft.com/eclipse
 - Azure Toolkit for Eclipse

Help / Check for Updates
Window / Show View / Other / Maven Repositories
Global Repositories / Central / Rebuild Index

# Usage

    git clone https://github.com/lukas2511/letsencrypt.sh.git
    cd letsencrypt.sh
    wget https://remedian.vault-tec.info/letsencrypt-azure.zip
    unzip letsencrypt-azure.zip
    echo 'your.domain.name' > domains.txt
    ./letsencrypt.sh --cron --hook letsencrypt-azure/letsencrypt-azure.sh --challenge dns-01

# References

 - [Getting Started with Azure Management Libraries for Java](https://azure.microsoft.com/en-us/blog/getting-started-with-the-azure-java-management-libraries/)
 - [Create Active Directory application and service principal using portal](https://azure.microsoft.com/en-us/documentation/articles/resource-group-create-service-principal-portal/)
 - [Authenticating a service principal with Azure Resource Manager](https://azure.microsoft.com/en-us/documentation/articles/resource-group-authenticate-service-principal/)
 - [Creating DNS zones and record sets using the .NET SDK](https://azure.microsoft.com/en-us/documentation/articles/dns-sdk/)
 - [AADSTS90014: The request body must contain the following parameter: 'client_secret or client_assertion'](https://github.com/Azure-Samples/active-directory-dotnet-graphapi-console/issues/4)
 - [ServicePrincipalExample Class](http://azure.github.io/azure-sdk-for-java/com/microsoft/azure/management/samples/authentication/ServicePrincipalExample.html)
 - [PublicClient.java](https://github.com/Azure-Samples/active-directory-java-native-headless/blob/master/src/main/java/PublicClient.java)
 - [AuthenticationContext Class](https://msdn.microsoft.com/en-us/library/microsoft.identitymodel.clients.activedirectory.authenticationcontext.aspx)
 - [401 when authenticating an OAuth 2.0 bearer token](http://stackoverflow.com/questions/26118671/401-when-authenticating-an-oauth-2-0-bearer-token-with-microsoft-azure-active-di)
 - [Microsoft Azure REST API + OAuth 2.0](https://ahmetalpbalkan.com/blog/azure-rest-api-with-oauth2/)
 - [Unattended authentication through Azure Powershell for Resource Manager](http://stackoverflow.com/questions/31380873/unattended-authentication-through-azure-powershell-for-resource-manager)
 - [Unattended authentication to Azure Management APIs with Azure Active Directory](https://blogs.msdn.microsoft.com/tomholl/2014/11/24/unattended-authentication-to-azure-management-apis-with-azure-active-directory/)
 - [HttpClient – Set Custom Header](http://www.baeldung.com/httpclient-custom-http-header)
 - [HttpClient Basic Authentication](http://www.baeldung.com/httpclient-4-basic-authentication)
