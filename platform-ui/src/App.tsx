import {
  ApiOutlined,
  AppstoreOutlined,
  ClockCircleOutlined,
  DownOutlined,
  ExperimentOutlined,
  GlobalOutlined,
  HomeOutlined,
  KeyOutlined,
  LockOutlined,
  PlusOutlined,
  ProfileOutlined,
  ReadOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { App as AntApp, Button, ConfigProvider, Input, Layout, Modal, Popover, Tooltip } from 'antd'
import enUS from 'antd/locale/en_US'
import zhCN from 'antd/locale/zh_CN'
import { QueryClient, QueryClientProvider, useQuery } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom'
import { ConfigPage } from './pages/ConfigPage'
import { CasesPage } from './pages/CasesPage'
import { ProjectsPage } from './pages/ProjectsPage'
import { RunsPage } from './pages/RunsPage'
import { SuitesPage } from './pages/SuitesPage'
import { WorkbenchPage } from './pages/WorkbenchPage'
import type { WorkspaceUnsavedGuard } from './pages/WorkbenchPage'
import { platformApi } from './api/platformApi'
import { LanguageSwitcher } from './components/LanguageSwitcher'
import { normalizeLanguage } from './i18n'

const queryClient = new QueryClient()
const { Header, Sider, Content } = Layout

function AppShell() {
  const location = useLocation()
  const navigate = useNavigate()
  const { t } = useTranslation(['common', 'workspace'])
  const { message } = AntApp.useApp()
  const [workspaceMenuOpen, setWorkspaceMenuOpen] = useState(false)
  const [workspaceFilter, setWorkspaceFilter] = useState('')
  const [unsavedGuard, setUnsavedGuard] = useState<WorkspaceUnsavedGuard>()
  const [pendingNavigation, setPendingNavigation] = useState<{ to: string, workspaceName?: string }>()
  const [savingBeforeNavigation, setSavingBeforeNavigation] = useState(false)
  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })
  const navigationItems = useMemo(() => [
    { key: 'scenarios', icon: <ExperimentOutlined />, label: t('workspace:navigation.scenarios') },
    { key: 'suites', icon: <ProfileOutlined />, label: t('workspace:navigation.suites') },
    { key: 'apis', icon: <ApiOutlined />, label: t('workspace:navigation.apis') },
    { key: 'environments', icon: <GlobalOutlined />, label: t('workspace:navigation.environments') },
    { key: 'profiles', icon: <KeyOutlined />, label: t('workspace:navigation.profiles') },
    { key: 'knowledge', icon: <ReadOutlined />, label: t('workspace:navigation.knowledge') },
    { key: 'runs', icon: <ClockCircleOutlined />, label: t('workspace:navigation.executionHistory') },
  ], [t])

  const searchParams = new URLSearchParams(location.search)
  const requestedView = searchParams.get('view')
  const selectedKey = location.pathname.startsWith('/workspace')
    ? navigationItems.some((item) => item.key === requestedView) ? requestedView! : 'scenarios'
    : location.pathname.startsWith('/suites')
        ? 'suites'
        : location.pathname.startsWith('/config')
          ? 'environments'
    : location.pathname.startsWith('/runs')
      ? 'runs'
      : 'scenarios'
  const selectedProjectId = searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id
  const selectedProject = projectsQuery.data?.find((project) => project.id === selectedProjectId)
  const filteredProjects = useMemo(() => {
    const keyword = workspaceFilter.trim().toLowerCase()
    return keyword
      ? (projectsQuery.data ?? []).filter((project) => `${project.name} ${project.code}`.toLowerCase().includes(keyword))
      : projectsQuery.data ?? []
  }, [projectsQuery.data, workspaceFilter])

  const navigateNow = (to: string) => {
    setPendingNavigation(undefined)
    navigate(to)
  }

  const requestNavigation = (to: string, workspaceName?: string) => {
    setWorkspaceMenuOpen(false)
    setWorkspaceFilter('')
    if (unsavedGuard?.dirtyTabs.length) {
      setPendingNavigation({ to, workspaceName })
      return
    }
    navigateNow(to)
  }

  const handleProjectChange = (projectId: string) => {
    if (projectId === selectedProjectId) {
      setWorkspaceMenuOpen(false)
      setWorkspaceFilter('')
      return
    }
    const project = projectsQuery.data?.find((record) => record.id === projectId)
    requestNavigation(
      `/workspace?projectId=${encodeURIComponent(projectId)}&view=${selectedKey}`,
      project?.name,
    )
  }

  return (
    <Layout className="app-layout">
      <Header className="app-workspace-bar">
        <div className="workspace-left">
          <Button
            className="workspace-home-button"
            icon={<HomeOutlined />}
            title={t('actions.home')}
            onClick={() => requestNavigation('/projects')}
          />
          <Popover
            arrow={false}
            open={workspaceMenuOpen}
            placement="bottomLeft"
            rootClassName="workspace-switcher-popover"
            trigger="click"
            onOpenChange={(open) => {
              setWorkspaceMenuOpen(open)
              if (!open) {
                setWorkspaceFilter('')
              }
            }}
            content={(
              <div className="workspace-switcher-panel">
                <div className="workspace-switcher-search-row">
                  <Input
                    allowClear
                    autoFocus
                    prefix={<SearchOutlined />}
                    placeholder={t('workspace:switcher.search')}
                    value={workspaceFilter}
                    onChange={(event) => setWorkspaceFilter(event.target.value)}
                  />
                  <Button
                    icon={<PlusOutlined />}
                    onClick={() => {
                      setWorkspaceMenuOpen(false)
                      requestNavigation('/projects?create=1')
                    }}
                  >
                    {t('workspace:switcher.create')}
                  </Button>
                </div>
                <div className="workspace-switcher-list">
                  {filteredProjects.map((project) => (
                    <button
                      key={project.id}
                      type="button"
                      className={project.id === selectedProjectId ? 'workspace-switcher-item active' : 'workspace-switcher-item'}
                      onClick={() => handleProjectChange(project.id)}
                    >
                      <LockOutlined />
                      <span>{project.name}</span>
                    </button>
                  ))}
                  {!projectsQuery.isLoading && !filteredProjects.length ? (
                    <span className="workspace-switcher-empty">{t('workspace:switcher.empty')}</span>
                  ) : null}
                </div>
                <button
                  type="button"
                  className="workspace-switcher-footer"
                  onClick={() => {
                    setWorkspaceMenuOpen(false)
                    requestNavigation('/projects')
                  }}
                >
                  <AppstoreOutlined />
                  <span>{t('workspace:switcher.viewAll')}</span>
                </button>
              </div>
            )}
          >
            <Button className="workspace-switcher-trigger" type="text">
              <LockOutlined />
              <span>
                {selectedProject?.name
                  ?? (projectsQuery.isLoading ? t('workspace:switcher.loading') : t('workspace:switcher.select'))}
              </span>
              <DownOutlined />
            </Button>
          </Popover>
        </div>
        <Input
          className="workspace-search"
          prefix={<SearchOutlined />}
          placeholder={t('search')}
        />
        <div className="workspace-actions">
          <LanguageSwitcher />
        </div>
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
                  onClick={() => navigate(`/workspace?projectId=${encodeURIComponent(selectedProjectId ?? '')}&view=${item.key}`)}
                />
              </Tooltip>
            ))}
          </div>
        </Sider>
        <Layout className="app-workspace-layout">
          <Content className={location.pathname.startsWith('/workspace') ? 'app-content workbench-host' : 'app-content'}>
            <Routes>
              <Route path="/" element={<Navigate to="/projects" replace />} />
              <Route path="/workspace" element={<WorkbenchPage onUnsavedGuardChange={setUnsavedGuard} />} />
              <Route path="/cases" element={<CasesPage />} />
              <Route path="/suites" element={<SuitesPage />} />
              <Route path="/runs" element={<RunsPage />} />
              <Route path="/config" element={<ConfigPage />} />
            </Routes>
          </Content>
        </Layout>
      </Layout>
      <Modal
        title={pendingNavigation?.workspaceName
          ? t('workspace:unsaved.switchTitle')
          : t('workspace:unsaved.leaveTitle')}
        open={Boolean(pendingNavigation)}
        closable={!savingBeforeNavigation}
        mask={{ closable: !savingBeforeNavigation }}
        footer={[
          <Button key="cancel" disabled={savingBeforeNavigation} onClick={() => setPendingNavigation(undefined)}>
            {t('actions.cancel')}
          </Button>,
          <Button
            key="discard"
            danger
            disabled={savingBeforeNavigation}
            onClick={() => {
              if (pendingNavigation) {
                navigateNow(pendingNavigation.to)
              }
            }}
          >
            {t('workspace:unsaved.discardAndContinue')}
          </Button>,
          <Button
            key="save"
            type="primary"
            loading={savingBeforeNavigation}
            onClick={async () => {
              if (!pendingNavigation || !unsavedGuard) {
                return
              }
              setSavingBeforeNavigation(true)
              try {
                await unsavedGuard.saveAll()
                navigateNow(pendingNavigation.to)
              } catch (error) {
                const detail = error instanceof Error ? error.message : t('workspace:unsaved.saveFailed')
                void message.error(detail)
              } finally {
                setSavingBeforeNavigation(false)
              }
            }}
          >
            {t('actions.saveAndContinue')}
          </Button>,
        ]}
        onCancel={() => {
          if (!savingBeforeNavigation) {
            setPendingNavigation(undefined)
          }
        }}
      >
        <p>
          {pendingNavigation?.workspaceName
            ? t('workspace:unsaved.saveBeforeSwitch', { name: pendingNavigation.workspaceName })
            : t('workspace:unsaved.saveBeforeLeave')}
        </p>
        <div className="workspace-unsaved-list">
          {(unsavedGuard?.dirtyTabs ?? []).map((tab) => (
            <span key={tab.key}>{tab.title}</span>
          ))}
        </div>
      </Modal>
    </Layout>
  )
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/projects" element={<ProjectsPage />} />
      <Route path="*" element={<AppShell />} />
    </Routes>
  )
}

export default function App() {
  const { i18n } = useTranslation()
  const antdLocale = normalizeLanguage(i18n.resolvedLanguage) === 'zh-CN' ? zhCN : enUS

  return (
    <QueryClientProvider client={queryClient}>
      <ConfigProvider
        locale={antdLocale}
        theme={{
          token: {
            borderRadius: 6,
            colorPrimary: '#2563eb',
          },
        }}
      >
        <AntApp>
          <BrowserRouter>
            <AppRoutes />
          </BrowserRouter>
        </AntApp>
      </ConfigProvider>
    </QueryClientProvider>
  )
}
