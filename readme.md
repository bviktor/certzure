# About

[Let's Encrypt](https://letsencrypt.org/) is an [ACME](https://datatracker.ietf.org/wg/acme/documents/) certificate authority. ACME allows for automated certificate provisioning and renewal. This requires certain verification methods to be in place. One of those is DNS-01 in which case the domain in question is verified by setting up TXT records for your domain.

**letsencrypt-azure** is a DNS-01 hook for the 3rd party [letsencrypt.sh](https://github.com/lukas2511/letsencrypt.sh) client. It allows for obtaining and/or renewing certificates for domains managed by [Azure DSN](https://azure.microsoft.com/en-us/services/dns/).

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

# Usage

    git clone https://github.com/lukas2511/letsencrypt.sh.git
    cd letsencrypt.sh
    wget https://remedian.vault-tec.info/letsencrypt-azure.zip
    unzip letsencrypt-azure.zip
    echo 'your.domain.name' > domains.txt
    ./letsencrypt.sh --cron --hook letsencrypt-azure/letsencrypt-azure.sh --challenge dns-01
