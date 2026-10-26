import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CodeOutlined,
  DeleteOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  RobotOutlined,
  SaveOutlined,
} from '@ant-design/icons'
import {
  Alert,
  App,
  Button,
  Flex,
  Input,
  Modal,
  Select,
  Space,
  Switch,
  Table,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState, type ReactNode } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { platformApi } from '../api/platformApi'
import { JsonObjectEditor } from '../components/JsonObjectEditor'
import { StatusPill } from '../components/StatusPill'
import { SuiteExecutionWorkspace } from '../components/SuiteExecutionWorkspace'
import { parseDsl } from '../utils/caseDsl'
import type {
  CaseRecord,
  SaveSuiteCaseInput,
  SaveSuiteInput,
  SuiteDraftRecord,
  SuiteRecord,
} from '../types/platform'

const { Text } = Typography

type SuiteCaseDraft = SaveSuiteCaseInput & {
  clientKey: string
}

type SuiteDraft = Omit<SaveSuiteInput, 'cases'> & {
  cases: SuiteCaseDraft[]
}

const emptyDraft: SuiteDraft = {
  name: '',
  description: '',
  status: 'ACTIVE',
  cases: [],
}

type SuitesPageProps = {
  embedded?: boolean
  projectId?: string
  suiteId?: string
  onDirtyChange?: (dirty: boolean) => void
  onTitleChange?: (title: string) => void
  onSaveReady?: (save?: () => Promise<void>) => void
  onDeleted?: () => void
  agentOpen?: boolean
  onAgentToggle?: () => void
  agentPane?: ReactNode
  agentDraft?: SuiteDraftRecord
  onAgentDraftApplied?: (suiteId: string) => void
}

export function SuitesPage({
  embedded = false,
  projectId,
  suiteId,
  onDirtyChange,
  onTitleChange,
  onSaveReady,
  onDeleted,
  agentOpen = false,
  onAgentToggle,
  agentPane,
  agentDraft,
  onAgentDraftApplied,
}: SuitesPageProps = {}) {
  const { t } = useTranslation(['common', 'suite', 'agent'])
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [draft, setDraft] = useState<SuiteDraft>(emptyDraft)
  const [isCreating, setIsCreating] = useState(false)
  const [caseToAddId, setCaseToAddId] = useState<string>()
  const [variablesEditor, setVariablesEditor] = useState<{
    index: number
    defaults: Record<string, unknown>
    value: Record<string, unknown>
  }>()
  const [variablesEditorError, setVariablesEditorError] = useState<string | null>(null)
  const [activeAssetView, setActiveAssetView] = useState<'cases' | 'runs'>('cases')
  const [selectedSuiteRunId, setSelectedSuiteRunId] = useState<string>()
  const [selectedChildRunId, setSelectedChildRunId] = useState<string>()

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const selectedProjectId = projectId ?? searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id
  const selectedSuiteId = suiteId ?? searchParams.get('suiteId')

  const suitesQuery = useQuery({
    queryKey: ['suites', selectedProjectId],
    queryFn: () => platformApi.listSuites(selectedProjectId!),
    enabled: Boolean(selectedProjectId),
  })

  const casesQuery = useQuery({
    queryKey: ['cases', selectedProjectId],
    queryFn: () => platformApi.listCases(selectedProjectId!),
    enabled: Boolean(selectedProjectId),
  })

  const environmentsQuery = useQuery({
    queryKey: ['environments', selectedProjectId],
    queryFn: () => platformApi.listEnvironments(selectedProjectId!),
    enabled: Boolean(selectedProjectId),
  })

  const profilesQuery = useQuery({
    queryKey: ['profiles', selectedProjectId],
    queryFn: () => platformApi.listProfiles(selectedProjectId!),
    enabled: Boolean(selectedProjectId),
  })

  const suiteRunsQuery = useQuery({
    queryKey: ['suite-runs'],
    queryFn: platformApi.listSuiteRuns,
    refetchInterval: (query) => query.state.data?.some((run) => run.status === 'RUNNING') ? 3000 : false,
  })

  const selectedSuite = useMemo(
    () => suitesQuery.data?.find((suite) => suite.id === selectedSuiteId) ?? suitesQuery.data?.[0],
    [selectedSuiteId, suitesQuery.data],
  )

  const selectedSuiteRuns = useMemo(
    () => (suiteRunsQuery.data ?? [])
      .filter((run) => run.suiteId === selectedSuite?.id)
      .sort((left, right) => new Date(right.startedAt).getTime() - new Date(left.startedAt).getTime()),
    [selectedSuite?.id, suiteRunsQuery.data],
  )

  const selectedSuiteRun = selectedSuiteRuns.find((run) => run.id === selectedSuiteRunId)
    ?? selectedSuiteRuns[0]

  useEffect(() => {
    if (selectedSuite && !isCreating) {
      setDraft(toDraft(selectedSuite))
    }
  }, [isCreating, selectedSuite])

  useEffect(() => {
    if (!agentDraft || agentDraft.suiteId !== selectedSuite?.id) return
    setDraft({
      name: agentDraft.document.name,
      description: agentDraft.document.description,
      status: agentDraft.document.status,
      cases: renumberSuiteCases(agentDraft.document.entries.map((entry) => ({
        id: entry.id,
        caseId: entry.scenarioId,
        displayName: entry.displayName ?? '',
        order: 0,
        enabled: entry.enabled,
        variableOverrides: entry.variableOverrides ?? {},
        environmentId: entry.environmentId ?? undefined,
        profileId: entry.profileId ?? undefined,
        clientKey: entry.id,
      }))),
    })
    onAgentDraftApplied?.(agentDraft.suiteId)
  }, [agentDraft, onAgentDraftApplied, selectedSuite?.id])

  useEffect(() => {
    setActiveAssetView('cases')
    setSelectedSuiteRunId(undefined)
    setSelectedChildRunId(undefined)
  }, [selectedSuite?.id])

  useEffect(() => {
    if (selectedSuiteRun?.runs.some((run) => run.id === selectedChildRunId)) {
      return
    }
    const preferredRun = selectedSuiteRun?.runs.find((run) => run.status === 'FAILED')
      ?? selectedSuiteRun?.runs[0]
    setSelectedChildRunId(preferredRun?.id)
  }, [selectedChildRunId, selectedSuiteRun])

  const caseLookup = useMemo(() => {
    return new Map((casesQuery.data ?? []).map((record) => [record.id, record]))
  }, [casesQuery.data])

  const saveSuiteMutation = useMutation({
    mutationFn: async () => {
      if (!selectedProjectId) {
        throw new Error(t('suite:selectProject'))
      }
      if (variablesEditorError) {
        throw new Error(t('suite:fixVariables', { error: variablesEditorError }))
      }
      if (!draft.name.trim()) {
        throw new Error(t('suite:nameRequired'))
      }
      const payload = normalizeDraftForSave(draft)
      return isCreating || !selectedSuite
        ? platformApi.createSuite(selectedProjectId, payload)
        : platformApi.updateSuite(selectedProjectId, selectedSuite.id, payload)
    },
    onSuccess: async (savedSuite) => {
      await queryClient.invalidateQueries({ queryKey: ['suites', selectedProjectId] })
      setIsCreating(false)
      if (!embedded) {
        setSearchParams({ projectId: selectedProjectId!, suiteId: savedSuite.id })
      }
      void message.success(t('suite:saved'))
    },
  })

  useEffect(() => {
    onSaveReady?.(async () => {
      await saveSuiteMutation.mutateAsync()
    })
    return () => onSaveReady?.(undefined)
  }, [onSaveReady, saveSuiteMutation])

  const deleteSuiteMutation = useMutation({
    mutationFn: async () => {
      if (!selectedSuite) {
        return
      }
      await platformApi.deleteSuite(selectedSuite.id)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['suites', selectedProjectId] })
      setIsCreating(false)
      setDraft(emptyDraft)
      if (embedded) {
        onDeleted?.()
      } else {
        setSearchParams(selectedProjectId ? { projectId: selectedProjectId } : {})
      }
      void message.success(t('suite:deleted'))
    },
  })

  const runSuiteMutation = useMutation({
    mutationFn: async () => {
      if (!selectedSuite || isCreating) {
        throw new Error(t('suite:saveBeforeRun'))
      }
      return platformApi.startSuiteRun({
        suiteId: selectedSuite.id,
        triggeredBy: 'manual',
      })
    },
    onSuccess: async (run) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['runs'] }),
        queryClient.invalidateQueries({ queryKey: ['suite-runs'] }),
      ])
      setSelectedSuiteRunId(run.id)
      setSelectedChildRunId((run.runs.find((child) => child.status === 'FAILED') ?? run.runs[0])?.id)
      setActiveAssetView('runs')
      if (run.status === 'PASSED') {
        void message.success(t('suite:runPassed'))
      } else {
        void message.warning(t('suite:runFailed'))
      }
    },
  })

  const handleProjectChange = (projectId: string) => {
    setSearchParams({ projectId })
    setIsCreating(false)
    setDraft(emptyDraft)
    setCaseToAddId(undefined)
  }

  const handleSelectSuite = (suite: SuiteRecord) => {
    setSearchParams({ projectId: selectedProjectId!, suiteId: suite.id })
    setIsCreating(false)
    setCaseToAddId(undefined)
  }

  const handleCreateSuite = () => {
    if (!selectedProjectId) {
      return
    }
    setDraft({
      ...emptyDraft,
      name: t('suite:newSuite'),
      description: '',
    })
    setIsCreating(true)
    setSearchParams({ projectId: selectedProjectId })
  }

  const handleAddCase = () => {
    const selectedCase = (casesQuery.data ?? []).find((record) => record.id === caseToAddId)
    if (!selectedCase) {
      return
    }
    setDraft((current) => ({
      ...current,
      cases: renumberSuiteCases([
        ...current.cases,
        {
          caseId: selectedCase.id,
          displayName: selectedCase.name,
          order: current.cases.length + 1,
          enabled: true,
          variableOverrides: {},
          clientKey: createClientKey(),
        },
      ]),
    }))
    setCaseToAddId(undefined)
  }

  const updateSuiteCase = (index: number, patch: Partial<SuiteCaseDraft>) => {
    setDraft((current) => ({
      ...current,
      cases: current.cases.map((suiteCase, currentIndex) =>
        currentIndex === index ? { ...suiteCase, ...patch } : suiteCase,
      ),
    }))
  }

  const moveSuiteCase = (index: number, direction: -1 | 1) => {
    setDraft((current) => {
      const nextIndex = index + direction
      if (nextIndex < 0 || nextIndex >= current.cases.length) {
        return current
      }
      const cases = [...current.cases]
      const [suiteCase] = cases.splice(index, 1)
      cases.splice(nextIndex, 0, suiteCase)
      return { ...current, cases: renumberSuiteCases(cases) }
    })
  }

  const removeSuiteCase = (index: number) => {
    setDraft((current) => ({
      ...current,
      cases: renumberSuiteCases(current.cases.filter((_item, currentIndex) => currentIndex !== index)),
    }))
  }

  const confirmDeleteSuite = () => {
    if (!selectedSuite || isCreating) {
      return
    }
    modal.confirm({
      title: t('suite:deleteConfirm'),
      content: t('suite:deleteDescription', { name: selectedSuite.name }),
      okText: t('common:actions.delete'),
      okButtonProps: { danger: true },
      onOk: () => deleteSuiteMutation.mutateAsync(),
    })
  }

  const saveVariableOverrides = () => {
    if (!variablesEditor || variablesEditorError) {
      return
    }
    updateSuiteCase(variablesEditor.index, {
      variableOverrides: calculateVariableOverrides(
        variablesEditor.defaults,
        variablesEditor.value,
      ),
    })
    setVariablesEditor(undefined)
    setVariablesEditorError(null)
  }

  const getCaseDisplay = (suiteCase: SaveSuiteCaseInput) => {
    const catalogCase = caseLookup.get(suiteCase.caseId)
    const suiteCaseRecord = selectedSuite?.cases.find((record) => record.caseId === suiteCase.caseId)
    return {
      name: catalogCase?.name ?? suiteCaseRecord?.caseName ?? t('suite:unknownCase'),
      code: catalogCase?.code ?? suiteCaseRecord?.caseCode ?? suiteCase.caseId,
    }
  }

  const suiteCaseColumns: ColumnsType<SuiteCaseDraft> = [
    {
      title: '',
      key: 'order',
      width: 96,
      render: (_value, _record, index) => (
        <Space size={4}>
          <Button
            size="small"
            type="text"
            icon={<ArrowUpOutlined />}
            disabled={index === 0}
            onClick={() => moveSuiteCase(index, -1)}
          />
          <Button
            size="small"
            type="text"
            icon={<ArrowDownOutlined />}
            disabled={index === draft.cases.length - 1}
            onClick={() => moveSuiteCase(index, 1)}
          />
        </Space>
      ),
    },
    {
      title: t('suite:case'),
      key: 'case',
      render: (_value, record) => {
        const display = getCaseDisplay(record)
        return (
          <Flex vertical gap={2}>
            <Text strong>{display.name}</Text>
            <Text type="secondary">{display.code}</Text>
          </Flex>
        )
      },
    },
    {
      title: t('suite:displayName'),
      dataIndex: 'displayName',
      width: 210,
      render: (value: string | undefined, _record, index) => (
        <Input
          value={value}
          placeholder={t('suite:displayNamePlaceholder')}
          onChange={(event) => updateSuiteCase(index, { displayName: event.target.value })}
        />
      ),
    },
    {
      title: t('common:fields.environment'),
      dataIndex: 'environmentId',
      width: 180,
      render: (value: string | undefined, record, index) => (
        <Select
          allowClear
          className="suite-case-select"
          placeholder={t('suite:suiteDefault')}
          value={value}
          options={(environmentsQuery.data ?? []).map((environment) => ({
            label: environment.name,
            value: environment.id,
            disabled: environment.status !== 'ACTIVE',
          }))}
          onChange={(nextValue) => {
            const currentProfile = (profilesQuery.data ?? []).find(
              (profile) => profile.id === record.profileId,
            )
            updateSuiteCase(index, {
              environmentId: nextValue,
              profileId: nextValue && currentProfile?.environmentIds.includes(nextValue)
                ? record.profileId
                : undefined,
            })
          }}
        />
      ),
    },
    {
      title: t('common:fields.profile'),
      dataIndex: 'profileId',
      width: 180,
      render: (value: string | undefined, record, index) => (
        <Select
          allowClear
          className="suite-case-select"
          placeholder={t('suite:suiteDefault')}
          value={value}
          options={(profilesQuery.data ?? [])
            .filter((profile) => (
              profile.enabled
              && (!record.environmentId || profile.environmentIds.includes(record.environmentId))
            ))
            .map((profile) => ({
              label: profile.name,
              value: profile.id,
            }))}
          onChange={(nextValue) => updateSuiteCase(index, { profileId: nextValue })}
        />
      ),
    },
    {
      title: t('suite:enabled'),
      dataIndex: 'enabled',
      width: 96,
      render: (value: boolean, _record, index) => (
        <Switch checked={value} onChange={(checked) => updateSuiteCase(index, { enabled: checked })} />
      ),
    },
    {
      title: '',
      key: 'actions',
      width: 100,
      render: (_value, record, index) => (
        <Space size={4}>
          <Button
            size="small"
            type="text"
            icon={<CodeOutlined />}
            onClick={() => {
              const defaults = scenarioVariables(caseLookup.get(record.caseId))
              setVariablesEditorError(null)
              setVariablesEditor({
                index,
                defaults,
                value: {
                  ...defaults,
                  ...(record.variableOverrides ?? {}),
                },
              })
            }}
          />
          <Button
            size="small"
            type="text"
            danger
            icon={<DeleteOutlined />}
            onClick={() => removeSuiteCase(index)}
          />
        </Space>
      ),
    },
  ]

  const canEdit = Boolean(selectedProjectId && (selectedSuite || isCreating))
  const isDraftDirty = useMemo(
    () => isCreating || Boolean(
      selectedSuite
      && JSON.stringify(normalizeDraftForSave(draft))
        !== JSON.stringify(normalizeDraftForSave(toDraft(selectedSuite))),
    ),
    [draft, isCreating, selectedSuite],
  )

  useEffect(() => {
    onDirtyChange?.(isDraftDirty || Boolean(variablesEditorError))
  }, [isDraftDirty, onDirtyChange, variablesEditorError])

  useEffect(() => {
    if (draft.name) {
      onTitleChange?.(draft.name)
    }
  }, [draft.name, onTitleChange])

  return (
    <div className={embedded ? 'page-shell suite-page embedded' : 'page-shell'}>
      {embedded ? null : <section className="hero-band">
        <div>
          <h1>{t('suite:title')}</h1>
          <p>{t('suite:heroDescription')}</p>
        </div>
        <Space wrap>
          <Select
            className="project-picker"
            placeholder={t('suite:selectProject')}
            value={selectedProjectId}
            options={(projectsQuery.data ?? []).map((project) => ({
              label: project.name,
              value: project.id,
            }))}
            onChange={handleProjectChange}
          />
          <Button icon={<PlusOutlined />} onClick={handleCreateSuite} disabled={!selectedProjectId}>
            {t('suite:newSuite')}
          </Button>
        </Space>
      </section>}

      <div className={embedded ? 'suites-layout embedded' : 'suites-layout'}>
        {embedded ? null : <section className="panel-section suites-library-panel">
          <div className="panel-title-row compact">
            <div>
              <h2 className="panel-title">{t('suite:library')}</h2>
              <Text type="secondary">
                {selectedProjectId ? t('suite:currentProjectDescription') : t('suite:chooseProjectFirst')}
              </Text>
            </div>
          </div>

          {suitesQuery.isError ? (
            <Alert className="section-alert" type="warning" showIcon message={t('suite:loadError')} />
          ) : null}

          <div className="suite-list">
            {suitesQuery.isLoading ? (
              <div className="panel-empty">{t('suite:loading')}</div>
            ) : null}
            {!suitesQuery.isLoading && (suitesQuery.data ?? []).length === 0 ? (
              <div className="panel-empty">{t('suite:empty')}</div>
            ) : null}
            {(suitesQuery.data ?? []).map((suite) => (
              <button
                key={suite.id}
                type="button"
                className={suite.id === selectedSuite?.id && !isCreating ? 'suite-list-item active' : 'suite-list-item'}
                onClick={() => handleSelectSuite(suite)}
              >
                <Flex vertical gap={4}>
                  <Space>
                    <Text strong>{suite.name}</Text>
                    <StatusPill status={suite.status} />
                  </Space>
                  <Text type="secondary">
                    {t('suite:caseCount', { count: suite.cases.length })}
                  </Text>
                </Flex>
              </button>
            ))}
          </div>
        </section>}

        <section className={embedded ? 'suite-editor-column embedded' : 'suite-editor-column'}>
          {canEdit ? (
            <>
              <section className={embedded ? 'panel-section suite-tab-header' : 'panel-section'}>
                <div className="suite-editor-head">
                  <div className="suite-title-field">
                    <Text className="designer-label">{t('suite:suiteName')}</Text>
                    <Input
                      size="large"
                      value={draft.name}
                      placeholder={t('suite:nameEditorPlaceholder')}
                      onChange={(event) => setDraft({ ...draft, name: event.target.value })}
                    />
                  </div>
                  <Space wrap align="end">
                    <div className="suite-status-field">
                      <Text className="designer-label">{t('common:fields.status')}</Text>
                      <Select
                        value={draft.status}
                        options={[
                          { value: 'ACTIVE', label: 'ACTIVE' },
                          { value: 'INACTIVE', label: 'INACTIVE' },
                        ]}
                        onChange={(value) => setDraft({ ...draft, status: value })}
                      />
                    </div>
                    <Button
                      icon={<SaveOutlined />}
                      disabled={!isDraftDirty}
                      loading={saveSuiteMutation.isPending}
                      onClick={() => void saveSuiteMutation.mutateAsync()}
                    >
                      {t('common:actions.save')}
                    </Button>
                    {embedded ? (
                      <Button
                        className={agentOpen ? 'asset-agent-button active' : 'asset-agent-button'}
                        icon={<RobotOutlined />}
                        disabled={isCreating || !selectedSuite}
                        aria-label={t('agent:openSuiteAgent')}
                        title={t('agent:openSuiteAgent')}
                        onClick={onAgentToggle}
                      >
                        {t('agent:shortTitle')}
                      </Button>
                    ) : null}
                    <Button
                      type="primary"
                      icon={<PlayCircleOutlined />}
                      disabled={isCreating || !selectedSuite || isDraftDirty || !draft.cases.some((suiteCase) => suiteCase.enabled)}
                      loading={runSuiteMutation.isPending}
                      onClick={() => void runSuiteMutation.mutateAsync()}
                    >
                      {t('common:actions.run')}
                    </Button>
                    <Button
                      danger
                      icon={<DeleteOutlined />}
                      disabled={isCreating || !selectedSuite}
                      loading={deleteSuiteMutation.isPending}
                      onClick={confirmDeleteSuite}
                    >
                      {t('common:actions.delete')}
                    </Button>
                  </Space>
                </div>

                {saveSuiteMutation.isError ? (
                  <Alert className="section-alert" type="error" showIcon message={t('suite:saveFailed')} />
                ) : null}
                {runSuiteMutation.isError ? (
                  <Alert
                    className="section-alert"
                    type="error"
                    showIcon
                    message={runSuiteMutation.error instanceof Error
                      ? runSuiteMutation.error.message
                      : t('suite:runFailedGeneric')}
                  />
                ) : null}
              </section>

              <div className={agentPane ? 'asset-tab-workspace agent-visible' : 'asset-tab-workspace'}>
                <div className="asset-tab-editor-pane suite-editor-pane">
                  <nav className="asset-context-tabs" aria-label={t('suite:sections')}>
                    <button
                      type="button"
                      className={activeAssetView === 'cases' ? 'active' : ''}
                      onClick={() => setActiveAssetView('cases')}
                    >
                      {t('suite:cases')}
                    </button>
                    <button
                      type="button"
                      className={activeAssetView === 'runs' ? 'active' : ''}
                      onClick={() => setActiveAssetView('runs')}
                    >
                      {t('suite:executions')} <span>{selectedSuiteRuns.length}</span>
                    </button>
                  </nav>

                  {activeAssetView === 'cases' ? (
                    <section className="panel-section">
                  <div className="suite-description-field">
                    <Text className="designer-label">{t('common:fields.description')}</Text>
                    <Input.TextArea
                      rows={3}
                      value={draft.description}
                      placeholder={t('suite:descriptionPlaceholder')}
                      onChange={(event) => setDraft({ ...draft, description: event.target.value })}
                    />
                  </div>

                  <div className="panel-title-row compact suite-case-order-head">
                    <div>
                      <h2 className="panel-title">{t('suite:caseOrder')}</h2>
                      <Text type="secondary">{t('suite:caseOrderDescription')}</Text>
                    </div>
                  </div>

                  <div className="suite-case-toolbar">
                    <Select
                      showSearch
                      allowClear
                      className="suite-add-case-select"
                      placeholder={t('suite:addExistingCase')}
                      optionFilterProp="label"
                      value={caseToAddId}
                      options={(casesQuery.data ?? []).map((record) => ({
                        label: `${record.name} - ${record.code}`,
                        value: record.id,
                      }))}
                      onChange={setCaseToAddId}
                    />
                    <Button icon={<PlusOutlined />} onClick={handleAddCase} disabled={!caseToAddId}>
                      {t('suite:addCase')}
                    </Button>
                  </div>

                  <Table<SuiteCaseDraft>
                    rowKey={(record) => record.id ?? record.clientKey}
                    className="suite-case-table"
                    size="small"
                    columns={suiteCaseColumns}
                    dataSource={draft.cases}
                    pagination={false}
                    locale={{
                      emptyText: t('suite:emptyCases'),
                    }}
                  />
                    </section>
                  ) : (
                    <section className="suite-context-runs">
                      {selectedSuiteRuns.length ? (
                        <main className="suite-context-run-detail">
                          <SuiteExecutionWorkspace
                            run={selectedSuiteRun}
                            executions={selectedSuiteRuns}
                            onSelectExecution={setSelectedSuiteRunId}
                            selectedRunId={selectedChildRunId}
                            onSelectRun={setSelectedChildRunId}
                          />
                        </main>
                      ) : (
                        <div className="context-runs-empty">
                          <Text strong>{t('suite:noExecutions')}</Text>
                          <Text type="secondary">{t('suite:noExecutionsDescription')}</Text>
                        </div>
                      )}
                    </section>
                  )}
                </div>
                {agentPane}
              </div>
            </>
          ) : (
            <section className="panel-section">
              <div className="panel-empty">
                {t('suite:selectOrCreate')}
              </div>
            </section>
          )}
        </section>
      </div>

      <Modal
        title={t('suite:variableOverrides')}
        open={Boolean(variablesEditor)}
        destroyOnHidden
        okButtonProps={{ disabled: Boolean(variablesEditorError) }}
        onCancel={() => {
          setVariablesEditor(undefined)
          setVariablesEditorError(null)
        }}
        onOk={saveVariableOverrides}
        okText={t('common:actions.apply')}
      >
        <div className="api-import-stack">
          <Text type="secondary">{t('suite:variableOverridesDescription')}</Text>
          <JsonObjectEditor
            ariaLabel={t('suite:variableEditor')}
            height="240px"
            resetLabel={t('suite:restoreDefault')}
            resetValue={variablesEditor?.defaults ?? {}}
            value={variablesEditor?.value ?? {}}
            onValidationChange={setVariablesEditorError}
            onCommit={(value) => {
              setVariablesEditor((current) => current ? { ...current, value } : current)
            }}
          />
        </div>
      </Modal>
    </div>
  )
}

function scenarioVariables(record: CaseRecord | undefined): Record<string, unknown> {
  if (!record) {
    return {}
  }
  const parsed = parseDsl(record.content)
  const variables = parsed.data?.variables
  return variables && typeof variables === 'object' && !Array.isArray(variables)
    ? structuredClone(variables as Record<string, unknown>)
    : {}
}

function calculateVariableOverrides(
  defaults: Record<string, unknown>,
  effectiveValue: Record<string, unknown>,
) {
  return Object.fromEntries(
    Object.entries(effectiveValue).filter(([key, value]) => (
      !(key in defaults) || !jsonValuesEqual(value, defaults[key])
    )),
  )
}

function jsonValuesEqual(left: unknown, right: unknown): boolean {
  if (Object.is(left, right)) {
    return true
  }
  if (Array.isArray(left) || Array.isArray(right)) {
    return Array.isArray(left)
      && Array.isArray(right)
      && left.length === right.length
      && left.every((value, index) => jsonValuesEqual(value, right[index]))
  }
  if (isJsonObject(left) || isJsonObject(right)) {
    if (!isJsonObject(left) || !isJsonObject(right)) {
      return false
    }
    const leftKeys = Object.keys(left).sort()
    const rightKeys = Object.keys(right).sort()
    return leftKeys.length === rightKeys.length
      && leftKeys.every((key, index) => (
        key === rightKeys[index] && jsonValuesEqual(left[key], right[key])
      ))
  }
  return false
}

function isJsonObject(value: unknown): value is Record<string, unknown> {
  return Boolean(value) && typeof value === 'object' && !Array.isArray(value)
}

function toDraft(suite: SuiteRecord): SuiteDraft {
  return {
    name: suite.name,
    description: suite.description ?? '',
    status: suite.status,
    cases: renumberSuiteCases(
      suite.cases.map((suiteCase) => ({
        id: suiteCase.id,
        caseId: suiteCase.caseId,
        displayName: suiteCase.displayName ?? '',
        order: suiteCase.order,
        enabled: suiteCase.enabled,
        variableOverrides: suiteCase.variableOverrides ?? {},
        environmentId: suiteCase.environmentId ?? undefined,
        profileId: suiteCase.profileId ?? undefined,
        clientKey: suiteCase.id,
      })),
    ),
  }
}

function normalizeDraftForSave(draft: SuiteDraft): SaveSuiteInput {
  return {
    name: draft.name.trim(),
    description: draft.description.trim(),
    status: draft.status || 'ACTIVE',
    cases: renumberSuiteCases(draft.cases).map((suiteCase) => {
      return {
        id: suiteCase.id,
        caseId: suiteCase.caseId,
        displayName: suiteCase.displayName?.trim(),
        order: suiteCase.order,
        enabled: suiteCase.enabled,
        environmentId: suiteCase.environmentId || undefined,
        profileId: suiteCase.profileId || undefined,
        variableOverrides: suiteCase.variableOverrides ?? {},
      }
    }),
  }
}

function renumberSuiteCases<T extends SaveSuiteCaseInput>(cases: T[]) {
  return cases.map((suiteCase, index) => ({
    ...suiteCase,
    order: index + 1,
  }))
}

function createClientKey() {
  return globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`
}
