import { Alert, App, Button, Flex, Form, Input, List, Select, Space, Tag, Typography } from 'antd'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { PlayCircleOutlined, PlusOutlined, SaveOutlined } from '@ant-design/icons'
import { useEffect, useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { CaseDesigner } from '../components/CaseDesigner'
import { platformApi } from '../api/platformApi'
import { StatusPill } from '../components/StatusPill'
import type {
  CaseRecord,
  SaveCaseInput,
} from '../types/platform'

const { Text, Title } = Typography

const emptyDraft: SaveCaseInput = {
  name: '',
  code: '',
  description: '',
  tags: [],
  dslVersion: 'v1',
  content: 'name: New API case\ndescription: \nprofiles: {}\nsteps: []\n',
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

  const selectedCase = useMemo(
    () => casesQuery.data?.find((item) => item.id === selectedCaseId) ?? casesQuery.data?.[0],
    [casesQuery.data, selectedCaseId],
  )

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

  const saveCaseMutation = useMutation({
    mutationFn: async () => {
      if (!selectedProjectId) {
        throw new Error('Select a project first.')
      }
      return isCreating || !selectedCase
        ? platformApi.createCase(selectedProjectId, draft)
        : platformApi.updateCase(selectedProjectId, selectedCase.id, draft)
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

  const handleCreateCase = () => {
    if (!selectedProjectId) {
      return
    }
    setIsCreating(true)
    setDraft(emptyDraft)
    setSearchParams({ projectId: selectedProjectId })
  }

  return (
    <div className="page-shell">
      <section className="hero-band">
        <div>
          <h1>Cases</h1>
          <p>Author YAML, bind execution context, and run directly into evidence view.</p>
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
          <Button icon={<PlusOutlined />} onClick={handleCreateCase} disabled={!selectedProjectId}>
            New Case
          </Button>
        </Space>
      </section>

      <div className="workspace-grid">
        <section className="panel-section list-panel">
          <div className="panel-title-row compact">
            <div>
              <h2 className="panel-title">Case Library</h2>
              <Text type="secondary">{selectedProjectId ? 'Project-scoped YAML assets' : 'Choose a project first'}</Text>
            </div>
          </div>
          {casesQuery.isError ? (
            <Alert
              className="section-alert"
              type="warning"
              showIcon
              message="Unable to load cases from platform-api."
            />
          ) : null}

          <List
            loading={casesQuery.isLoading}
            dataSource={casesQuery.data ?? []}
            locale={{ emptyText: 'No cases yet.' }}
            renderItem={(record) => (
              <List.Item
                className={
                  record.id === selectedCase?.id && !isCreating ? 'case-list-item active' : 'case-list-item'
                }
                onClick={() => handleSelectCase(record)}
              >
                <Flex vertical gap={4}>
                  <Space>
                    <Text strong>{record.name}</Text>
                    <StatusPill status={record.status} />
                  </Space>
                  <Text type="secondary">{record.code}</Text>
                  <Space size={[4, 4]} wrap>
                    {record.tags.map((tag) => (
                      <Tag key={tag}>{tag}</Tag>
                    ))}
                  </Space>
                </Flex>
              </List.Item>
            )}
          />
        </section>

        <section className="panel-section editor-panel">
          <div className="panel-title-row">
            <div>
              <h2 className="panel-title">{isCreating ? 'Create Case' : selectedCase?.name ?? 'Case Detail'}</h2>
              <Text type="secondary">Keep the DSL readable, then pick environment and profile for execution.</Text>
            </div>
            <Space>
              <Button
                icon={<SaveOutlined />}
                type="primary"
                loading={saveCaseMutation.isPending}
                onClick={() => void saveCaseMutation.mutateAsync()}
              >
                Save
              </Button>
            </Space>
          </div>
          {saveCaseMutation.isError ? (
            <Alert className="section-alert" type="error" showIcon message="Failed to save case." />
          ) : null}
          {runMutation.isError ? (
            <Alert className="section-alert" type="error" showIcon message="Failed to start run." />
          ) : null}

          <Form layout="vertical">
            <div className="meta-form-grid">
              <Form.Item label="Name">
                <Input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
              </Form.Item>
              <Form.Item label="Code">
                <Input value={draft.code} onChange={(event) => setDraft({ ...draft, code: event.target.value })} />
              </Form.Item>
              <Form.Item label="Status">
                <Select
                  value={draft.status}
                  options={[
                    { value: 'ACTIVE', label: 'ACTIVE' },
                    { value: 'INACTIVE', label: 'INACTIVE' },
                  ]}
                  onChange={(value) => setDraft({ ...draft, status: value })}
                />
              </Form.Item>
              <Form.Item label="DSL Version">
                <Input
                  value={draft.dslVersion}
                  onChange={(event) => setDraft({ ...draft, dslVersion: event.target.value })}
                />
              </Form.Item>
            </div>
            <Form.Item label="Description">
              <Input.TextArea
                rows={2}
                value={draft.description}
                onChange={(event) => setDraft({ ...draft, description: event.target.value })}
              />
            </Form.Item>
            <Form.Item label="Tags">
              <Select
                mode="tags"
                value={draft.tags}
                onChange={(value) => setDraft({ ...draft, tags: value })}
              />
            </Form.Item>
          </Form>

          <section className="run-band">
            <div>
              <Title level={5}>Run Context</Title>
              <Text type="secondary">Choose runtime bindings before triggering execution.</Text>
            </div>
            <Space wrap>
              <Select
                className="context-select"
                placeholder="Environment"
                value={environmentId}
                options={(environmentsQuery.data ?? []).map((environment) => ({
                  label: environment.name,
                  value: environment.id,
                }))}
                onChange={setEnvironmentId}
              />
              <Select
                className="context-select"
                placeholder="Profile"
                value={profileId}
                options={(profilesQuery.data ?? []).map((profile) => ({
                  label: profile.name,
                  value: profile.id,
                }))}
                onChange={setProfileId}
              />
              <Button
                type="primary"
                icon={<PlayCircleOutlined />}
                loading={runMutation.isPending}
                disabled={!selectedCase || isCreating}
                onClick={() => void runMutation.mutateAsync()}
              >
                Run Case
              </Button>
            </Space>
          </section>

          <CaseDesigner draft={draft} onChange={setDraft} />
        </section>
      </div>
    </div>
  )
}
