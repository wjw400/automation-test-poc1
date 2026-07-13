import {
  Alert,
  App,
  AutoComplete,
  Button,
  Input,
  Modal,
  Popover,
  Select,
  Space,
  Typography,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ControlOutlined,
  DeleteOutlined,
  DownOutlined,
  FileTextOutlined,
  FolderOutlined,
  FolderOpenOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  RightOutlined,
  SaveOutlined,
  UpOutlined,
} from '@ant-design/icons'
import { useEffect, useMemo, useRef, useState } from 'react'
import type { MouseEvent as ReactMouseEvent } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CaseDesigner } from '../components/CaseDesigner'
import { RunDetail } from '../components/RunDetail'
import { platformApi } from '../api/platformApi'
import { StatusPill } from '../components/StatusPill'
import { parseDsl, stringifyDsl } from '../utils/caseDsl'
import type {
  ApiOperationRecord,
  CaseRecord,
  RunRecord,
  SaveCaseInput,
} from '../types/platform'

const { Text } = Typography

const emptyDraft: SaveCaseInput = {
  name: '',
  code: '',
  description: '',
  folderPath: '',
  tags: [],
  dslVersion: 'v1',
  content: stringifyDsl({
    dslVersion: 'v1',
    name: 'New API case',
    description: '',
    tags: [],
    runtime: {},
    variables: {},
    steps: [],
  }),
  status: 'ACTIVE',
}

export function CasesPage() {
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [draft, setDraft] = useState<SaveCaseInput>(emptyDraft)
  const [draftCaseId, setDraftCaseId] = useState<string>()
  const [environmentId, setEnvironmentId] = useState<string>()
  const [profileId, setProfileId] = useState<string>()
  const [isCreating, setIsCreating] = useState(false)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [createOperationId, setCreateOperationId] = useState<string>()
  const [caseToMove, setCaseToMove] = useState<CaseRecord>()
  const [moveFolderPath, setMoveFolderPath] = useState('')
  const [selectedEvidenceStepIndex, setSelectedEvidenceStepIndex] = useState(1)
  const [caseFilter, setCaseFilter] = useState('')
  const [expandedFolderKeys, setExpandedFolderKeys] = useState<Set<string>>(() => new Set())
  const [isResultsCollapsed, setIsResultsCollapsed] = useState(true)
  const [resultsPanelHeight, setResultsPanelHeight] = useState(300)
  const [runResultOverride, setRunResultOverride] = useState<RunRecord>()
  const titleBeforeEditRef = useRef('')
  const isCancellingTitleEditRef = useRef(false)

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const selectedProjectId = searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id
  const selectedCaseId = searchParams.get('caseId')
  const selectedStepParam = searchParams.get('step')

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

  const operationsQuery = useQuery({
    queryKey: ['operations', selectedProjectId],
    queryFn: () => platformApi.listOperations(selectedProjectId!),
    enabled: Boolean(selectedProjectId),
  })

  const runsQuery = useQuery({
    queryKey: ['runs'],
    queryFn: platformApi.listRuns,
    refetchInterval: 15000,
  })

  const selectedCase = useMemo(
    () => casesQuery.data?.find((item) => item.id === selectedCaseId) ?? casesQuery.data?.[0],
    [casesQuery.data, selectedCaseId],
  )

  const filteredCases = useMemo(() => {
    const keyword = caseFilter.trim().toLowerCase()
    if (!keyword) {
      return casesQuery.data ?? []
    }
    return (casesQuery.data ?? []).filter((record) => {
      const haystack = [record.name, record.code, record.description, record.folderPath, ...(record.tags ?? [])]
        .join(' ')
        .toLowerCase()
      return haystack.includes(keyword)
    })
  }, [caseFilter, casesQuery.data])

  const selectedEnvironment = useMemo(
    () => (environmentsQuery.data ?? []).find((environment) => environment.id === environmentId),
    [environmentId, environmentsQuery.data],
  )

  const selectedProfile = useMemo(
    () => (profilesQuery.data ?? []).find((profile) => profile.id === profileId),
    [profileId, profilesQuery.data],
  )

  useEffect(() => {
    if (!selectedCase || isCreating || draftCaseId === selectedCase.id) {
      return
    }
    setDraft(draftFromCase(selectedCase))
    setDraftCaseId(selectedCase.id)
  }, [draftCaseId, isCreating, selectedCase])

  useEffect(() => {
    if (runResultOverride && runResultOverride.caseId !== selectedCase?.id) {
      setRunResultOverride(undefined)
    }
  }, [runResultOverride, selectedCase?.id])

  const parsedDraft = useMemo(() => parseDsl(draft.content), [draft.content])
  const draftSteps = Array.isArray(parsedDraft.data?.steps) ? parsedDraft.data.steps : []
  const requestedStepIndex = selectedStepParam == null ? -1 : Number(selectedStepParam)
  const activeStepIndex = Number.isInteger(requestedStepIndex)
    ? Math.min(Math.max(requestedStepIndex, 0), Math.max(draftSteps.length - 1, 0))
    : 0
  const selectedEditorView = selectedStepParam != null && draftSteps[activeStepIndex] ? 'step' : 'case'
  const latestPersistedCaseRun = useMemo(() => {
    if (!selectedCase?.id) {
      return undefined
    }
    return (runsQuery.data ?? [])
      .filter((run) => run.caseId === selectedCase.id)
      .sort((left, right) => new Date(right.startedAt).getTime() - new Date(left.startedAt).getTime())[0]
  }, [runsQuery.data, selectedCase?.id])
  const latestCaseRun = runResultOverride?.caseId === selectedCase?.id
    ? runResultOverride
    : latestPersistedCaseRun
  const latestCaseRunFirstStepIndex = latestCaseRun?.steps?.[0]?.index

  const latestRunsByCaseId = useMemo(() => {
    const runs = [...(runsQuery.data ?? [])]
    if (runResultOverride) {
      runs.unshift(runResultOverride)
    }
    return runs
      .sort((left, right) => new Date(right.startedAt).getTime() - new Date(left.startedAt).getTime())
      .reduce((latestRuns, run) => {
        if (!latestRuns.has(run.caseId)) {
          latestRuns.set(run.caseId, run)
        }
        return latestRuns
      }, new Map<string, RunRecord>())
  }, [runResultOverride, runsQuery.data])

  const isDraftDirty = useMemo(
    () => isCreating || (selectedCase ? isCaseDraftDirty(draft, selectedCase) : false),
    [draft, isCreating, selectedCase],
  )

  const explorerNodes = useMemo(() => {
    const records = filteredCases.map((record) => {
      if (record.id !== selectedCase?.id || isCreating) {
        return record
      }
      return {
        ...record,
        content: draft.content,
        folderPath: draft.folderPath,
      }
    })
    return buildCaseExplorerTree(records)
  }, [draft.content, draft.folderPath, filteredCases, isCreating, selectedCase?.id])

  const selectedFolderKeys = useMemo(
    () => folderKeysForPath(selectedCase?.id && !isCreating ? draft.folderPath : undefined),
    [draft.folderPath, isCreating, selectedCase?.id],
  )

  const folderPaths = useMemo(
    () => collectFolderPaths(casesQuery.data ?? []),
    [casesQuery.data],
  )

  useEffect(() => {
    if (selectedEditorView === 'case' && latestCaseRunFirstStepIndex) {
      setSelectedEvidenceStepIndex(latestCaseRunFirstStepIndex)
    }
  }, [latestCaseRun?.id, latestCaseRunFirstStepIndex, selectedEditorView])

  useEffect(() => {
    if (!selectedFolderKeys.length) {
      return
    }
    setExpandedFolderKeys((current) => {
      const next = new Set(current)
      let changed = false
      selectedFolderKeys.forEach((key) => {
        if (!next.has(key)) {
          next.add(key)
          changed = true
        }
      })
      return changed ? next : current
    })
  }, [selectedFolderKeys])

  const saveCaseMutation = useMutation({
    mutationFn: async () => {
      if (!selectedProjectId) {
        throw new Error('Select a project first.')
      }
      const payload = normalizeDraftForSave(draft)
      return isCreating || !selectedCase
        ? platformApi.createCase(selectedProjectId, payload)
        : platformApi.updateCase(selectedProjectId, selectedCase.id, payload)
    },
    onSuccess: async (savedCase) => {
      await queryClient.invalidateQueries({ queryKey: ['cases', selectedProjectId] })
      setDraft(draftFromCase(savedCase))
      setDraftCaseId(savedCase.id)
      setIsCreating(false)
      setSearchParams({
        projectId: selectedProjectId!,
        caseId: savedCase.id,
        ...(selectedEditorView === 'step' ? { step: String(activeStepIndex) } : {}),
      })
      void message.success('Case saved')
    },
  })

  const deleteCaseMutation = useMutation({
    mutationFn: platformApi.deleteCase,
    onSuccess: async (_result, deletedCaseId) => {
      const remainingCases = (casesQuery.data ?? []).filter((record) => record.id !== deletedCaseId)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cases', selectedProjectId] }),
        queryClient.invalidateQueries({ queryKey: ['suites', selectedProjectId] }),
      ])

      if (selectedCase?.id === deletedCaseId) {
        const nextCase = remainingCases[0]
        if (selectedProjectId && nextCase) {
          setSearchParams({ projectId: selectedProjectId, caseId: nextCase.id })
        } else if (selectedProjectId) {
          setSearchParams({ projectId: selectedProjectId })
          setDraft(emptyDraft)
          setDraftCaseId(undefined)
        } else {
          setSearchParams({})
          setDraft(emptyDraft)
          setDraftCaseId(undefined)
        }
        setIsCreating(false)
      }

      void message.success('Case deleted')
    },
  })

  const moveCaseMutation = useMutation({
    mutationFn: async () => {
      if (!selectedProjectId || !caseToMove) {
        throw new Error('Select a scenario to move.')
      }
      return platformApi.updateCase(
        selectedProjectId,
        caseToMove.id,
        normalizeDraftForSave({
          ...draftFromCase(caseToMove),
          folderPath: moveFolderPath,
        }),
      )
    },
    onSuccess: async (savedCase) => {
      await queryClient.invalidateQueries({ queryKey: ['cases', selectedProjectId] })
      if (savedCase.id === selectedCase?.id) {
        setDraft(draftFromCase(savedCase))
        setDraftCaseId(savedCase.id)
      }
      setCaseToMove(undefined)
      void message.success('Scenario moved')
    },
  })

  const saveTitleMutation = useMutation({
    mutationFn: async ({ record, name }: { record: CaseRecord; name: string }) => {
      if (!selectedProjectId) {
        throw new Error('Select a project first.')
      }
      return platformApi.updateCase(
        selectedProjectId,
        record.id,
        normalizeDraftForSave({
          ...draftFromCase(record),
          name,
        }),
      )
    },
    onSuccess: async (savedCase) => {
      await queryClient.invalidateQueries({ queryKey: ['cases', selectedProjectId] })
      if (savedCase.id === selectedCase?.id) {
        setDraft((current) => ({ ...current, name: savedCase.name }))
      }
    },
    onError: () => {
      void message.error('Unable to save scenario name')
    },
  })

  const runMutation = useMutation({
    mutationFn: async () => {
      const caseId = selectedCase?.id
      if (!caseId) {
        throw new Error('Save the case before running it.')
      }
      return platformApi.startRun({
        caseId,
        environmentId,
        profileId,
        triggeredBy: 'platform-ui',
      })
    },
    onSuccess: async (run) => {
      await queryClient.invalidateQueries({ queryKey: ['runs'] })
      setRunResultOverride(run)
      setSelectedEvidenceStepIndex(run.steps[0]?.index ?? 1)
      setIsResultsCollapsed(false)
      void message.success(`Run ${run.status.toLowerCase()}`)
    },
  })

  const handleSelectCase = (record: CaseRecord) => {
    setSearchParams({ projectId: selectedProjectId!, caseId: record.id })
    setIsCreating(false)
  }

  const handleSelectDraftStep = (stepIndex: number) => {
    if (!selectedProjectId || !selectedCase) {
      return
    }
    setSearchParams({ projectId: selectedProjectId, caseId: selectedCase.id, step: String(stepIndex) })
    setSelectedEvidenceStepIndex(stepIndex + 1)
  }

  const handleSelectCaseOverview = () => {
    if (!selectedProjectId || !selectedCase) {
      return
    }
    setSearchParams({ projectId: selectedProjectId, caseId: selectedCase.id })
  }

  const confirmDeleteCase = (record: CaseRecord) => {
    modal.confirm({
      title: 'Delete case?',
      content: `This will remove "${record.name}" from the case library and suites. Run history is kept.`,
      okText: 'Delete',
      okButtonProps: { danger: true },
      cancelText: 'Cancel',
      onOk: () => deleteCaseMutation.mutateAsync(record.id),
    })
  }

  const openCreateCaseModal = () => {
    if (!selectedProjectId) {
      return
    }
    setCreateOperationId(undefined)
    setIsCreateModalOpen(true)
  }

  const handleCreateCase = () => {
    const operation = (operationsQuery.data ?? []).find((item) => item.id === createOperationId)
    const nextDraft = operation ? createDraftFromOperation(operation) : emptyDraft
    setDraft(nextDraft)
    setDraftCaseId(undefined)
    setIsCreating(true)
    setSearchParams({ projectId: selectedProjectId! })
    setIsCreateModalOpen(false)
  }

  const toggleFolder = (folderKey: string) => {
    setExpandedFolderKeys((current) => {
      const next = new Set(current)
      if (next.has(folderKey)) {
        next.delete(folderKey)
      } else {
        next.add(folderKey)
      }
      return next
    })
  }

  const openMoveScenario = (record: CaseRecord) => {
    if (record.id === selectedCase?.id && isDraftDirty) {
      void message.warning('Save changes before moving this scenario.')
      return
    }
    setCaseToMove(record)
    setMoveFolderPath(record.folderPath ?? '')
  }

  const autoSaveTitle = (name: string) => {
    if (!selectedCase || isCreating) {
      return
    }
    const normalizedName = name.trim()
    if (!normalizedName) {
      setDraft((current) => ({ ...current, name: selectedCase.name }))
      void message.warning('Scenario name is required')
      return
    }
    if (normalizedName === selectedCase.name) {
      if (name !== normalizedName) {
        setDraft((current) => ({ ...current, name: normalizedName }))
      }
      return
    }
    void saveTitleMutation.mutateAsync({ record: selectedCase, name: normalizedName })
  }

  const startResultsResize = (event: ReactMouseEvent<HTMLDivElement>) => {
    event.preventDefault()
    const startY = event.clientY
    const startHeight = resultsPanelHeight
    const maxHeight = Math.max(220, Math.floor(window.innerHeight * 0.7))

    const handleMouseMove = (moveEvent: MouseEvent) => {
      const nextHeight = Math.min(Math.max(startHeight + startY - moveEvent.clientY, 180), maxHeight)
      setResultsPanelHeight(nextHeight)
    }

    const handleMouseUp = () => {
      window.removeEventListener('mousemove', handleMouseMove)
      window.removeEventListener('mouseup', handleMouseUp)
    }

    window.addEventListener('mousemove', handleMouseMove)
    window.addEventListener('mouseup', handleMouseUp)
  }

  const renderExplorerNode = (node: ExplorerNode, depth = 0) => {
    if (node.type === 'folder') {
      const isExpanded = Boolean(caseFilter.trim()) || selectedFolderKeys.includes(node.key) || expandedFolderKeys.has(node.key)
      return (
        <div key={node.key} className="scenario-folder-group">
          <button
            type="button"
            className="scenario-folder-row"
            style={{ paddingLeft: 8 + depth * 14 }}
            aria-expanded={isExpanded}
            onClick={() => toggleFolder(node.key)}
          >
            {isExpanded ? <DownOutlined className="scenario-folder-toggle" /> : <RightOutlined className="scenario-folder-toggle" />}
            <FolderOpenOutlined className="scenario-tree-icon" />
            <span className="scenario-folder-name">{node.name}</span>
          </button>
          {isExpanded ? (
            <div className="scenario-folder-children">
              {node.children.map((child) => renderExplorerNode(child, depth + 1))}
            </div>
          ) : null}
        </div>
      )
    }

    const record = node.record
    const isSelectedCase = record.id === selectedCase?.id && !isCreating
    const stepCount = node.stepCount
    const latestRun = latestRunsByCaseId.get(record.id)

    return (
      <div key={record.id} className="scenario-tree-group">
        <div
          role="button"
          tabIndex={0}
          className={isSelectedCase ? 'scenario-case-row active' : 'scenario-case-row'}
          style={{ paddingLeft: 8 + depth * 14 }}
          onClick={() => handleSelectCase(record)}
          onKeyDown={(event) => {
            if (event.key === 'Enter' || event.key === ' ') {
              event.preventDefault()
              handleSelectCase(record)
            }
          }}
        >
          <FileTextOutlined className="scenario-tree-icon" />
          <span className="scenario-case-copy">
            <span className="scenario-case-name">{record.name}</span>
            <span className="scenario-case-path">{stepCount} step{stepCount === 1 ? '' : 's'}</span>
          </span>
          {latestRun ? <span className={`scenario-run-indicator ${latestRun.status.toLowerCase()}`} title={`Last run: ${latestRun.status}`} /> : null}
          <Button
            aria-label={`Move ${record.name}`}
            className="scenario-case-move"
            icon={<FolderOutlined />}
            size="small"
            title="Move scenario"
            type="text"
            onClick={(event) => {
              event.stopPropagation()
              openMoveScenario(record)
            }}
            onKeyDown={(event) => event.stopPropagation()}
          />
          <Button
            aria-label={`Delete ${record.name}`}
            className="scenario-case-delete"
            danger
            icon={<DeleteOutlined />}
            loading={deleteCaseMutation.isPending && deleteCaseMutation.variables === record.id}
            size="small"
            title="Delete case"
            type="text"
            onClick={(event) => {
              event.stopPropagation()
              confirmDeleteCase(record)
            }}
            onKeyDown={(event) => event.stopPropagation()}
          />
        </div>
      </div>
    )
  }

  const runContextContent = (
    <div className="scenario-context-popover">
      <div className="scenario-context-field">
        <Text className="designer-label">Environment</Text>
        <Select
          allowClear
          placeholder="Select environment"
          value={environmentId}
          options={(environmentsQuery.data ?? []).map((environment) => ({
            label: environment.name,
            value: environment.id,
          }))}
          onChange={setEnvironmentId}
        />
      </div>
      <div className="scenario-context-field">
        <Text className="designer-label">Profile</Text>
        <Select
          allowClear
          placeholder="Select profile"
          value={profileId}
          options={(profilesQuery.data ?? []).map((profile) => ({
            label: profile.name,
            value: profile.id,
          }))}
          onChange={setProfileId}
        />
      </div>
    </div>
  )

  return (
    <div className="scenario-page">
      <div className="scenario-shell">
        <aside className="scenario-sidebar">
          <div className="scenario-sidebar-head">
            <Text className="scenario-nav-label">Scenario Explorer</Text>
            <Button type="text" size="small" icon={<PlusOutlined />} onClick={openCreateCaseModal} disabled={!selectedProjectId} />
          </div>

          <Input
            className="scenario-search"
            allowClear
            placeholder="Search"
            value={caseFilter}
            onChange={(event) => setCaseFilter(event.target.value)}
          />

          {casesQuery.isError ? (
            <Alert className="section-alert" type="warning" showIcon message="Unable to load cases from platform-api." />
          ) : null}

          <div className="scenario-case-list scenario-tree">
            {casesQuery.isLoading ? <div className="scenario-muted-row">Loading cases...</div> : null}
            {!casesQuery.isLoading && filteredCases.length === 0 ? <div className="scenario-muted-row">No cases yet.</div> : null}
            {explorerNodes.map((node) => renderExplorerNode(node))}
          </div>
        </aside>

        <main className="scenario-main">
          <section className="scenario-topbar">
            <div className="scenario-titlebar">
              <div className="scenario-title-copy">
                {draft.folderPath ? <Text type="secondary" className="scenario-breadcrumb">{formatFolderBreadcrumb(draft.folderPath)}</Text> : null}
                <Input
                  className="scenario-title-input"
                  htmlSize={Math.max(8, Math.min(draft.name.length + 1, 36))}
                  value={draft.name}
                  placeholder="Scenario name"
                  variant="borderless"
                  onBlur={(event) => {
                    if (isCancellingTitleEditRef.current) {
                      isCancellingTitleEditRef.current = false
                      return
                    }
                    autoSaveTitle(event.target.value)
                  }}
                  onChange={(event) => setDraft({ ...draft, name: event.target.value })}
                  onFocus={() => {
                    titleBeforeEditRef.current = draft.name
                  }}
                  onKeyDown={(event) => {
                    if (event.key !== 'Escape') {
                      return
                    }
                    event.preventDefault()
                    isCancellingTitleEditRef.current = true
                    setDraft((current) => ({ ...current, name: titleBeforeEditRef.current }))
                    event.currentTarget.blur()
                  }}
                  onPressEnter={(event) => event.currentTarget.blur()}
                />
              </div>
              <Text className={isDraftDirty ? 'scenario-draft-state dirty' : 'scenario-draft-state'}>
                {saveTitleMutation.isPending ? 'Saving...' : isDraftDirty ? 'Unsaved' : 'Saved'}
              </Text>
            </div>

            <Space wrap size={[8, 8]} className="scenario-toolbar-actions">
              <Popover placement="bottomRight" title="Run context" content={runContextContent} trigger="click">
                <Button className="scenario-run-context" icon={<ControlOutlined />}>
                  <span className="scenario-run-context-copy">
                    <span>Run context</span>
                    <span>{selectedEnvironment?.name ?? 'No environment'} · {selectedProfile?.name ?? 'No profile'}</span>
                  </span>
                  <DownOutlined className="scenario-run-context-chevron" />
                </Button>
              </Popover>
              <Button
                icon={<SaveOutlined />}
                disabled={!isDraftDirty}
                loading={saveCaseMutation.isPending}
                onClick={() => void saveCaseMutation.mutateAsync()}
              >
                Save
              </Button>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                loading={runMutation.isPending}
                disabled={!selectedCase || isCreating || isDraftDirty}
                title={isDraftDirty ? 'Save changes before running this scenario' : 'Run scenario'}
                onClick={() => void runMutation.mutateAsync()}
              >
                Run
              </Button>
            </Space>
          </section>

          {saveCaseMutation.isError ? (
            <Alert className="section-alert" type="error" showIcon message="Failed to save case." />
          ) : null}
          {runMutation.isError ? (
            <Alert className="section-alert" type="error" showIcon message="Failed to start run." />
          ) : null}
          {deleteCaseMutation.isError ? (
            <Alert className="section-alert" type="error" showIcon message="Failed to delete case." />
          ) : null}

          <CaseDesigner
            draft={draft}
            onChange={setDraft}
            operations={operationsQuery.data ?? []}
            view={selectedEditorView}
            activeStepIndex={activeStepIndex}
            onSelectCaseOverview={handleSelectCaseOverview}
            onSelectStep={handleSelectDraftStep}
            onActiveStepChange={setSelectedEvidenceStepIndex}
          />

          <section
            className={isResultsCollapsed ? 'scenario-response-panel collapsed' : 'scenario-response-panel'}
            style={isResultsCollapsed ? undefined : { flexBasis: resultsPanelHeight }}
          >
            {!isResultsCollapsed ? <div className="scenario-results-resize-handle" onMouseDown={startResultsResize} /> : null}
            <div className="scenario-response-head">
              <Space>
                <Text strong>Results</Text>
                {latestCaseRun ? <StatusPill status={latestCaseRun.status} /> : <Text type="secondary">No runs yet</Text>}
              </Space>
              <Space size={4}>
                {latestCaseRun ? <Text type="secondary">{latestCaseRun.durationMs} ms</Text> : null}
                <Button
                  type="text"
                  size="small"
                  aria-label={isResultsCollapsed ? 'Expand results' : 'Collapse results'}
                  icon={isResultsCollapsed ? <UpOutlined /> : <DownOutlined />}
                  title={isResultsCollapsed ? 'Expand results' : 'Collapse results'}
                  onClick={() => setIsResultsCollapsed((collapsed) => !collapsed)}
                />
              </Space>
            </div>

            {!isResultsCollapsed && latestCaseRun ? (
              <div className="scenario-response-detail">
                <RunDetail
                  run={latestCaseRun}
                  selectedStepIndex={selectedEvidenceStepIndex}
                  onSelectStep={setSelectedEvidenceStepIndex}
                />
              </div>
            ) : !isResultsCollapsed ? (
              <div className="scenario-response-empty">
                <Text type="secondary">Run this scenario to inspect requests, responses, and validations.</Text>
              </div>
            ) : null}
          </section>
        </main>

      </div>
      <Modal
        title="Move Scenario"
        open={Boolean(caseToMove)}
        okText="Move"
        okButtonProps={{ loading: moveCaseMutation.isPending }}
        onCancel={() => setCaseToMove(undefined)}
        onOk={() => void moveCaseMutation.mutateAsync()}
      >
        <div className="api-import-stack">
          <Text type="secondary">{caseToMove?.name}</Text>
          <AutoComplete
            allowClear
            value={moveFolderPath}
            options={folderPaths.map((folderPath) => ({
              value: folderPath,
              label: formatFolderBreadcrumb(folderPath),
            }))}
            placeholder="Folder path, or leave empty for root"
            onChange={setMoveFolderPath}
          />
        </div>
      </Modal>

      <Modal
        title="Create Case"
        open={isCreateModalOpen}
        onCancel={() => setIsCreateModalOpen(false)}
        onOk={handleCreateCase}
        okText="Create"
      >
        <div className="api-import-stack">
          <Text type="secondary">Choose an imported operation to create the first step of the case.</Text>
          <Select
            showSearch
            allowClear
            placeholder="Select operation"
            optionFilterProp="label"
            value={createOperationId}
            options={(operationsQuery.data ?? []).map((operation) => ({
              label: `${operation.method} ${operation.path} - ${operation.summary || operation.operationId || 'Operation'}`,
              value: operation.id,
            }))}
            onChange={setCreateOperationId}
          />
        </div>
      </Modal>
    </div>
  )
}

function createDraftFromOperation(operation: ApiOperationRecord): SaveCaseInput {
  const content = stringifyDsl({
    dslVersion: 'v1',
    name: operation.summary || operation.operationId || 'New API case',
    description: '',
    tags: operation.tagName ? [operation.tagName] : [],
    runtime: {},
    variables: {},
    steps: [
      {
        name: operation.path,
        type: 'http',
        operation: {
          id: operation.id,
          apiSpecId: operation.apiSpecId,
          apiSpecName: operation.apiSpecName,
          operationId: operation.operationId,
          summary: operation.summary,
          service: operation.tagName || operation.apiSpecName || 'defaultService',
          method: operation.method,
          path: operation.path,
        },
        runtime: {},
        request: {
          query: {},
          headers: {
            'Content-Type': 'application/json',
          },
          body: {},
        },
        actions: {
          beforeSend: [],
          afterReceive: [],
        },
        response: {
          status: 200,
          assertions: [
            {
              source: 'response.body',
              path: '$.code',
              operator: 'equals',
              expected: 'SUCCESS',
            },
          ],
          extract: {},
        },
      },
    ],
  })

  return {
    name: operation.summary || operation.operationId || 'New API case',
    code: slugify(operation.operationId || `${operation.method}-${operation.path}`),
    description: '',
    folderPath: operation.tagName || operation.apiSpecName || '',
    tags: operation.tagName ? [operation.tagName] : [],
    dslVersion: 'v1',
    content,
    status: 'ACTIVE',
  }
}

function normalizeDraftForSave(draft: SaveCaseInput): SaveCaseInput {
  return {
    ...draft,
    name: draft.name.trim(),
    code: draft.code.trim() || slugify(draft.name),
    description: draft.description.trim(),
    folderPath: normalizeFolderPath(draft.folderPath),
    dslVersion: draft.dslVersion || 'v1',
    status: draft.status || 'ACTIVE',
  }
}

function draftFromCase(record: CaseRecord): SaveCaseInput {
  return {
    name: record.name,
    code: record.code,
    description: record.description ?? '',
    folderPath: record.folderPath ?? '',
    tags: record.tags ?? [],
    dslVersion: record.dslVersion,
    content: record.content,
    status: record.status,
  }
}

function isCaseDraftDirty(draft: SaveCaseInput, record: CaseRecord) {
  return draft.name !== record.name
    || draft.code !== record.code
    || draft.description !== (record.description ?? '')
    || normalizeFolderPath(draft.folderPath) !== normalizeFolderPath(record.folderPath ?? '')
    || draft.dslVersion !== record.dslVersion
    || draft.content !== record.content
    || draft.status !== record.status
    || !sameStringList(draft.tags, record.tags ?? [])
}

function sameStringList(left: string[], right: string[]) {
  return left.length === right.length && left.every((value, index) => value === right[index])
}

function formatFolderBreadcrumb(folderPath: string) {
  const segments = splitFolderPath(folderPath)
  return segments.join(' / ')
}

function folderKeysForPath(folderPath?: string | null) {
  let currentPath = ''
  return splitFolderPath(folderPath).map((segment) => {
    currentPath = currentPath ? `${currentPath}/${segment}` : segment
    return `folder:${currentPath}`
  })
}

function collectFolderPaths(records: CaseRecord[]) {
  const paths = new Set<string>()
  records.forEach((record) => {
    let currentPath = ''
    splitFolderPath(record.folderPath).forEach((segment) => {
      currentPath = currentPath ? `${currentPath}/${segment}` : segment
      paths.add(currentPath)
    })
  })
  return [...paths].sort((left, right) => left.localeCompare(right))
}

type ExplorerFolderNode = {
  type: 'folder'
  key: string
  name: string
  children: ExplorerNode[]
}

type ExplorerCaseNode = {
  type: 'case'
  record: CaseRecord
  stepCount: number
}

type ExplorerNode = ExplorerFolderNode | ExplorerCaseNode

function buildCaseExplorerTree(records: CaseRecord[]) {
  const root: ExplorerFolderNode = {
    type: 'folder',
    key: 'root',
    name: 'root',
    children: [],
  }
  const folders = new Map<string, ExplorerFolderNode>([['', root]])

  records.forEach((record) => {
    const segments = splitFolderPath(record.folderPath)
    let parent = root
    let currentPath = ''

    segments.forEach((segment) => {
      currentPath = currentPath ? `${currentPath}/${segment}` : segment
      let folder = folders.get(currentPath)
      if (!folder) {
        folder = {
          type: 'folder',
          key: `folder:${currentPath}`,
          name: segment,
          children: [],
        }
        folders.set(currentPath, folder)
        parent.children.push(folder)
      }
      parent = folder
    })

    parent.children.push({
      type: 'case',
      record,
      stepCount: getCaseStepCount(record),
    })
  })

  sortExplorerNodes(root.children)
  return root.children
}

function sortExplorerNodes(nodes: ExplorerNode[]) {
  nodes.sort((left, right) => {
    if (left.type !== right.type) {
      return left.type === 'folder' ? -1 : 1
    }
    const leftName = left.type === 'folder' ? left.name : left.record.name
    const rightName = right.type === 'folder' ? right.name : right.record.name
    return leftName.localeCompare(rightName)
  })

  nodes.forEach((node) => {
    if (node.type === 'folder') {
      sortExplorerNodes(node.children)
    }
  })
}

function splitFolderPath(folderPath?: string | null) {
  return normalizeFolderPath(folderPath ?? '')
    .split('/')
    .filter(Boolean)
}

function normalizeFolderPath(value: string) {
  return value
    .replace(/\\/g, '/')
    .split('/')
    .map((segment) => segment.trim())
    .filter(Boolean)
    .join('/')
}

function getCaseStepCount(record: CaseRecord) {
  const parsed = parseDsl(record.content)
  const steps = Array.isArray(parsed.data?.steps) ? parsed.data.steps : []
  return steps.length
}

function slugify(value: string) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60) || 'api-case'
}
