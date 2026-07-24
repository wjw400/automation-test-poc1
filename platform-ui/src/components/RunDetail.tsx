import { Alert, Descriptions, List, Space, Table, Tabs, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import type { RunRecord, StepRun, ValidationReport } from '../types/platform'
import { JsonPane } from './JsonPane'
import { StatusPill } from './StatusPill'

const { Text } = Typography

type RunDetailProps = {
  run?: RunRecord
  selectedStepIndex: number
  onSelectStep: (stepIndex: number) => void
  summaryColumns?: 1 | 2 | 3
  showSummary?: boolean
}

const validationColumns: ColumnsType<ValidationReport> = [
  {
    title: 'Status',
    dataIndex: 'status',
    width: 96,
    render: (value: string) => <StatusPill status={value} />,
  },
  {
    title: 'Type',
    dataIndex: 'type',
    width: 96,
  },
  {
    title: 'Target',
    dataIndex: 'target',
    ellipsis: true,
  },
  {
    title: 'Expected',
    dataIndex: 'expected',
    render: (value: unknown) => <JsonPane value={value} emptyLabel="-" />,
  },
  {
    title: 'Actual',
    dataIndex: 'actual',
    render: (value: unknown) => <JsonPane value={value} emptyLabel="-" />,
  },
  {
    title: 'Message',
    dataIndex: 'message',
    ellipsis: true,
  },
]

export function RunDetail({
  run,
  selectedStepIndex,
  onSelectStep,
  summaryColumns = 3,
  showSummary = true,
}: RunDetailProps) {
  if (!run) {
    return <div className="panel-empty">Select a run to inspect request and response evidence.</div>
  }

  const selectedStep =
    run.steps.find((step) => step.index === selectedStepIndex) ?? run.steps[0]

  return (
    <div className="workspace-detail">
      {showSummary ? (
        <section className="panel-section">
          <div className="panel-title-row">
            <div>
              <h2 className="panel-title">Execution Detail</h2>
              <Text type="secondary">Trace every step, payload, and validation.</Text>
            </div>
            <StatusPill status={run.status} />
          </div>
          <Descriptions
            size="small"
            column={summaryColumns}
            items={[
              { key: 'runId', label: 'Run ID', children: run.id },
              { key: 'caseId', label: 'Case ID', children: run.caseId },
              { key: 'duration', label: 'Duration', children: `${run.durationMs} ms` },
              { key: 'environment', label: 'Environment', children: run.environmentName ?? run.environmentId ?? '-' },
              { key: 'profile', label: 'Profile', children: run.profileName ?? run.profileId ?? '-' },
              { key: 'startedAt', label: 'Started At', children: run.startedAt },
            ]}
          />
          {run.errorMessage ? (
            <Alert className="section-alert" type="error" message={run.errorMessage} showIcon />
          ) : null}
        </section>
      ) : null}

      <div className="detail-grid">
        <section className="panel-section">
          <h3 className="panel-subtitle">Steps</h3>
          <List
            dataSource={run.steps}
            renderItem={(step) => (
              <List.Item
                className={step.index === selectedStep?.index ? 'step-list-item active' : 'step-list-item'}
                onClick={() => onSelectStep(step.index)}
              >
                <Space direction="vertical" size={2} className="step-list-meta">
                  <Space>
                    <Text strong>{`#${step.index}`}</Text>
                    <StatusPill status={step.status} />
                  </Space>
                  <Text>{step.name}</Text>
                  <Text type="secondary">{step.profile || 'No profile'}</Text>
                </Space>
              </List.Item>
            )}
          />
        </section>

        <section className="panel-section">
          {selectedStep ? <StepEvidence step={selectedStep} /> : <div className="panel-empty">No steps recorded.</div>}
        </section>
      </div>
    </div>
  )
}

function StepEvidence({ step }: { step: StepRun }) {
  return (
    <>
      <div className="panel-title-row compact">
        <div>
          <h3 className="panel-title">{step.name}</h3>
          <Text type="secondary">{step.profile || 'No profile selected'}</Text>
        </div>
        <StatusPill status={step.status} />
      </div>

      <Tabs
        items={[
          {
            key: 'request',
            label: 'Request',
            children: (
              <div className="tab-stack">
                <Descriptions
                  size="small"
                  column={2}
                  items={[
                    { key: 'method', label: 'Method', children: step.request?.method ?? '-' },
                    { key: 'url', label: 'URL', children: step.request?.url ?? '-' },
                  ]}
                />
                <JsonPane value={step.request?.headers ?? {}} emptyLabel="No headers" />
                <JsonPane value={step.request?.body} emptyLabel="No request body" />
              </div>
            ),
          },
          {
            key: 'response',
            label: 'Response',
            children: (
              <div className="tab-stack">
                <Descriptions
                  size="small"
                  column={1}
                  items={[
                    { key: 'statusCode', label: 'Status Code', children: step.response?.statusCode ?? '-' },
                  ]}
                />
                <JsonPane value={step.response?.headers ?? {}} emptyLabel="No response headers" />
                <JsonPane value={step.response?.body} emptyLabel="No response body" />
              </div>
            ),
          },
          {
            key: 'actions',
            label: 'Actions',
            children: (
              <List
                dataSource={step.actions ?? []}
                locale={{ emptyText: 'No actions executed' }}
                renderItem={(action) => (
                  <List.Item>
                    <div className="action-evidence">
                      <div className="panel-title-row compact">
                        <Space>
                          <Text strong>{`${action.stage} #${action.index}`}</Text>
                          <Text>{action.name}</Text>
                          <Text type="secondary">{action.type}</Text>
                        </Space>
                        <Space>
                          <Text type="secondary">{`${action.durationMs} ms`}</Text>
                          <StatusPill status={action.status} />
                        </Space>
                      </div>
                      <div className="designer-grid">
                        <div>
                          <Text type="secondary">Input</Text>
                          <JsonPane value={action.input} emptyLabel="-" />
                        </div>
                        <div>
                          <Text type="secondary">Output</Text>
                          <JsonPane value={action.output} emptyLabel="-" />
                        </div>
                      </div>
                      {action.errorMessage ? <Alert type="error" showIcon message={action.errorMessage} /> : null}
                    </div>
                  </List.Item>
                )}
              />
            ),
          },
          {
            key: 'decrypted',
            label: 'Decrypted',
            children: <JsonPane value={step.decryptedPayloads} emptyLabel="No decrypted payloads" />,
          },
          {
            key: 'validations',
            label: 'Validations',
            children: (
              <Table<ValidationReport>
                rowKey={(record, index) => `${record.target}-${index ?? 0}`}
                columns={validationColumns}
                dataSource={step.validations}
                pagination={false}
                size="small"
              />
            ),
          },
          {
            key: 'extracted',
            label: 'Extracted',
            children: <JsonPane value={step.extractedValues} emptyLabel="No extracted values" />,
          },
          {
            key: 'error',
            label: 'Error',
            children: step.errorMessage ? (
              <Alert type="error" showIcon message={step.errorMessage} />
            ) : (
              <div className="panel-empty">No step error.</div>
            ),
          },
        ]}
      />
    </>
  )
}
