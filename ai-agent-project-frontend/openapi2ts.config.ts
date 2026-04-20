import path from 'node:path'
import { fileURLToPath } from 'node:url'

const __filename = fileURLToPath(import.meta.url)
const __dirname = path.dirname(__filename)

export default {
  schemaPath: path.join(__dirname, 'openapi', 'schema.json'),
  serversPath: './src/api',
  requestLibPath: '@/request',
}
