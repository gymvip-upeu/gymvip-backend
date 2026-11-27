targetScope = 'resourceGroup'

@allowed(['staging', 'production'])
param environment string
param location string = resourceGroup().location
param containerEnvironmentId string
param registryServer string
param identityId string
param coreImage string
param commsImage string
param coreSecretUrls object
param commsSecretUrls object
param commsPrivateUrl string
param allowedOrigins string
param staticLocation string
param staticPrefix string

module core './runtime.bicep' = {
  name: 'gymvip-api-${environment}'
  params: {
    name: 'gymvip-api-${environment}'
    location: location
    environmentId: containerEnvironmentId
    image: coreImage
    port: 8080
    healthPath: '/api/v1/health/readiness'
    registryServer: registryServer
    identityId: identityId
    secretUrls: coreSecretUrls
    publicConfig: {
      PORT: '8080'
      SPRING_PROFILES_ACTIVE: 'prod'
      APP_SEED_DEV_DEFAULTS_ENABLED: 'false'
      CORS_ALLOWED_ORIGINS: allowedOrigins
      APP_COMMS_URL: commsPrivateUrl
      GYMVIP_MANAGEMENT_ENDPOINTS: 'health,info,prometheus'
      GYMVIP_OPERATIONS_METRICS_ENABLED: 'true'
      CULQI_LIVE_ENABLED: 'false'
    }
  }
}

module comms './runtime.bicep' = {
  name: 'gymvip-comms-${environment}'
  params: {
    name: 'gymvip-comms-${environment}'
    location: location
    environmentId: containerEnvironmentId
    image: commsImage
    port: 8085
    healthPath: '/actuator/health'
    registryServer: registryServer
    identityId: identityId
    secretUrls: commsSecretUrls
    publicConfig: {
      PORT: '8085'
      COMMS_MODE: 'simulation'
      EMAIL_MODE: 'simulation'
      GYMVIP_MANAGEMENT_ENDPOINTS: 'health,info,prometheus'
      GYMVIP_OPERATIONS_METRICS_ENABLED: 'true'
    }
  }
}

resource staticSites 'Microsoft.Web/staticSites@2023-12-01' = [for site in ['portal', 'publico']: {
  name: '${staticPrefix}-${site}-${environment}'
  location: staticLocation
  sku: { name: 'Standard', tier: 'Standard' }
  properties: { stagingEnvironmentPolicy: 'Disabled' }
}]

output apiFqdn string = core.outputs.fqdn
output commsFqdn string = comms.outputs.fqdn
