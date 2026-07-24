import { Alert, Descriptions, List, Space, Table, Tabs, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useTranslation } from 'react-i18next'
import { formatDateTime } from '../i18n'
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

export function RunDetail({
  run,
  selectedStepIndex,
  onSelectStep,
  summaryColumns = 3,
  showSummary = true,
}: RunDetailProps) {
  const { t } = useTranslation(['common', 'execution'])

  if (!run) {
    return <div className="panel-empty">{t('execution:selectRun')}</div>
  }

  const selectedStep =
    run.steps.find((step) => step.index === selectedStepIndex) ?? run.steps[0]

  return (
    <div className="workspace-detail">
      {showSummary ? (
        <section className="panel-section">
          <div className="panel-title-row">
            <div>
              <h2 className="panel-title">{t('execution:detail')}</h2>
              <Text type="secondary">{t('execution:detailDescription')}</Text>
            </div>
            <StatusPill status={run.status} />
          </div>
          <Descriptions
            size="small"
            column={summaryColumns}
            items={[
              { key: 'runId', label: t('execution:runId'), children: run.id },
              { key: 'caseId', label: t('execution:caseId'), children: run.caseId },
              {
                key: 'duration',
                label: t('execution:durationLabel'),
                children: t('execution:duration', { duration: run.durationMs }),
              },
              {
                key: 'environment',
                label: t('common:fields.environment'),
                children: run.environmentName ?? run.environmentId ?? '-',
              },
              {
                key: 'profile',
                label: t('common:fields.profile'),
                children: run.profileName ?? run.profileId ?? '-',
              },
              { key: 'startedAt', label: t('execution:startedAt'), children: formatDateTime(run.startedAt) },
            ]}
          />
          {run.errorMessage ? (
            <Alert className="section-alert" type="error" message={run.errorMessage} showIcon />
          ) : null}
        </section>
      ) : null}

      <div className="detail-grid">
        <section className="panel-section">
          <h3 className="panel-subtitle">{t('execution:steps')}</h3>
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
                  <Text type="secondary">{step.profile || t('common:noProfile')}</Text>
                </Space>
              </List.Item>
            )}
          />
        </section>

        <section className="panel-section">
          {selectedStep
            ? <StepEvidence step={selectedStep} />
            : <div className="panel-empty">{t('execution:noSteps')}</div>}
        </section>
      </div>
    </div>
  )
}

function StepEvidence({ step }: { step: StepRun }) {
  const { t } = useTranslation(['common', 'execution'])
  const validationColumns: ColumnsType<ValidationReport> = [
    {
      title: t('common:fields.status'),
      dataIndex: 'status',
      width: 96,
      render: (value: string) => <StatusPill status={value} />,
    },
    {
      title: t('execution:type'),
      dataIndex: 'type',
      width: 96,
    },
    {
      title: t('execution:target'),
      dataIndex: 'target',
      ellipsis: true,
    },
    {
      title: t('execution:expected'),
      dataIndex: 'expected',
      render: (value: unknown) => <JsonPane value={value} emptyLabel="-" />,
    },
    {
      title: t('execution:actual'),
      dataIndex: 'actual',
      render: (value: unknown) => <JsonPane value={value} emptyLabel="-" />,
    },
    {
      title: t('execution:message'),
      dataIndex: 'message',
      ellipsis: true,
    },
  ]

  return (
    <>
      <div className="panel-title-row compact">
        <div>
          <h3 className="panel-title">{step.name}</h3>
          <Text type="secondary">{step.profile || t('common:noProfile')}</Text>
        </div>
        <StatusPill status={step.status} />
      </div>

      <Tabs
        items={[
          {
            key: 'request',
            label: t('execution:request'),
            children: (
              <div className="tab-stack">
                <Descriptions
                  size="small"
                  column={2}
                  items={[
                    { key: 'method', label: t('execution:method'), children: step.request?.method ?? '-' },
                    { key: 'url', label: t('execution:url'), children: step.request?.url ?? '-' },
                  ]}
                />
                <JsonPane value={step.request?.headers ?? {}} emptyLabel={t('execution:noHeaders')} />
                <JsonPane value={step.request?.body} emptyLabel={t('execution:noRequestBody')} />
              </div>
            ),
          },
          {
            key: 'response',
            label: t('execution:response'),
            children: (
              <div className="tab-stack">
                <Descriptions
                  size="small"
                  column={1}
                  items={[
                    { key: 'statusCode', label: t('execution:statusCode'), children: step.response?.statusCode ?? '-' },
                  ]}
                />
                <JsonPane value={step.response?.headers ?? {}} emptyLabel={t('execution:noResponseHeaders')} />
                <JsonPane value={step.response?.body} emptyLabel={t('execution:noResponseBody')} />
              </div>
            ),
          },
          {
            key: 'actions',
            label: t('execution:actions'),
            children: (
              <List
                dataSource={step.actions ?? []}
                locale={{ emptyText: t('execution:noActions') }}
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
                          <Text type="secondary">{t('execution:input')}</Text>
                          <JsonPane value={action.input} emptyLabel="-" />
                        </div>
                        <div>
                          <Text type="secondary">{t('execution:output')}</Text>
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
            label: t('execution:decrypted'),
            children: <JsonPane value={step.decryptedPayloads} emptyLabel={t('execution:noDecrypted')} />,
          },
          {
            key: 'validations',
            label: t('execution:validations'),
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
            label: t('execution:extracted'),
            children: <JsonPane value={step.extractedValues} emptyLabel={t('execution:noExtracted')} />,
          },
          {
            key: 'error',
            label: t('execution:error'),
            children: step.errorMessage ? (
              <Alert type="error" showIcon message={step.errorMessage} />
            ) : (
              <div className="panel-empty">{t('execution:noStepError')}</div>
            ),
          },
        ]}
      />
    </>
  )
}
