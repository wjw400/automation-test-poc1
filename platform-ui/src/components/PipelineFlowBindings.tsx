import { ArrowRightOutlined } from '@ant-design/icons'
import type { PipelineCatalogRecord, PipelineFlowEdgeRecord } from '../types/platform'

type PipelineOptions = {
  inputs: Record<string, unknown>
  outputs: Record<string, unknown>
  parameters: Record<string, unknown>
}

type FlowEndpoint = {
  group: 'inputs' | 'outputs' | 'parameters' | 'steps'
  name: string
  port?: string
}

export function PipelineStepBindings({
  pipeline,
  stepId,
  options,
}: {
  pipeline: PipelineCatalogRecord
  stepId: string
  options?: PipelineOptions
}) {
  const flow = pipeline.flow ?? []
  const incoming = flow.filter((edge) => isStepEndpoint(edge.to, stepId))
  const outgoing = flow.filter((edge) => isStepEndpoint(edge.from, stepId))
  if (!incoming.length && !outgoing.length) return null

  return (
    <div className="pipeline-step-bindings">
      {incoming.length ? (
        <BindingGroup
          title="Consumes"
          edges={incoming}
          pipeline={pipeline}
          options={options}
          direction="incoming"
        />
      ) : null}
      {outgoing.length ? (
        <BindingGroup
          title="Produces"
          edges={outgoing}
          pipeline={pipeline}
          options={options}
          direction="outgoing"
        />
      ) : null}
    </div>
  )
}

function BindingGroup({
  title,
  edges,
  pipeline,
  options,
  direction,
}: {
  title: string
  edges: PipelineFlowEdgeRecord[]
  pipeline: PipelineCatalogRecord
  options?: PipelineOptions
  direction: 'incoming' | 'outgoing'
}) {
  return (
    <div className="pipeline-binding-group">
      <span>{title}</span>
      {edges.map((edge) => {
        const source = parseEndpoint(edge.from)
        const target = parseEndpoint(edge.to)
        return (
          <div className="pipeline-binding-row" key={`${edge.from}:${edge.to}`}>
            <Endpoint
              endpoint={source}
              pipeline={pipeline}
              options={options}
              emphasize={direction === 'incoming'}
            />
            <ArrowRightOutlined />
            <Endpoint
              endpoint={target}
              pipeline={pipeline}
              options={options}
              emphasize={direction === 'outgoing'}
            />
          </div>
        )
      })}
    </div>
  )
}

function Endpoint({
  endpoint,
  pipeline,
  options,
  emphasize,
}: {
  endpoint: FlowEndpoint
  pipeline: PipelineCatalogRecord
  options?: PipelineOptions
  emphasize: boolean
}) {
  const value = effectiveValue(endpoint, pipeline, options)
  const override = isOverride(endpoint, options)
  return (
    <span className={`pipeline-binding-endpoint ${emphasize ? 'emphasize' : ''}`}>
      <small>{endpointLabel(endpoint, pipeline)}</small>
      <code>{endpoint.port ?? endpoint.name}</code>
      {value !== undefined ? <em className={override ? 'override' : ''}>{formatValue(value)}</em> : null}
    </span>
  )
}

function parseEndpoint(reference: string): FlowEndpoint {
  const [group, name, port] = reference.split('.')
  return { group: group as FlowEndpoint['group'], name, port }
}

function isStepEndpoint(reference: string, stepId: string) {
  const endpoint = parseEndpoint(reference)
  return endpoint.group === 'steps' && endpoint.name === stepId
}

function endpointLabel(endpoint: FlowEndpoint, pipeline: PipelineCatalogRecord) {
  if (endpoint.group === 'steps') {
    const index = pipeline.steps.findIndex((step) => step.id === endpoint.name)
    return index >= 0 ? `Step ${index + 1} · ${endpoint.name}` : `Step · ${endpoint.name}`
  }
  return endpoint.group === 'parameters'
    ? 'Option'
    : endpoint.group.slice(0, -1)
}

function effectiveValue(endpoint: FlowEndpoint, pipeline: PipelineCatalogRecord, options?: PipelineOptions) {
  if (endpoint.group === 'inputs') {
    return options?.inputs[endpoint.name] ?? pipeline.inputs[endpoint.name]?.defaultLocation
  }
  if (endpoint.group === 'outputs') {
    return options?.outputs[endpoint.name] ?? pipeline.outputs[endpoint.name]?.defaultLocation
  }
  if (endpoint.group === 'parameters') {
    return options?.parameters[endpoint.name] ?? pipeline.parameters[endpoint.name]?.defaultValue
  }
  return undefined
}

function isOverride(endpoint: FlowEndpoint, options?: PipelineOptions) {
  if (!options || endpoint.group === 'steps') return false
  const group = options[endpoint.group]
  return Object.prototype.hasOwnProperty.call(group, endpoint.name)
}

function formatValue(value: unknown) {
  if (typeof value === 'string') return value
  try {
    return JSON.stringify(value)
  } catch {
    return String(value)
  }
}
