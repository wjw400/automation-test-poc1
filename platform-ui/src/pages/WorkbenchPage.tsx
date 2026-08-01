import {
  ApiOutlined,
  ApartmentOutlined,
  CloseOutlined,
  CopyOutlined,
  DeleteOutlined,
  EditOutlined,
  ExperimentOutlined,
  FileTextOutlined,
  FolderOutlined,
  FolderOpenOutlined,
  GlobalOutlined,
  KeyOutlined,
  MoreOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  ProfileOutlined,
  ReadOutlined,
  RightOutlined,
  SearchOutlined,
  UploadOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { App, Button, Dropdown, Input, Modal, Select, Typography, Upload } from 'antd'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { platformApi } from '../api/platformApi'
import {
  EnvironmentAssetEditor,
  ProfileAssetEditor,
} from '../components/ConfigAssetEditors'
import { RunDetail } from '../components/RunDetail'
import { StatusPill } from '../components/StatusPill'
import { ApiReference } from '../components/ApiReference'
import { AgentPanel } from '../components/AgentPanel'
import { PipelineCatalog } from '../components/PipelineCatalog'
import { SuiteRunDetail } from '../components/SuiteRunDetail'
import { TestKnowledgeEditor } from '../components/TestKnowledgeEditor'
import type {
  ApiSpecRecord,
  ApiSpecReferenceRecord,
  CaseRecord,
  Environment,
  FolderRecord,
  Profile,
  ResourceReferenceRecord,
  RunRecord,
  ScenarioDraftRecord,
  SaveCaseInput,
  SaveEnvironmentInput,
  SaveProfileInput,
  SaveSuiteInput,
  SuiteRecord,
  SuiteRunRecord,
  TestKnowledgeRecord,
} from '../types/platform'
import { parseDsl, stringifyDsl } from '../utils/caseDsl'
import { formatDateTime } from '../i18n'
import { CasesPage } from './CasesPage'
import { SuitesPage } from './SuitesPage'
import { inspectAgentDraftApply } from '../utils/agentDraft'

const { Text } = Typography

type WorkbenchMode = 'scenarios' | 'suites' | 'apis' | 'processing' | 'environments' | 'profiles' | 'knowledge' | 'runs'
type WorkbenchTabType = 'scenario' | 'suite' | 'apiSpec' | 'pipelineCatalog' | 'run' | 'suiteRun' | 'environment' | 'profile' | 'knowledge'

type WorkbenchTab = {
  key: string
  type: WorkbenchTabType
  resourceId: string
  title: string
  dirty: boolean
}

type FolderDialogState = {
  mode: 'create' | 'rename' | 'move'
  folder?: FolderRecord
  parentFolderId?: string
}

export type WorkspaceUnsavedGuard = {
  dirtyTabs: Array<{ key: string, title: string }>
  saveAll: () => Promise<void>
}

type WorkbenchPageProps = {
  agentPanelOpen?: boolean
  agentPanelPinned?: boolean
  onAgentPanelOpenChange?: (open: boolean) => void
  onAgentPanelPinnedChange?: (pinned: boolean) => void
  onUnsavedGuardChange?: (guard?: WorkspaceUnsavedGuard) => void
}

export function WorkbenchPage({
  agentPanelOpen = false,
  agentPanelPinned = false,
  onAgentPanelOpenChange,
  onAgentPanelPinnedChange,
  onUnsavedGuardChange,
}: WorkbenchPageProps = {}) {
  const { t } = useTranslation(['common', 'workspace', 'agent', 'scenario', 'suite', 'execution', 'config', 'api'])
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
  const [createFolderId, setCreateFolderId] = useState<string>()
  const [scenarioToRename, setScenarioToRename] = useState<CaseRecord>()
  const [scenarioName, setScenarioName] = useState('')
  const [scenarioToMove, setScenarioToMove] = useState<CaseRecord>()
  const [moveFolderId, setMoveFolderId] = useState<string>()
  const [suiteToRename, setSuiteToRename] = useState<SuiteRecord>()
  const [suiteName, setSuiteName] = useState('')
  const [environmentToRename, setEnvironmentToRename] = useState<Environment>()
  const [environmentName, setEnvironmentName] = useState('')
  const [profileToRename, setProfileToRename] = useState<Profile>()
  const [profileName, setProfileName] = useState('')
  const [isImportApiSpecOpen, setIsImportApiSpecOpen] = useState(false)
  const [folderDialog, setFolderDialog] = useState<FolderDialogState>()
  const [folderName, setFolderName] = useState('')
  const [folderParentId, setFolderParentId] = useState<string>()
  const [pendingAgentDrafts, setPendingAgentDrafts] = useState<Record<string, ScenarioDraftRecord>>({})
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
  const operationsQuery = useQuery({
    queryKey: ['operations', projectId],
    queryFn: () => platformApi.listOperations(projectId!),
    enabled: Boolean(projectId),
  })
  const testKnowledgeQuery = useQuery({
    queryKey: ['test-knowledge', projectId],
    queryFn: () => platformApi.listTestKnowledge(projectId!),
    enabled: Boolean(projectId),
  })
  const runsQuery = useQuery({
    queryKey: ['runs'],
    queryFn: platformApi.listRuns,
    refetchInterval: (query) => query.state.data?.some((run) => run.status === 'RUNNING') ? 3000 : false,
  })
  const suiteRunsQuery = useQuery({
    queryKey: ['suite-runs'],
    queryFn: platformApi.listSuiteRuns,
    refetchInterval: (query) => query.state.data?.some((run) => run.status === 'RUNNING') ? 3000 : false,
  })

  useEffect(() => {
    saveHandlersRef.current.clear()
    setTabs([])
    setActiveTabKey(undefined)
    setCreateDialog(undefined)
    setCreateFolderId(undefined)
    setScenarioToRename(undefined)
    setScenarioToMove(undefined)
    setSuiteToRename(undefined)
    setEnvironmentToRename(undefined)
    setProfileToRename(undefined)
    setIsImportApiSpecOpen(false)
    setFolderDialog(undefined)
    setPendingAgentDrafts({})
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
    type: 'environment' | 'profile' | 'knowledge',
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
          folderId: createFolderId,
          status: 'ACTIVE',
        }),
      }
    },
    onSuccess: async ({ type, record }) => {
      await queryClient.invalidateQueries({ queryKey: [type === 'suite' ? 'suites' : 'cases', projectId] })
      setCreateDialog(undefined)
      setCreateName('')
      setCreateFolderId(undefined)
      openTab(type, record.id, record.name)
      void message.success(type === 'suite' ? 'Suite created' : 'Scenario created')
    },
  })

  const importApiSpecMutation = useMutation({
    mutationFn: async (file: File) => {
      if (!projectId) {
        throw new Error(t('scenario:messages.selectProjectFirst'))
      }
      return platformApi.importApiSpec(projectId, file)
    },
    onSuccess: async (record) => {
      await queryClient.invalidateQueries({ queryKey: ['api-specs', projectId] })
      setIsImportApiSpecOpen(false)
      openTab('apiSpec', record.id, record.title || record.name)
      void message.success(t('api:imported'))
    },
    onError: () => {
      void message.error(t('api:importError'))
    },
  })

  const deleteApiSpecMutation = useMutation({
    mutationFn: async (apiSpecId: string) => {
      if (!projectId) {
        throw new Error(t('scenario:messages.selectProjectFirst'))
      }
      await platformApi.deleteApiSpec(projectId, apiSpecId)
      return apiSpecId
    },
    onSuccess: async (apiSpecId) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['api-specs', projectId] }),
        queryClient.invalidateQueries({ queryKey: ['operations', projectId] }),
        queryClient.invalidateQueries({ queryKey: ['environments', projectId] }),
      ])
      closeTabNow(`apiSpec:${apiSpecId}`)
      void message.success(t('api:deleted'))
    },
    onError: () => {
      void message.error(t('api:deleteFailed'))
    },
  })

  const renameEnvironmentMutation = useMutation({
    mutationFn: async ({ record, name }: { record: Environment, name: string }) => {
      if (!projectId) throw new Error(t('scenario:messages.selectProjectFirst'))
      return platformApi.updateEnvironment(projectId, record.id, environmentInputFromRecord(record, { name }))
    },
    onSuccess: async (record) => {
      await queryClient.invalidateQueries({ queryKey: ['environments', projectId] })
      updateTab(`environment:${record.id}`, { title: record.name })
      setEnvironmentToRename(undefined)
      void message.success(t('config:environmentSaved'))
    },
    onError: () => void message.error(t('config:environmentSaveFailed')),
  })

  const renameProfileMutation = useMutation({
    mutationFn: async ({ record, name }: { record: Profile, name: string }) => {
      if (!projectId) throw new Error(t('scenario:messages.selectProjectFirst'))
      return platformApi.updateProfile(projectId, record.id, profileInputFromRecord(record, { name }))
    },
    onSuccess: async (record) => {
      await queryClient.invalidateQueries({ queryKey: ['profiles', projectId] })
      updateTab(`profile:${record.id}`, { title: record.name })
      setProfileToRename(undefined)
      void message.success(t('config:profileSaved'))
    },
    onError: () => void message.error(t('config:profileSaveFailed')),
  })

  const deleteEnvironmentMutation = useMutation({
    mutationFn: async (environmentId: string) => {
      if (!projectId) throw new Error(t('scenario:messages.selectProjectFirst'))
      await platformApi.deleteEnvironment(projectId, environmentId)
      return environmentId
    },
    onSuccess: async (environmentId) => {
      await queryClient.invalidateQueries({ queryKey: ['environments', projectId] })
      closeTabNow(`environment:${environmentId}`)
      void message.success(t('config:environmentDeleted'))
    },
    onError: () => void message.error(t('config:environmentDeleteFailed')),
  })

  const deleteProfileMutation = useMutation({
    mutationFn: async (profileId: string) => {
      if (!projectId) throw new Error(t('scenario:messages.selectProjectFirst'))
      await platformApi.deleteProfile(projectId, profileId)
      return profileId
    },
    onSuccess: async (profileId) => {
      await queryClient.invalidateQueries({ queryKey: ['profiles', projectId] })
      closeTabNow(`profile:${profileId}`)
      void message.success(t('config:profileDeleted'))
    },
    onError: () => void message.error(t('config:profileDeleteFailed')),
  })

  const renameScenarioMutation = useMutation({
    mutationFn: async ({ record, name }: { record: CaseRecord, name: string }) => {
      if (!projectId) {
        throw new Error(t('scenario:messages.selectProjectFirst'))
      }
      return platformApi.updateCase(projectId, record.id, caseInputFromRecord(record, {
        name,
        content: renameScenarioInDsl(record.content, name),
      }))
    },
    onSuccess: async (record) => {
      await queryClient.invalidateQueries({ queryKey: ['cases', projectId] })
      updateTab(`scenario:${record.id}`, { title: record.name })
      setScenarioToRename(undefined)
    },
    onError: () => {
      void message.error(t('scenario:messages.renameFailed'))
    },
  })

  const moveScenarioMutation = useMutation({
    mutationFn: async () => {
      if (!projectId || !scenarioToMove) {
        throw new Error(t('scenario:messages.selectScenarioToMove'))
      }
      return platformApi.updateCase(projectId, scenarioToMove.id, caseInputFromRecord(scenarioToMove, {
        folderId: moveFolderId,
      }))
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['cases', projectId] })
      setScenarioToMove(undefined)
      void message.success(t('scenario:messages.moved'))
    },
  })

  const duplicateScenarioMutation = useMutation({
    mutationFn: async (record: CaseRecord) => {
      if (!projectId) {
        throw new Error(t('scenario:messages.selectProjectFirst'))
      }
      const name = `${record.name} copy`
      return platformApi.createCase(projectId, caseInputFromRecord(record, {
        name,
        code: `${record.code.slice(0, 75)}-copy-${Date.now()}`,
        content: renameScenarioInDsl(record.content, name),
      }))
    },
    onSuccess: async (record) => {
      await queryClient.invalidateQueries({ queryKey: ['cases', projectId] })
      openTab('scenario', record.id, record.name)
      void message.success(t('scenario:messages.duplicated'))
    },
  })

  const deleteScenarioMutation = useMutation({
    mutationFn: platformApi.deleteCase,
    onSuccess: async (_, caseId) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['cases', projectId] }),
        queryClient.invalidateQueries({ queryKey: ['suites', projectId] }),
      ])
      closeTabNow(`scenario:${caseId}`)
      void message.success(t('scenario:messages.caseDeleted'))
    },
    onError: () => {
      void message.error(t('scenario:messages.deleteFailed'))
    },
  })

  const runScenarioMutation = useMutation({
    mutationFn: async (record: CaseRecord) => platformApi.startRun({
      caseId: record.id,
      triggeredBy: 'platform-ui',
    }),
    onSuccess: async (run) => {
      await queryClient.invalidateQueries({ queryKey: ['runs'] })
      openTab('run', run.id, `${t('execution:run')} ${shortId(run.id)}`)
      void message.success(run.status === 'FAILED' ? t('scenario:runFailed') : t('scenario:runPassed'))
    },
    onError: () => {
      void message.error(t('scenario:messages.runFailed'))
    },
  })

  const saveFolderMutation = useMutation({
    mutationFn: async () => {
      if (!projectId || !folderDialog) {
        throw new Error(t('scenario:folders.selectFirst'))
      }
      const name = folderName.trim()
      if (!name) {
        throw new Error(t('scenario:folders.nameRequired'))
      }
      const parentFolderId = folderDialog.mode === 'move'
        ? folderParentId
        : folderDialog.parentFolderId ?? folderDialog.folder?.parentFolderId ?? undefined
      return folderDialog.folder
        ? platformApi.updateFolder(projectId, folderDialog.folder.id, { name, parentFolderId })
        : platformApi.createFolder(projectId, { name, parentFolderId })
    },
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['folders', projectId] })
      setFolderDialog(undefined)
      void message.success(t('scenario:folders.saved'))
    },
    onError: (error) => {
      void message.error(error instanceof Error ? error.message : t('scenario:folders.saveFailed'))
    },
  })

  const deleteFolderMutation = useMutation({
    mutationFn: platformApi.deleteFolder,
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['folders', projectId] })
      void message.success(t('scenario:folders.deleted'))
    },
    onError: (error) => {
      void message.error(error instanceof Error ? error.message : t('scenario:folders.emptyError'))
    },
  })

  const renameSuiteMutation = useMutation({
    mutationFn: async ({ record, name }: { record: SuiteRecord, name: string }) => {
      if (!projectId) {
        throw new Error(t('suite:selectProject'))
      }
      return platformApi.updateSuite(projectId, record.id, suiteInputFromRecord(record, { name }))
    },
    onSuccess: async (record) => {
      await queryClient.invalidateQueries({ queryKey: ['suites', projectId] })
      updateTab(`suite:${record.id}`, { title: record.name })
      setSuiteToRename(undefined)
      void message.success(t('suite:saved'))
    },
    onError: () => {
      void message.error(t('suite:saveFailed'))
    },
  })

  const duplicateSuiteMutation = useMutation({
    mutationFn: async (record: SuiteRecord) => {
      if (!projectId) {
        throw new Error(t('suite:selectProject'))
      }
      return platformApi.createSuite(
        projectId,
        suiteInputFromRecord(record, { name: `${record.name} copy` }, false),
      )
    },
    onSuccess: async (record) => {
      await queryClient.invalidateQueries({ queryKey: ['suites', projectId] })
      openTab('suite', record.id, record.name)
      void message.success(t('suite:duplicated'))
    },
    onError: () => {
      void message.error(t('suite:saveFailed'))
    },
  })

  const deleteSuiteMutation = useMutation({
    mutationFn: platformApi.deleteSuite,
    onSuccess: async (_, suiteId) => {
      await queryClient.invalidateQueries({ queryKey: ['suites', projectId] })
      closeTabNow(`suite:${suiteId}`)
      void message.success(t('suite:deleted'))
    },
    onError: () => {
      void message.error(t('suite:saveFailed'))
    },
  })

  const runSuiteMutation = useMutation({
    mutationFn: async (record: SuiteRecord) => platformApi.startSuiteRun({
      suiteId: record.id,
      triggeredBy: 'platform-ui',
    }),
    onSuccess: async (run) => {
      await Promise.all([
        queryClient.invalidateQueries({ queryKey: ['runs'] }),
        queryClient.invalidateQueries({ queryKey: ['suite-runs'] }),
      ])
      openTab('suiteRun', run.id, `${run.suiteName} · ${t('execution:execution')}`)
      void message.success(run.status === 'PASSED' ? t('suite:runPassed') : t('suite:runFailed'))
    },
    onError: () => {
      void message.error(t('suite:runFailedGeneric'))
    },
  })

  const isScenarioTabDirty = (caseId: string) => Boolean(
    tabs.find((tab) => tab.key === `scenario:${caseId}`)?.dirty,
  )
  const isSuiteTabDirty = (suiteId: string) => Boolean(
    tabs.find((tab) => tab.key === `suite:${suiteId}`)?.dirty,
  )
  const isConfigTabDirty = (type: 'environment' | 'profile', resourceId: string) => Boolean(
    tabs.find((tab) => tab.key === `${type}:${resourceId}`)?.dirty,
  )

  const openCreateScenario = (folderId?: string) => {
    setCreateName('')
    setCreateFolderId(folderId)
    setCreateDialog('scenario')
  }

  const openFolderDialog = (mode: FolderDialogState['mode'], folder?: FolderRecord, parentFolderId?: string) => {
    setFolderDialog({ mode, folder, parentFolderId })
    setFolderName(mode === 'create' ? '' : folder?.name ?? '')
    setFolderParentId(mode === 'move'
      ? folder?.parentFolderId ?? undefined
      : parentFolderId ?? folder?.parentFolderId ?? undefined)
  }

  const openRenameScenario = (record: CaseRecord) => {
    if (isScenarioTabDirty(record.id)) {
      void message.warning(t('scenario:messages.saveBeforeRename'))
      return
    }
    setScenarioToRename(record)
    setScenarioName(record.name)
  }

  const openMoveScenario = (record: CaseRecord) => {
    if (isScenarioTabDirty(record.id)) {
      void message.warning(t('scenario:messages.saveBeforeMove'))
      return
    }
    setScenarioToMove(record)
    setMoveFolderId(record.folderId ?? undefined)
  }

  const runScenario = (record: CaseRecord) => {
    if (isScenarioTabDirty(record.id)) {
      void message.warning(t('scenario:messages.saveBeforeRun'))
      return
    }
    void runScenarioMutation.mutateAsync(record)
  }

  const confirmDeleteScenario = (record: CaseRecord) => {
    modal.confirm({
      title: t('scenario:dialogs.delete'),
      content: t('scenario:messages.deleteCaseDescription', { name: record.name }),
      okText: t('common:actions.delete'),
      okButtonProps: { danger: true },
      cancelText: t('common:actions.cancel'),
      onOk: () => deleteScenarioMutation.mutateAsync(record.id),
    })
  }

  const confirmDeleteFolder = (folder: FolderRecord) => {
    modal.confirm({
      title: t('scenario:folders.deleteConfirm'),
      content: t('scenario:folders.deleteDescription', { name: folder.name }),
      okText: t('common:actions.delete'),
      okButtonProps: { danger: true },
      cancelText: t('common:actions.cancel'),
      onOk: () => deleteFolderMutation.mutateAsync(folder.id),
    })
  }

  const openRenameSuite = (record: SuiteRecord) => {
    if (isSuiteTabDirty(record.id)) {
      void message.warning(t('suite:saveBeforeChange'))
      return
    }
    setSuiteToRename(record)
    setSuiteName(record.name)
  }

  const runSuite = (record: SuiteRecord) => {
    if (isSuiteTabDirty(record.id)) {
      void message.warning(t('suite:saveBeforeRun'))
      return
    }
    void runSuiteMutation.mutateAsync(record)
  }

  const confirmDeleteSuite = (record: SuiteRecord) => {
    modal.confirm({
      title: t('suite:deleteConfirm'),
      content: t('suite:deleteDescription', { name: record.name }),
      okText: t('common:actions.delete'),
      okButtonProps: { danger: true },
      cancelText: t('common:actions.cancel'),
      onOk: () => deleteSuiteMutation.mutateAsync(record.id),
    })
  }

  const confirmDeleteApiSpec = async (record: ApiSpecRecord) => {
    if (!projectId) {
      void message.error(t('scenario:messages.selectProjectFirst'))
      return
    }
    let references: ApiSpecReferenceRecord[]
    try {
      references = await platformApi.listApiSpecReferences(projectId, record.id)
    } catch {
      void message.error(t('api:referenceCheckFailed'))
      return
    }

    if (references.length) {
      modal.warning({
        title: t('api:deleteBlockedTitle'),
        content: (
          <div className="api-spec-reference-list">
            <Text>{t('api:deleteBlockedDescription')}</Text>
            <ul>{references.map((reference) => <li key={reference.caseId}>{reference.caseName}</li>)}</ul>
          </div>
        ),
        okText: t('common:actions.close'),
      })
      return
    }

    modal.confirm({
      title: t('api:deleteConfirm'),
      content: t('api:deleteDescription', { name: record.title || record.name }),
      okText: t('common:actions.delete'),
      okButtonProps: { danger: true },
      cancelText: t('common:actions.cancel'),
      onOk: () => deleteApiSpecMutation.mutateAsync(record.id),
    })
  }

  const openRenameEnvironment = (record: Environment) => {
    if (isConfigTabDirty('environment', record.id)) {
      void message.warning(t('config:saveBeforeChange'))
      return
    }
    setEnvironmentToRename(record)
    setEnvironmentName(record.name)
  }

  const openRenameProfile = (record: Profile) => {
    if (isConfigTabDirty('profile', record.id)) {
      void message.warning(t('config:saveBeforeChange'))
      return
    }
    setProfileToRename(record)
    setProfileName(record.name)
  }

  const confirmDeleteConfigAsset = async (
    kind: 'environment' | 'profile',
    record: Environment | Profile,
  ) => {
    if (!projectId) {
      void message.error(t('scenario:messages.selectProjectFirst'))
      return
    }
    try {
      const references: ResourceReferenceRecord[] = kind === 'environment'
        ? await platformApi.listEnvironmentReferences(projectId, record.id)
        : await platformApi.listProfileReferences(projectId, record.id)
      if (references.length) {
        modal.warning({
          title: t(kind === 'environment' ? 'config:environmentDeleteBlockedTitle' : 'config:profileDeleteBlockedTitle'),
          content: (
            <div className="api-spec-reference-list">
              <Text>{t(kind === 'environment' ? 'config:environmentDeleteBlockedDescription' : 'config:profileDeleteBlockedDescription')}</Text>
              <ul>{references.map((reference) => <li key={`${reference.type}:${reference.id}`}>{reference.name}</li>)}</ul>
            </div>
          ),
          okText: t('common:actions.close'),
        })
        return
      }
    } catch {
      void message.error(t('config:referenceCheckFailed'))
      return
    }

    const isEnvironment = kind === 'environment'
    modal.confirm({
      title: t(isEnvironment ? 'config:environmentDeleteConfirm' : 'config:profileDeleteConfirm'),
      content: t(isEnvironment ? 'config:environmentDeleteDescription' : 'config:profileDeleteDescription', { name: record.name }),
      okText: t('common:actions.delete'),
      okButtonProps: { danger: true },
      cancelText: t('common:actions.cancel'),
      onOk: () => isEnvironment
        ? deleteEnvironmentMutation.mutateAsync(record.id)
        : deleteProfileMutation.mutateAsync(record.id),
    })
  }

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
  const filteredTestKnowledge = useMemo(
    () => filterByName(testKnowledgeQuery.data ?? [], assetFilter, (record) => `${record.name} ${record.description} ${record.tags.join(' ')}`),
    [assetFilter, testKnowledgeQuery.data],
  )
  const folderOptions = useMemo(
    () => buildFolderOptions(foldersQuery.data ?? []),
    [foldersQuery.data],
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
  const activeScenario = activeTab?.type === 'scenario'
    ? (casesQuery.data ?? []).find((record) => record.id === activeTab.resourceId)
    : undefined

  const openAgentScenario = useCallback((scenarioId: string) => {
    const scenario = (casesQuery.data ?? []).find((record) => record.id === scenarioId)
    if (!scenario) return
    openTab('scenario', scenario.id, scenario.name)
  }, [casesQuery.data, openTab])

  const openAgentKnowledge = useCallback((knowledgeId: string) => {
    const record = (testKnowledgeQuery.data ?? []).find((item) => item.id === knowledgeId)
    if (!record) {
      void message.warning(t('agent:knowledgeUnavailable'))
      return
    }
    openTab('knowledge', record.id, record.name)
  }, [message, openTab, t, testKnowledgeQuery.data])

  const applyAgentDraft = useCallback(async (agentDraft: ScenarioDraftRecord) => {
    const scenario = (casesQuery.data ?? []).find((record) => record.id === agentDraft.scenarioId)
    const targetTab = scenario ? tabs.find((tab) => tab.key === `scenario:${scenario.id}`) : undefined
    const inspection = inspectAgentDraftApply(scenario, agentDraft, Boolean(targetTab?.dirty))
    if (inspection.reason === 'MISSING_SCENARIO') throw new Error(t('agent:noScenario'))
    if (inspection.reason === 'STALE_REVISION') throw new Error(t('agent:draftStale'))
    if (!scenario) throw new Error(t('agent:noScenario'))
    if (inspection.requiresDiscardConfirmation) {
      await new Promise<void>((resolve, reject) => {
        modal.confirm({
          title: t('agent:replaceUnsavedTitle'),
          content: t('agent:replaceUnsavedDescription'),
          okText: t('agent:replaceUnsaved'),
          okButtonProps: { danger: true },
          cancelText: t('common:actions.cancel'),
          onOk: () => resolve(),
          onCancel: () => reject(new Error(t('common:actions.cancel'))),
        })
      })
    }
    setPendingAgentDrafts((current) => ({ ...current, [scenario.id]: agentDraft }))
    openTab('scenario', scenario.id, scenario.name)
  }, [casesQuery.data, modal, openTab, t, tabs])

  return (
    <div className={agentPanelOpen ? 'workbench-page agent-open' : 'workbench-page'}>
      <aside className="workbench-context-sidebar">
        {mode === 'scenarios' ? (
          <ScenariosSidebar
            cases={filteredCases}
            folders={foldersQuery.data ?? []}
            filter={assetFilter}
            activeScenarioId={activeTab?.type === 'scenario' ? activeTab.resourceId : undefined}
            onFilterChange={setAssetFilter}
            onOpenScenario={(record) => openTab('scenario', record.id, record.name)}
            onCreateScenario={openCreateScenario}
            onCreateFolder={(parentFolderId) => openFolderDialog('create', undefined, parentFolderId)}
            onRunScenario={runScenario}
            onRenameScenario={openRenameScenario}
            onDuplicateScenario={(record) => void duplicateScenarioMutation.mutateAsync(record)}
            onMoveScenario={openMoveScenario}
            onDeleteScenario={confirmDeleteScenario}
            onRenameFolder={(folder) => openFolderDialog('rename', folder)}
            onMoveFolder={(folder) => openFolderDialog('move', folder)}
            onDeleteFolder={confirmDeleteFolder}
          />
        ) : null}
        {mode === 'suites' ? (
          <ResourceListSidebar
            title={t('suite:title')}
            searchPlaceholder={t('workspace:workbench.searchSuites')}
            filter={assetFilter}
            onFilterChange={setAssetFilter}
            onCreate={() => {
              setCreateName('')
              setCreateDialog('suite')
            }}
          >
            {filteredSuites.map((record) => (
              <AssetRow
                key={record.id}
                active={activeTab?.type === 'suite' && activeTab.resourceId === record.id}
                icon={<ProfileOutlined />}
                title={record.name}
                onClick={() => openTab('suite', record.id, record.name)}
                action={(
                  <Dropdown
                    trigger={['click']}
                    menu={{
                      items: [
                        {
                          key: 'run',
                          label: t('common:actions.run'),
                          icon: <PlayCircleOutlined />,
                          disabled: !record.cases.length,
                        },
                        { key: 'rename', label: t('common:actions.rename'), icon: <EditOutlined /> },
                        { key: 'duplicate', label: t('common:actions.duplicate'), icon: <CopyOutlined /> },
                        { type: 'divider' },
                        { key: 'delete', label: t('common:actions.delete'), icon: <DeleteOutlined />, danger: true },
                      ],
                      onClick: ({ key }) => {
                        if (key === 'run') runSuite(record)
                        if (key === 'rename') openRenameSuite(record)
                        if (key === 'duplicate') void duplicateSuiteMutation.mutateAsync(record)
                        if (key === 'delete') confirmDeleteSuite(record)
                      },
                    }}
                  >
                    <Button
                      aria-label={t('scenario:actions.manage', { name: record.name })}
                      className="workbench-tree-action"
                      icon={<MoreOutlined />}
                      size="small"
                      title={t('scenario:actions.manage', { name: record.name })}
                      type="text"
                      onClick={(event) => event.stopPropagation()}
                      onKeyDown={(event) => event.stopPropagation()}
                    />
                  </Dropdown>
                )}
              />
            ))}
          </ResourceListSidebar>
        ) : null}
        {mode === 'apis' ? (
          <ResourceListSidebar
            title={t('api:apiSpecs')}
            searchPlaceholder={t('workspace:workbench.searchApis')}
            filter={assetFilter}
            onFilterChange={setAssetFilter}
            onCreate={() => setIsImportApiSpecOpen(true)}
            createTitle={t('api:addSpec')}
          >
            {filteredApiSpecs.map((record) => (
              <AssetRow
                key={record.id}
                active={activeTab?.type === 'apiSpec' && activeTab.resourceId === record.id}
                icon={<ApiOutlined />}
                title={record.title || record.name}
                onClick={() => openTab('apiSpec', record.id, record.title || record.name)}
                action={(
                  <Button
                    aria-label={t('common:actions.delete')}
                    className="workbench-tree-action"
                    danger
                    icon={<DeleteOutlined />}
                    size="small"
                    title={t('common:actions.delete')}
                    type="text"
                    onClick={(event) => {
                      event.stopPropagation()
                      void confirmDeleteApiSpec(record)
                    }}
                    onKeyDown={(event) => event.stopPropagation()}
                  />
                )}
              />
            ))}
          </ResourceListSidebar>
        ) : null}
        {mode === 'processing' ? (
          <ResourceListSidebar
            title="Processing"
            searchPlaceholder="Search built-in processing"
            filter=""
            onFilterChange={() => undefined}
          >
            <AssetRow
              active={activeTab?.type === 'pipelineCatalog'}
              icon={<ApartmentOutlined />}
              title="Built-in pipeline catalog"
              onClick={() => openTab('pipelineCatalog', 'builtin', 'Processing pipelines')}
            />
          </ResourceListSidebar>
        ) : null}
        {mode === 'environments' ? (
          <ResourceListSidebar
            title={t('config:environments')}
            searchPlaceholder={t('workspace:workbench.searchEnvironments')}
            filter={assetFilter}
            onFilterChange={setAssetFilter}
            onCreate={() => openTab('environment', `new-${Date.now()}`, t('config:newEnvironment'))}
            createTitle={t('config:newEnvironment')}
          >
            {filteredEnvironments.map((record) => (
              <AssetRow
                key={record.id}
                active={activeTab?.type === 'environment' && activeTab.resourceId === record.id}
                icon={<GlobalOutlined />}
                title={record.name}
                onClick={() => openTab('environment', record.id, record.name)}
                action={(
                  <Dropdown
                    trigger={['click']}
                    menu={{
                      items: [
                        { key: 'rename', label: t('common:actions.rename'), icon: <EditOutlined /> },
                        { type: 'divider' },
                        { key: 'delete', label: t('common:actions.delete'), icon: <DeleteOutlined />, danger: true },
                      ],
                      onClick: ({ key }) => {
                        if (key === 'rename') openRenameEnvironment(record)
                        if (key === 'delete') void confirmDeleteConfigAsset('environment', record)
                      },
                    }}
                  >
                    <Button
                      aria-label={t('scenario:actions.manage', { name: record.name })}
                      className="workbench-tree-action"
                      icon={<MoreOutlined />}
                      size="small"
                      title={t('scenario:actions.manage', { name: record.name })}
                      type="text"
                      onClick={(event) => event.stopPropagation()}
                      onKeyDown={(event) => event.stopPropagation()}
                    />
                  </Dropdown>
                )}
              />
            ))}
          </ResourceListSidebar>
        ) : null}
        {mode === 'profiles' ? (
          <ResourceListSidebar
            title={t('config:profiles')}
            searchPlaceholder={t('workspace:workbench.searchProfiles')}
            filter={assetFilter}
            onFilterChange={setAssetFilter}
            onCreate={() => openTab('profile', `new-${Date.now()}`, t('config:newProfile'))}
            createTitle={t('config:newProfile')}
          >
            {filteredProfiles.map((record) => (
              <AssetRow
                key={record.id}
                active={activeTab?.type === 'profile' && activeTab.resourceId === record.id}
                icon={<KeyOutlined />}
                title={record.name}
                onClick={() => openTab('profile', record.id, record.name)}
                action={(
                  <Dropdown
                    trigger={['click']}
                    menu={{
                      items: [
                        { key: 'rename', label: t('common:actions.rename'), icon: <EditOutlined /> },
                        { type: 'divider' },
                        { key: 'delete', label: t('common:actions.delete'), icon: <DeleteOutlined />, danger: true },
                      ],
                      onClick: ({ key }) => {
                        if (key === 'rename') openRenameProfile(record)
                        if (key === 'delete') void confirmDeleteConfigAsset('profile', record)
                      },
                    }}
                  >
                    <Button
                      aria-label={t('scenario:actions.manage', { name: record.name })}
                      className="workbench-tree-action"
                      icon={<MoreOutlined />}
                      size="small"
                      title={t('scenario:actions.manage', { name: record.name })}
                      type="text"
                      onClick={(event) => event.stopPropagation()}
                      onKeyDown={(event) => event.stopPropagation()}
                    />
                  </Dropdown>
                )}
              />
            ))}
          </ResourceListSidebar>
        ) : null}
        {mode === 'knowledge' ? (
          <ResourceListSidebar
            title="Test Knowledge"
            searchPlaceholder="Search test knowledge"
            filter={assetFilter}
            onFilterChange={setAssetFilter}
            onCreate={() => openTab('knowledge', `new-${Date.now()}`, 'New Test Knowledge')}
            createTitle="New Test Knowledge"
          >
            {filteredTestKnowledge.map((record) => (
              <AssetRow
                key={record.id}
                active={activeTab?.type === 'knowledge' && activeTab.resourceId === record.id}
                icon={<ReadOutlined />}
                title={record.name}
                onClick={() => openTab('knowledge', record.id, record.name)}
                action={(
                  <Button
                    aria-label="Delete test knowledge"
                    className="workbench-tree-action"
                    danger
                    icon={<DeleteOutlined />}
                    size="small"
                    title="Delete"
                    type="text"
                    onClick={(event) => {
                      event.stopPropagation()
                      modal.confirm({
                        title: 'Delete test knowledge?',
                        content: `Delete "${record.name}"? This action cannot be undone.`,
                        okText: 'Delete', okButtonProps: { danger: true },
                        onOk: async () => {
                          if (!projectId) return
                          await platformApi.deleteTestKnowledge(projectId, record.id)
                          await queryClient.invalidateQueries({ queryKey: ['test-knowledge', projectId] })
                          closeTabNow(`knowledge:${record.id}`)
                          void message.success('Test knowledge deleted')
                        },
                      })
                    }}
                    onKeyDown={(event) => event.stopPropagation()}
                  />
                )}
              />
            ))}
            {!testKnowledgeQuery.isLoading && !filteredTestKnowledge.length
              ? <Text type="secondary" className="workbench-sidebar-empty">No test knowledge yet.</Text>
              : null}
          </ResourceListSidebar>
        ) : null}
        {mode === 'runs' ? (
          <RunsSidebar
            records={projectRuns}
            suiteRecords={projectSuiteRuns}
            cases={casesQuery.data ?? []}
            suites={suitesQuery.data ?? []}
            activeRunId={activeTab?.type === 'run' ? activeTab.resourceId : undefined}
            activeSuiteRunId={activeTab?.type === 'suiteRun' ? activeTab.resourceId : undefined}
            filter={runFilter}
            onFilterChange={setRunFilter}
            onOpen={(record) => openTab('run', record.id, `${t('execution:run')} ${shortId(record.id)}`)}
            onOpenSuiteExecution={(record) => openTab('suiteRun', record.id, `${record.suiteName} · ${t('execution:execution')}`)}
            onOpenScenario={(record) => openTab('scenario', record.id, record.name)}
            onOpenSuite={(record) => openTab('suite', record.id, record.name)}
          />
        ) : null}
      </aside>

      <main className="workbench-main">
        <div className="workbench-tab-strip" role="tablist" aria-label={t('workspace:workbench.openItems')}>
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
                {tab.dirty ? <span className="workbench-tab-dirty" title={t('workspace:workbench.unsavedChanges')} /> : null}
              </button>
              <Button
                aria-label={t('workspace:workbench.closeItem', { title: tab.title })}
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
                { key: 'scenario', label: t('scenario:newScenarioShort'), icon: <ExperimentOutlined /> },
                { key: 'suite', label: t('suite:newSuite'), icon: <ProfileOutlined /> },
                { key: 'environment', label: t('config:newEnvironment'), icon: <GlobalOutlined /> },
                { key: 'profile', label: t('config:newProfile'), icon: <KeyOutlined /> },
              ],
              onClick: ({ key }) => {
                if (key === 'scenario' || key === 'suite') {
                  setCreateName('')
                  setCreateDialog(key)
                } else if (key === 'environment') {
                  openTab('environment', `new-${Date.now()}`, t('config:newEnvironment'))
                } else {
                  openTab('profile', `new-${Date.now()}`, t('config:newProfile'))
                }
              },
            }}
          >
            <Button className="workbench-new-tab" type="text" icon={<PlusOutlined />} title={t('workspace:workbench.newTab')} />
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
                testKnowledge: testKnowledgeQuery.data ?? [],
                environments: environmentsQuery.data ?? [],
                profiles: profilesQuery.data ?? [],
                updateTab,
                closeTab: closeTabNow,
                replaceCreatedTab,
                registerSaveHandler,
                openTab,
                agentDraft: tab.type === 'scenario' ? pendingAgentDrafts[tab.resourceId] : undefined,
                onAgentDraftApplied: (scenarioId) => setPendingAgentDrafts((current) => {
                  const next = { ...current }
                  delete next[scenarioId]
                  return next
                }),
              }) : null}
            </div>
          ))}
          {!activeTab ? (
            <div className="workbench-empty">
              <FileTextOutlined />
              <Text strong>{t('workspace:workbench.openItem')}</Text>
              <Text type="secondary">{t('workspace:workbench.chooseItem')}</Text>
            </div>
          ) : null}
        </div>
      </main>

      {agentPanelOpen && projectId ? (
        <AgentPanel
          projectId={projectId}
          activeScenario={activeScenario}
          scenarios={casesQuery.data ?? []}
          suites={suitesQuery.data ?? []}
          operations={operationsQuery.data ?? []}
          runs={(runsQuery.data ?? []).filter((run) => run.projectId === projectId)}
          knowledge={testKnowledgeQuery.data ?? []}
          pinned={agentPanelPinned}
          onPinnedChange={(pinned) => onAgentPanelPinnedChange?.(pinned)}
          onClose={() => onAgentPanelOpenChange?.(false)}
          onOpenScenario={openAgentScenario}
          onOpenKnowledge={openAgentKnowledge}
          onApplyDraft={applyAgentDraft}
        />
      ) : null}

      <Modal
        title={t('api:addSpec')}
        open={isImportApiSpecOpen}
        footer={null}
        destroyOnHidden
        onCancel={() => {
          if (!importApiSpecMutation.isPending) {
            setIsImportApiSpecOpen(false)
          }
        }}
      >
        <div className="api-import-stack">
          <Text type="secondary">{t('api:importDescription')}</Text>
          <Upload.Dragger
            accept=".yaml,.yml"
            multiple={false}
            showUploadList={false}
            disabled={!projectId || importApiSpecMutation.isPending}
            beforeUpload={(file) => {
              void importApiSpecMutation.mutateAsync(file as File)
              return false
            }}
          >
            <p className="ant-upload-drag-icon"><UploadOutlined /></p>
            <p className="ant-upload-text">{t('api:addSpec')}</p>
          </Upload.Dragger>
        </div>
      </Modal>

      <Modal
        title={createDialog === 'suite' ? t('suite:newSuite') : t('scenario:newScenarioShort')}
        open={Boolean(createDialog)}
        okText={t('common:actions.create')}
        confirmLoading={createMutation.isPending}
        okButtonProps={{ disabled: !createName.trim() }}
        onCancel={() => {
          setCreateDialog(undefined)
          setCreateFolderId(undefined)
        }}
        onOk={() => void createMutation.mutateAsync()}
      >
        <Input
          autoFocus
          aria-label={t('common:fields.name')}
          placeholder={createDialog === 'suite' ? t('suite:namePlaceholder') : t('scenario:namePlaceholder')}
          value={createName}
          onChange={(event) => setCreateName(event.target.value)}
          onPressEnter={() => {
            if (createName.trim()) {
              void createMutation.mutateAsync()
            }
          }}
        />
      </Modal>

      <Modal
        title={t('scenario:dialogs.rename')}
        open={Boolean(scenarioToRename)}
        okText={t('common:actions.rename')}
        confirmLoading={renameScenarioMutation.isPending}
        okButtonProps={{ disabled: !scenarioName.trim() }}
        onCancel={() => setScenarioToRename(undefined)}
        onOk={() => {
          if (scenarioToRename && scenarioName.trim()) {
            void renameScenarioMutation.mutateAsync({
              record: scenarioToRename,
              name: scenarioName.trim(),
            })
          }
        }}
      >
        <Input
          autoFocus
          aria-label={t('common:fields.name')}
          value={scenarioName}
          onChange={(event) => setScenarioName(event.target.value)}
          onPressEnter={() => {
            if (scenarioToRename && scenarioName.trim()) {
              void renameScenarioMutation.mutateAsync({
                record: scenarioToRename,
                name: scenarioName.trim(),
              })
            }
          }}
        />
      </Modal>

      <Modal
        title={t('scenario:dialogs.move')}
        open={Boolean(scenarioToMove)}
        okText={t('common:actions.move')}
        confirmLoading={moveScenarioMutation.isPending}
        onCancel={() => setScenarioToMove(undefined)}
        onOk={() => void moveScenarioMutation.mutateAsync()}
      >
        <Select
          allowClear
          className="scenario-folder-select"
          placeholder={t('scenario:folders.root')}
          value={moveFolderId}
          options={folderOptions}
          onChange={setMoveFolderId}
        />
      </Modal>

      <Modal
        title={t('suite:rename')}
        open={Boolean(suiteToRename)}
        okText={t('common:actions.rename')}
        confirmLoading={renameSuiteMutation.isPending}
        okButtonProps={{ disabled: !suiteName.trim() }}
        onCancel={() => setSuiteToRename(undefined)}
        onOk={() => {
          if (suiteToRename && suiteName.trim()) {
            void renameSuiteMutation.mutateAsync({
              record: suiteToRename,
              name: suiteName.trim(),
            })
          }
        }}
      >
        <Input
          autoFocus
          aria-label={t('common:fields.name')}
          value={suiteName}
          onChange={(event) => setSuiteName(event.target.value)}
          onPressEnter={() => {
            if (suiteToRename && suiteName.trim()) {
              void renameSuiteMutation.mutateAsync({
                record: suiteToRename,
                name: suiteName.trim(),
              })
            }
          }}
        />
      </Modal>

      <Modal
        title={t('config:renameEnvironment')}
        open={Boolean(environmentToRename)}
        okText={t('common:actions.rename')}
        confirmLoading={renameEnvironmentMutation.isPending}
        okButtonProps={{ disabled: !environmentName.trim() }}
        onCancel={() => setEnvironmentToRename(undefined)}
        onOk={() => {
          if (environmentToRename && environmentName.trim()) {
            void renameEnvironmentMutation.mutateAsync({
              record: environmentToRename,
              name: environmentName.trim(),
            })
          }
        }}
      >
        <Input
          autoFocus
          aria-label={t('common:fields.name')}
          value={environmentName}
          onChange={(event) => setEnvironmentName(event.target.value)}
          onPressEnter={() => {
            if (environmentToRename && environmentName.trim()) {
              void renameEnvironmentMutation.mutateAsync({
                record: environmentToRename,
                name: environmentName.trim(),
              })
            }
          }}
        />
      </Modal>

      <Modal
        title={t('config:renameProfile')}
        open={Boolean(profileToRename)}
        okText={t('common:actions.rename')}
        confirmLoading={renameProfileMutation.isPending}
        okButtonProps={{ disabled: !profileName.trim() }}
        onCancel={() => setProfileToRename(undefined)}
        onOk={() => {
          if (profileToRename && profileName.trim()) {
            void renameProfileMutation.mutateAsync({
              record: profileToRename,
              name: profileName.trim(),
            })
          }
        }}
      >
        <Input
          autoFocus
          aria-label={t('common:fields.name')}
          value={profileName}
          onChange={(event) => setProfileName(event.target.value)}
          onPressEnter={() => {
            if (profileToRename && profileName.trim()) {
              void renameProfileMutation.mutateAsync({
                record: profileToRename,
                name: profileName.trim(),
              })
            }
          }}
        />
      </Modal>

      <Modal
        title={folderDialog?.mode === 'rename'
          ? t('scenario:folders.rename')
          : folderDialog?.mode === 'move'
            ? t('scenario:folders.move')
            : t('scenario:folders.add')}
        open={Boolean(folderDialog)}
        okText={t(folderDialog?.mode === 'rename'
          ? 'common:actions.rename'
          : folderDialog?.mode === 'move'
            ? 'common:actions.move'
            : 'common:actions.create')}
        confirmLoading={saveFolderMutation.isPending}
        okButtonProps={{ disabled: !folderName.trim() }}
        onCancel={() => setFolderDialog(undefined)}
        onOk={() => void saveFolderMutation.mutateAsync()}
      >
        <div className="scenario-dialog-stack">
          <Input
            autoFocus
            aria-label={t('common:fields.name')}
            placeholder={t('scenario:folders.namePlaceholder')}
            value={folderName}
            onChange={(event) => setFolderName(event.target.value)}
            onPressEnter={() => {
              if (folderName.trim()) {
                void saveFolderMutation.mutateAsync()
              }
            }}
          />
          {folderDialog?.mode === 'move' ? (
            <Select
              allowClear
              className="scenario-folder-select"
              placeholder={t('scenario:folders.root')}
              value={folderParentId}
              options={folderOptions.filter(
                (option) => !isFolderBlockedTarget(
                  option.value,
                  folderDialog.folder?.id,
                  foldersQuery.data ?? [],
                ),
              )}
              onChange={setFolderParentId}
            />
          ) : null}
        </div>
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
  testKnowledge: TestKnowledgeRecord[]
  environments: Awaited<ReturnType<typeof platformApi.listEnvironments>>
  profiles: Awaited<ReturnType<typeof platformApi.listProfiles>>
  updateTab: (key: string, patch: Partial<WorkbenchTab>) => void
  closeTab: (key: string) => void
  replaceCreatedTab: (
    oldKey: string,
    type: 'environment' | 'profile' | 'knowledge',
    resourceId: string,
    title: string,
  ) => void
  registerSaveHandler: (key: string, save?: () => Promise<void>) => void
  openTab: (type: WorkbenchTabType, resourceId: string, title: string) => void
  agentDraft?: ScenarioDraftRecord
  onAgentDraftApplied: (scenarioId: string) => void
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
        agentDraft={options.agentDraft}
        onAgentDraftApplied={options.onAgentDraftApplied}
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
  if (tab.type === 'pipelineCatalog') {
    return <PipelineCatalog />
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
  if (tab.type === 'knowledge') {
    const knowledge = options.testKnowledge.find((record) => record.id === tab.resourceId)
    return (
      <TestKnowledgeEditor
        projectId={projectId}
        knowledge={knowledge}
        {...commonCallbacks}
        onSaved={(saved) => replaceCreatedTab(tab.key, 'knowledge', saved.id, saved.name)}
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
  const { t } = useTranslation(['execution', 'scenario'])
  const [selectedStepIndex, setSelectedStepIndex] = useState(1)
  useEffect(() => {
    setSelectedStepIndex(run?.steps[0]?.index ?? 1)
  }, [run?.id, run?.steps])
  return (
    <div className="run-tab-pane">
      <header className="run-context-header">
        <div>
          <Text type="secondary">{t('execution:scenarioExecution')}</Text>
          <h1>{scenarioName ?? run?.suiteDisplayName ?? t('execution:detail')}</h1>
        </div>
        <div className="run-context-actions">
          {onOpenSuiteRun
            ? <Button size="small" onClick={onOpenSuiteRun}>{t('execution:openSuiteExecution')}</Button>
            : null}
          {onOpenScenario
            ? <Button size="small" onClick={onOpenScenario}>{t('scenario:openScenario')}</Button>
            : null}
        </div>
      </header>
      <div className="run-tab-detail">
        <RunDetail run={run} selectedStepIndex={selectedStepIndex} onSelectStep={setSelectedStepIndex} />
      </div>
    </div>
  )
}

function ApiSpecTabPane({ projectId, spec }: { projectId: string, spec?: ApiSpecRecord }) {
  const { t } = useTranslation('api')

  if (!spec) {
    return <div className="workbench-empty"><Text type="secondary">{t('specNotFound')}</Text></div>
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
          <span>{t('operationCount', { count: spec.operationCount })}</span>
        </div>
      </header>
      <div className="api-spec-tab-content">
        <ApiReference projectId={projectId} apiSpecId={spec.id} />
      </div>
    </div>
  )
}

function ScenariosSidebar({
  cases,
  folders,
  filter,
  activeScenarioId,
  onFilterChange,
  onOpenScenario,
  onCreateScenario,
  onCreateFolder,
  onRunScenario,
  onRenameScenario,
  onDuplicateScenario,
  onMoveScenario,
  onDeleteScenario,
  onRenameFolder,
  onMoveFolder,
  onDeleteFolder,
}: {
  cases: CaseRecord[]
  folders: FolderRecord[]
  filter: string
  activeScenarioId?: string
  onFilterChange: (value: string) => void
  onOpenScenario: (record: CaseRecord) => void
  onCreateScenario: (folderId?: string) => void
  onCreateFolder: (parentFolderId?: string) => void
  onRunScenario: (record: CaseRecord) => void
  onRenameScenario: (record: CaseRecord) => void
  onDuplicateScenario: (record: CaseRecord) => void
  onMoveScenario: (record: CaseRecord) => void
  onDeleteScenario: (record: CaseRecord) => void
  onRenameFolder: (folder: FolderRecord) => void
  onMoveFolder: (folder: FolderRecord) => void
  onDeleteFolder: (folder: FolderRecord) => void
}) {
  const { t } = useTranslation(['common', 'scenario', 'workspace'])
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
  casesByFolder.forEach((records) => records.sort((left, right) => left.name.localeCompare(right.name)))
  childFolders.forEach((records) => records.sort(
    (left, right) => left.sortOrder - right.sortOrder || left.name.localeCompare(right.name),
  ))

  const renderScenario = (record: CaseRecord, depth: number) => (
    <div
      key={record.id}
      role="button"
      tabIndex={0}
      className={record.id === activeScenarioId
        ? 'workbench-asset-row workbench-scenario-row active'
        : 'workbench-asset-row workbench-scenario-row'}
      style={{ paddingLeft: 10 + depth * 12 }}
      onClick={() => onOpenScenario(record)}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onOpenScenario(record)
        }
      }}
    >
      <ExperimentOutlined />
      <span className="workbench-tree-item-name">{record.name}</span>
      <Dropdown
        trigger={['click']}
        menu={{
          items: [
            {
              key: 'run',
              label: t('common:actions.run'),
              icon: <PlayCircleOutlined />,
              disabled: !record.defaultEnvironmentId || !record.defaultProfileId,
            },
            { key: 'rename', label: t('common:actions.rename'), icon: <EditOutlined /> },
            { key: 'duplicate', label: t('common:actions.duplicate'), icon: <CopyOutlined /> },
            { key: 'move', label: t('common:actions.move'), icon: <FolderOutlined /> },
            { type: 'divider' },
            { key: 'delete', label: t('common:actions.delete'), icon: <DeleteOutlined />, danger: true },
          ],
          onClick: ({ key }) => {
            if (key === 'run') onRunScenario(record)
            if (key === 'rename') onRenameScenario(record)
            if (key === 'duplicate') onDuplicateScenario(record)
            if (key === 'move') onMoveScenario(record)
            if (key === 'delete') onDeleteScenario(record)
          },
        }}
      >
        <Button
          aria-label={t('scenario:actions.manage', { name: record.name })}
          className="workbench-tree-action"
          icon={<MoreOutlined />}
          size="small"
          title={t('scenario:actions.manage', { name: record.name })}
          type="text"
          onClick={(event) => event.stopPropagation()}
          onKeyDown={(event) => event.stopPropagation()}
        />
      </Dropdown>
    </div>
  )

  const renderFolder = (folder: FolderRecord, depth: number): React.ReactNode => (
    <details key={folder.id} className="workbench-tree-folder" open>
      <summary style={{ paddingLeft: 8 + depth * 12 }}>
        <RightOutlined className="workbench-tree-chevron" />
        <FolderOpenOutlined />
        <span className="workbench-tree-item-name">{folder.name}</span>
        <span className="workbench-tree-actions" onClick={(event) => event.stopPropagation()}>
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                { key: 'scenario', label: t('scenario:addScenario'), icon: <ExperimentOutlined /> },
                { key: 'folder', label: t('scenario:folders.add'), icon: <FolderOutlined /> },
              ],
              onClick: ({ key }) => key === 'scenario'
                ? onCreateScenario(folder.id)
                : onCreateFolder(folder.id),
            }}
          >
            <Button
              aria-label={t('scenario:actions.addTo', { name: folder.name })}
              className="workbench-tree-action"
              icon={<PlusOutlined />}
              size="small"
              title={t('scenario:actions.addTo', { name: folder.name })}
              type="text"
            />
          </Dropdown>
          <Dropdown
            trigger={['click']}
            menu={{
              items: [
                { key: 'rename', label: t('common:actions.rename'), icon: <EditOutlined /> },
                { key: 'move', label: t('common:actions.move'), icon: <FolderOutlined /> },
                { type: 'divider' },
                { key: 'delete', label: t('common:actions.delete'), icon: <DeleteOutlined />, danger: true },
              ],
              onClick: ({ key }) => {
                if (key === 'rename') onRenameFolder(folder)
                if (key === 'move') onMoveFolder(folder)
                if (key === 'delete') onDeleteFolder(folder)
              },
            }}
          >
            <Button
              aria-label={t('scenario:actions.manage', { name: folder.name })}
              className="workbench-tree-action"
              icon={<MoreOutlined />}
              size="small"
              title={t('scenario:actions.manage', { name: folder.name })}
              type="text"
            />
          </Dropdown>
        </span>
      </summary>
      {(casesByFolder.get(folder.id) ?? []).map((record) => (
        renderScenario(record, depth + 1)
      ))}
      {(childFolders.get(folder.id) ?? []).map((child) => renderFolder(child, depth + 1))}
    </details>
  )
  return (
    <>
      <div className="workbench-sidebar-head">
        <Text strong>{t('scenario:title')}</Text>
        <Dropdown
          trigger={['click']}
          menu={{
            items: [
              { key: 'scenario', label: t('scenario:addScenario'), icon: <ExperimentOutlined /> },
              { key: 'folder', label: t('scenario:folders.add'), icon: <FolderOutlined /> },
            ],
            onClick: ({ key }) => key === 'scenario'
              ? onCreateScenario()
              : onCreateFolder(),
          }}
        >
          <Button
            type="text"
            size="small"
            icon={<PlusOutlined />}
            title={t('common:actions.add')}
          />
        </Dropdown>
      </div>
      <Input
        allowClear
        className="workbench-sidebar-search"
        prefix={<SearchOutlined />}
        placeholder={t('workspace:workbench.searchScenarios')}
        value={filter}
        onChange={(event) => onFilterChange(event.target.value)}
      />
      <div className="workbench-sidebar-scroll">
        {(casesByFolder.get(undefined) ?? []).map((record) => (
          renderScenario(record, 0)
        ))}
        {(childFolders.get(undefined) ?? []).map((folder) => renderFolder(folder, 0))}
        {!cases.length && !folders.length
          ? <Text type="secondary" className="workbench-sidebar-empty">{t('scenario:list.empty')}</Text>
          : null}
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
  onCreate?: () => void
  createTitle?: string
  children: React.ReactNode
}) {
  return (
    <>
      <div className="workbench-sidebar-head">
        <Text strong>{title}</Text>
        {onCreate ? <Button type="text" size="small" icon={<PlusOutlined />} title={createTitle} onClick={onCreate} /> : null}
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
  suites,
  activeRunId,
  activeSuiteRunId,
  filter,
  onFilterChange,
  onOpen,
  onOpenSuiteExecution,
  onOpenScenario,
  onOpenSuite,
}: {
  records: RunRecord[]
  suiteRecords: SuiteRunRecord[]
  cases: CaseRecord[]
  suites: SuiteRecord[]
  activeRunId?: string
  activeSuiteRunId?: string
  filter: string
  onFilterChange: (value: string) => void
  onOpen: (record: RunRecord) => void
  onOpenSuiteExecution: (record: SuiteRunRecord) => void
  onOpenScenario: (record: CaseRecord) => void
  onOpenSuite: (record: SuiteRecord) => void
}) {
  const { t, i18n } = useTranslation(['common', 'execution', 'scenario', 'suite', 'workspace'])
  const casesById = new Map(cases.map((record) => [record.id, record]))
  const suitesById = new Map(suites.map((record) => [record.id, record]))
  return (
    <>
      <div className="workbench-sidebar-head"><Text strong>{t('execution:title')}</Text></div>
      <Input
        allowClear
        className="workbench-sidebar-search"
        prefix={<SearchOutlined />}
        placeholder={t('workspace:workbench.searchExecutions')}
        value={filter}
        onChange={(event) => onFilterChange(event.target.value)}
      />
      <div className="workbench-sidebar-scroll">
        <div className="run-history-group-title">{t('execution:suiteExecutions')}</div>
        {suiteRecords.map((record) => {
          const suite = suitesById.get(record.suiteId)
          return (
            <RunHistoryRow
              key={record.id}
              active={record.id === activeSuiteRunId}
              title={record.suiteName}
              status={record.status}
              startedAt={formatDateTime(record.startedAt, i18n.resolvedLanguage)}
              summary={`${t('execution:scenarioCount', { count: record.runs.length })} · ${t('execution:duration', { duration: record.durationMs })}`}
              onOpen={() => onOpenSuiteExecution(record)}
              action={(
                <Dropdown
                  trigger={['click']}
                  menu={{
                    items: [
                      { key: 'execution', label: t('common:actions.open'), icon: <FileTextOutlined /> },
                      { key: 'suite', label: t('execution:openSuite'), icon: <ProfileOutlined />, disabled: !suite },
                    ],
                    onClick: ({ key }) => {
                      if (key === 'execution') onOpenSuiteExecution(record)
                      if (key === 'suite' && suite) onOpenSuite(suite)
                    },
                  }}
                >
                  <Button
                    aria-label={t('scenario:actions.manage', { name: record.suiteName })}
                    className="workbench-tree-action"
                    icon={<MoreOutlined />}
                    size="small"
                    title={t('scenario:actions.manage', { name: record.suiteName })}
                    type="text"
                    onClick={(event) => event.stopPropagation()}
                    onKeyDown={(event) => event.stopPropagation()}
                  />
                </Dropdown>
              )}
            />
          )
        })}
        {!suiteRecords.length
          ? <Text type="secondary" className="workbench-sidebar-empty">{t('execution:noSuiteExecutions')}</Text>
          : null}
        <div className="run-history-group-title">{t('execution:scenarioExecutions')}</div>
        {records.map((record) => {
          const scenario = casesById.get(record.caseId)
          const title = scenario?.name ?? shortId(record.id)
          return (
            <RunHistoryRow
              key={record.id}
              active={record.id === activeRunId}
              title={title}
              status={record.status}
              startedAt={formatDateTime(record.startedAt, i18n.resolvedLanguage)}
              summary={t('execution:duration', { duration: record.durationMs })}
              onOpen={() => onOpen(record)}
              action={(
                <Dropdown
                  trigger={['click']}
                  menu={{
                    items: [
                      { key: 'execution', label: t('common:actions.open'), icon: <FileTextOutlined /> },
                      { key: 'scenario', label: t('scenario:openScenario'), icon: <ExperimentOutlined />, disabled: !scenario },
                    ],
                    onClick: ({ key }) => {
                      if (key === 'execution') onOpen(record)
                      if (key === 'scenario' && scenario) onOpenScenario(scenario)
                    },
                  }}
                >
                  <Button
                    aria-label={t('scenario:actions.manage', { name: title })}
                    className="workbench-tree-action"
                    icon={<MoreOutlined />}
                    size="small"
                    title={t('scenario:actions.manage', { name: title })}
                    type="text"
                    onClick={(event) => event.stopPropagation()}
                    onKeyDown={(event) => event.stopPropagation()}
                  />
                </Dropdown>
              )}
            />
          )
        })}
        {!records.length
          ? <Text type="secondary" className="workbench-sidebar-empty">{t('execution:noScenarioExecutions')}</Text>
          : null}
      </div>
    </>
  )
}

function RunHistoryRow({
  title,
  status,
  startedAt,
  summary,
  active = false,
  action,
  onOpen,
}: {
  title: string
  status: string
  startedAt: string
  summary: string
  active?: boolean
  action: React.ReactNode
  onOpen: () => void
}) {
  return (
    <div
      role="button"
      tabIndex={0}
      className={active ? 'run-history-row active' : 'run-history-row'}
      onClick={onOpen}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onOpen()
        }
      }}
    >
      <span className="run-history-row-title">
        <span className="workbench-tree-item-name">{title}</span>
        <span className="run-history-row-actions">
          <StatusPill status={status} />
          {action}
        </span>
      </span>
      <span>{startedAt}</span>
      <span>{summary}</span>
    </div>
  )
}

function AssetRow({
  icon,
  title,
  depth = 0,
  active = false,
  action,
  onClick,
}: {
  icon: React.ReactNode
  title: string
  depth?: number
  active?: boolean
  action?: React.ReactNode
  onClick: () => void
}) {
  return (
    <div
      role="button"
      tabIndex={0}
      className={active ? 'workbench-asset-row active' : 'workbench-asset-row'}
      style={{ paddingLeft: 10 + depth * 12 }}
      onClick={onClick}
      onKeyDown={(event) => {
        if (event.key === 'Enter' || event.key === ' ') {
          event.preventDefault()
          onClick()
        }
      }}
    >
      {icon}
      <span className="workbench-tree-item-name">{title}</span>
      {action}
    </div>
  )
}

function tabIcon(type: WorkbenchTabType) {
  if (type === 'scenario') return <ExperimentOutlined />
  if (type === 'suite') return <ProfileOutlined />
  if (type === 'apiSpec') return <ApiOutlined />
  if (type === 'pipelineCatalog') return <ApartmentOutlined />
  if (type === 'suiteRun') return <ProfileOutlined />
  if (type === 'run') return <FileTextOutlined />
  if (type === 'environment') return <GlobalOutlined />
  if (type === 'knowledge') return <ReadOutlined />
  return <KeyOutlined />
}

function isWorkbenchMode(value: string | null): value is WorkbenchMode {
  return value === 'scenarios'
    || value === 'suites'
    || value === 'apis'
    || value === 'processing'
    || value === 'environments'
    || value === 'profiles'
    || value === 'knowledge'
    || value === 'runs'
}

function modeForTabType(type: WorkbenchTabType): WorkbenchMode | undefined {
  if (type === 'scenario') return 'scenarios'
  if (type === 'suite') return 'suites'
  if (type === 'apiSpec') return 'apis'
  if (type === 'pipelineCatalog') return 'processing'
  if (type === 'environment') return 'environments'
  if (type === 'profile') return 'profiles'
  if (type === 'knowledge') return 'knowledge'
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

function caseInputFromRecord(record: CaseRecord, overrides: Partial<SaveCaseInput> = {}): SaveCaseInput {
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
    ...overrides,
  }
}

function suiteInputFromRecord(
  record: SuiteRecord,
  overrides: Partial<SaveSuiteInput> = {},
  preserveCaseIds = true,
): SaveSuiteInput {
  return {
    name: record.name,
    description: record.description ?? '',
    status: record.status,
    cases: record.cases.map((suiteCase) => ({
      ...(preserveCaseIds ? { id: suiteCase.id } : {}),
      caseId: suiteCase.caseId,
      displayName: suiteCase.displayName ?? undefined,
      order: suiteCase.order,
      enabled: suiteCase.enabled,
      variableOverrides: suiteCase.variableOverrides ?? {},
      environmentId: suiteCase.environmentId ?? undefined,
      profileId: suiteCase.profileId ?? undefined,
    })),
    ...overrides,
  }
}

function environmentInputFromRecord(
  record: Environment,
  overrides: Partial<SaveEnvironmentInput> = {},
): SaveEnvironmentInput {
  return {
    name: record.name,
    baseUrl: record.baseUrl,
    endpoints: record.endpoints ?? {},
    variables: record.variables ?? {},
    status: record.status,
    ...overrides,
  }
}

function profileInputFromRecord(
  record: Profile,
  overrides: Partial<SaveProfileInput> = {},
): SaveProfileInput {
  return {
    name: record.name,
    type: record.type,
    senderPublicKey: record.senderPublicKey ?? '',
    senderPrivateKey: record.senderPrivateKey ?? '',
    senderPrivateKeyPassphrase: record.senderPrivateKeyPassphrase ?? '',
    recipientPublicKey: record.recipientPublicKey ?? '',
    recipientPrivateKey: record.recipientPrivateKey ?? '',
    recipientPrivateKeyPassphrase: record.recipientPrivateKeyPassphrase ?? '',
    environmentIds: record.environmentIds ?? [],
    enabled: record.enabled,
    ...overrides,
  }
}

function renameScenarioInDsl(content: string, name: string) {
  const parsed = parseDsl(content)
  return parsed.data ? stringifyDsl({ ...parsed.data, name }) : content
}

function buildFolderOptions(folders: FolderRecord[]) {
  const foldersByParentId = new Map<string | undefined, FolderRecord[]>()
  folders.forEach((folder) => {
    const parentId = folder.parentFolderId ?? undefined
    foldersByParentId.set(parentId, [...(foldersByParentId.get(parentId) ?? []), folder])
  })
  foldersByParentId.forEach((siblings) => siblings.sort(
    (left, right) => left.sortOrder - right.sortOrder || left.name.localeCompare(right.name),
  ))

  const options: Array<{ value: string, label: string }> = []
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

function scenarioCode(name: string) {
  const normalized = name
    .trim()
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-|-$/g, '')
  return `${normalized || 'scenario'}-${Date.now()}`
}
