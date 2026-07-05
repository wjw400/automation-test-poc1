import { ApiOutlined, DatabaseOutlined, ExperimentOutlined, PlaySquareOutlined, ProfileOutlined } from '@ant-design/icons'
import { Alert, App, Button, Space, Table, Typography } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useQuery } from '@tanstack/react-query'
import { useNavigate } from 'react-router-dom'
import { platformApi } from '../api/platformApi'
import { StatusPill } from '../components/StatusPill'
import type { Project } from '../types/platform'

const { Text } = Typography

const columns: ColumnsType<Project> = [
  {
    title: 'Project',
    dataIndex: 'name',
    render: (_value: string, record) => (
      <Space direction="vertical" size={2}>
        <Text strong>{record.name}</Text>
        <Text type="secondary">{record.code}</Text>
      </Space>
    ),
  },
  {
    title: 'Status',
    dataIndex: 'status',
    width: 120,
    render: (status: string) => <StatusPill status={status} />,
  },
  {
    title: 'Updated',
    dataIndex: 'updatedAt',
    width: 220,
  },
]

export function ProjectsPage() {
  const navigate = useNavigate()
  const { message } = App.useApp()
  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  return (
    <div className="page-shell">
      <section className="hero-band">
        <div>
          <h1>Projects</h1>
          <p>Select a workspace for API assets, scenarios, configuration, and runs.</p>
        </div>
      </section>

      <section className="panel-section">
        <div className="panel-title-row">
          <div>
            <h2 className="panel-title">Workspace Projects</h2>
            <Text type="secondary">Open the project context before editing scenarios or runtime settings.</Text>
          </div>
        </div>
        {projectsQuery.isError ? (
          <Alert
            className="section-alert"
            type="warning"
            showIcon
            message="Unable to load projects from platform-api. Start the backend on localhost:8090 and refresh."
          />
        ) : null}

        <Table<Project>
          rowKey="id"
          loading={projectsQuery.isLoading}
          columns={[
            ...columns,
            {
              title: 'Actions',
              key: 'actions',
              width: 440,
              render: (_value, record) => (
                <Space wrap size={[8, 8]}>
                  <Button icon={<ApiOutlined />} onClick={() => navigate(`/apis?projectId=${record.id}`)}>
                    APIs
                  </Button>
                  <Button icon={<ExperimentOutlined />} onClick={() => navigate(`/cases?projectId=${record.id}`)}>
                    Cases
                  </Button>
                  <Button icon={<ProfileOutlined />} onClick={() => navigate(`/suites?projectId=${record.id}`)}>
                    Suites
                  </Button>
                  <Button icon={<PlaySquareOutlined />} onClick={() => navigate(`/runs?projectId=${record.id}`)}>
                    Runs
                  </Button>
                  <Button icon={<DatabaseOutlined />} onClick={() => navigate(`/config?projectId=${record.id}`)}>
                    Config
                  </Button>
                </Space>
              ),
            },
          ]}
          dataSource={projectsQuery.data ?? []}
          pagination={false}
          locale={{
            emptyText: 'No projects available yet.',
          }}
          onRow={(record) => ({
            onDoubleClick: () => {
              void message.success(`Opening ${record.name}`)
              navigate(`/cases?projectId=${record.id}`)
            },
          })}
        />
      </section>
    </div>
  )
}
