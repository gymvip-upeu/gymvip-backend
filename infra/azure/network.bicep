param location string = resourceGroup().location
param name string
param vnetPrefix string
param applicationSubnetPrefix string
param databaseSubnetPrefix string
param monitoringSubnetPrefix string

resource network 'Microsoft.Network/virtualNetworks@2024-05-01' = {
  name: '${name}-vnet'
  location: location
  properties: {
    addressSpace: { addressPrefixes: [vnetPrefix] }
    subnets: [
      {
        name: 'apps'
        properties: {
          addressPrefix: applicationSubnetPrefix
          delegations: [{ name: 'container-apps', properties: { serviceName: 'Microsoft.App/environments' } }]
        }
      }
      {
        name: 'postgres'
        properties: {
          addressPrefix: databaseSubnetPrefix
          delegations: [{ name: 'postgres-flexible', properties: { serviceName: 'Microsoft.DBforPostgreSQL/flexibleServers' } }]
        }
      }
      {
        name: 'monitoring'
        properties: { addressPrefix: monitoringSubnetPrefix }
      }
    ]
  }
}

resource appsSubnet 'Microsoft.Network/virtualNetworks/subnets@2024-05-01' existing = {
  parent: network
  name: 'apps'
}
resource dbSubnet 'Microsoft.Network/virtualNetworks/subnets@2024-05-01' existing = {
  parent: network
  name: 'postgres'
}
resource monitoringSubnet 'Microsoft.Network/virtualNetworks/subnets@2024-05-01' existing = {
  parent: network
  name: 'monitoring'
}

resource environment 'Microsoft.App/managedEnvironments@2024-03-01' = {
  name: '${name}-internal'
  location: location
  properties: {
    vnetConfiguration: { infrastructureSubnetId: appsSubnet.id, internal: true }
    workloadProfiles: [{ name: 'Consumption', workloadProfileType: 'Consumption' }]
  }
}

module applicationDns './application-dns.bicep' = {
  name: '${name}-dns'
  params: {
    zone: environment.properties.defaultDomain
    privateIp: environment.properties.staticIp
    networkId: network.id
  }
}

resource dbDns 'Microsoft.Network/privateDnsZones@2024-06-01' = {
  name: '${name}.postgres.database.azure.com'
  location: 'global'
}
resource dbVnetLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2024-06-01' = {
  parent: dbDns
  name: 'link-postgres'
  location: 'global'
  properties: { registrationEnabled: false, virtualNetwork: { id: network.id } }
}

output containerEnvironmentId string = environment.id
output postgresSubnetId string = dbSubnet.id
output postgresDnsZoneId string = dbDns.id
output monitoringSubnetId string = monitoringSubnet.id
