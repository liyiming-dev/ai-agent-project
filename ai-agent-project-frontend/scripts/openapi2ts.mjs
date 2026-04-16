import { access, mkdir, writeFile } from 'node:fs/promises'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

import { generateService } from '@umijs/openapi'

const defaultSchemaUrl = 'http://localhost:8123/api/v3/api-docs'
const cacheFlag = '--cached'
const isCachedMode = process.argv.includes(cacheFlag)

const scriptDir = path.dirname(fileURLToPath(import.meta.url))
const projectDir = path.resolve(scriptDir, '..')
const schemaCachePath = path.join(projectDir, 'openapi', 'schema.json')
const schemaUrl = process.env.OPENAPI_SCHEMA_URL || defaultSchemaUrl

async function fileExists(filePath) {
  try {
    await access(filePath)
    return true
  } catch {
    return false
  }
}

async function updateSchemaCache() {
  const response = await fetch(schemaUrl, {
    headers: {
      Accept: 'application/json',
    },
    signal: AbortSignal.timeout(5000),
  })

  if (!response.ok) {
    throw new Error(`OpenAPI 请求失败，HTTP ${response.status}`)
  }

  const schema = await response.json()

  await mkdir(path.dirname(schemaCachePath), { recursive: true })
  await writeFile(schemaCachePath, `${JSON.stringify(schema, null, 2)}\n`, 'utf8')
}

async function generateFromCache() {
  await generateService({
    schemaPath: schemaCachePath,
    serversPath: './src/api',
    requestLibPath: '@/request',
  })
}

async function main() {
  if (!isCachedMode) {
    try {
      await updateSchemaCache()
      console.log(`OpenAPI schema 已刷新: ${schemaUrl}`)
    } catch (error) {
      const hasCache = await fileExists(schemaCachePath)
      const details = error instanceof Error ? error.message : String(error)
      console.error(`无法获取 OpenAPI 文档: ${schemaUrl}`)
      console.error(details)
      console.error('请先启动后端服务，或通过 OPENAPI_SCHEMA_URL 指定正确的文档地址。')

      if (hasCache) {
        console.error('如需继续使用上次缓存，可运行: npm run openapi2ts:cached')
      }

      process.exit(1)
    }
  }

  if (!(await fileExists(schemaCachePath))) {
    console.error(`未找到本地 OpenAPI 缓存: ${schemaCachePath}`)
    console.error('请先运行 npm run openapi2ts 刷新缓存。')
    process.exit(1)
  }

  await generateFromCache()
}

main().catch((error) => {
  const details = error instanceof Error ? error.message : String(error)
  console.error(`openapi2ts 执行失败: ${details}`)
  process.exit(1)
})
