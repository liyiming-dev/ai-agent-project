import path from 'node:path'

const openapiConfig = {
  schemaPath: path.join(__dirname, '..', 'openapi', 'schema.json'),
  serversPath: './src/api',
  requestLibPath: '@/request',
}

export default openapiConfig
