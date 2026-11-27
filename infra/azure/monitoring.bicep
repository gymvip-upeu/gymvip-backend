param location string = resourceGroup().location
param name string
param subnetId string
param operatorSubnetPrefix string
param adminUsername string = 'gymvip'
param sshPublicKey string

resource inbound 'Microsoft.Network/networkSecurityGroups@2024-05-01' = {
  name: '${name}-nsg'
  location: location
  properties: {
    securityRules: [
      {
        name: 'operator-ssh'
        properties: {
          priority: 100
          direction: 'Inbound'
          protocol: 'Tcp'
          access: 'Allow'
          sourcePortRange: '*'
          destinationPortRange: '22'
          sourceAddressPrefix: operatorSubnetPrefix
          destinationAddressPrefix: '*'
        }
      }
      {
        name: 'deny-inbound'
        properties: {
          priority: 200
          direction: 'Inbound'
          protocol: '*'
          access: 'Deny'
          sourcePortRange: '*'
          destinationPortRange: '*'
          sourceAddressPrefix: '*'
          destinationAddressPrefix: '*'
        }
      }
    ]
  }
}

resource nic 'Microsoft.Network/networkInterfaces@2024-05-01' = {
  name: '${name}-nic'
  location: location
  properties: {
    networkSecurityGroup: { id: inbound.id }
    ipConfigurations: [{
      name: 'private'
      properties: { privateIPAllocationMethod: 'Dynamic', subnet: { id: subnetId } }
    }]
  }
}

resource monitoring 'Microsoft.Compute/virtualMachines@2024-03-01' = {
  name: name
  location: location
  properties: {
    hardwareProfile: { vmSize: 'Standard_B2s' }
    osProfile: {
      computerName: name
      adminUsername: adminUsername
      linuxConfiguration: {
        disablePasswordAuthentication: true
        ssh: {
          publicKeys: [{
            path: '/home/${adminUsername}/.ssh/authorized_keys'
            keyData: sshPublicKey
          }]
        }
      }
    }
    storageProfile: {
      imageReference: { publisher: 'Canonical', offer: 'ubuntu-24_04-lts', sku: 'server', version: 'latest' }
      osDisk: { createOption: 'FromImage', managedDisk: { storageAccountType: 'StandardSSD_LRS' } }
      dataDisks: [{
        lun: 0
        createOption: 'Empty'
        diskSizeGB: 32
        managedDisk: { storageAccountType: 'StandardSSD_LRS' }
        deleteOption: 'Detach'
      }]
    }
    networkProfile: { networkInterfaces: [{ id: nic.id }] }
  }
}
output machineName string = monitoring.name
