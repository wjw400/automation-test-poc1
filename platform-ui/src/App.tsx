import {
  ApartmentOutlined,
  ApiOutlined,
  DatabaseOutlined,
  ExperimentOutlined,
  HomeOutlined,
  LockOutlined,
  PlaySquareOutlined,
  ProfileOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { App as AntApp, Button, ConfigProvider, Input, Layout, Select, Tooltip } from 'antd'
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ConfigPage } from './pages/ConfigPage'
import { CasesPage } from './pages/CasesPage'
import { ApiCatalogPage } from './pages/ApiCatalogPage'
import { ProjectsPage } from './pages/ProjectsPage'
import { RunsPage } from './pages/RunsPage'
import { SuitesPage } from './pages/SuitesPage'
import { platformApi } from './api/platformApi'

const queryClient = new QueryClient()
const { Header, Sider, Content } = Layout

const navigationItems = [
  { key: '/projects', icon: <ApartmentOutlined />, label: 'Projects' },
  { key: '/apis', icon: <ApiOutlined />, label: 'APIs' },
  { key: '/cases', icon: <ExperimentOutlined />, label: 'Cases' },
  { key: '/suites', icon: <ProfileOutlined />, label: 'Suites' },
  { key: '/runs', icon: <PlaySquareOutlined />, label: 'Runs' },
  { key: '/config', icon: <DatabaseOutlined />, label: 'Config' },
]

function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const selectedKey = location.pathname.startsWith('/cases')
    ? '/cases'
    : location.pathname.startsWith('/suites')
      ? '/suites'
    : location.pathname.startsWith('/runs')
      ? '/runs'
      : location.pathname.startsWith('/apis')
        ? '/apis'
      : location.pathname.startsWith('/config')
        ? '/config'
        : '/projects'
  const searchParams = new URLSearchParams(location.search)
  const selectedProjectId = searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id

  const handleProjectChange = (projectId: string) => {
    navigate(`${selectedKey}?projectId=${encodeURIComponent(projectId)}`)
  }

  return (
    <Layout className="app-layout">
      <Header className="app-workspace-bar">
        <div className="workspace-left">
          <Button className="workspace-home-button" icon={<HomeOutlined />} onClick={() => navigate('/projects')} />
          <Select
            className="workspace-select"
            loading={projectsQuery.isLoading}
            placeholder="Select workspace"
            value={selectedProjectId}
            prefix={<LockOutlined />}
            variant="borderless"
            options={(projectsQuery.data ?? []).map((project) => ({
              label: project.name,
              value: project.id,
            }))}
            onChange={handleProjectChange}
          />
        </div>
        <Input
          className="workspace-search"
          prefix={<SearchOutlined />}
          placeholder="Search"
        />
      </Header>
      <Layout>
        <Sider width={56} theme="light" className="app-rail">
          <div className="app-rail-items">
            {navigationItems.map((item) => (
              <Tooltip key={item.key} title={item.label} placement="right">
                <Button
                  aria-label={item.label}
                  className={item.key === selectedKey ? 'rail-nav-button active' : 'rail-nav-button'}
                  icon={item.icon}
                  type="text"
                  onClick={() => navigate(item.key)}
                />
              </Tooltip>
            ))}
          </div>
        </Sider>
        <Layout className="app-workspace-layout">
          <Content className="app-content">
            <Routes>
              <Route path="/" element={<Navigate to="/projects" replace />} />
              <Route path="/projects" element={<ProjectsPage />} />
              <Route path="/apis" element={<ApiCatalogPage />} />
              <Route path="/cases" element={<CasesPage />} />
              <Route path="/suites" element={<SuitesPage />} />
              <Route path="/runs" element={<RunsPage />} />
              <Route path="/config" element={<ConfigPage />} />
            </Routes>
          </Content>
        </Layout>
      </Layout>
    </Layout>
  )
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider
        theme={{
          token: {
            borderRadius: 6,
            colorPrimary: '#2563eb',
          },
        }}
      >
        <AntApp>
          <BrowserRouter>
            <AppShell />
          </BrowserRouter>
        </AntApp>
      </ConfigProvider>
    </QueryClientProvider>
  )
}
