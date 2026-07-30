import { LockOutlined } from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Alert, Empty, Spin, Tag, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { platformApi } from '../api/platformApi'
import type { PipelineCatalogRecord } from '../types/platform'

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
            <Tag color="blue">{selected.reference}</Tag>
            {selected.requiresRuntimeProfile ? <Tag icon={<LockOutlined />}>Runtime profile required</Tag> : null}
          </div>
        </header>
        {selected.description ? <Paragraph type="secondary">{selected.description}</Paragraph> : null}
        <section className="pipeline-catalog-section">
          <h2>Processing steps</h2>
          <ol className="pipeline-catalog-steps">
            {selected.steps.map((step) => (
              <li key={step.id}>
                <strong>{step.displayName}</strong>
                <code>{step.uses}</code>
                {step.description ? <Text type="secondary">{step.description}</Text> : null}
              </li>
            ))}
          </ol>
        </section>
        <div className="pipeline-catalog-grid">
          <PortList title="Inputs" ports={selected.inputs} />
          <PortList title="Outputs" ports={selected.outputs} />
        </div>
        <ParameterList pipeline={selected} />
      </main>
    </div>
  )
}

function PortList({ title, ports }: { title: string, ports: PipelineCatalogRecord['inputs'] }) {
  return (
    <section className="pipeline-catalog-section">
      <h2>{title}</h2>
      <dl className="pipeline-catalog-definition-list">
        {Object.entries(ports).map(([name, port]) => (
          <div key={name}>
            <dt><code>{name}</code><Tag>{port.type}</Tag></dt>
            <dd>{port.defaultLocation || '-'}{port.description ? ` · ${port.description}` : ''}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}

function ParameterList({ pipeline }: { pipeline: PipelineCatalogRecord }) {
  const entries = Object.entries(pipeline.parameters)
  if (!entries.length) return null
  return (
    <section className="pipeline-catalog-section">
      <h2>Parameters</h2>
      <dl className="pipeline-catalog-definition-list">
        {entries.map(([name, parameter]) => (
          <div key={name}>
            <dt><code>{name}</code><Tag>{parameter.type}</Tag></dt>
            <dd>Default: {String(parameter.defaultValue ?? '-')} {parameter.description ? `· ${parameter.description}` : ''}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
