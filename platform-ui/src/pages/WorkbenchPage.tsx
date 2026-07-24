import {
  ApiOutlined,
  CloseOutlined,
  ExperimentOutlined,
  FileTextOutlined,
  FolderOpenOutlined,
  GlobalOutlined,
  KeyOutlined,
  PlusOutlined,
  ProfileOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Dropdown, Input, Modal, Typography } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { platformApi } from '../api/platformApi'
import {
  EnvironmentAssetEditor,
  ProfileAssetEditor,
} from '../components/ConfigAssetEditors'
import { RunDetail } from '../components/RunDetail'
import { StatusPill } from '../components/StatusPill'
import { StoplightApi } from '../components/StoplightApi'
import { SuiteRunDetail } from '../components/SuiteRunDetail'
import type {
  ApiSpecRecord,
  CaseRecord,
  FolderRecord,
  RunRecord,
  SuiteRunRecord,
} from '../types/platform'
import { stringifyDsl } from '../utils/caseDsl'
import { CasesPage } from './CasesPage'
import { SuitesPage } from './SuitesPage'

const { Text } = Typography

type WorkbenchMode = 'scenarios' | 'suites' | 'apis' | 'environments' | 'profiles' | 'runs'
type WorkbenchTabType = 'scenario' | 'suite' | 'apiSpec' | 'run' | 'suiteRun' | 'environment' | 'profile'

type WorkbenchTab = {
  key: string
  type: WorkbenchTabType
  resourceId: string
  title: string
  dirty: boolean
}

export type WorkspaceUnsavedGuard = {
  dirtyTabs: Array<{ key: string, title: string }>
  saveAll: () => Promise<void>
}

type WorkbenchPageProps = {
  onUnsavedGuardChange?: (guard?: WorkspaceUnsavedGuard) => void
}

export function WorkbenchPage({ onUnsavedGuardChange }: WorkbenchPageProps = {}) {
  const { modal, message } = App.useApp()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams] = useSearchParams()
  const [tabs, setTabs] = useState<WorkbenchTab[]>([])
  const [activeTabKey, setActiveTabKey] = useState<string>()
  const [assetFilter, setAssetFilter] = useState('')
  const [runFilter, setRunFilter] = useState('')
  const [createDialog, setCreateDialog] = useState<'scenario' | 'suite'>()
  const [createName, setCreateName] = useState('')
  const saveHandlersRef = useRef(new Map<string, () => Promise<void>>())

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })
  const projectId = searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id
  const requestedMode = searchParams.get('view')
  const mode: WorkbenchMode = isWorkbenchMode(requestedMode) ? requestedMode : 'scenarios'

  const casesQuery = useQuery({
    queryKey: ['cases', projectId],
    queryFn: () => platformApi.listCases(projectId!),
    enabled: Boolean(projectId),
  })
  const suitesQuery = useQuery({
    queryKey: ['suites', projectId],
    queryFn: () => platformApi.listSuites(projectId!),
    enabled: Boolean(projectId),
  })
  const foldersQuery = useQuery({
    queryKey: ['folders', projectId],
    queryFn: () => platformApi.listFolders(projectId!),
    enabled: Boolean(projectId),
  })
  const environmentsQuery = useQuery({
    queryKey: ['environments', projectId],
    queryFn: () => platformApi.listEnvironments(projectId!),
    enabled: Boolean(projectId),
  })
  const profilesQuery = useQuery({
    queryKey: ['profiles', projectId],
    queryFn: () => platformApi.listProfiles(projectId!),
    enabled: Boolean(projectId),
  })
  const apiSpecsQuery = useQuery({
    queryKey: ['api-specs', projectId],
    queryFn: () => platformApi.listApiSpecs(projectId!),
    enabled: Boolean(projectId),
  })
  const runsQuery = useQuery({
    queryKey: ['runs'],
    queryFn: platformApi.listRuns,
    refetchInterval: 15000,
  })
  const suiteRunsQuery = useQuery({
    queryKey: ['suite-runs'],
    queryFn: platformApi.listSuiteRuns,
    refetchInterval: 15000,
  })

  useEffect(() => {
    saveHandlersRef.current.clear()
    setTabs([])
    setActiveTabKey(undefined)
  }, [projectId])

  useEffect(() => {
    setAssetFilter('')
  }, [mode])

  const openTab = useCallback((type: WorkbenchTabType, resourceId: string, title: string) => {
    const key = `${type}:${resourceId}`
    setTabs((current) => current.some((tab) => tab.key === key)
      ? current
      : [...current, { key, type, resourceId, title, dirty: false }])
    setActiveTabKey(key)
    const targetMode = modeForTabType(type)
    if (projectId && targetMode && targetMode !== mode) {
      navigate(`/workspace?projectId=${encodeURIComponent(projectId)}&view=${targetMode}`)
    }
  }, [mode, navigate, projectId])

  const activateTab = (tab: WorkbenchTab) => {
    setActiveTabKey(tab.key)
    const targetMode = modeForTabType(tab.type)
    if (projectId && targetMode && targetMode !== mode) {
      navigate(`/workspace?projectId=${encodeURIComponent(projectId)}&view=${targetMode}`)
    }
  }

  const updateTab = useCallback((key: string, patch: Partial<WorkbenchTab>) => {
    setTabs((current) => {
      let changed = false
      const next = current.map((tab) => {
        if (tab.key !== key) {
          return tab
        }
        const hasChange = Object.entries(patch).some(
          ([field, value]) => tab[field as keyof WorkbenchTab] !== value,
        )
        if (!hasChange) {
          return tab
        }
        changed = true
        return { ...tab, ...patch }
      })
      return changed ? next : current
    })
  }, [])

  const registerSaveHandler = useCallback((key: string, save?: () => Promise<void>) => {
    if (save) {
      saveHandlersRef.current.set(key, save)
    } else {
      saveHandlersRef.current.delete(key)
    }
  }, [])

  useEffect(() => {
    const dirtyTabs = tabs.filter((tab) => tab.dirty)
    if (!dirtyTabs.length) {
      onUnsavedGuardChange?.(undefined)
      return
    }
    onUnsavedGuardChange?.({
      dirtyTabs: dirtyTabs.map(({ key, title }) => ({ key, title })),
      saveAll: async () => {
        for (const tab of dirtyTabs) {
          const save = saveHandlersRef.current.get(tab.key)
          if (!save) {
            throw new Error(`"${tab.title}" cannot be saved from the workspace switcher.`)
          }
          setActiveTabKey(tab.key)
          await save()
        }
      },
    })
  }, [onUnsavedGuardChange, tabs])

  useEffect(() => () => onUnsavedGuardChange?.(undefined), [onUnsavedGuardChange])

  const closeTabNow = useCallback((key: string) => {
    saveHandlersRef.current.delete(key)
    setTabs((current) => {
      const index = current.findIndex((tab) => tab.key === key)
      const next = current.filter((tab) => tab.key !== key)
      setActiveTabKey((active) => {
        if (active !== key) {
          return active
        }
        return next[Math.min(index, next.length - 1)]?.key
      })
      return next
    })
  }, [])

  const requestCloseTab = (tab: WorkbenchTab) => {
    if (!tab.dirty) {
      closeTabNow(tab.key)
      return
    }
    modal.confirm({
      title: 'Close unsaved tab?',
      content: `Changes in "${tab.title}" will be discarded.`,
      okText: 'Discard and close',
      okButtonProps: { danger: true },
      cancelText: 'Keep open',
      onOk: () => closeTabNow(tab.key),
    })
  }

  const replaceCreatedTab = useCallback((
    oldKey: string,
    type: 'environment' | 'profile',
    resourceId: string,
    title: string,
  ) => {
    const nextKey = `${type}:${resourceId}`
    setTabs((current) => current.map((tab) => tab.key === oldKey
      ? { ...tab, key: nextKey, resourceId, title, dirty: false }
      : tab))
    setActiveTabKey(nextKey)
  }, [])

  const createMutation = useMutation({
    mutationFn: async () => {
      if (!projectId || !createDialog || !createName.trim()) {
        throw new Error('Name is required.')
      }
      if (createDialog === 'suite') {
        return {
          type: 'suite' as const,
          record: await platformApi.createSuite(projectId, {
            name: createName.trim(),
            description: '',
            status: 'ACTIVE',
            cases: [],
          }),
        }
      }
      const name = createName.trim()
      return {
        type: 'scenario' as const,
        record: await platformApi.createCase(projectId, {
          name,
          code: scenarioCode(name),
          description: '',
          tags: [],
          dslVersion: 'v1',
          content: stringifyDsl({
            dslVersion: 'v1',
            name,
            description: '',
            tags: [],
            runtime: {},
            variables: {},
            steps: [],
          }),
          status: 'ACTIVE',
        }),
      }
    },
    onSuccess: async ({ type, record }) => {
      await queryClient.invalidateQueries({ queryKey: [type === 'suite' ? 'suites' : 'cases', projectId] })
      setCreateDialog(undefined)
      setCreateName('')
      openTab(type, record.id, record.name)
      void message.success(type === 'suite' ? 'Suite created' : 'Scenario created')
    },
  })

  const filteredCases = useMemo(
    () => filterByName(casesQuery.data ?? [], assetFilter),
    [assetFilter, casesQuery.data],
  )
  const filteredSuites = useMemo(
    () => filterByName(suitesQuery.data ?? [], assetFilter),
    [assetFilter, suitesQuery.data],
  )
  const filteredEnvironments = useMemo(
    () => filterByName(environmentsQuery.data ?? [], assetFilter),
    [assetFilter, environmentsQuery.data],
  )
  const filteredProfiles = useMemo(
    () => filterByName(profilesQuery.data ?? [], assetFilter),
    [assetFilter, profilesQuery.data],
  )
  const filteredApiSpecs = useMemo(
    () => filterByName(apiSpecsQuery.data ?? [], assetFilter, (record) => `${record.title ?? ''} ${record.name}`),
    [apiSpecsQuery.data, assetFilter],
  )
  const projectRuns = useMemo(() => {
    const records = (runsQuery.data ?? []).filter((run) => run.projectId === projectId && !run.suiteRunId)
    const caseNamesById = new Map((casesQuery.data ?? []).map((record) => [record.id, record.name]))
    return filterByName(records, runFilter, (run) => (
      `${run.id} ${run.status} ${run.caseId} ${caseNamesById.get(run.caseId) ?? ''}`
    ))
      .sort((left, right) => new Date(right.startedAt).getTime() - new Date(left.startedAt).getTime())
  }, [casesQuery.data, projectId, runFilter, runsQuery.data])
  const projectSuiteRuns = useMemo(() => {
    const records = (suiteRunsQuery.data ?? []).filter((run) => run.projectId === projectId)
    return filterByName(records, runFilter, (run) => `${run.id} ${run.status} ${run.suiteName}`)
      .sort((left, right) => new Date(right.startedAt).getTime() - new Date(left.startedAt).getTime())
  }, [projectId, runFilter, suiteRunsQuery.data])

  const activeTab = tabs.find((tab) => tab.key === activeTabKey)

  return (
    <div className="workbench-page">
      <aside className="workbench-context-sidebar">
        {mode === 'scenarios' ? (
          <ScenariosSidebar
            cases={filteredCases}
            folders={foldersQuery.data ?? []}
            filter={assetFilter}
            onFilterChange={setAssetFilter}
            onOpenScenario={(record) => openTab('scenario', record.id, record.name)}
            onCreate={() => {
              setCreateName('')
              setCreateDialog('scenario')
            }}
          />
        ) : null}
        {mode === 'suites' ? (
          <ResourceListSidebar
            title="Suites"
            searchPlaceholder="Search suites"
            filter={assetFilter}
            onFilterChange={setAssetFilter}
            onCreate={() => {
              setCreateName('')
              setCreateDialog('suite')
            }}
          >
            {filteredSuites.map((record) => (
              <AssetRow key={record.id} icon={<ProfileOutlined />} title={record.name} onClick={() => openTab('suite', record.id, record.name)} />
            ))}
          </ResourceListSidebar>
        ) : null}
        {mode === 'apis' ? (
          <ResourceListSidebar
            title="API Specs"
            searchPlaceholder="Search API specs"
            filter={assetFilter}
            onFilterChange={setAssetFilter}
            onCreate={() => navigate(`/apis?projectId=${encodeURIComponent(projectId ?? '')}`)}
            createTitle="Manage API specs"
          >
            {filteredApiSpecs.map((record) => (
              <AssetRow
                key={record.id}
                icon={<ApiOutlined />}
                title={record.title || record.name}
                onClick={() => openTab('apiSpec', record.id, record.title || record.name)}
              />
            ))}
          </ResourceListSidebar>
        ) : null}
        {mode === 'environments' ? (
          <ResourceListSidebar
            title="Environments"
            searchPlaceholder="Search environments"
            filter={assetFilter}
            onFilterChange={setAssetFilter}
            onCreate={() => openTab('environment', `new-${Date.now()}`, 'New Environment')}
            createTitle="New environment"
          >
            {filteredEnvironments.map((record) => (
              <AssetRow key={record.id} icon={<GlobalOutlined />} title={record.name} onClick={() => openTab('environment', record.id, record.name)} />
            ))}
          </ResourceListSidebar>
        ) : null}
        {mode === 'profiles' ? (
          <ResourceListSidebar
            title="Profiles"
            searchPlaceholder="Search profiles"
            filter={assetFilter}
            onFilterChange={setAssetFilter}
            onCreate={() => openTab('profile', `new-${Date.now()}`, 'New Profile')}
            createTitle="New profile"
          >
            {filteredProfiles.map((record) => (
              <AssetRow key={record.id} icon={<KeyOutlined />} title={record.name} onClick={() => openTab('profile', record.id, record.name)} />
            ))}
          </ResourceListSidebar>
        ) : null}
        {mode === 'runs' ? (
          <RunsSidebar
            records={projectRuns}
            suiteRecords={projectSuiteRuns}
            cases={casesQuery.data ?? []}
            filter={runFilter}
            onFilterChange={setRunFilter}
            onOpen={(record) => openTab('run', record.id, `Run ${shortId(record.id)}`)}
            onOpenSuite={(record) => openTab('suiteRun', record.id, `${record.suiteName} Execution`)}
          />
        ) : null}
      </aside>

      <main className="workbench-main">
        <div className="workbench-tab-strip" role="tablist" aria-label="Open work items">
          {tabs.map((tab) => (
            <div
              key={tab.key}
              className={tab.key === activeTabKey ? 'workbench-tab active' : 'workbench-tab'}
            >
              <button
                type="button"
                role="tab"
                aria-selected={tab.key === activeTabKey}
                className="workbench-tab-select"
                onClick={() => activateTab(tab)}
              >
                {tabIcon(tab.type)}
                <span>{tab.title}</span>
                {tab.dirty ? <span className="workbench-tab-dirty" title="Unsaved changes" /> : null}
              </button>
              <Button
                aria-label={`Close ${tab.title}`}
                className="workbench-tab-close"
                icon={<CloseOutlined />}
                size="small"
                type="text"
                onClick={() => requestCloseTab(tab)}
              />
            </div>
          ))}
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                { key: 'scenario', label: 'New Scenario', icon: <ExperimentOutlined /> },
                { key: 'suite', label: 'New Suite', icon: <ProfileOutlined /> },
                { key: 'environment', label: 'New Environment', icon: <GlobalOutlined /> },
                { key: 'profile', label: 'New Profile', icon: <KeyOutlined /> },
              ],
              onClick: ({ key }) => {
                if (key === 'scenario' || key === 'suite') {
                  setCreateName('')
                  setCreateDialog(key)
                } else if (key === 'environment') {
                  openTab('environment', `new-${Date.now()}`, 'New Environment')
                } else {
                  openTab('profile', `new-${Date.now()}`, 'New Profile')
                }
              },
            }}
          >
            <Button className="workbench-new-tab" type="text" icon={<PlusOutlined />} title="New tab" />
          </Dropdown>
        </div>

        <div className="workbench-content">
          {tabs.map((tab) => (
            <div
              key={tab.key}
              className={tab.key === activeTabKey ? 'workbench-tab-pane active' : 'workbench-tab-pane'}
            >
              {projectId ? renderTabContent({
                tab,
                projectId,
                cases: casesQuery.data ?? [],
                suites: suitesQuery.data ?? [],
                runs: runsQuery.data ?? [],
                suiteRuns: suiteRunsQuery.data ?? [],
                apiSpecs: apiSpecsQuery.data ?? [],
                environments: environmentsQuery.data ?? [],
                profiles: profilesQuery.data ?? [],
                updateTab,
                closeTab: closeTabNow,
                replaceCreatedTab,
                registerSaveHandler,
                openTab,
              }) : null}
            </div>
          ))}
          {!activeTab ? (
            <div className="workbench-empty">
              <FileTextOutlined />
              <Text strong>Open a work item</Text>
              <Text type="secondary">Choose an item from the sidebar.</Text>
            </div>
          ) : null}
        </div>
      </main>

      <Modal
        title={createDialog === 'suite' ? 'New Suite' : 'New Scenario'}
        open={Boolean(createDialog)}
        okText="Create"
        confirmLoading={createMutation.isPending}
        okButtonProps={{ disabled: !createName.trim() }}
        onCancel={() => setCreateDialog(undefined)}
        onOk={() => void createMutation.mutateAsync()}
      >
        <Input
          autoFocus
          aria-label="Name"
          placeholder={createDialog === 'suite' ? 'Suite name' : 'Scenario name'}
          value={createName}
          onChange={(event) => setCreateName(event.target.value)}
          onPressEnter={() => {
            if (createName.trim()) {
              void createMutation.mutateAsync()
            }
          }}
        />
      </Modal>
    </div>
  )
}

type RenderTabOptions = {
  tab: WorkbenchTab
  projectId: string
  cases: Awaited<ReturnType<typeof platformApi.listCases>>
  suites: Awaited<ReturnType<typeof platformApi.listSuites>>
  runs: RunRecord[]
  suiteRuns: SuiteRunRecord[]
  apiSpecs: ApiSpecRecord[]
  environments: Awaited<ReturnType<typeof platformApi.listEnvironments>>
  profiles: Awaited<ReturnType<typeof platformApi.listProfiles>>
  updateTab: (key: string, patch: Partial<WorkbenchTab>) => void
  closeTab: (key: string) => void
  replaceCreatedTab: (
    oldKey: string,
    type: 'environment' | 'profile',
    resourceId: string,
    title: string,
  ) => void
  registerSaveHandler: (key: string, save?: () => Promise<void>) => void
  openTab: (type: WorkbenchTabType, resourceId: string, title: string) => void
}

function renderTabContent(options: RenderTabOptions) {
  const { tab, projectId, updateTab, closeTab, replaceCreatedTab, registerSaveHandler, openTab } = options
  const commonCallbacks = {
    onDirtyChange: (dirty: boolean) => updateTab(tab.key, { dirty }),
    onTitleChange: (title: string) => updateTab(tab.key, { title }),
    onSaveReady: (save?: () => Promise<void>) => registerSaveHandler(tab.key, save),
  }
  if (tab.type === 'scenario') {
    return (
      <CasesPage
        embedded
        projectId={projectId}
        caseId={tab.resourceId}
        {...commonCallbacks}
        onDeleted={() => closeTab(tab.key)}
      />
    )
  }
  if (tab.type === 'suite') {
    return (
      <SuitesPage
        embedded
        projectId={projectId}
        suiteId={tab.resourceId}
        {...commonCallbacks}
        onDeleted={() => closeTab(tab.key)}
      />
    )
  }
  if (tab.type === 'run') {
    const run = options.runs.find((record) => record.id === tab.resourceId)
    const scenario = options.cases.find((record) => record.id === run?.caseId)
    const parentSuiteRun = options.suiteRuns.find((record) => record.id === run?.suiteRunId)
    return (
      <RunTabPane
        run={run}
        scenarioName={scenario?.name}
        onOpenScenario={scenario ? () => openTab('scenario', scenario.id, scenario.name) : undefined}
        onOpenSuiteRun={parentSuiteRun
          ? () => openTab('suiteRun', parentSuiteRun.id, `${parentSuiteRun.suiteName} Execution`)
          : undefined}
      />
    )
  }
  if (tab.type === 'suiteRun') {
    const suiteRun = options.suiteRuns.find((record) => record.id === tab.resourceId)
    const suite = options.suites.find((record) => record.id === suiteRun?.suiteId)
    return (
      <SuiteRunDetail
        run={suiteRun}
        onOpenRun={(run) => openTab('run', run.id, run.suiteDisplayName || `Run ${shortId(run.id)}`)}
        onOpenSuite={suite ? () => openTab('suite', suite.id, suite.name) : undefined}
      />
    )
  }
  if (tab.type === 'apiSpec') {
    return (
      <ApiSpecTabPane
        projectId={projectId}
        spec={options.apiSpecs.find((record) => record.id === tab.resourceId)}
      />
    )
  }
  if (tab.type === 'environment') {
    const environment = options.environments.find((record) => record.id === tab.resourceId)
    return (
      <EnvironmentAssetEditor
        projectId={projectId}
        environment={environment}
        {...commonCallbacks}
        onSaved={(saved) => replaceCreatedTab(tab.key, 'environment', saved.id, saved.name)}
      />
    )
  }
  const profile = options.profiles.find((record) => record.id === tab.resourceId)
  return (
    <ProfileAssetEditor
      projectId={projectId}
      profile={profile}
      {...commonCallbacks}
      onSaved={(saved) => replaceCreatedTab(tab.key, 'profile', saved.id, saved.name)}
    />
  )
}

function RunTabPane({
  run,
  scenarioName,
  onOpenScenario,
  onOpenSuiteRun,
}: {
  run?: RunRecord
  scenarioName?: string
  onOpenScenario?: () => void
  onOpenSuiteRun?: () => void
}) {
  const [selectedStepIndex, setSelectedStepIndex] = useState(1)
  useEffect(() => {
    setSelectedStepIndex(run?.steps[0]?.index ?? 1)
  }, [run?.id, run?.steps])
  return (
    <div className="run-tab-pane">
      <header className="run-context-header">
        <div>
          <Text type="secondary">Scenario Execution</Text>
          <h1>{scenarioName ?? run?.suiteDisplayName ?? 'Execution Detail'}</h1>
        </div>
        <div className="run-context-actions">
          {onOpenSuiteRun ? <Button size="small" onClick={onOpenSuiteRun}>Open Suite Execution</Button> : null}
          {onOpenScenario ? <Button size="small" onClick={onOpenScenario}>Open Scenario</Button> : null}
        </div>
      </header>
      <div className="run-tab-detail">
        <RunDetail run={run} selectedStepIndex={selectedStepIndex} onSelectStep={setSelectedStepIndex} />
      </div>
    </div>
  )
}

function ApiSpecTabPane({ projectId, spec }: { projectId: string, spec?: ApiSpecRecord }) {
  if (!spec) {
    return <div className="workbench-empty"><Text type="secondary">API spec not found.</Text></div>
  }
  return (
    <div className="api-spec-tab-pane">
      <header className="api-spec-tab-header">
        <div>
          <h1>{spec.title || spec.name}</h1>
          <Text type="secondary">{spec.name}</Text>
        </div>
        <div className="api-spec-tab-meta">
          {spec.specVersion ? <span>{spec.specVersion}</span> : null}
          <span>{spec.operationCount} operations</span>
        </div>
      </header>
      <div className="api-spec-tab-content">
        <StoplightApi apiDescriptionUrl={platformApi.getApiSpecContentUrl(projectId, spec.id)} />
      </div>
    </div>
  )
}

function ScenariosSidebar({
  cases,
  folders,
  filter,
  onFilterChange,
  onOpenScenario,
  onCreate,
}: {
  cases: CaseRecord[]
  folders: FolderRecord[]
  filter: string
  onFilterChange: (value: string) => void
  onOpenScenario: (record: CaseRecord) => void
  onCreate: () => void
}) {
  const casesByFolder = new Map<string | undefined, CaseRecord[]>()
  cases.forEach((record) => {
    const key = record.folderId ?? undefined
    casesByFolder.set(key, [...(casesByFolder.get(key) ?? []), record])
  })
  const childFolders = new Map<string | undefined, FolderRecord[]>()
  folders.forEach((folder) => {
    const key = folder.parentFolderId ?? undefined
    childFolders.set(key, [...(childFolders.get(key) ?? []), folder])
  })
  const renderFolder = (folder: FolderRecord, depth: number): React.ReactNode => (
    <details key={folder.id} className="workbench-tree-folder" open>
      <summary style={{ paddingLeft: 8 + depth * 12 }}>
        <FolderOpenOutlined />
        <span>{folder.name}</span>
      </summary>
      {(casesByFolder.get(folder.id) ?? []).map((record) => (
        <AssetRow key={record.id} depth={depth + 1} icon={<ExperimentOutlined />} title={record.name} onClick={() => onOpenScenario(record)} />
      ))}
      {(childFolders.get(folder.id) ?? []).map((child) => renderFolder(child, depth + 1))}
    </details>
  )
  return (
    <>
      <div className="workbench-sidebar-head">
        <Text strong>Scenarios</Text>
        <Button type="text" size="small" icon={<PlusOutlined />} title="New scenario" onClick={onCreate} />
      </div>
      <Input
        allowClear
        className="workbench-sidebar-search"
        prefix={<SearchOutlined />}
        placeholder="Search scenarios"
        value={filter}
        onChange={(event) => onFilterChange(event.target.value)}
      />
      <div className="workbench-sidebar-scroll">
        {(casesByFolder.get(undefined) ?? []).map((record) => (
          <AssetRow key={record.id} icon={<ExperimentOutlined />} title={record.name} onClick={() => onOpenScenario(record)} />
        ))}
        {(childFolders.get(undefined) ?? []).map((folder) => renderFolder(folder, 0))}
      </div>
    </>
  )
}

function ResourceListSidebar({
  title,
  searchPlaceholder,
  filter,
  onFilterChange,
  onCreate,
  createTitle = `New ${title.toLowerCase()}`,
  children,
}: {
  title: string
  searchPlaceholder: string
  filter: string
  onFilterChange: (value: string) => void
  onCreate: () => void
  createTitle?: string
  children: React.ReactNode
}) {
  return (
    <>
      <div className="workbench-sidebar-head">
        <Text strong>{title}</Text>
        <Button type="text" size="small" icon={<PlusOutlined />} title={createTitle} onClick={onCreate} />
      </div>
      <Input
        allowClear
        className="workbench-sidebar-search"
        prefix={<SearchOutlined />}
        placeholder={searchPlaceholder}
        value={filter}
        onChange={(event) => onFilterChange(event.target.value)}
      />
      <div className="workbench-sidebar-scroll">{children}</div>
    </>
  )
}

function RunsSidebar({
  records,
  suiteRecords,
  cases,
  filter,
  onFilterChange,
  onOpen,
  onOpenSuite,
}: {
  records: RunRecord[]
  suiteRecords: SuiteRunRecord[]
  cases: CaseRecord[]
  filter: string
  onFilterChange: (value: string) => void
  onOpen: (record: RunRecord) => void
  onOpenSuite: (record: SuiteRunRecord) => void
}) {
  const caseNamesById = new Map(cases.map((record) => [record.id, record.name]))
  return (
    <>
      <div className="workbench-sidebar-head"><Text strong>Execution History</Text></div>
      <Input
        allowClear
        className="workbench-sidebar-search"
        prefix={<SearchOutlined />}
        placeholder="Search executions"
        value={filter}
        onChange={(event) => onFilterChange(event.target.value)}
      />
      <div className="workbench-sidebar-scroll">
        <div className="run-history-group-title">Suite Executions</div>
        {suiteRecords.map((record) => (
          <button key={record.id} type="button" className="run-history-row" onClick={() => onOpenSuite(record)}>
            <span className="run-history-row-title">
              <span>{record.suiteName}</span>
              <StatusPill status={record.status} />
            </span>
            <span>{new Date(record.startedAt).toLocaleString()}</span>
            <span>{record.runs.length} scenarios · {record.durationMs} ms</span>
          </button>
        ))}
        {!suiteRecords.length ? <Text type="secondary" className="workbench-sidebar-empty">No suite executions.</Text> : null}
        <div className="run-history-group-title">Scenario Executions</div>
        {records.map((record) => (
          <button key={record.id} type="button" className="run-history-row" onClick={() => onOpen(record)}>
            <span className="run-history-row-title">
              <span>{caseNamesById.get(record.caseId) ?? shortId(record.id)}</span>
              <StatusPill status={record.status} />
            </span>
            <span>{new Date(record.startedAt).toLocaleString()}</span>
            <span>{record.durationMs} ms</span>
          </button>
        ))}
        {!records.length ? <Text type="secondary" className="workbench-sidebar-empty">No scenario executions.</Text> : null}
      </div>
    </>
  )
}

function AssetRow({
  icon,
  title,
  depth = 0,
  onClick,
}: {
  icon: React.ReactNode
  title: string
  depth?: number
  onClick: () => void
}) {
  return (
    <button
      type="button"
      className="workbench-asset-row"
      style={{ paddingLeft: 10 + depth * 12 }}
      onClick={onClick}
    >
      {icon}
      <span>{title}</span>
    </button>
  )
}

function tabIcon(type: WorkbenchTabType) {
  if (type === 'scenario') return <ExperimentOutlined />
  if (type === 'suite') return <ProfileOutlined />
  if (type === 'apiSpec') return <ApiOutlined />
  if (type === 'suiteRun') return <ProfileOutlined />
  if (type === 'run') return <FileTextOutlined />
  if (type === 'environment') return <GlobalOutlined />
  return <KeyOutlined />
}

function isWorkbenchMode(value: string | null): value is WorkbenchMode {
  return value === 'scenarios'
    || value === 'suites'
    || value === 'apis'
    || value === 'environments'
    || value === 'profiles'
    || value === 'runs'
}

function modeForTabType(type: WorkbenchTabType): WorkbenchMode | undefined {
  if (type === 'scenario') return 'scenarios'
  if (type === 'suite') return 'suites'
  if (type === 'apiSpec') return 'apis'
  if (type === 'environment') return 'environments'
  if (type === 'profile') return 'profiles'
  return undefined
}

function filterByName<T>(
  records: T[],
  filter: string,
  toText: (record: T) => string = (record) => String((record as { name?: string }).name ?? ''),
) {
  const keyword = filter.trim().toLowerCase()
  return keyword
    ? records.filter((record) => toText(record).toLowerCase().includes(keyword))
    : records
}

function shortId(value: string) {
  return value.slice(0, 8)
}

function scenarioCode(name: string) {
  const normalized = name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
  return `${normalized || 'scenario'}-${Date.now()}`
}
