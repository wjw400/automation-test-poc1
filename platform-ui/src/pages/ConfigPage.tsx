import { Alert, App, Button, Drawer, Form, Input, Select, Switch, Table, Tabs, Typography } from 'antd'
import { EditOutlined, PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { platformApi } from '../api/platformApi'
import { StatusPill } from '../components/StatusPill'
import type {
  Environment,
  Profile,
  SaveEnvironmentInput,
  SaveProfileInput,
} from '../types/platform'

const { Text } = Typography

const emptyEnvironment: SaveEnvironmentInput = {
  name: '',
  baseUrl: '',
  endpoints: {},
  variables: {},
  status: 'ACTIVE',
}

const emptyProfile: SaveProfileInput = {
  name: '',
  type: 'PGP',
  senderPublicKey: '',
  senderPrivateKey: '',
  senderPrivateKeyPassphrase: '',
  recipientPublicKey: '',
  recipientPrivateKey: '',
  recipientPrivateKeyPassphrase: '',
  environmentIds: [],
  enabled: true,
}

export function ConfigPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [environmentDraft, setEnvironmentDraft] = useState<SaveEnvironmentInput>(emptyEnvironment)
  const [profileDraft, setProfileDraft] = useState<SaveProfileInput>(emptyProfile)
  const [editingEnvironmentId, setEditingEnvironmentId] = useState<string>()
  const [editingProfileId, setEditingProfileId] = useState<string>()
  const [showEnvironmentDrawer, setShowEnvironmentDrawer] = useState(false)
  const [showProfileDrawer, setShowProfileDrawer] = useState(false)

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const selectedProjectId = searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id
  const requestedTab = searchParams.get('tab')
  const activeTab = requestedTab === 'profiles'
    ? requestedTab
    : 'environments'

  const updateSearchParams = (nextValues: Record<string, string>) => {
    const next = new URLSearchParams(searchParams)
    Object.entries(nextValues).forEach(([key, value]) => next.set(key, value))
    setSearchParams(next)
  }

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

  const apiSpecsQuery = useQuery({
    queryKey: ['api-specs', selectedProjectId],
    queryFn: () => platformApi.listApiSpecs(selectedProjectId!),
    enabled: Boolean(selectedProjectId),
  })

  const saveEnvironmentMutation = useMutation({
    mutationFn: () => editingEnvironmentId
      ? platformApi.updateEnvironment(selectedProjectId!, editingEnvironmentId, environmentDraft)
      : platformApi.createEnvironment(selectedProjectId!, environmentDraft),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['environments', selectedProjectId] })
      setShowEnvironmentDrawer(false)
      setEditingEnvironmentId(undefined)
      setEnvironmentDraft(emptyEnvironment)
      void message.success(editingEnvironmentId ? 'Environment updated' : 'Environment created')
    },
  })

  const saveProfileMutation = useMutation({
    mutationFn: () => editingProfileId
      ? platformApi.updateProfile(selectedProjectId!, editingProfileId, profileDraft)
      : platformApi.createProfile(selectedProjectId!, profileDraft),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['profiles', selectedProjectId] })
      setShowProfileDrawer(false)
      setEditingProfileId(undefined)
      setProfileDraft(emptyProfile)
      void message.success(editingProfileId ? 'Profile updated' : 'Profile created')
    },
  })

  const openEnvironmentDrawer = (environment?: Environment) => {
    setEditingEnvironmentId(environment?.id)
    setEnvironmentDraft(environment
      ? {
          name: environment.name,
          baseUrl: environment.baseUrl,
          endpoints: environment.endpoints ?? {},
          variables: environment.variables,
          status: environment.status,
        }
      : emptyEnvironment)
    setShowEnvironmentDrawer(true)
  }

  const openProfileDrawer = (profile?: Profile) => {
    setEditingProfileId(profile?.id)
    setProfileDraft(profile
      ? {
          name: profile.name,
          type: profile.type,
          senderPublicKey: profile.senderPublicKey ?? '',
          senderPrivateKey: profile.senderPrivateKey ?? '',
          senderPrivateKeyPassphrase: profile.senderPrivateKeyPassphrase ?? '',
          recipientPublicKey: profile.recipientPublicKey ?? '',
          recipientPrivateKey: profile.recipientPrivateKey ?? '',
          recipientPrivateKeyPassphrase: profile.recipientPrivateKeyPassphrase ?? '',
          environmentIds: profile.environmentIds,
          enabled: profile.enabled,
        }
      : emptyProfile)
    setShowProfileDrawer(true)
  }

  const variablePreview = useMemo(
    () => JSON.stringify(environmentDraft.variables, null, 2),
    [environmentDraft.variables],
  )

  return (
    <div className="page-shell">
      <section className="hero-band">
        <div>
          <h1>Configuration</h1>
          <p>Manage deployment environments, service endpoints, and reusable identities.</p>
        </div>
        <Select
          className="project-picker"
          placeholder="Select project"
          value={selectedProjectId}
          options={(projectsQuery.data ?? []).map((project) => ({
            label: project.name,
            value: project.id,
          }))}
          onChange={(value) => updateSearchParams({ projectId: value })}
        />
      </section>

      <Tabs
        className="config-tabs"
        activeKey={activeTab}
        onChange={(tab) => updateSearchParams({ tab })}
        items={[
          {
            key: 'environments',
            label: 'Environments',
            children: (
              <section className="panel-section">
          <div className="panel-title-row compact">
            <div>
              <h2 className="panel-title">Environments</h2>
              <Text type="secondary">Service endpoints and runtime variables for each deployment.</Text>
            </div>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openEnvironmentDrawer()}
              disabled={!selectedProjectId}
            >
              New Environment
            </Button>
          </div>
          {environmentsQuery.isError ? (
            <Alert className="section-alert" type="warning" showIcon message="Unable to load environments." />
          ) : null}

          <Table
            rowKey="id"
            loading={environmentsQuery.isLoading}
            pagination={false}
            columns={[
              { title: 'Name', dataIndex: 'name' },
              { title: 'Default Base URL', dataIndex: 'baseUrl' },
              {
                title: 'API Endpoints',
                dataIndex: 'endpoints',
                render: (value: Record<string, string>) => (
                  <Text type="secondary">{formatEndpointCount(value)}</Text>
                ),
              },
              {
                title: 'Variables',
                dataIndex: 'variables',
                render: (value: Record<string, string>) => (
                  <Text type="secondary">{formatVariableCount(value)}</Text>
                ),
              },
              {
                title: 'Status',
                dataIndex: 'status',
                render: (status: string) => <StatusPill status={status} />,
              },
              {
                title: '',
                key: 'actions',
                width: 56,
                render: (_value: unknown, environment: Environment) => (
                  <Button
                    type="text"
                    icon={<EditOutlined />}
                    aria-label={`Edit ${environment.name}`}
                    title="Edit environment"
                    onClick={() => openEnvironmentDrawer(environment)}
                  />
                ),
              },
            ]}
            dataSource={environmentsQuery.data ?? []}
          />
              </section>
            ),
          },
          {
            key: 'profiles',
            label: 'Profiles',
            children: (
              <section className="panel-section">
          <div className="panel-title-row compact">
            <div>
              <h2 className="panel-title">Profiles</h2>
              <Text type="secondary">Reusable identities and the environments where they are valid.</Text>
            </div>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => openProfileDrawer()}
              disabled={!selectedProjectId}
            >
              New Profile
            </Button>
          </div>
          {profilesQuery.isError ? (
            <Alert className="section-alert" type="warning" showIcon message="Unable to load profiles." />
          ) : null}

          <Table
            rowKey="id"
            loading={profilesQuery.isLoading}
            pagination={false}
            columns={[
              { title: 'Name', dataIndex: 'name' },
              { title: 'Type', dataIndex: 'type' },
              {
                title: 'Available Environments',
                dataIndex: 'environmentIds',
                render: (environmentIds: string[]) => (
                  <Text type="secondary">
                    {formatEnvironmentNames(environmentIds, environmentsQuery.data ?? [])}
                  </Text>
                ),
              },
              {
                title: 'Enabled',
                dataIndex: 'enabled',
                render: (value: boolean) => <StatusPill status={value ? 'ACTIVE' : 'INACTIVE'} />,
              },
              {
                title: '',
                key: 'actions',
                width: 56,
                render: (_value: unknown, profile: Profile) => (
                  <Button
                    type="text"
                    icon={<EditOutlined />}
                    aria-label={`Edit ${profile.name}`}
                    title="Edit profile"
                    onClick={() => openProfileDrawer(profile)}
                  />
                ),
              },
            ]}
            dataSource={profilesQuery.data ?? []}
          />
              </section>
            ),
          },
        ]}
      />

      <Drawer
        open={showEnvironmentDrawer}
        size="large"
        title={editingEnvironmentId ? 'Edit Environment' : 'New Environment'}
        onClose={() => {
          setShowEnvironmentDrawer(false)
          setEditingEnvironmentId(undefined)
          setEnvironmentDraft(emptyEnvironment)
        }}
        extra={
          <Button
            type="primary"
            loading={saveEnvironmentMutation.isPending}
            disabled={!environmentDraft.name.trim() || !environmentDraft.baseUrl.trim()}
            onClick={() => void saveEnvironmentMutation.mutateAsync()}
          >
            Save
          </Button>
        }
      >
        <Form layout="vertical">
          <Form.Item label="Name">
            <Input
              value={environmentDraft.name}
              onChange={(event) =>
                setEnvironmentDraft({ ...environmentDraft, name: event.target.value })
              }
            />
          </Form.Item>
          <Form.Item
            label="Default Base URL"
            extra="Used when an API Spec does not have an explicit endpoint binding."
          >
            <Input
              value={environmentDraft.baseUrl}
              onChange={(event) =>
                setEnvironmentDraft({ ...environmentDraft, baseUrl: event.target.value })
              }
            />
          </Form.Item>
          <Form.Item
            label="API Endpoint Bindings"
            extra="Each operation resolves its base URL from the API Spec it belongs to."
          >
            <div className="endpoint-binding-list">
              {(apiSpecsQuery.data ?? []).map((apiSpec) => (
                <div className="endpoint-binding-row" key={apiSpec.id}>
                  <div className="endpoint-binding-name">
                    <strong>{apiSpec.title || apiSpec.name}</strong>
                    <Text type="secondary">{apiSpec.name}</Text>
                  </div>
                  <Input
                    aria-label={`${apiSpec.name} base URL`}
                    placeholder={environmentDraft.baseUrl || 'https://api.example.com'}
                    value={environmentDraft.endpoints[apiSpec.id] ?? ''}
                    onChange={(event) => {
                      const endpoints = { ...environmentDraft.endpoints }
                      const value = event.target.value
                      if (value) {
                        endpoints[apiSpec.id] = value
                      } else {
                        delete endpoints[apiSpec.id]
                      }
                      setEnvironmentDraft({ ...environmentDraft, endpoints })
                    }}
                  />
                </div>
              ))}
              {!apiSpecsQuery.data?.length ? (
                <Text type="secondary">Import an API Spec to configure service-specific endpoints.</Text>
              ) : null}
            </div>
          </Form.Item>
          <Form.Item label="Status">
            <Select
              value={environmentDraft.status}
              options={[
                { value: 'ACTIVE', label: 'ACTIVE' },
                { value: 'INACTIVE', label: 'INACTIVE' },
              ]}
              onChange={(value) => setEnvironmentDraft({ ...environmentDraft, status: value })}
            />
          </Form.Item>
          <Form.Item label="Variables" extra="Paste a JSON object for runtime variables. A structured editor can replace this later.">
            <Input.TextArea
              rows={8}
              value={variablePreview}
              onChange={(event) => {
                try {
                  const parsed = JSON.parse(event.target.value) as Record<string, string>
                  setEnvironmentDraft({ ...environmentDraft, variables: parsed })
                } catch {
                  setEnvironmentDraft({
                    ...environmentDraft,
                    variables: environmentDraft.variables,
                  })
                }
              }}
            />
          </Form.Item>
        </Form>
      </Drawer>

      <Drawer
        open={showProfileDrawer}
        size="large"
        title={editingProfileId ? 'Edit Profile' : 'New Profile'}
        onClose={() => {
          setShowProfileDrawer(false)
          setEditingProfileId(undefined)
          setProfileDraft(emptyProfile)
        }}
        extra={
          <Button
            type="primary"
            loading={saveProfileMutation.isPending}
            disabled={!profileDraft.name.trim() || profileDraft.environmentIds.length === 0}
            onClick={() => void saveProfileMutation.mutateAsync()}
          >
            Save
          </Button>
        }
      >
        <Form layout="vertical">
          <Form.Item label="Name">
            <Input
              value={profileDraft.name}
              onChange={(event) => setProfileDraft({ ...profileDraft, name: event.target.value })}
            />
          </Form.Item>
          <Form.Item label="Type">
            <Select
              value={profileDraft.type}
              options={[{ value: 'PGP', label: 'PGP' }]}
              onChange={(value) => setProfileDraft({ ...profileDraft, type: value })}
            />
          </Form.Item>
          <Form.Item
            label="Available Environments"
            required
            extra="The profile can only be selected when the run uses one of these environments."
          >
            <Select
              mode="multiple"
              aria-label="Available environments"
              value={profileDraft.environmentIds}
              placeholder="Select one or more environments"
              options={(environmentsQuery.data ?? []).map((environment) => ({
                label: environment.name,
                value: environment.id,
                disabled: environment.status !== 'ACTIVE',
              }))}
              onChange={(environmentIds) => setProfileDraft({
                ...profileDraft,
                environmentIds,
              })}
            />
          </Form.Item>
          <Form.Item label="Enabled">
            <Switch
              checked={profileDraft.enabled}
              onChange={(checked) => setProfileDraft({ ...profileDraft, enabled: checked })}
            />
          </Form.Item>
          <Form.Item label="Sender Public Key">
            <Input.TextArea
              rows={4}
              value={profileDraft.senderPublicKey}
              onChange={(event) =>
                setProfileDraft({ ...profileDraft, senderPublicKey: event.target.value })
              }
            />
          </Form.Item>
          <Form.Item label="Sender Private Key">
            <Input.TextArea
              rows={4}
              value={profileDraft.senderPrivateKey}
              onChange={(event) =>
                setProfileDraft({ ...profileDraft, senderPrivateKey: event.target.value })
              }
            />
          </Form.Item>
          <Form.Item label="Sender Passphrase">
            <Input.Password
              value={profileDraft.senderPrivateKeyPassphrase}
              onChange={(event) =>
                setProfileDraft({
                  ...profileDraft,
                  senderPrivateKeyPassphrase: event.target.value,
                })
              }
            />
          </Form.Item>
          <Form.Item label="Recipient Public Key">
            <Input.TextArea
              rows={4}
              value={profileDraft.recipientPublicKey}
              onChange={(event) =>
                setProfileDraft({ ...profileDraft, recipientPublicKey: event.target.value })
              }
            />
          </Form.Item>
          <Form.Item label="Recipient Private Key">
            <Input.TextArea
              rows={4}
              value={profileDraft.recipientPrivateKey}
              onChange={(event) =>
                setProfileDraft({ ...profileDraft, recipientPrivateKey: event.target.value })
              }
            />
          </Form.Item>
          <Form.Item label="Recipient Passphrase">
            <Input.Password
              value={profileDraft.recipientPrivateKeyPassphrase}
              onChange={(event) =>
                setProfileDraft({
                  ...profileDraft,
                  recipientPrivateKeyPassphrase: event.target.value,
                })
              }
            />
          </Form.Item>
        </Form>
      </Drawer>

    </div>
  )
}

function formatVariableCount(value?: Record<string, string>) {
  const count = Object.keys(value ?? {}).length
  return count === 1 ? '1 variable' : `${count} variables`
}

function formatEndpointCount(value?: Record<string, string>) {
  const count = Object.keys(value ?? {}).length
  return count === 1 ? '1 binding' : `${count} bindings`
}

function formatEnvironmentNames(environmentIds: string[], environments: Environment[]) {
  const namesById = new Map(environments.map((environment) => [environment.id, environment.name]))
  return environmentIds.map((id) => namesById.get(id) ?? id).join(', ')
}
