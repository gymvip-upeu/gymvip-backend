param location string = resourceGroup().location
param serverName string
param delegatedSubnetId string
param privateDnsZoneId string
param administratorLogin string
@secure()
param administratorPassword string

resource postgres 'Microsoft.DBforPostgreSQL/flexibleServers@2024-08-01' = {
  name: serverName
  location: location
  sku: { name: 'Standard_B2s', tier: 'Burstable' }
  properties: {
    version: '18'
    administratorLogin: administratorLogin
    administratorLoginPassword: administratorPassword
    storage: { storageSizeGB: 32 }
    backup: { backupRetentionDays: 14, geoRedundantBackup: 'Disabled' }
    network: {
      delegatedSubnetResourceId: delegatedSubnetId
      privateDnsZoneArmResourceId: privateDnsZoneId
      publicNetworkAccess: 'Disabled'
    }
  }
}

resource databases 'Microsoft.DBforPostgreSQL/flexibleServers/databases@2024-08-01' = [for database in ['gymvip_core', 'gymvip_comms']: {
  parent: postgres
  name: database
  properties: { charset: 'utf8', collation: 'en_US.utf8' }
}]

output serverFqdn string = postgres.properties.fullyQualifiedDomainName
