import {
  ArrowDownOutlined,
  ArrowUpOutlined,
  CodeOutlined,
  DeleteOutlined,
  PlusOutlined,
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
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { platformApi } from '../api/platformApi'
import { StatusPill } from '../components/StatusPill'
import type {
  SaveSuiteCaseInput,
  SaveSuiteInput,
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

export function SuitesPage() {
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [draft, setDraft] = useState<SuiteDraft>(emptyDraft)
  const [isCreating, setIsCreating] = useState(false)
  const [caseToAddId, setCaseToAddId] = useState<string>()
  const [variablesEditor, setVariablesEditor] = useState<{ index: number; text: string }>()

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const selectedProjectId = searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id
  const selectedSuiteId = searchParams.get('suiteId')

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

  const selectedSuite = useMemo(
    () => suitesQuery.data?.find((suite) => suite.id === selectedSuiteId) ?? suitesQuery.data?.[0],
    [selectedSuiteId, suitesQuery.data],
  )

  useEffect(() => {
    if (selectedSuite && !isCreating) {
      setDraft(toDraft(selectedSuite))
    }
  }, [isCreating, selectedSuite])

  const caseLookup = useMemo(() => {
    return new Map((casesQuery.data ?? []).map((record) => [record.id, record]))
  }, [casesQuery.data])

  const saveSuiteMutation = useMutation({
    mutationFn: async () => {
      if (!selectedProjectId) {
        throw new Error('Select a project first.')
      }
      const payload = normalizeDraftForSave(draft)
      return isCreating || !selectedSuite
        ? platformApi.createSuite(selectedProjectId, payload)
        : platformApi.updateSuite(selectedProjectId, selectedSuite.id, payload)
    },
    onSuccess: async (savedSuite) => {
      await queryClient.invalidateQueries({ queryKey: ['suites', selectedProjectId] })
      setIsCreating(false)
      setSearchParams({ projectId: selectedProjectId!, suiteId: savedSuite.id })
      void message.success('Suite saved')
    },
  })

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
      setSearchParams(selectedProjectId ? { projectId: selectedProjectId } : {})
      void message.success('Suite deleted')
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
      name: 'New Suite',
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
          alias: selectedCase.name,
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
      title: 'Delete suite?',
      content: `This removes "${selectedSuite.name}" from the platform catalog.`,
      okText: 'Delete',
      okButtonProps: { danger: true },
      onOk: () => deleteSuiteMutation.mutateAsync(),
    })
  }

  const saveVariableOverrides = () => {
    if (!variablesEditor) {
      return
    }
    try {
      const parsed = variablesEditor.text.trim() ? JSON.parse(variablesEditor.text) : {}
      if (!parsed || Array.isArray(parsed) || typeof parsed !== 'object') {
        throw new Error('Variable overrides must be a JSON object.')
      }
      updateSuiteCase(variablesEditor.index, {
        variableOverrides: parsed as Record<string, unknown>,
      })
      setVariablesEditor(undefined)
    } catch (error) {
      void message.error(error instanceof Error ? error.message : 'Invalid JSON object.')
    }
  }

  const getCaseDisplay = (suiteCase: SaveSuiteCaseInput) => {
    const catalogCase = caseLookup.get(suiteCase.caseId)
    const suiteCaseRecord = selectedSuite?.cases.find((record) => record.caseId === suiteCase.caseId)
    return {
      name: catalogCase?.name ?? suiteCaseRecord?.caseName ?? 'Unknown case',
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
      title: 'Case',
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
      title: 'Alias',
      dataIndex: 'alias',
      width: 210,
      render: (value: string | undefined, _record, index) => (
        <Input
          value={value}
          placeholder="Optional run label"
          onChange={(event) => updateSuiteCase(index, { alias: event.target.value })}
        />
      ),
    },
    {
      title: 'Environment',
      dataIndex: 'environmentId',
      width: 180,
      render: (value: string | undefined, _record, index) => (
        <Select
          allowClear
          className="suite-case-select"
          placeholder="Suite default"
          value={value}
          options={(environmentsQuery.data ?? []).map((environment) => ({
            label: environment.name,
            value: environment.id,
          }))}
          onChange={(nextValue) => updateSuiteCase(index, { environmentId: nextValue })}
        />
      ),
    },
    {
      title: 'Profile',
      dataIndex: 'profileId',
      width: 180,
      render: (value: string | undefined, _record, index) => (
        <Select
          allowClear
          className="suite-case-select"
          placeholder="Suite default"
          value={value}
          options={(profilesQuery.data ?? []).map((profile) => ({
            label: profile.name,
            value: profile.id,
          }))}
          onChange={(nextValue) => updateSuiteCase(index, { profileId: nextValue })}
        />
      ),
    },
    {
      title: 'Enabled',
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
            onClick={() =>
              setVariablesEditor({
                index,
                text: JSON.stringify(record.variableOverrides ?? {}, null, 2),
              })
            }
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

  return (
    <div className="page-shell">
      <section className="hero-band">
        <div>
          <h1>Suites</h1>
          <p>Group reusable cases into ordered suites without changing the cases themselves.</p>
        </div>
        <Space wrap>
          <Select
            className="project-picker"
            placeholder="Select project"
            value={selectedProjectId}
            options={(projectsQuery.data ?? []).map((project) => ({
              label: project.name,
              value: project.id,
            }))}
            onChange={handleProjectChange}
          />
          <Button icon={<PlusOutlined />} onClick={handleCreateSuite} disabled={!selectedProjectId}>
            New Suite
          </Button>
        </Space>
      </section>

      <div className="suites-layout">
        <section className="panel-section suites-library-panel">
          <div className="panel-title-row compact">
            <div>
              <h2 className="panel-title">Suite Library</h2>
              <Text type="secondary">
                {selectedProjectId ? 'Reusable execution groups for the current project' : 'Choose a project first'}
              </Text>
            </div>
          </div>

          {suitesQuery.isError ? (
            <Alert className="section-alert" type="warning" showIcon message="Unable to load suites from platform-api." />
          ) : null}

          <div className="suite-list">
            {suitesQuery.isLoading ? (
              <div className="panel-empty">Loading suites...</div>
            ) : null}
            {!suitesQuery.isLoading && (suitesQuery.data ?? []).length === 0 ? (
              <div className="panel-empty">No suites yet.</div>
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
                    {suite.cases.length} case{suite.cases.length === 1 ? '' : 's'}
                  </Text>
                </Flex>
              </button>
            ))}
          </div>
        </section>

        <section className="suite-editor-column">
          {canEdit ? (
            <>
              <section className="panel-section">
                <div className="suite-editor-head">
                  <div className="suite-title-field">
                    <Text className="designer-label">Suite Name</Text>
                    <Input
                      size="large"
                      value={draft.name}
                      placeholder="Name this suite"
                      onChange={(event) => setDraft({ ...draft, name: event.target.value })}
                    />
                  </div>
                  <Space wrap align="end">
                    <div className="suite-status-field">
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
                    <Button
                      icon={<SaveOutlined />}
                      type="primary"
                      loading={saveSuiteMutation.isPending}
                      onClick={() => void saveSuiteMutation.mutateAsync()}
                    >
                      Save
                    </Button>
                    <Button
                      danger
                      icon={<DeleteOutlined />}
                      disabled={isCreating || !selectedSuite}
                      loading={deleteSuiteMutation.isPending}
                      onClick={confirmDeleteSuite}
                    >
                      Delete
                    </Button>
                  </Space>
                </div>

                <div className="suite-description-field">
                  <Text className="designer-label">Description</Text>
                  <Input.TextArea
                    rows={3}
                    value={draft.description}
                    placeholder="Optional note for why these cases belong together"
                    onChange={(event) => setDraft({ ...draft, description: event.target.value })}
                  />
                </div>

                {saveSuiteMutation.isError ? (
                  <Alert className="section-alert" type="error" showIcon message="Failed to save suite." />
                ) : null}
              </section>

              <section className="panel-section">
                <div className="panel-title-row compact">
                  <div>
                    <h2 className="panel-title">Case Order</h2>
                    <Text type="secondary">Suites reference existing cases and can override runtime context per entry.</Text>
                  </div>
                </div>

                <div className="suite-case-toolbar">
                  <Select
                    showSearch
                    allowClear
                    className="suite-add-case-select"
                    placeholder="Add existing case"
                    optionFilterProp="label"
                    value={caseToAddId}
                    options={(casesQuery.data ?? []).map((record) => ({
                      label: `${record.name} - ${record.code}`,
                      value: record.id,
                    }))}
                    onChange={setCaseToAddId}
                  />
                  <Button icon={<PlusOutlined />} onClick={handleAddCase} disabled={!caseToAddId}>
                    Add Case
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
                    emptyText: 'Add cases to define this suite.',
                  }}
                />
              </section>
            </>
          ) : (
            <section className="panel-section">
              <div className="panel-empty">
                Select a suite from the library or create a new suite for this project.
              </div>
            </section>
          )}
        </section>
      </div>

      <Modal
        title="Variable Overrides"
        open={Boolean(variablesEditor)}
        onCancel={() => setVariablesEditor(undefined)}
        onOk={saveVariableOverrides}
        okText="Apply"
      >
        <div className="api-import-stack">
          <Text type="secondary">Optional JSON object passed to this suite case entry.</Text>
          <Input.TextArea
            rows={10}
            value={variablesEditor?.text}
            onChange={(event) =>
              setVariablesEditor((current) => current ? { ...current, text: event.target.value } : current)
            }
          />
        </div>
      </Modal>
    </div>
  )
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
        alias: suiteCase.alias ?? '',
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
        alias: suiteCase.alias?.trim(),
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
