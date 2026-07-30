import {
  CopyOutlined,
  LockOutlined,
  SearchOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, App, Button, Empty, Input, Segmented, Spin, Tabs, Tag, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { platformApi } from '../api/platformApi'
import type {
  ApiSpecContractRecord,
  ContentContractRecord,
  OperationContractRecord,
  ProcessingContractRecord,
  ResponseContractRecord,
} from '../types/platform'

const { Text, Paragraph } = Typography

type ApiReferenceProps = {
  projectId: string
  apiSpecId: string
}

type DetailTab = 'parameters' | 'request' | 'responses' | 'processing' | 'source'

export function ApiReference({ projectId, apiSpecId }: ApiReferenceProps) {
  const { message } = App.useApp()
  const [filter, setFilter] = useState('')
  const [selectedKey, setSelectedKey] = useState<string>()
  const [activeTab, setActiveTab] = useState<DetailTab>('request')
  const contractQuery = useQuery({
    queryKey: ['api-spec-contract', projectId, apiSpecId],
    queryFn: () => platformApi.getApiSpecContract(projectId, apiSpecId),
  })
  const sourceQuery = useQuery({
    queryKey: ['api-spec-source', projectId, apiSpecId],
    queryFn: () => platformApi.getApiSpecSource(projectId, apiSpecId),
    enabled: activeTab === 'source',
  })

  const operations = useMemo(() => contractQuery.data?.operations ?? [], [contractQuery.data])
  const filteredOperations = useMemo(() => filterOperations(operations, filter), [filter, operations])
  const selectedOperation = filteredOperations.find((operation) => operationKey(operation) === selectedKey)
    ?? operations.find((operation) => operationKey(operation) === selectedKey)
    ?? filteredOperations[0]
    ?? operations[0]

  useEffect(() => {
    if (selectedOperation && operationKey(selectedOperation) !== selectedKey) {
      setSelectedKey(operationKey(selectedOperation))
    }
  }, [selectedKey, selectedOperation])

  if (contractQuery.isPending) {
    return <div className="api-reference-loading"><Spin tip="Loading API contract…" /></div>
  }
  if (contractQuery.isError) {
    return (
      <div className="api-reference-state">
        <Alert
          type="error"
          showIcon
          message="The API contract could not be loaded"
          description="The original OpenAPI source is still available from this API specification."
        />
      </div>
    )
  }
  if (!contractQuery.data || !operations.length) {
    return <div className="api-reference-state"><Empty description="No operations were found in this OpenAPI document." /></div>
  }

  return (
    <div className="api-reference">
      <aside className="api-reference-nav" aria-label="API operations">
        <Input
          allowClear
          prefix={<SearchOutlined />}
          placeholder="Search operations"
          value={filter}
          onChange={(event) => setFilter(event.target.value)}
        />
        <div className="api-reference-operation-list">
          <OperationList
            operations={filteredOperations}
            selectedKey={selectedOperation ? operationKey(selectedOperation) : undefined}
            onSelect={(operation) => setSelectedKey(operationKey(operation))}
          />
          {!filteredOperations.length ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No matching operations" /> : null}
        </div>
      </aside>

      <main className="api-reference-detail">
        {selectedOperation ? (
          <OperationDetail
            contract={contractQuery.data}
            operation={selectedOperation}
            activeTab={activeTab}
            source={sourceQuery.data}
            sourceLoading={sourceQuery.isPending}
            onTabChange={(value) => setActiveTab(value as DetailTab)}
            onCopy={(value) => void copyToClipboard(value, message)}
          />
        ) : null}
      </main>
    </div>
  )
}

function OperationList({
  operations,
  selectedKey,
  onSelect,
}: {
  operations: OperationContractRecord[]
  selectedKey?: string
  onSelect: (operation: OperationContractRecord) => void
}) {
  const groups = useMemo(() => {
    const result = new Map<string, OperationContractRecord[]>()
    operations.forEach((operation) => {
      const group = operation.tags[0] || 'Operations'
      result.set(group, [...(result.get(group) ?? []), operation])
    })
    return [...result.entries()]
  }, [operations])

  return groups.map(([tag, items]) => (
    <section className="api-reference-operation-group" key={tag}>
      <h2>{tag}</h2>
      {items.map((operation) => {
        const selected = operationKey(operation) === selectedKey
        return (
          <button
            className={`api-reference-operation ${selected ? 'selected' : ''}`}
            key={operationKey(operation)}
            type="button"
            onClick={() => onSelect(operation)}
          >
            <span className={`method-pill method-${operation.method.toLowerCase()}`}>{operation.method}</span>
            <span className="api-reference-operation-copy">
              <strong>{operation.summary || operation.operationId || operation.path}</strong>
              <small>{operation.path}</small>
            </span>
            {operation.capabilities.encrypted ? <LockOutlined title="Encrypted payload" /> : null}
            {operation.diagnostics.length ? <WarningOutlined className="api-reference-warning" title="Contract diagnostics" /> : null}
          </button>
        )
      })}
    </section>
  ))
}

function OperationDetail({
  contract,
  operation,
  activeTab,
  source,
  sourceLoading,
  onTabChange,
  onCopy,
}: {
  contract: ApiSpecContractRecord
  operation: OperationContractRecord
  activeTab: DetailTab
  source?: string
  sourceLoading: boolean
  onTabChange: (value: string) => void
  onCopy: (value: string) => void
}) {
  return (
    <>
      <header className="api-reference-operation-header">
        <div className="api-reference-operation-title">
          <span className={`method-pill method-${operation.method.toLowerCase()}`}>{operation.method}</span>
          <h2>{operation.path}</h2>
          {operation.capabilities.encrypted ? <Tag icon={<LockOutlined />} color="blue">Encrypted</Tag> : null}
        </div>
        <div>
          <h1>{operation.summary || operation.operationId || 'Operation'}</h1>
          {operation.description ? <Paragraph type="secondary">{operation.description}</Paragraph> : null}
        </div>
      </header>

      {operation.diagnostics.length ? (
        <div className="api-reference-diagnostics">
          <Alert
            type="warning"
            showIcon
            message="Contract diagnostics"
            description={operation.diagnostics.map((diagnostic) => `${diagnostic.code}: ${diagnostic.message}`).join(' · ')}
          />
        </div>
      ) : null}

      <Tabs
        className="api-reference-tabs"
        activeKey={activeTab}
        onChange={onTabChange}
        items={[
          { key: 'parameters', label: `Parameters${operation.parameters.length ? ` (${operation.parameters.length})` : ''}`, children: <Parameters parameters={operation.parameters} /> },
          { key: 'request', label: 'Request', children: <PayloadPanel label="Request" payload={operation.request} registry={contract.schemaRegistry} onCopy={onCopy} /> },
          { key: 'responses', label: `Responses${operation.responses.length ? ` (${operation.responses.length})` : ''}`, children: <Responses responses={operation.responses} registry={contract.schemaRegistry} onCopy={onCopy} /> },
          { key: 'processing', label: 'Processing', children: <Processing operation={operation} /> },
          { key: 'source', label: 'Source', children: <SourceViewer source={source} loading={sourceLoading} rawOperation={operation.rawOperation} onCopy={onCopy} /> },
        ]}
      />
    </>
  )
}

function Parameters({ parameters }: { parameters: OperationContractRecord['parameters'] }) {
  if (!parameters.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="This operation has no parameters." />
  }
  return (
    <div className="api-reference-table-wrap">
      <table className="api-reference-table">
        <thead><tr><th>Name</th><th>In</th><th>Type</th><th>Required</th><th>Description</th></tr></thead>
        <tbody>
          {parameters.map((parameter, index) => (
            <tr key={`${parameter.location}:${parameter.name}:${index}`}>
              <td><code>{parameter.name}</code></td>
              <td>{parameter.location}</td>
              <td>{schemaType(parameter.schema)}</td>
              <td>{parameter.required ? 'Yes' : 'No'}</td>
              <td>{parameter.description || '—'}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}

function PayloadPanel({
  label,
  payload,
  registry,
  onCopy,
}: {
  label: string
  payload: OperationContractRecord['request']
  registry: ApiSpecContractRecord['schemaRegistry']
  onCopy: (value: string) => void
}) {
  const defaultView = payload.business ? 'business' : 'wire'
  const [view, setView] = useState<'business' | 'wire'>(defaultView)
  useEffect(() => setView(defaultView), [defaultView, payload])
  const content = view === 'business' ? payload.business : payload.wire
  return (
    <div className="api-reference-payload">
      {payload.business && payload.wire ? (
        <Segmented
          options={[
            { label: `Business ${label}`, value: 'business' },
            { label: `Wire ${label}`, value: 'wire' },
          ]}
          value={view}
          onChange={(value) => setView(value as 'business' | 'wire')}
        />
      ) : null}
      <ContentPanel content={content} registry={registry} onCopy={onCopy} emptyMessage={`No ${view} ${label.toLowerCase()} schema is declared.`} />
    </div>
  )
}

function Responses({
  responses,
  registry,
  onCopy,
}: {
  responses: ResponseContractRecord[]
  registry: ApiSpecContractRecord['schemaRegistry']
  onCopy: (value: string) => void
}) {
  const [status, setStatus] = useState(responses[0]?.status)
  const response = responses.find((item) => item.status === status) ?? responses[0]
  const defaultView = response?.business ? 'business' : 'wire'
  const [view, setView] = useState<'business' | 'wire'>(defaultView)
  useEffect(() => setView(defaultView), [defaultView, response])
  if (!response) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="This operation does not declare any responses." />
  }
  return (
    <div className="api-reference-responses">
      <Segmented options={responses.map((item) => ({ label: item.status, value: item.status }))} value={response.status} onChange={(value) => setStatus(value as string)} />
      {response.description ? <Text type="secondary">{response.description}</Text> : null}
      {response.business && response.wire ? (
        <Segmented
          options={[
            { label: 'Business Response', value: 'business' },
            { label: 'Wire Response', value: 'wire' },
          ]}
          value={view}
          onChange={(value) => setView(value as 'business' | 'wire')}
        />
      ) : null}
      <ContentPanel content={view === 'business' ? response.business : response.wire} registry={registry} onCopy={onCopy} emptyMessage="No schema is declared for this response." />
    </div>
  )
}

function ContentPanel({
  content,
  registry,
  onCopy,
  emptyMessage,
}: {
  content?: ContentContractRecord | null
  registry: ApiSpecContractRecord['schemaRegistry']
  onCopy: (value: string) => void
  emptyMessage: string
}) {
  if (!content?.schema) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyMessage} />
  }
  const sample = content.example ?? schemaExample(content.schema, registry)
  return (
    <div className="api-reference-content-panel">
      <div className="api-reference-content-meta">
        <Text type="secondary">{content.mediaType || 'application/json'}</Text>
        {content.schemaRef ? <Tag>{shortReference(content.schemaRef)}</Tag> : null}
      </div>
      <SchemaTree schema={content.schema} registry={registry} />
      <section className="api-reference-example">
        <div className="api-reference-section-heading">
          <h3>Example</h3>
          <Button size="small" icon={<CopyOutlined />} onClick={() => onCopy(JSON.stringify(sample, null, 2))}>Copy</Button>
        </div>
        <pre>{JSON.stringify(sample, null, 2)}</pre>
      </section>
    </div>
  )
}

function SchemaTree({ schema, registry }: { schema: Record<string, unknown>, registry: ApiSpecContractRecord['schemaRegistry'] }) {
  const resolved = resolveSchema(schema, registry)
  return (
    <section className="api-reference-schema">
      <div className="api-reference-section-heading"><h3>Schema</h3></div>
      <div className="api-reference-schema-head"><span>Field</span><span>Type</span><span>Required</span><span>Constraints / description</span></div>
      <SchemaFields schema={resolved.schema} registry={registry} refs={resolved.ref ? new Set([resolved.ref]) : new Set()} depth={0} required={new Set(asStrings(resolved.schema.required))} />
    </section>
  )
}

function SchemaFields({
  schema,
  registry,
  refs,
  depth,
  required,
}: {
  schema: Record<string, unknown>
  registry: ApiSpecContractRecord['schemaRegistry']
  refs: Set<string>
  depth: number
  required: Set<string>
}) {
  const properties = object(schema.properties)
  const allOf = asObjects(schema.allOf)
  const entries = Object.entries(properties)
  return (
    <>
      {entries.map(([name, value]) => <SchemaField key={`${depth}:${name}`} name={name} schema={object(value)} registry={registry} refs={refs} depth={depth} required={required.has(name)} />)}
      {allOf.map((value, index) => (
        <SchemaFields key={`allOf:${index}`} schema={resolveSchema(value, registry).schema} registry={registry} refs={refs} depth={depth} required={new Set(asStrings(resolveSchema(value, registry).schema.required))} />
      ))}
      {!entries.length && !allOf.length ? <div className="api-reference-schema-empty">{schemaType(schema)}</div> : null}
    </>
  )
}

function SchemaField({
  name,
  schema,
  registry,
  refs,
  depth,
  required,
}: {
  name: string
  schema: Record<string, unknown>
  registry: ApiSpecContractRecord['schemaRegistry']
  refs: Set<string>
  depth: number
  required: boolean
}) {
  const ref = string(schema.$ref)
  const circular = Boolean(ref && refs.has(ref))
  const resolved = circular ? { schema, ref } : resolveSchema(schema, registry)
  const nextRefs = new Set(ref ? [...refs, ref] : refs)
  const nested = object(resolved.schema.properties)
  const items = object(resolved.schema.items)
  const constraints = schemaConstraints(resolved.schema)
  return (
    <>
      <div className="api-reference-schema-row" style={{ paddingLeft: `${depth * 18}px` }}>
        <code>{name}</code>
        <span>{schemaType(resolved.schema)}{resolved.ref ? ` / ${shortReference(resolved.ref)}` : ''}</span>
        <span>{required ? 'Required' : '-'}</span>
        <span>{[constraints, string(resolved.schema.description)].filter(Boolean).join(' / ') || '-'}</span>
      </div>
      {circular ? <div className="api-reference-schema-cycle" style={{ paddingLeft: `${(depth + 1) * 18}px` }}>Circular reference to {shortReference(ref)}</div> : null}
      {!circular && Object.keys(nested).length ? <SchemaFields schema={resolved.schema} registry={registry} refs={nextRefs} depth={depth + 1} required={new Set(asStrings(resolved.schema.required))} /> : null}
      {!circular && Object.keys(items).length ? <SchemaField name="items" schema={items} registry={registry} refs={nextRefs} depth={depth + 1} required={true} /> : null}
    </>
  )
}

function Processing({ operation }: { operation: OperationContractRecord }) {
  const responseProcessing = operation.responses.filter((response) => response.processing)
  if (!operation.request.processing && !responseProcessing.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="This operation does not declare message processing." />
  }
  return (
    <div className="api-reference-protocol">
      {operation.request.processing ? <PipelineSummary title="Request processing" start="Business request" end="Wire request" processing={operation.request.processing} /> : null}
      {responseProcessing.map((response) => (
        <PipelineSummary key={response.status} title={`Response ${response.status} processing`} start="Wire response" end="Business response" processing={response.processing!} />
      ))}
    </div>
  )
}

function PipelineSummary({
  title,
  start,
  end,
  processing,
}: {
  title: string
  start: string
  end: string
  processing: ProcessingContractRecord
}) {
  const pipeline = processing.pipeline
  if (!pipeline) {
    return (
      <Alert
        type="error"
        showIcon
        message={`Pipeline unavailable: ${processing.pipelineReference}`}
        description="The OpenAPI document references a pipeline that is not bundled with this application."
      />
    )
  }
  return (
    <section className="api-reference-transform">
      <h3>{title}</h3>
      <div className="api-reference-processing-meta">
        <Tag color="blue">{pipeline.displayName}</Tag>
        <code>{processing.pipelineReference}</code>
        {pipeline.requiresRuntimeProfile ? <Tag icon={<LockOutlined />}>Runtime profile required</Tag> : null}
      </div>
      {pipeline.description ? <Text type="secondary">{pipeline.description}</Text> : null}
      <ol>
        <li className="api-reference-transform-endpoint">{start}</li>
        {pipeline.steps.map((step) => <li key={step.id}>{step.displayName}</li>)}
        <li className="api-reference-transform-endpoint">{end}</li>
      </ol>
      <PipelineBindings label="Inputs" declared={pipeline.inputs} overrides={processing.inputs} />
      <PipelineBindings label="Outputs" declared={pipeline.outputs} overrides={processing.outputs} />
      <PipelineParameters processing={processing} />
    </section>
  )
}

function PipelineBindings({
  label,
  declared,
  overrides,
}: {
  label: string
  declared: Record<string, { type: string, defaultLocation?: string | null }>
  overrides: Record<string, unknown>
}) {
  const entries = Object.entries(declared)
  if (!entries.length) return null
  return (
    <div className="api-reference-processing-bindings">
      <Text strong>{label}</Text>
      {entries.map(([name, port]) => (
        <div key={name}><code>{name}</code><span>{String(overrides[name] ?? port.defaultLocation ?? '-')}</span><Text type="secondary">{port.type}</Text></div>
      ))}
    </div>
  )
}

function PipelineParameters({ processing }: { processing: ProcessingContractRecord }) {
  const parameters = processing.pipeline ? Object.entries(processing.pipeline.parameters) : []
  if (!parameters.length) return null
  return (
    <div className="api-reference-processing-bindings">
      <Text strong>Parameters</Text>
      {parameters.map(([name, parameter]) => (
        <div key={name}><code>{name}</code><span>{String(processing.parameters[name] ?? parameter.defaultValue ?? '-')}</span><Text type="secondary">{parameter.type}</Text></div>
      ))}
    </div>
  )
}

function SourceViewer({ source, loading, rawOperation, onCopy }: { source?: string, loading: boolean, rawOperation: Record<string, unknown>, onCopy: (value: string) => void }) {
  const [mode, setMode] = useState<'operation' | 'document'>('operation')
  const text = mode === 'document' ? source : JSON.stringify(rawOperation, null, 2)
  return (
    <div className="api-reference-source">
      <div className="api-reference-source-actions">
        <Segmented options={[{ label: 'Operation', value: 'operation' }, { label: 'OpenAPI YAML', value: 'document' }]} value={mode} onChange={(value) => setMode(value as 'operation' | 'document')} />
        <Button size="small" icon={<CopyOutlined />} disabled={!text} onClick={() => text && onCopy(text)}>Copy</Button>
      </div>
      {mode === 'document' && loading ? <Spin /> : <pre>{text || 'Source is unavailable.'}</pre>}
    </div>
  )
}

function filterOperations(operations: OperationContractRecord[], filter: string) {
  const needle = filter.trim().toLowerCase()
  if (!needle) return operations
  return operations.filter((operation) => [operation.path, operation.summary, operation.operationId, ...operation.tags]
    .filter(Boolean)
    .some((value) => String(value).toLowerCase().includes(needle)))
}

function operationKey(operation: OperationContractRecord) {
  return operation.id || `${operation.method} ${operation.path}`
}

function resolveSchema(schema: Record<string, unknown>, registry: ApiSpecContractRecord['schemaRegistry']) {
  const ref = string(schema.$ref)
  return ref && registry[ref] ? { schema: registry[ref], ref } : { schema, ref }
}

function schemaExample(schema: Record<string, unknown>, registry: ApiSpecContractRecord['schemaRegistry'], depth = 0): unknown {
  if (depth > 10) return null
  const resolved = resolveSchema(schema, registry).schema
  if ('example' in resolved) return resolved.example
  if ('default' in resolved) return resolved.default
  const enumValues = asArray(resolved.enum)
  if (enumValues.length) return enumValues[0]
  const allOf = asObjects(resolved.allOf)
  if (allOf.length) return allOf.reduce<Record<string, unknown>>((result, item) => ({ ...result, ...object(schemaExample(item, registry, depth + 1)) }), {})
  if (asObjects(resolved.oneOf).length) return schemaExample(asObjects(resolved.oneOf)[0], registry, depth + 1)
  if (resolved.type === 'array') return [schemaExample(object(resolved.items), registry, depth + 1)]
  if (resolved.type === 'object' || Object.keys(object(resolved.properties)).length) {
    const required = new Set(asStrings(resolved.required))
    return Object.fromEntries(Object.entries(object(resolved.properties))
      .filter(([name, value]) => required.has(name) || hasExample(object(value)))
      .map(([name, value]) => [name, schemaExample(object(value), registry, depth + 1)]))
  }
  if (resolved.type === 'boolean') return true
  if (resolved.type === 'integer' || resolved.type === 'number') return typeof resolved.minimum === 'number' ? resolved.minimum : 0
  if (resolved.format === 'date-time') return '2026-01-01T00:00:00Z'
  if (resolved.format === 'date') return '2026-01-01'
  return 'string'
}

function schemaType(schema?: Record<string, unknown> | null): string {
  if (!schema) return '-'
  if (string(schema.$ref)) return shortReference(string(schema.$ref))
  if (schema.type === 'array') return `array<${schemaType(object(schema.items))}>`
  if (schema.type) return `${schema.type}${schema.format ? ` (${schema.format})` : ''}`
  if (asObjects(schema.allOf).length) return 'allOf'
  if (asObjects(schema.oneOf).length) return 'oneOf'
  return 'object'
}

function schemaConstraints(schema: Record<string, unknown>) {
  const values = [
    asArray(schema.enum).length ? `enum: ${asArray(schema.enum).join(', ')}` : '',
    schema.minimum != null ? `min: ${schema.minimum}` : '',
    schema.maximum != null ? `max: ${schema.maximum}` : '',
    schema.minItems != null ? `min items: ${schema.minItems}` : '',
    schema.pattern ? `pattern: ${schema.pattern}` : '',
  ]
  return values.filter(Boolean).join(', ')
}

function shortReference(reference?: string | null) {
  return reference?.split('/').at(-1) || 'reference'
}

function object(value: unknown): Record<string, unknown> {
  return value && typeof value === 'object' && !Array.isArray(value) ? value as Record<string, unknown> : {}
}

function asArray(value: unknown): unknown[] {
  return Array.isArray(value) ? value : []
}

function asObjects(value: unknown): Array<Record<string, unknown>> {
  return asArray(value).map(object)
}

function asStrings(value: unknown): string[] {
  return asArray(value).map(String)
}

function string(value: unknown): string | undefined {
  return typeof value === 'string' ? value : undefined
}

function hasExample(schema: Record<string, unknown>) {
  return 'example' in schema || 'default' in schema || asArray(schema.enum).length > 0
}

async function copyToClipboard(value: string, message: ReturnType<typeof App.useApp>['message']) {
  await navigator.clipboard.writeText(value)
  void message.success('Copied')
}
