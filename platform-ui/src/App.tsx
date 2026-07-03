import { ApartmentOutlined, ApiOutlined, DatabaseOutlined, ExperimentOutlined, PlaySquareOutlined } from '@ant-design/icons'
import { App as AntApp, ConfigProvider, Layout, Menu, Typography } from 'antd'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ConfigPage } from './pages/ConfigPage'
import { CasesPage } from './pages/CasesPage'
import { ApiCatalogPage } from './pages/ApiCatalogPage'
import { ProjectsPage } from './pages/ProjectsPage'
import { RunsPage } from './pages/RunsPage'

const queryClient = new QueryClient()
const { Header, Sider, Content } = Layout

function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()

  const selectedKey = location.pathname.startsWith('/cases')
    ? '/cases'
    : location.pathname.startsWith('/runs')
      ? '/runs'
      : location.pathname.startsWith('/apis')
        ? '/apis'
      : location.pathname.startsWith('/config')
        ? '/config'
        : '/projects'

  return (
    <Layout className="app-layout">
      <Sider width={248} theme="light" className="app-sider">
        <div className="brand-block">
          <Typography.Title level={4}>Automation Platform</Typography.Title>
          <Typography.Text type="secondary">
            API case workbench
          </Typography.Text>
        </div>
        <Menu
          mode="inline"
          selectedKeys={[selectedKey]}
          items={[
            { key: '/projects', icon: <ApartmentOutlined />, label: 'Projects' },
            { key: '/apis', icon: <ApiOutlined />, label: 'APIs' },
            { key: '/cases', icon: <ExperimentOutlined />, label: 'Cases' },
            { key: '/runs', icon: <PlaySquareOutlined />, label: 'Runs' },
            { key: '/config', icon: <DatabaseOutlined />, label: 'Config' },
          ]}
          onClick={({ key }) => navigate(key)}
        />
      </Sider>
      <Layout>
        <Header className="app-header">
          <div>
            <Typography.Text strong>Platform POC</Typography.Text>
            <Typography.Text type="secondary">
              {' '}
              | YAML cases, secure profiles, step evidence
            </Typography.Text>
          </div>
        </Header>
        <Content className="app-content">
          <Routes>
            <Route path="/" element={<Navigate to="/projects" replace />} />
            <Route path="/projects" element={<ProjectsPage />} />
            <Route path="/apis" element={<ApiCatalogPage />} />
            <Route path="/cases" element={<CasesPage />} />
            <Route path="/runs" element={<RunsPage />} />
            <Route path="/config" element={<ConfigPage />} />
          </Routes>
        </Content>
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
