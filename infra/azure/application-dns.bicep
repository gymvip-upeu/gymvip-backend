param zone string
param privateIp string
param networkId string

resource applicationDns 'Microsoft.Network/privateDnsZones@2024-06-01' = {
  name: zone
  location: 'global'
}
resource appVnetLink 'Microsoft.Network/privateDnsZones/virtualNetworkLinks@2024-06-01' = {
  parent: applicationDns
  name: 'link-apps'
  location: 'global'
  properties: { registrationEnabled: false, virtualNetwork: { id: networkId } }
}
resource fqdns 'Microsoft.Network/privateDnsZones/A@2024-06-01' = [for host in ['@', '*']: {
  parent: applicationDns
  name: host
  properties: { ttl: 300, aRecords: [{ ipv4Address: privateIp }] }
}]
