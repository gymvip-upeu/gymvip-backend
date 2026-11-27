param name string
param location string
param environmentId string
param image string
param port int
param healthPath string
param registryServer string
param identityId string
param publicConfig object
param secretUrls object

var secretNames = items(secretUrls)
var environmentValues = [for entry in items(publicConfig): { name: entry.key, value: entry.value }]
var environmentSecrets = [for entry in secretNames: { name: entry.key, secretRef: toLower(replace(entry.key, '_', '-')) }]
resource app 'Microsoft.App/containerApps@2024-03-01' = {
  name: name
  location: location
  identity: {
    type: 'UserAssigned'
    userAssignedIdentities: {
      '${identityId}': {}
    }
  }
  properties: {
    managedEnvironmentId: environmentId
    configuration: {
      activeRevisionsMode: 'Single'
      ingress: {
        // In an internal workload environment, this ingress is reachable only within the VNet.
        external: true
        targetPort: port
        transport: 'http'
        allowInsecure: false
      }
      registries: [{ server: registryServer, identity: identityId }]
      secrets: [for entry in secretNames: {
        name: toLower(replace(entry.key, '_', '-'))
        keyVaultUrl: entry.value
        identity: identityId
      }]
    }
    template: {
      containers: [{
        name: name
        image: image
        resources: { cpu: json('0.5'), memory: '1Gi' }
        env: concat(environmentValues, environmentSecrets)
        probes: [{
          type: 'Readiness'
          httpGet: { path: healthPath, port: port, scheme: 'HTTP' }
          periodSeconds: 15
          initialDelaySeconds: 15
          failureThreshold: 3
        }]
      }]
      scale: { minReplicas: 1, maxReplicas: 3 }
    }
  }
}
output fqdn string = app.properties.configuration.ingress.fqdn
