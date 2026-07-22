import { Alert, App, Button, Drawer, Form, Input, Select, Switch, Table, Tabs, Typography } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { platformApi } from '../api/platformApi'
import { StatusPill } from '../components/StatusPill'
import type { SaveEnvironmentInput, SaveProfileInput } from '../types/platform'

const { Text } = Typography

const emptyEnvironment: SaveEnvironmentInput = {
  name: '',
  baseUrl: '',
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
  enabled: true,
}

export function ConfigPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [environmentDraft, setEnvironmentDraft] = useState<SaveEnvironmentInput>(emptyEnvironment)
  const [profileDraft, setProfileDraft] = useState<SaveProfileInput>(emptyProfile)
  const [showEnvironmentDrawer, setShowEnvironmentDrawer] = useState(false)
  const [showProfileDrawer, setShowProfileDrawer] = useState(false)

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const selectedProjectId = searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id
  const activeTab = searchParams.get('tab') === 'profiles' ? 'profiles' : 'environments'

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

  const createEnvironmentMutation = useMutation({
    mutationFn: () => platformApi.createEnvironment(selectedProjectId!, environmentDraft),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['environments', selectedProjectId] })
      setShowEnvironmentDrawer(false)
      setEnvironmentDraft(emptyEnvironment)
      void message.success('Environment created')
    },
  })

  const createProfileMutation = useMutation({
    mutationFn: () => platformApi.createProfile(selectedProjectId!, profileDraft),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['profiles', selectedProjectId] })
      setShowProfileDrawer(false)
      setProfileDraft(emptyProfile)
      void message.success('Profile created')
    },
  })

  const variablePreview = useMemo(
    () => JSON.stringify(environmentDraft.variables, null, 2),
    [environmentDraft.variables],
  )

  return (
    <div className="page-shell">
      <section className="hero-band">
        <div>
          <h1>Configuration</h1>
          <p>Bind environments and PGP profiles once, then reuse them across cases and runs.</p>
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
              <Text type="secondary">Base URLs and runtime variables per target.</Text>
            </div>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setShowEnvironmentDrawer(true)}
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
              { title: 'Base URL', dataIndex: 'baseUrl' },
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
              <Text type="secondary">Execution-time security material and protocol bindings.</Text>
            </div>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={() => setShowProfileDrawer(true)}
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
                title: 'Enabled',
                dataIndex: 'enabled',
                render: (value: boolean) => <StatusPill status={value ? 'ACTIVE' : 'INACTIVE'} />,
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
        width={520}
        title="New Environment"
        onClose={() => setShowEnvironmentDrawer(false)}
        extra={
          <Button
            type="primary"
            loading={createEnvironmentMutation.isPending}
            onClick={() => void createEnvironmentMutation.mutateAsync()}
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
          <Form.Item label="Base URL">
            <Input
              value={environmentDraft.baseUrl}
              onChange={(event) =>
                setEnvironmentDraft({ ...environmentDraft, baseUrl: event.target.value })
              }
            />
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
        width={620}
        title="New Profile"
        onClose={() => setShowProfileDrawer(false)}
        extra={
          <Button
            type="primary"
            loading={createProfileMutation.isPending}
            onClick={() => void createProfileMutation.mutateAsync()}
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
