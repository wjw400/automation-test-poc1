import {
  Alert,
  App,
  Button,
  Dropdown,
  Input,
  Modal,
  Select,
  Space,
  Typography,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ControlOutlined,
  DeleteOutlined,
  DownOutlined,
  EditOutlined,
  FileTextOutlined,
  FolderOutlined,
  FolderOpenOutlined,
  MoreOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  RightOutlined,
  SaveOutlined,
} from '@ant-design/icons'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { CaseDesigner } from '../components/CaseDesigner'
import { ScenarioRunHistory } from '../components/ScenarioRunHistory'
import { platformApi } from '../api/platformApi'
import { parseDsl, stringifyDsl } from '../utils/caseDsl'
import type {
  CaseRecord,
  FolderRecord,
  RunRecord,
  SaveCaseInput,
} from '../types/platform'

const { Text } = Typography

const emptyDraft: SaveCaseInput = {
  name: '',
  code: '',
  description: '',
  folderId: undefined,
  defaultEnvironmentId: undefined,
  defaultProfileId: undefined,
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

type CasesPageProps = {
  embedded?: boolean
  projectId?: string
  caseId?: string
  onDirtyChange?: (dirty: boolean) => void
  onTitleChange?: (title: string) => void
  onSaveReady?: (save?: () => Promise<void>) => void
  onDeleted?: () => void
}

export function CasesPage({
  embedded = false,
  projectId,
  caseId,
  onDirtyChange,
  onTitleChange,
  onSaveReady,
  onDeleted,
}: CasesPageProps = {}) {
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [draft, setDraft] = useState<SaveCaseInput>(emptyDraft)
  const [draftCaseId, setDraftCaseId] = useState<string>()
  const [batchEnvironmentIds, setBatchEnvironmentIds] = useState<string[]>([])
  const [batchProfileId, setBatchProfileId] = useState<string>()
  const [isBatchRunModalOpen, setIsBatchRunModalOpen] = useState(false)
  const [isCreating, setIsCreating] = useState(false)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [createCaseName, setCreateCaseName] = useState('')
  const [createCaseFolderId, setCreateCaseFolderId] = useState<string>()
  const [caseToMove, setCaseToMove] = useState<CaseRecord>()
  const [moveFolderId, setMoveFolderId] = useState<string>()
  const [folderDialog, setFolderDialog] = useState<{
    mode: 'create' | 'rename' | 'move'
    folder?: FolderRecord
    parentFolderId?: string
  }>()
  const [folderName, setFolderName] = useState('')
  const [folderParentId, setFolderParentId] = useState<string>()
  const [scenarioToRename, setScenarioToRename] = useState<CaseRecord>()
  const [scenarioName, setScenarioName] = useState('')
  const [caseFilter, setCaseFilter] = useState('')
  const [expandedFolderKeys, setExpandedFolderKeys] = useState<Set<string>>(() => new Set())
  const [runResultOverride, setRunResultOverride] = useState<RunRecord>()
  const [activeAssetView, setActiveAssetView] = useState<'definition' | 'runs'>('definition')
  const [selectedRunId, setSelectedRunId] = useState<string>()
  const [variablesJsonError, setVariablesJsonError] = useState<string | null>(null)
  const [embeddedStepIndex, setEmbeddedStepIndex] = useState<number>()
  const titleBeforeEditRef = useRef('')
  const isCancellingTitleEditRef = useRef(false)

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const selectedProjectId = projectId ?? searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id
  const selectedCaseId = caseId ?? searchParams.get('caseId')
  const selectedStepParam = embedded
    ? embeddedStepIndex == null ? null : String(embeddedStepIndex)
    : searchParams.get('step')

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

  const foldersQuery = useQuery({
    queryKey: ['folders', selectedProjectId],
    queryFn: () => platformApi.listFolders(selectedProjectId!),
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

  const suiteRunsQuery = useQuery({
    queryKey: ['suite-runs'],
    queryFn: platformApi.listSuiteRuns,
    refetchInterval: 15000,
  })

  const selectedCase = useMemo(
    () => casesQuery.data?.find((item) => item.id === selectedCaseId) ?? casesQuery.data?.[0],
    [casesQuery.data, selectedCaseId],
  )

  const folderNamesById = useMemo(
    () => new Map((foldersQuery.data ?? []).map((folder) => [folder.id, formatFolderBreadcrumb(folder.id, foldersQuery.data ?? [])])),
    [foldersQuery.data],
  )

  const filteredCases = useMemo(() => {
    const keyword = caseFilter.trim().toLowerCase()
    if (!keyword) {
      return casesQuery.data ?? []
    }
    return (casesQuery.data ?? []).filter((record) => {
      const haystack = [record.name, record.code, record.description, folderNamesById.get(record.folderId ?? ''), ...(record.tags ?? [])]
        .join(' ')
        .toLowerCase()
      return haystack.includes(keyword)
    })
  }, [caseFilter, casesQuery.data, folderNamesById])

  const activeEnvironments = useMemo(
    () => (environmentsQuery.data ?? []).filter((environment) => environment.status === 'ACTIVE'),
    [environmentsQuery.data],
  )
  const selectedEnvironment = useMemo(
    () => activeEnvironments.find((environment) => environment.id === draft.defaultEnvironmentId),
    [activeEnvironments, draft.defaultEnvironmentId],
  )
  const compatibleProfiles = useMemo(
    () => (profilesQuery.data ?? []).filter((profile) => (
      profile.enabled && selectedEnvironment && profile.environmentIds.includes(selectedEnvironment.id)
    )),
    [profilesQuery.data, selectedEnvironment],
  )
  const selectedProfile = useMemo(
    () => compatibleProfiles.find((profile) => profile.id === draft.defaultProfileId),
    [compatibleProfiles, draft.defaultProfileId],
  )

  const selectedProject = useMemo(
    () => (projectsQuery.data ?? []).find((project) => project.id === selectedProjectId),
    [projectsQuery.data, selectedProjectId],
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

  useEffect(() => {
    setVariablesJsonError(null)
  }, [isCreating, selectedCase?.id])

  const parsedDraft = useMemo(() => parseDsl(draft.content), [draft.content])
  const draftSteps = Array.isArray(parsedDraft.data?.steps) ? parsedDraft.data.steps : []
  const requestedStepIndex = selectedStepParam == null ? -1 : Number(selectedStepParam)
  const activeStepIndex = Number.isInteger(requestedStepIndex)
    ? Math.min(Math.max(requestedStepIndex, 0), Math.max(draftSteps.length - 1, 0))
    : 0
  const selectedEditorView = selectedStepParam != null && draftSteps[activeStepIndex] ? 'step' : 'case'
  const scenarioRuns = useMemo(() => {
    if (!selectedCase?.id) {
      return []
    }
    const records = [...(runsQuery.data ?? [])]
    if (runResultOverride && !records.some((run) => run.id === runResultOverride.id)) {
      records.push(runResultOverride)
    }
    return records
      .filter((run) => run.caseId === selectedCase.id)
      .sort((left, right) => new Date(right.startedAt).getTime() - new Date(left.startedAt).getTime())
  }, [runResultOverride, runsQuery.data, selectedCase?.id])

  const suiteNamesById = useMemo(
    () => new Map((suiteRunsQuery.data ?? []).map((run) => [run.id, run.suiteName])),
    [suiteRunsQuery.data],
  )

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

  useEffect(() => {
    onDirtyChange?.(isDraftDirty || Boolean(variablesJsonError))
  }, [isDraftDirty, onDirtyChange, variablesJsonError])

  useEffect(() => {
    if (draft.name) {
      onTitleChange?.(draft.name)
    }
  }, [draft.name, onTitleChange])

  const explorerNodes = useMemo(() => {
    const records = filteredCases.map((record) => {
      if (record.id !== selectedCase?.id || isCreating) {
        return record
      }
      return {
        ...record,
        content: draft.content,
        folderId: draft.folderId,
      }
    })
    return buildCaseExplorerTree(foldersQuery.data ?? [], records)
  }, [draft.content, draft.folderId, filteredCases, foldersQuery.data, isCreating, selectedCase?.id])

  const selectedFolderKeys = useMemo(
    () => folderKeysForFolderId(selectedCase?.id && !isCreating ? draft.folderId : undefined, foldersQuery.data ?? []),
    [draft.folderId, foldersQuery.data, isCreating, selectedCase?.id],
  )

  const folderOptions = useMemo(
    () => buildFolderOptions(foldersQuery.data ?? []),
    [foldersQuery.data],
  )

  const currentFolderBreadcrumb = useMemo(
    () => formatFolderBreadcrumb(draft.folderId, foldersQuery.data ?? []),
    [draft.folderId, foldersQuery.data],
  )

  useEffect(() => {
    setActiveAssetView('definition')
    setSelectedRunId(undefined)
  }, [selectedCase?.id])

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
      if (variablesJsonError) {
        throw new Error(`Fix Variables JSON before saving: ${variablesJsonError}`)
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
      if (!embedded) {
        setSearchParams({
          projectId: selectedProjectId!,
          caseId: savedCase.id,
          ...(selectedEditorView === 'step' ? { step: String(activeStepIndex) } : {}),
        })
      }
      void message.success('Case saved')
    },
  })

  useEffect(() => {
    onSaveReady?.(async () => {
      await saveCaseMutation.mutateAsync()
    })
    return () => onSaveReady?.(undefined)
  }, [onSaveReady, saveCaseMutation])

  const deleteCaseMutation = useMutation({
    mutationFn: platformApi.deleteCase,
    onSuccess: async (_result, deletedCaseId) => {
      const remainingCases = (casesQuery.data ?? []).filter((record) => record.id !== deletedCaseId)
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cases', selectedProjectId] }),
        queryClient.invalidateQueries({ queryKey: ['suites', selectedProjectId] }),
      ])

      if (selectedCase?.id === deletedCaseId) {
        if (embedded) {
          onDeleted?.()
          return
        }
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
          folderId: moveFolderId,
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
          content: renameScenarioInDsl(record.content, name),
        }),
      )
    },
    onSuccess: async (savedCase) => {
      await queryClient.invalidateQueries({ queryKey: ['cases', selectedProjectId] })
      if (savedCase.id === selectedCase?.id) {
        setDraft(draftFromCase(savedCase))
        setDraftCaseId(savedCase.id)
      }
      setScenarioToRename(undefined)
    },
    onError: () => {
      void message.error('Unable to save scenario name')
    },
  })

  const saveFolderMutation = useMutation({
    mutationFn: async () => {
      if (!selectedProjectId || !folderDialog) {
        throw new Error('Select a folder first.')
      }
      const name = folderName.trim()
      if (!name) {
        throw new Error('Folder name is required.')
      }
      const payload = {
        name,
        parentFolderId: folderDialog.mode === 'move' ? folderParentId : folderDialog.parentFolderId ?? folderDialog.folder?.parentFolderId ?? undefined,
      }
      return folderDialog.folder
        ? platformApi.updateFolder(selectedProjectId, folderDialog.folder.id, payload)
        : platformApi.createFolder(selectedProjectId, payload)
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['folders', selectedProjectId] })
      setFolderDialog(undefined)
      void message.success('Folder saved')
    },
    onError: (error) => {
      void message.error(error instanceof Error ? error.message : 'Unable to save folder')
    },
  })

  const deleteFolderMutation = useMutation({
    mutationFn: platformApi.deleteFolder,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['folders', selectedProjectId] })
      void message.success('Folder deleted')
    },
    onError: (error) => {
      void message.error(error instanceof Error ? error.message : 'Folder is not empty')
    },
  })

  const duplicateCaseMutation = useMutation({
    mutationFn: async (record: CaseRecord) => {
      if (!selectedProjectId) {
        throw new Error('Select a project first.')
      }
      return platformApi.createCase(selectedProjectId, normalizeDraftForSave({
        ...draftFromCase(record),
        name: `${record.name} copy`,
        code: `${record.code.slice(0, 75)}-copy-${Date.now()}`,
      }))
    },
    onSuccess: async (savedCase) => {
      await queryClient.invalidateQueries({ queryKey: ['cases', selectedProjectId] })
      setSearchParams({ projectId: selectedProjectId!, caseId: savedCase.id })
      void message.success('Scenario duplicated')
    },
  })

  const runMutation = useMutation({
    mutationFn: async ({
      caseId,
      executions,
    }: {
      caseId?: string
      executions?: Array<{ environmentId: string; profileId: string }>
    } = {}) => {
      const targetCaseId = caseId ?? selectedCase?.id
      if (!targetCaseId) {
        throw new Error('Save the case before running it.')
      }
      const runs: RunRecord[] = []
      const selectedExecutions = executions?.length ? executions : [undefined]
      for (const execution of selectedExecutions) {
        runs.push(await platformApi.startRun({
          caseId: targetCaseId,
          ...(execution
            ? {
                environmentId: execution.environmentId,
                profileId: execution.profileId,
              }
            : {}),
          triggeredBy: 'platform-ui',
        }))
      }
      return runs
    },
    onSuccess: async (runs) => {
      await queryClient.invalidateQueries({ queryKey: ['runs'] })
      const run = runs.at(-1)
      if (!run) {
        return
      }
      setRunResultOverride(run)
      setSelectedRunId(run.id)
      setActiveAssetView('runs')
      void message.success(runs.length === 1
        ? `Run ${run.status.toLowerCase()}`
        : `${runs.length} environments completed`)
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
    if (embedded) {
      setEmbeddedStepIndex(stepIndex)
      return
    }
    setSearchParams({ projectId: selectedProjectId, caseId: selectedCase.id, step: String(stepIndex) })
  }

  const handleSelectCaseOverview = () => {
    if (!selectedProjectId || !selectedCase) {
      return
    }
    if (embedded) {
      setEmbeddedStepIndex(undefined)
      return
    }
    setSearchParams({ projectId: selectedProjectId, caseId: selectedCase.id })
  }

  const confirmDeleteCase = (record: CaseRecord) => {
    modal.confirm({
      title: 'Delete case?',
      content: `This will remove "${record.name}" from the case library and suites. Execution history is kept.`,
      okText: 'Delete',
      okButtonProps: { danger: true },
      cancelText: 'Cancel',
      onOk: () => deleteCaseMutation.mutateAsync(record.id),
    })
  }

  const openCreateCaseModal = (folderId?: string) => {
    if (!selectedProjectId) {
      return
    }
    setCreateCaseName('')
    setCreateCaseFolderId(folderId)
    setIsCreateModalOpen(true)
  }

  const handleCreateCase = () => {
    const name = createCaseName.trim()
    if (!name) {
      void message.warning('Scenario name is required')
      return
    }
    setDraft(createEmptyDraft(name, createCaseFolderId))
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
    setMoveFolderId(record.folderId ?? undefined)
  }

  const openFolderDialog = (mode: 'create' | 'rename' | 'move', folder?: FolderRecord, parentFolderId?: string) => {
    setFolderDialog({ mode, folder, parentFolderId })
    setFolderName(mode === 'create' ? '' : folder?.name ?? '')
    setFolderParentId(mode === 'move' ? folder?.parentFolderId ?? undefined : parentFolderId ?? folder?.parentFolderId ?? undefined)
  }

  const confirmDeleteFolder = (folder: FolderRecord) => {
    modal.confirm({
      title: 'Delete folder?',
      content: `Delete "${folder.name}"? Folders containing scenarios or subfolders cannot be deleted.`,
      okText: 'Delete',
      okButtonProps: { danger: true },
      cancelText: 'Cancel',
      onOk: () => deleteFolderMutation.mutateAsync(folder.id),
    })
  }

  const openRenameScenario = (record: CaseRecord) => {
    if (record.id === selectedCase?.id && isDraftDirty) {
      void message.warning('Save changes before renaming this scenario.')
      return
    }
    setScenarioToRename(record)
    setScenarioName(record.name)
  }

  const handleRenameScenario = () => {
    if (!scenarioToRename) {
      return
    }
    const name = scenarioName.trim()
    if (!name) {
      void message.warning('Scenario name is required')
      return
    }
    void saveTitleMutation.mutateAsync({ record: scenarioToRename, name })
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

  const renderExplorerNode = (node: ExplorerNode, depth = 0) => {
    if (node.type === 'folder') {
      const isExpanded = Boolean(caseFilter.trim()) || selectedFolderKeys.includes(node.key) || expandedFolderKeys.has(node.key)
      const folder = node.folder
      return (
        <div key={node.key} className="scenario-folder-group">
          <div className="scenario-folder-row" style={{ paddingLeft: 8 + depth * 14 }}>
            <button
              type="button"
              className="scenario-tree-primary"
              aria-expanded={isExpanded}
              onClick={() => toggleFolder(node.key)}
            >
              {isExpanded ? <DownOutlined className="scenario-folder-toggle" /> : <RightOutlined className="scenario-folder-toggle" />}
              <FolderOpenOutlined className="scenario-tree-icon" />
              <span className="scenario-folder-name">{folder.name}</span>
            </button>
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  { key: 'scenario', label: 'Add Test Scenario', icon: <FileTextOutlined /> },
                  { key: 'folder', label: 'Add Folder', icon: <FolderOutlined /> },
                ],
                onClick: ({ key }) => key === 'scenario'
                  ? openCreateCaseModal(folder.id)
                  : openFolderDialog('create', undefined, folder.id),
              }}
            >
              <Button className="scenario-tree-action" icon={<PlusOutlined />} size="small" type="text" title={`Add to ${folder.name}`} />
            </Dropdown>
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  { key: 'rename', label: 'Rename', icon: <EditOutlined /> },
                  { key: 'move', label: 'Move', icon: <FolderOutlined /> },
                  { type: 'divider' },
                  { key: 'delete', label: 'Delete', icon: <DeleteOutlined />, danger: true },
                ],
                onClick: ({ key }) => {
                  if (key === 'rename') openFolderDialog('rename', folder)
                  if (key === 'move') openFolderDialog('move', folder)
                  if (key === 'delete') confirmDeleteFolder(folder)
                },
              }}
            >
              <Button className="scenario-tree-action" icon={<MoreOutlined />} size="small" type="text" title={`Manage ${folder.name}`} />
            </Dropdown>
          </div>
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
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                {
                  key: 'run',
                  label: 'Run',
                  icon: <PlayCircleOutlined />,
                  disabled: !record.defaultEnvironmentId || !record.defaultProfileId,
                },
                { key: 'rename', label: 'Rename', icon: <EditOutlined /> },
                { key: 'duplicate', label: 'Duplicate', icon: <FileTextOutlined /> },
                { key: 'move', label: 'Move', icon: <FolderOutlined /> },
                { type: 'divider' },
                { key: 'delete', label: 'Delete', icon: <DeleteOutlined />, danger: true },
              ],
              onClick: ({ key }) => {
                if (key === 'run') void runMutation.mutateAsync({ caseId: record.id })
                if (key === 'rename') openRenameScenario(record)
                if (key === 'duplicate') void duplicateCaseMutation.mutateAsync(record)
                if (key === 'move') openMoveScenario(record)
                if (key === 'delete') confirmDeleteCase(record)
              },
            }}
          >
            <Button
              aria-label={`Manage ${record.name}`}
              className="scenario-tree-action"
              icon={<MoreOutlined />}
              loading={deleteCaseMutation.isPending && deleteCaseMutation.variables === record.id}
              size="small"
              title={`Manage ${record.name}`}
              type="text"
              onClick={(event) => event.stopPropagation()}
              onKeyDown={(event) => event.stopPropagation()}
            />
          </Dropdown>
        </div>
      </div>
    )
  }

  const selectEnvironment = (environmentId: string) => {
    const currentProfileIsCompatible = Boolean(
      draft.defaultProfileId
      && (profilesQuery.data ?? []).find(
        (profile) => profile.id === draft.defaultProfileId
          && profile.enabled
          && profile.environmentIds.includes(environmentId),
      ),
    )
    setDraft({
      ...draft,
      defaultEnvironmentId: environmentId,
      defaultProfileId: currentProfileIsCompatible ? draft.defaultProfileId : undefined,
    })
  }

  const selectProfile = (profileId: string) => {
    setDraft({ ...draft, defaultProfileId: profileId })
  }

  const openBatchRunModal = () => {
    setBatchProfileId(selectedProfile?.id)
    setBatchEnvironmentIds(selectedEnvironment ? [selectedEnvironment.id] : [])
    setIsBatchRunModalOpen(true)
  }

  const batchProfile = (profilesQuery.data ?? []).find((profile) => profile.id === batchProfileId)
  const batchEnvironments = activeEnvironments.filter(
    (environment) => batchProfile?.environmentIds.includes(environment.id),
  )

  return (
    <div className={embedded ? 'scenario-page embedded' : 'scenario-page'}>
      <div className={embedded ? 'scenario-shell embedded' : 'scenario-shell'}>
        {embedded ? null : <aside className="scenario-sidebar">
          <div className="scenario-root-row">
            <button type="button" className="scenario-tree-primary scenario-root-primary">
              <DownOutlined className="scenario-folder-toggle" />
              <FolderOpenOutlined className="scenario-tree-icon" />
              <span className="scenario-folder-name">{selectedProject?.name ?? 'Project'}</span>
            </button>
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  { key: 'scenario', label: 'Add Test Scenario', icon: <FileTextOutlined /> },
                  { key: 'folder', label: 'Add Folder', icon: <FolderOutlined /> },
                ],
                onClick: ({ key }) => key === 'scenario'
                  ? openCreateCaseModal()
                  : openFolderDialog('create'),
              }}
            >
              <Button type="text" size="small" className="scenario-tree-action" icon={<PlusOutlined />} disabled={!selectedProjectId} title="Add" />
            </Dropdown>
            <Dropdown
              trigger={['click']}
              menu={{
                items: [
                  { key: 'scenario', label: 'Add Test Scenario', icon: <FileTextOutlined /> },
                  { key: 'folder', label: 'Add Folder', icon: <FolderOutlined /> },
                ],
                onClick: ({ key }) => key === 'scenario'
                  ? openCreateCaseModal()
                  : openFolderDialog('create'),
              }}
            >
              <Button type="text" size="small" className="scenario-tree-action" icon={<MoreOutlined />} disabled={!selectedProjectId} title="Project actions" />
            </Dropdown>
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
            {!casesQuery.isLoading && !foldersQuery.isLoading && explorerNodes.length === 0 ? <div className="scenario-muted-row">No scenarios or folders yet.</div> : null}
            {explorerNodes.map((node) => renderExplorerNode(node))}
          </div>
        </aside>}

        <main className="scenario-main">
          <section className="scenario-topbar">
            <div className="scenario-titlebar">
              <div className="scenario-title-copy">
                {currentFolderBreadcrumb ? <Text type="secondary" className="scenario-breadcrumb">{currentFolderBreadcrumb}</Text> : null}
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
              <Text className={variablesJsonError ? 'scenario-draft-state invalid' : isDraftDirty ? 'scenario-draft-state dirty' : 'scenario-draft-state'}>
                {variablesJsonError ? 'Invalid JSON' : saveTitleMutation.isPending ? 'Saving...' : isDraftDirty ? 'Unsaved' : 'Saved'}
              </Text>
            </div>

            <Space wrap size={[8, 8]} className="scenario-toolbar-actions">
              <div
                className="scenario-execution-picker"
                title="Scenario default execution context"
              >
                <ControlOutlined />
                <Select
                  aria-label="Environment"
                  value={selectedEnvironment?.id}
                  placeholder="Environment"
                  popupMatchSelectWidth={false}
                  options={activeEnvironments.map((environment) => ({
                    label: environment.name,
                    value: environment.id,
                  }))}
                  onChange={selectEnvironment}
                />
                <Select
                  aria-label="Profile"
                  value={selectedProfile?.id}
                  placeholder={selectedEnvironment ? 'Profile' : 'Select environment first'}
                  popupMatchSelectWidth={false}
                  disabled={!selectedEnvironment}
                  options={compatibleProfiles.map((profile) => ({
                    label: profile.name,
                    value: profile.id,
                  }))}
                  onChange={selectProfile}
                />
              </div>
              <Button
                icon={<SaveOutlined />}
                disabled={!isDraftDirty || Boolean(variablesJsonError)}
                loading={saveCaseMutation.isPending}
                title={variablesJsonError ? 'Fix Variables JSON before saving' : 'Save scenario'}
                onClick={() => void saveCaseMutation.mutateAsync()}
              >
                Save
              </Button>
              <Space.Compact>
                <Button
                  type="primary"
                  icon={<PlayCircleOutlined />}
                  loading={runMutation.isPending}
                  disabled={!selectedCase || !selectedEnvironment || !selectedProfile || isCreating || isDraftDirty || Boolean(variablesJsonError)}
                  title={variablesJsonError ? 'Fix Variables JSON before running this scenario' : isDraftDirty ? 'Save changes before running this scenario' : 'Run selected environment and profile'}
                  onClick={() => void runMutation.mutateAsync({})}
                >
                  Run
                </Button>
                <Dropdown
                  trigger={['click']}
                  menu={{
                    items: [{
                      key: 'multiple',
                      label: 'Run in multiple environments...',
                      disabled: !(profilesQuery.data ?? []).some((profile) => (
                        profile.enabled && profile.environmentIds.length > 0
                      )),
                    }],
                    onClick: ({ key }) => {
                      if (key === 'multiple') {
                        openBatchRunModal()
                      }
                    },
                  }}
                >
                  <Button
                    type="primary"
                    aria-label="More run options"
                    icon={<DownOutlined />}
                    disabled={!selectedCase || isCreating || isDraftDirty || Boolean(variablesJsonError) || runMutation.isPending}
                    title="More run options"
                  />
                </Dropdown>
              </Space.Compact>
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

          <nav className="asset-context-tabs" aria-label="Scenario sections">
            <button
              type="button"
              className={activeAssetView === 'definition' ? 'active' : ''}
              onClick={() => setActiveAssetView('definition')}
            >
              Definition
            </button>
            <button
              type="button"
              className={activeAssetView === 'runs' ? 'active' : ''}
              onClick={() => setActiveAssetView('runs')}
            >
              Executions <span>{scenarioRuns.length}</span>
            </button>
          </nav>

          <div className="asset-context-content">
            {activeAssetView === 'definition' ? (
              <CaseDesigner
                key={isCreating ? '__new__' : selectedCase?.id ?? '__empty__'}
                draft={draft}
                onChange={setDraft}
                operations={operationsQuery.data ?? []}
                view={selectedEditorView}
                activeStepIndex={activeStepIndex}
                onSelectCaseOverview={handleSelectCaseOverview}
                onSelectStep={handleSelectDraftStep}
                onVariablesValidationChange={setVariablesJsonError}
              />
            ) : (
              <ScenarioRunHistory
                runs={scenarioRuns}
                selectedRunId={selectedRunId}
                suiteNamesById={suiteNamesById}
                onSelect={setSelectedRunId}
              />
            )}
          </div>
        </main>

      </div>
      <Modal
        title="Run in Multiple Environments"
        open={isBatchRunModalOpen}
        okText={`Run ${batchEnvironmentIds.length || ''}`.trim()}
        okButtonProps={{ disabled: !batchProfileId || batchEnvironmentIds.length === 0 }}
        confirmLoading={runMutation.isPending}
        onCancel={() => setIsBatchRunModalOpen(false)}
        onOk={() => {
          if (!batchProfileId) {
            return
          }
          void runMutation.mutateAsync({
            executions: batchEnvironmentIds.map((environmentId) => ({
              environmentId,
              profileId: batchProfileId,
            })),
          }).then(() => {
            setIsBatchRunModalOpen(false)
          })
        }}
      >
        <div className="batch-run-fields">
          <Text type="secondary">
            Run the same scenario and profile against one or more compatible environments.
          </Text>
          <Select
            aria-label="Batch profile"
            placeholder="Select profile"
            value={batchProfileId}
            options={(profilesQuery.data ?? [])
              .filter((profile) => profile.enabled)
              .map((profile) => ({
                label: profile.name,
                value: profile.id,
              }))}
            onChange={(profileId) => {
              const profile = (profilesQuery.data ?? []).find((item) => item.id === profileId)
              const availableEnvironmentIds = new Set(profile?.environmentIds ?? [])
              const nextEnvironmentIds = batchEnvironmentIds.filter(
                (environmentId) => availableEnvironmentIds.has(environmentId),
              )
              setBatchProfileId(profileId)
              setBatchEnvironmentIds(nextEnvironmentIds.length
                ? nextEnvironmentIds
                : activeEnvironments
                    .filter((environment) => availableEnvironmentIds.has(environment.id))
                    .slice(0, 1)
                    .map((environment) => environment.id))
            }}
          />
          <Select
            mode="multiple"
            aria-label="Batch environments"
            placeholder="Select environments"
            value={batchEnvironmentIds}
            options={batchEnvironments.map((environment) => ({
              label: environment.name,
              value: environment.id,
            }))}
            onChange={setBatchEnvironmentIds}
          />
        </div>
      </Modal>

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
          <Select
            allowClear
            value={moveFolderId}
            options={folderOptions}
            placeholder="Root"
            onChange={setMoveFolderId}
          />
        </div>
      </Modal>

      <Modal
        title={folderDialog?.mode === 'create' ? 'Add Folder' : folderDialog?.mode === 'move' ? 'Move Folder' : 'Rename Folder'}
        open={Boolean(folderDialog)}
        okText={folderDialog?.mode === 'move' ? 'Move' : 'Save'}
        okButtonProps={{ loading: saveFolderMutation.isPending }}
        onCancel={() => setFolderDialog(undefined)}
        onOk={() => void saveFolderMutation.mutateAsync()}
      >
        <div className="api-import-stack">
          {folderDialog?.mode !== 'move' ? (
            <Input
              autoFocus
              placeholder="Folder name"
              value={folderName}
              onChange={(event) => setFolderName(event.target.value)}
              onPressEnter={() => void saveFolderMutation.mutateAsync()}
            />
          ) : (
            <>
              <Text type="secondary">{folderDialog.folder?.name}</Text>
              <Select
                allowClear
                value={folderParentId}
                options={folderOptions.filter((option) => !isFolderBlockedTarget(option.value, folderDialog.folder?.id, foldersQuery.data ?? []))}
                placeholder="Root"
                onChange={setFolderParentId}
              />
            </>
          )}
        </div>
      </Modal>

      <Modal
        title="Rename Test Scenario"
        open={Boolean(scenarioToRename)}
        okText="Save"
        okButtonProps={{ loading: saveTitleMutation.isPending }}
        onCancel={() => setScenarioToRename(undefined)}
        onOk={handleRenameScenario}
      >
        <Input
          autoFocus
          placeholder="Scenario name"
          value={scenarioName}
          onChange={(event) => setScenarioName(event.target.value)}
          onPressEnter={handleRenameScenario}
        />
      </Modal>

      <Modal
        title="Add Test Scenario"
        open={isCreateModalOpen}
        onCancel={() => setIsCreateModalOpen(false)}
        onOk={handleCreateCase}
        okText="Create"
      >
        <div className="api-import-stack">
          <Input
            autoFocus
            placeholder="Scenario name"
            value={createCaseName}
            onChange={(event) => setCreateCaseName(event.target.value)}
            onPressEnter={handleCreateCase}
          />
        </div>
      </Modal>
    </div>
  )
}

function createEmptyDraft(name: string, folderId?: string): SaveCaseInput {
  const content = stringifyDsl({
    dslVersion: 'v1',
    name,
    description: '',
    tags: [],
    runtime: {},
    variables: {},
    steps: [],
  })

  return {
    name,
    code: scenarioCode(name),
    description: '',
    folderId,
    defaultEnvironmentId: undefined,
    defaultProfileId: undefined,
    tags: [],
    dslVersion: 'v1',
    content,
    status: 'ACTIVE',
  }
}

function normalizeDraftForSave(draft: SaveCaseInput): SaveCaseInput {
  return {
    ...draft,
    name: draft.name.trim(),
    code: draft.code.trim() || scenarioCode(draft.name),
    description: draft.description.trim(),
    folderId: draft.folderId || undefined,
    dslVersion: draft.dslVersion || 'v1',
    status: draft.status || 'ACTIVE',
  }
}

function draftFromCase(record: CaseRecord): SaveCaseInput {
  return {
    name: record.name,
    code: record.code,
    description: record.description ?? '',
    folderId: record.folderId ?? undefined,
    defaultEnvironmentId: record.defaultEnvironmentId ?? undefined,
    defaultProfileId: record.defaultProfileId ?? undefined,
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
    || (draft.folderId ?? undefined) !== (record.folderId ?? undefined)
    || (draft.defaultEnvironmentId ?? undefined) !== (record.defaultEnvironmentId ?? undefined)
    || (draft.defaultProfileId ?? undefined) !== (record.defaultProfileId ?? undefined)
    || draft.dslVersion !== record.dslVersion
    || draft.content !== record.content
    || draft.status !== record.status
    || !sameStringList(draft.tags, record.tags ?? [])
}

function sameStringList(left: string[], right: string[]) {
  return left.length === right.length && left.every((value, index) => value === right[index])
}

type ExplorerFolderNode = {
  type: 'folder'
  key: string
  folder: FolderRecord
  children: ExplorerNode[]
}

type ExplorerCaseNode = {
  type: 'case'
  record: CaseRecord
  stepCount: number
}

type ExplorerNode = ExplorerFolderNode | ExplorerCaseNode

function buildCaseExplorerTree(folders: FolderRecord[], records: CaseRecord[]) {
  const root: { children: ExplorerNode[] } = { children: [] }
  const nodesById = new Map<string, ExplorerFolderNode>()

  folders.forEach((folder) => {
    nodesById.set(folder.id, {
      type: 'folder',
      key: `folder:${folder.id}`,
      folder,
      children: [],
    })
  })

  folders.forEach((folder) => {
    const node = nodesById.get(folder.id)!
    const parent = folder.parentFolderId ? nodesById.get(folder.parentFolderId) : undefined
    ;(parent?.children ?? root.children).push(node)
  })

  records.forEach((record) => {
    const parent = record.folderId ? nodesById.get(record.folderId) : undefined
    ;(parent?.children ?? root.children).push({
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
    if (left.type === 'folder' && right.type === 'folder' && left.folder.sortOrder !== right.folder.sortOrder) {
      return left.folder.sortOrder - right.folder.sortOrder
    }
    const leftName = left.type === 'folder' ? left.folder.name : left.record.name
    const rightName = right.type === 'folder' ? right.folder.name : right.record.name
    return leftName.localeCompare(rightName)
  })

  nodes.forEach((node) => {
    if (node.type === 'folder') {
      sortExplorerNodes(node.children)
    }
  })
}

function formatFolderBreadcrumb(folderId: string | undefined, folders: FolderRecord[]) {
  if (!folderId) {
    return ''
  }
  const foldersById = new Map(folders.map((folder) => [folder.id, folder]))
  const segments: string[] = []
  let current = foldersById.get(folderId)
  while (current) {
    segments.unshift(current.name)
    current = current.parentFolderId ? foldersById.get(current.parentFolderId) : undefined
  }
  return segments.join(' / ')
}

function folderKeysForFolderId(folderId: string | undefined, folders: FolderRecord[]) {
  const foldersById = new Map(folders.map((folder) => [folder.id, folder]))
  const keys: string[] = []
  let current = folderId ? foldersById.get(folderId) : undefined
  while (current) {
    keys.unshift(`folder:${current.id}`)
    current = current.parentFolderId ? foldersById.get(current.parentFolderId) : undefined
  }
  return keys
}

function buildFolderOptions(folders: FolderRecord[]) {
  const foldersByParentId = new Map<string | undefined, FolderRecord[]>()
  folders.forEach((folder) => {
    const parentId = folder.parentFolderId ?? undefined
    const siblings = foldersByParentId.get(parentId) ?? []
    siblings.push(folder)
    foldersByParentId.set(parentId, siblings)
  })
  foldersByParentId.forEach((siblings) => siblings.sort((left, right) => left.sortOrder - right.sortOrder || left.name.localeCompare(right.name)))

  const options: Array<{ value: string; label: string }> = []
  const visit = (parentId: string | undefined, prefix: string) => {
    ;(foldersByParentId.get(parentId) ?? []).forEach((folder) => {
      options.push({ value: folder.id, label: `${prefix}${folder.name}` })
      visit(folder.id, `${prefix}  `)
    })
  }
  visit(undefined, '')
  return options
}

function isFolderBlockedTarget(targetFolderId: string, movingFolderId: string | undefined, folders: FolderRecord[]) {
  if (!movingFolderId || targetFolderId === movingFolderId) {
    return targetFolderId === movingFolderId
  }
  const foldersById = new Map(folders.map((folder) => [folder.id, folder]))
  let current = foldersById.get(targetFolderId)
  while (current) {
    if (current.id === movingFolderId) {
      return true
    }
    current = current.parentFolderId ? foldersById.get(current.parentFolderId) : undefined
  }
  return false
}

function getCaseStepCount(record: CaseRecord) {
  const parsed = parseDsl(record.content)
  const steps = Array.isArray(parsed.data?.steps) ? parsed.data.steps : []
  return steps.length
}

function renameScenarioInDsl(content: string, name: string) {
  const parsed = parseDsl(content)
  if (!parsed.data) {
    return content
  }
  return stringifyDsl({ ...parsed.data, name })
}

function slugify(value: string) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60)
}

function scenarioCode(name: string) {
  return slugify(name) || `scenario-${Date.now()}`
}
