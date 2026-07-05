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
import { FileSearchOutlined, PlayCircleOutlined, PlusOutlined, SaveOutlined, SettingOutlined } from '@ant-design/icons'
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
  const { message } = App.useApp()
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
  const [activeStepIndex, setActiveStepIndex] = useState(0)
  const [selectedEvidenceStepIndex, setSelectedEvidenceStepIndex] = useState(1)
  const [caseFilter, setCaseFilter] = useState('')

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const selectedProjectId = searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id
  const selectedCaseId = searchParams.get('caseId')

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
    if (latestCaseRunFirstStepIndex) {
      setSelectedEvidenceStepIndex(latestCaseRunFirstStepIndex)
    }
  }, [latestCaseRun?.id, latestCaseRunFirstStepIndex])

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
      setSearchParams({ projectId: selectedProjectId!, caseId: savedCase.id })
      void message.success('Case saved')
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
    setActiveStepIndex(0)
    setSearchParams({ projectId: selectedProjectId! })
    setIsCreateModalOpen(false)
  }

  return (
    <div className="scenario-page">
      <div className="scenario-shell">
        <aside className="scenario-sidebar">
          <div className="scenario-sidebar-head">
            <Text className="scenario-nav-label">Collections</Text>
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

          <div className="scenario-case-list">
            {casesQuery.isLoading ? <div className="scenario-muted-row">Loading cases...</div> : null}
            {!casesQuery.isLoading && filteredCases.length === 0 ? <div className="scenario-muted-row">No cases yet.</div> : null}
            {filteredCases.map((record) => {
              const summary = summarizeCase(record)
              return (
                <button
                  key={record.id}
                  type="button"
                  className={record.id === selectedCase?.id && !isCreating ? 'scenario-case-row active' : 'scenario-case-row'}
                  onClick={() => handleSelectCase(record)}
                >
                  <span className={`method-pill method-${summary.method.toLowerCase()}`}>{summary.method}</span>
                  <span className="scenario-case-copy">
                    <span className="scenario-case-name">{record.name}</span>
                    <span className="scenario-case-path">{summary.path}</span>
                  </span>
                </button>
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

          <CaseDesigner
            draft={draft}
            onChange={setDraft}
            operations={operationsQuery.data ?? []}
            onActiveStepChange={setActiveStepIndex}
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

        <aside className="scenario-side-panel">
          <section className="scenario-side-section">
            <Text strong>Operation</Text>
            {selectedOperation ? (
              <div className="scenario-side-stack">
                <Space size={[6, 6]} wrap>
                  <Tag color="blue">{selectedOperation.method}</Tag>
                  <Text code>{selectedOperation.path}</Text>
                </Space>
                <Text>{selectedOperation.summary || selectedOperation.operationId || 'Selected operation'}</Text>
                <Button size="small" icon={<FileSearchOutlined />} onClick={() => setIsApiReferenceDrawerOpen(true)}>
                  Reference
                </Button>
              </div>
            ) : (
              <Text type="secondary">No operation selected.</Text>
            )}
          </section>

          <section className="scenario-side-section">
            <Text strong>Case</Text>
            <div className="scenario-side-stack">
              <StatusPill status={draft.status} />
              <Text type="secondary">{draft.tags.length ? draft.tags.join(', ') : 'No tags'}</Text>
              <Text type="secondary">{draftSteps.length} step{draftSteps.length === 1 ? '' : 's'}</Text>
            </div>
          </section>
        </aside>
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

function summarizeCase(record: CaseRecord) {
  const parsed = parseDsl(record.content)
  const firstStep = Array.isArray(parsed.data?.steps) ? parsed.data.steps[0] : null
  const method = String(firstStep?.operation?.method ?? 'CASE').toUpperCase()
  const path = String(firstStep?.operation?.path ?? record.code)
  return {
    method,
    path,
  }
}

function slugify(value: string) {
  return value
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, '-')
    .replace(/^-+|-+$/g, '')
    .slice(0, 60) || 'api-case'
}
