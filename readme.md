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
