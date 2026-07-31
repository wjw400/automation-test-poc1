import { ArrowRightOutlined, LockOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Empty, Spin, Tag, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { platformApi } from '../api/platformApi'
import type { PipelineCatalogRecord } from '../types/platform'
import { PipelineStepBindings } from './PipelineFlowBindings'

const { Text, Paragraph } = Typography

/** Read-only inspector for processing pipelines bundled with the application. */
export function PipelineCatalog() {
  const catalogQuery = useQuery({
    queryKey: ['pipeline-catalog'],
    queryFn: platformApi.listPipelineCatalog,
  })
  const [selectedReference, setSelectedReference] = useState<string>()
  const selected = useMemo(
    () => catalogQuery.data?.find((pipeline) => pipeline.reference === selectedReference) ?? catalogQuery.data?.[0],
    [catalogQuery.data, selectedReference],
  )
  const pipelines = catalogQuery.data ?? []

  useEffect(() => {
    if (selected && selected.reference !== selectedReference) {
      setSelectedReference(selected.reference)
    }
  }, [selected, selectedReference])

  if (catalogQuery.isPending) {
    return <div className="pipeline-catalog-loading"><Spin tip="Loading built-in pipelines…" /></div>
  }
  if (catalogQuery.isError) {
    return <Alert type="error" showIcon message="The built-in pipeline catalog could not be loaded." />
  }
  if (!pipelines.length || !selected) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="No built-in pipelines are available." />
  }

  return (
    <div className="pipeline-catalog">
      <aside className="pipeline-catalog-list" aria-label="Built-in pipelines">
        <div className="pipeline-catalog-list-heading">
          <h2>Built-in pipelines</h2>
          <Text type="secondary">Read-only application catalog</Text>
        </div>
        {pipelines.map((pipeline) => (
          <button
            key={pipeline.reference}
            type="button"
            className={pipeline.reference === selected.reference ? 'pipeline-catalog-item active' : 'pipeline-catalog-item'}
            onClick={() => setSelectedReference(pipeline.reference)}
          >
            <strong>{pipeline.displayName}</strong>
            <span>{pipeline.reference}</span>
          </button>
        ))}
      </aside>
      <main className="pipeline-catalog-detail">
        <header>
          <div>
            <Text type="secondary">{selected.direction} processing</Text>
            <h1>{selected.displayName}</h1>
          </div>
          <div className="pipeline-catalog-tags">
            <Tag>Built-in · Read-only</Tag>
            <Tag color="blue">{selected.reference}</Tag>
            {selected.requiresRuntimeProfile ? <Tag icon={<LockOutlined />}>Runtime profile required</Tag> : null}
          </div>
        </header>
        {selected.description ? <Paragraph type="secondary">{selected.description}</Paragraph> : null}
        <PipelineDefinitionFlow pipeline={selected} />
      </main>
    </div>
  )
}

function PipelineDefinitionFlow({ pipeline }: { pipeline: PipelineCatalogRecord }) {
  return (
    <section className="pipeline-catalog-flow-section">
      <div className="pipeline-catalog-section-heading">
        <div>
          <h2>Data flow</h2>
          <Text type="secondary">Every input and option is traced to the step port that consumes it</Text>
        </div>
        <Tag>{pipeline.direction}</Tag>
      </div>
      <div className="pipeline-catalog-flow">
        <div className="pipeline-catalog-source-column">
          <span className="pipeline-catalog-column-label">Sources</span>
          <EndpointGroup pipeline={pipeline} title="Inputs" group="inputs" records={pipeline.inputs} />
          <EndpointGroup pipeline={pipeline} title="Options" group="parameters" records={pipeline.parameters} />
        </div>
        <ArrowRightOutlined className="pipeline-catalog-flow-arrow" />
        <div className="pipeline-catalog-step-column">
          <span className="pipeline-catalog-column-label">Processing steps</span>
          {pipeline.steps.map((step, index) => (
            <div className="pipeline-catalog-step-card" key={step.id}>
              <b>{index + 1}</b>
              <div>
                <strong>{step.displayName}</strong>
                <code>{step.uses}</code>
                {step.description ? <Text type="secondary">{step.description}</Text> : null}
                <PipelineStepBindings pipeline={pipeline} stepId={step.id} />
              </div>
            </div>
          ))}
        </div>
        <ArrowRightOutlined className="pipeline-catalog-flow-arrow" />
        <EndpointGroup pipeline={pipeline} title="Outputs" group="outputs" records={pipeline.outputs} />
      </div>
    </section>
  )
}

function EndpointGroup({
  pipeline,
  title,
  group,
  records,
}: {
  pipeline: PipelineCatalogRecord
  title: string
  group: 'inputs' | 'outputs' | 'parameters'
  records: PipelineCatalogRecord['inputs'] | PipelineCatalogRecord['parameters']
}) {
  const direction = group === 'outputs' ? 'to' : 'from'
  return (
    <div className="pipeline-catalog-port-column">
      <span className="pipeline-catalog-source-label">{title}</span>
      {Object.entries(records).map(([name, record]) => {
        const isParameter = group === 'parameters'
        const defaultValue = isParameter && 'defaultValue' in record
          ? record.defaultValue
          : 'defaultLocation' in record
            ? record.defaultLocation
            : undefined
        const usages = endpointUsage(pipeline, `${group}.${name}`, direction)
        return (
        <div className="pipeline-catalog-port-card" key={name}>
          <div><code>{name}</code><Tag>{record.type}</Tag></div>
          <strong>{formatDefault(defaultValue)}</strong>
          {record.description ? <Text type="secondary">{record.description}</Text> : null}
          {usages.map((usage) => <span className="pipeline-catalog-usage" key={usage}>{direction === 'from' ? 'Used by' : 'Produced by'} <b>{usage}</b></span>)}
        </div>
        )
      })}
    </div>
  )
}

function formatDefault(value: unknown) {
  if (value === undefined || value === null || value === '') return 'No default value'
  return typeof value === 'string' ? value : JSON.stringify(value)
}

function endpointUsage(
  pipeline: PipelineCatalogRecord,
  reference: string,
  direction: 'from' | 'to',
) {
  return (pipeline.flow ?? [])
    .filter((edge) => edge[direction] === reference)
    .map((edge) => describeEndpoint(pipeline, edge[direction === 'from' ? 'to' : 'from']))
}

function describeEndpoint(pipeline: PipelineCatalogRecord, reference: string) {
  const [group, name, port] = reference.split('.')
  if (group === 'steps') {
    const index = pipeline.steps.findIndex((step) => step.id === name)
    return `Step ${index + 1} · ${name}.${port}`
  }
  return `${group.slice(0, -1)} · ${name}`
}
