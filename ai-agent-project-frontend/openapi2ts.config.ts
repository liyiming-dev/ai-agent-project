import path from 'node:path'

export default {
  schemaPath: path.join(__dirname, 'openapi', 'schema.json'),
  serversPath: './src/api',
  requestLibPath: '@/request',
}
