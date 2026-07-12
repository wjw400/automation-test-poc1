import {
  Alert,
  App,
  Button,
  Drawer,
  Input,
  Modal,
  Select,
  Space,
  Tag,
  Typography,
} from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import {
  ApiOutlined,
  DeleteOutlined,
  FileTextOutlined,
  FileSearchOutlined,
  PlayCircleOutlined,
  PlusOutlined,
  SaveOutlined,
  SettingOutlined,
} from '@ant-design/icons'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { CaseDesigner } from '../components/CaseDesigner'
import { JsonPane } from '../components/JsonPane'
import { RunDetail } from '../components/RunDetail'
import { StoplightApi } from '../components/StoplightApi'
import { platformApi } from '../api/platformApi'
import { StatusPill } from '../components/StatusPill'
import { parseDsl, stringifyDsl } from '../utils/caseDsl'
import type {
  ApiOperationRecord,
  CaseRecord,
  SaveCaseInput,
} from '../types/platform'

const { Text } = Typography

const emptyDraft: SaveCaseInput = {
  name: '',
  code: '',
  description: '',
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
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [draft, setDraft] = useState<SaveCaseInput>(emptyDraft)
  const [environmentId, setEnvironmentId] = useState<string>()
  const [profileId, setProfileId] = useState<string>()
  const [isCreating, setIsCreating] = useState(false)
  const [isCreateModalOpen, setIsCreateModalOpen] = useState(false)
  const [isSettingsDrawerOpen, setIsSettingsDrawerOpen] = useState(false)
  const [isApiReferenceDrawerOpen, setIsApiReferenceDrawerOpen] = useState(false)
  const [createOperationId, setCreateOperationId] = useState<string>()
  const [selectedEvidenceStepIndex, setSelectedEvidenceStepIndex] = useState(1)
  const [caseFilter, setCaseFilter] = useState('')

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
      const haystack = [record.name, record.code, record.description, ...(record.tags ?? [])]
        .join(' ')
        .toLowerCase()
      return haystack.includes(keyword)
    })
  }, [caseFilter, casesQuery.data])

  useEffect(() => {
    if (selectedCase && !isCreating) {
      setDraft({
        name: selectedCase.name,
        code: selectedCase.code,
        description: selectedCase.description ?? '',
        tags: selectedCase.tags ?? [],
        dslVersion: selectedCase.dslVersion,
        content: selectedCase.content,
        status: selectedCase.status,
      })
    }
  }, [selectedCase, isCreating])

  const parsedDraft = useMemo(() => parseDsl(draft.content), [draft.content])
  const draftSteps = Array.isArray(parsedDraft.data?.steps) ? parsedDraft.data.steps : []
  const requestedStepIndex = selectedStepParam == null ? -1 : Number(selectedStepParam)
  const activeStepIndex = Number.isInteger(requestedStepIndex)
    ? Math.min(Math.max(requestedStepIndex, 0), Math.max(draftSteps.length - 1, 0))
    : 0
  const selectedEditorView = selectedStepParam != null && draftSteps[activeStepIndex] ? 'step' : 'case'
  const selectedDraftStep = draftSteps[activeStepIndex]

  const selectedOperation = useMemo(() => {
    if (!selectedDraftStep) {
      return null
    }
    const operations = operationsQuery.data ?? []
    const operationRefId = selectedDraftStep.operation?.id
    if (operationRefId) {
      return operations.find((item) => item.id === operationRefId) ?? null
    }
    const operationId = selectedDraftStep.operation?.operationId
    if (operationId) {
      return operations.find((item) => item.operationId === operationId) ?? null
    }
    return null
  }, [operationsQuery.data, selectedDraftStep])

  const latestCaseRun = useMemo(() => {
    if (!selectedCase?.id) {
      return undefined
    }
    return (runsQuery.data ?? [])
      .filter((run) => run.caseId === selectedCase.id)
      .sort((left, right) => new Date(right.startedAt).getTime() - new Date(left.startedAt).getTime())[0]
  }, [runsQuery.data, selectedCase?.id])
  const latestCaseRunFirstStepIndex = latestCaseRun?.steps?.[0]?.index

  useEffect(() => {
    if (selectedEditorView === 'case' && latestCaseRunFirstStepIndex) {
      setSelectedEvidenceStepIndex(latestCaseRunFirstStepIndex)
    }
  }, [latestCaseRun?.id, latestCaseRunFirstStepIndex, selectedEditorView])

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
        } else {
          setSearchParams({})
          setDraft(emptyDraft)
        }
        setIsCreating(false)
      }

      void message.success('Case deleted')
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
      void message.success('Run started')
      navigate(`/runs?runId=${run.id}`)
    },
  })

  const handleProjectChange = (projectId: string) => {
    setSearchParams({ projectId })
    setEnvironmentId(undefined)
    setProfileId(undefined)
    setIsCreating(false)
  }

  const handleSelectCase = (record: CaseRecord) => {
    setSearchParams({ projectId: selectedProjectId!, caseId: record.id })
    setIsCreating(false)
  }

  const handleSelectStep = (record: CaseRecord, stepIndex: number) => {
    setSearchParams({ projectId: selectedProjectId!, caseId: record.id, step: String(stepIndex) })
    setSelectedEvidenceStepIndex(stepIndex + 1)
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
    setIsCreating(true)
    setSearchParams({ projectId: selectedProjectId! })
    setIsCreateModalOpen(false)
  }

  return (
    <div className="scenario-page">
      <div className="scenario-shell">
        <aside className="scenario-sidebar">
          <div className="scenario-sidebar-head">
            <Text className="scenario-nav-label">Scenario Explorer</Text>
            <Button type="text" size="small" icon={<PlusOutlined />} onClick={openCreateCaseModal} disabled={!selectedProjectId} />
          </div>

          <Select
            className="scenario-project-select"
            placeholder="Select project"
            value={selectedProjectId}
            options={(projectsQuery.data ?? []).map((project) => ({
              label: project.name,
              value: project.id,
            }))}
            onChange={handleProjectChange}
          />

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
            {filteredCases.map((record) => {
              const isSelectedCase = record.id === selectedCase?.id && !isCreating
              const steps = getCaseSteps(isSelectedCase ? { ...record, content: draft.content } : record)
              return (
                <div key={record.id} className="scenario-tree-group">
                  <div
                    role="button"
                    tabIndex={0}
                    className={isSelectedCase && selectedEditorView === 'case' ? 'scenario-case-row active' : 'scenario-case-row'}
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
                      <span className="scenario-case-path">{steps.length} step{steps.length === 1 ? '' : 's'}</span>
                    </span>
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
                  <div className="scenario-tree-children">
                    {steps.length ? steps.map((step, index) => {
                      const active = isSelectedCase && selectedEditorView === 'step' && activeStepIndex === index
                      return (
                        <div
                          key={`${record.id}-step-${index}`}
                          role="button"
                          tabIndex={0}
                          className={active ? 'scenario-step-row active' : 'scenario-step-row'}
                          onClick={() => handleSelectStep(record, index)}
                          onKeyDown={(event) => {
                            if (event.key === 'Enter' || event.key === ' ') {
                              event.preventDefault()
                              handleSelectStep(record, index)
                            }
                          }}
                        >
                          <ApiOutlined className="scenario-tree-icon" />
                          <span className={`method-pill method-${step.method.toLowerCase()}`}>{step.method}</span>
                          <span className="scenario-case-copy">
                            <span className="scenario-case-name">{step.name}</span>
                            <span className="scenario-case-path">{step.path}</span>
                          </span>
                        </div>
                      )
                    }) : (
                      <div className="scenario-empty-step-row">No steps</div>
                    )}
                  </div>
                </div>
              )
            })}
          </div>
        </aside>

        <main className="scenario-main">
          <section className="scenario-topbar">
            <div className="scenario-titlebar">
              <Text type="secondary">{selectedCase?.code ?? 'new-case'}</Text>
              <Input
                className="scenario-title-input"
                value={draft.name}
                placeholder="Scenario name"
                onChange={(event) => setDraft({ ...draft, name: event.target.value })}
              />
            </div>

            <Space wrap size={[8, 8]} className="scenario-toolbar-actions">
              <Select
                allowClear
                className="scenario-context-select"
                placeholder="Environment"
                value={environmentId}
                options={(environmentsQuery.data ?? []).map((environment) => ({
                  label: environment.name,
                  value: environment.id,
                }))}
                onChange={setEnvironmentId}
              />
              <Select
                allowClear
                className="scenario-context-select"
                placeholder="Profile"
                value={profileId}
                options={(profilesQuery.data ?? []).map((profile) => ({
                  label: profile.name,
                  value: profile.id,
                }))}
                onChange={setProfileId}
              />
              <Button icon={<SettingOutlined />} onClick={() => setIsSettingsDrawerOpen(true)} />
              <Button icon={<FileSearchOutlined />} onClick={() => setIsApiReferenceDrawerOpen(true)} disabled={!selectedOperation} />
              <Button
                icon={<SaveOutlined />}
                loading={saveCaseMutation.isPending}
                onClick={() => void saveCaseMutation.mutateAsync()}
              >
                Save
              </Button>
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                loading={runMutation.isPending}
                disabled={!selectedCase || isCreating}
                onClick={() => void runMutation.mutateAsync()}
              >
                Send
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

          <section className="scenario-response-panel">
            <div className="scenario-response-head">
              <Space>
                <Text strong>Response</Text>
                {latestCaseRun ? <StatusPill status={latestCaseRun.status} /> : <Text type="secondary">History</Text>}
              </Space>
              {latestCaseRun ? <Text type="secondary">{latestCaseRun.durationMs} ms</Text> : null}
            </div>

            {latestCaseRun ? (
              <div className="scenario-response-detail">
                <RunDetail
                  run={latestCaseRun}
                  selectedStepIndex={selectedEvidenceStepIndex}
                  onSelectStep={setSelectedEvidenceStepIndex}
                />
              </div>
            ) : (
              <div className="scenario-response-empty">
                <Text type="secondary">No response yet.</Text>
              </div>
            )}
          </section>
        </main>

      </div>

      <Drawer
        open={isSettingsDrawerOpen}
        width={460}
        title="Case Settings"
        onClose={() => setIsSettingsDrawerOpen(false)}
      >
        <div className="drawer-stack">
          <div className="designer-field">
            <Text className="designer-label">Code</Text>
            <Input
              value={draft.code}
              placeholder="Generated automatically if empty"
              onChange={(event) => setDraft({ ...draft, code: event.target.value })}
            />
          </div>
          <div className="designer-field">
            <Text className="designer-label">Status</Text>
            <Select
              value={draft.status}
              options={[
                { value: 'ACTIVE', label: 'ACTIVE' },
                { value: 'INACTIVE', label: 'INACTIVE' },
              ]}
              onChange={(value) => setDraft({ ...draft, status: value })}
            />
          </div>
          <div className="designer-field">
            <Text className="designer-label">Tags</Text>
            <Select
              mode="tags"
              value={draft.tags}
              onChange={(value) => setDraft({ ...draft, tags: value })}
            />
          </div>
          <div className="designer-field">
            <Text className="designer-label">Description</Text>
            <Input.TextArea
              rows={4}
              value={draft.description}
              placeholder="Optional note for the scenario"
              onChange={(event) => setDraft({ ...draft, description: event.target.value })}
            />
          </div>
        </div>
      </Drawer>

      <Drawer
        open={isApiReferenceDrawerOpen}
        width={540}
        title="API Reference"
        onClose={() => setIsApiReferenceDrawerOpen(false)}
      >
        {selectedProjectId && selectedOperation ? (
          <div className="drawer-stack">
            <div>
              <Space wrap size={[8, 8]}>
                <Tag color="blue">{selectedOperation.method}</Tag>
                <Text code>{selectedOperation.path}</Text>
              </Space>
              <Text strong>{selectedOperation.summary || selectedOperation.operationId || 'Selected operation'}</Text>
            </div>
            <JsonPane value={selectedOperation.requestBody} emptyLabel="No request schema" />
            <JsonPane value={selectedOperation.responses} emptyLabel="No response schema" />
            <div className="mini-stoplight-shell">
              <StoplightApi
                apiDescriptionUrl={platformApi.getApiSpecContentUrl(selectedProjectId, selectedOperation.apiSpecId)}
              />
            </div>
          </div>
        ) : (
          <div className="panel-empty">Select a step with a bound operation to inspect its API reference.</div>
        )}
      </Drawer>

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
        name: operation.summary || `${operation.method} ${operation.path}`,
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
    dslVersion: draft.dslVersion || 'v1',
    status: draft.status || 'ACTIVE',
  }
}

function getCaseSteps(record: CaseRecord) {
  const parsed = parseDsl(record.content)
  const steps = Array.isArray(parsed.data?.steps) ? parsed.data.steps : []
  return steps.map((step: any, index: number) => ({
    method: String(step?.operation?.method ?? 'HTTP').toUpperCase(),
    name: String(step?.name ?? `Step ${index + 1}`),
    path: String(step?.operation?.path ?? 'Select operation'),
  }))
}

function slugify(value: string) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60) || 'api-case'
}
