import { ReloadOutlined } from '@ant-design/icons'
import { Alert, Button, Select, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { platformApi } from '../api/platformApi'
import { RunDetail } from '../components/RunDetail'
import { StatusPill } from '../components/StatusPill'
import type { RunRecord } from '../types/platform'

const { Text } = Typography

const columns: ColumnsType<RunRecord> = [
  {
    title: 'Run',
    dataIndex: 'id',
    ellipsis: true,
  },
  {
    title: 'Case',
    dataIndex: 'caseId',
    ellipsis: true,
  },
  {
    title: 'Status',
    dataIndex: 'status',
    width: 110,
    render: (status: string) => <StatusPill status={status} />,
  },
  {
    title: 'Duration',
    dataIndex: 'durationMs',
    width: 120,
    render: (value: number) => `${value} ms`,
  },
]

export function RunsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedStepIndex, setSelectedStepIndex] = useState(1)

  const runsQuery = useQuery({
    queryKey: ['runs'],
    queryFn: platformApi.listRuns,
    refetchInterval: 15000,
  })

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const selectedProjectId = searchParams.get('projectId')
  const selectedRunId = searchParams.get('runId')

  const filteredRuns = useMemo(() => {
    const runs = runsQuery.data ?? []
    return selectedProjectId ? runs.filter((run) => run.projectId === selectedProjectId) : runs
  }, [runsQuery.data, selectedProjectId])

  const selectedRun =
    filteredRuns.find((run) => run.id === selectedRunId) ?? filteredRuns[0]

  return (
    <div className="page-shell">
      <section className="hero-band">
        <div>
          <h1>Runs</h1>
          <p>Read execution evidence the way an engineer debugs: one step, one payload, one assertion at a time.</p>
        </div>
        <Space wrap>
          <Select
            allowClear
            className="project-picker"
            placeholder="Filter by project"
            value={selectedProjectId ?? undefined}
            options={(projectsQuery.data ?? []).map((project) => ({
              label: project.name,
              value: project.id,
            }))}
            onChange={(value) => {
              const next = new URLSearchParams(searchParams)
              if (value) {
                next.set('projectId', value)
              } else {
                next.delete('projectId')
              }
              next.delete('runId')
              setSearchParams(next)
            }}
          />
          <Button icon={<ReloadOutlined />} onClick={() => void runsQuery.refetch()}>
            Refresh
          </Button>
        </Space>
      </section>

      <div className="workspace-grid">
        <section className="panel-section list-panel">
          <div className="panel-title-row compact">
            <div>
              <h2 className="panel-title">Run History</h2>
              <Text type="secondary">Most recent executions, including failures and protocol traces.</Text>
            </div>
          </div>
          {runsQuery.isError ? (
            <Alert
              className="section-alert"
              type="warning"
              showIcon
              message="Unable to load run history from platform-api."
            />
          ) : null}

          <Table<RunRecord>
            rowKey="id"
            loading={runsQuery.isLoading}
            size="small"
            columns={columns}
            dataSource={filteredRuns}
            pagination={{ pageSize: 8 }}
            onRow={(record) => ({
              onClick: () => {
                const next = new URLSearchParams(searchParams)
                next.set('runId', record.id)
                setSearchParams(next)
                setSelectedStepIndex(record.steps[0]?.index ?? 1)
              },
            })}
          />
        </section>

        <RunDetail
          run={selectedRun}
          selectedStepIndex={selectedStepIndex}
          onSelectStep={setSelectedStepIndex}
        />
      </div>
    </div>
  )
}
