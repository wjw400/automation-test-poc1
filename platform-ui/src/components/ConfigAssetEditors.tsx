import { SaveOutlined } from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Form, Input, Select, Space, Switch, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { platformApi } from '../api/platformApi'
import type {
  Environment,
  Profile,
  SaveEnvironmentInput,
  SaveProfileInput,
} from '../types/platform'
import { JsonObjectEditor } from './JsonObjectEditor'

const { Text } = Typography

type EditorCallbacks<T> = {
  projectId: string
  onDirtyChange?: (dirty: boolean) => void
  onTitleChange?: (title: string) => void
  onSaveReady?: (save?: () => Promise<void>) => void
  onSaved?: (record: T) => void
}

const emptyEnvironment: SaveEnvironmentInput = {
  name: '',
  baseUrl: '',
  endpoints: {},
  variables: {},
  status: 'ACTIVE',
}

export function EnvironmentAssetEditor({
  projectId,
  environment,
  onDirtyChange,
  onTitleChange,
  onSaveReady,
  onSaved,
}: EditorCallbacks<Environment> & { environment?: Environment }) {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const initialValue = useMemo(() => environmentDraft(environment), [environment])
  const [draft, setDraft] = useState<SaveEnvironmentInput>(initialValue)
  const [baseline, setBaseline] = useState<SaveEnvironmentInput>(initialValue)
  const [variablesError, setVariablesError] = useState<string | null>(null)

  const apiSpecsQuery = useQuery({
    queryKey: ['api-specs', projectId],
    queryFn: () => platformApi.listApiSpecs(projectId),
  })

  useEffect(() => {
    setDraft(initialValue)
    setBaseline(initialValue)
    setVariablesError(null)
  }, [initialValue])

  const dirty = JSON.stringify(draft) !== JSON.stringify(baseline)

  useEffect(() => {
    onDirtyChange?.(dirty || Boolean(variablesError))
  }, [dirty, onDirtyChange, variablesError])

  useEffect(() => {
    onTitleChange?.(draft.name || 'New Environment')
  }, [draft.name, onTitleChange])

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (variablesError) {
        throw new Error(`Fix Variables JSON before saving: ${variablesError}`)
      }
      if (!draft.name.trim() || !draft.baseUrl.trim()) {
        throw new Error('Environment name and base URL are required.')
      }
      return environment
        ? platformApi.updateEnvironment(projectId, environment.id, draft)
        : platformApi.createEnvironment(projectId, draft)
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['environments', projectId] })
      const next = environmentDraft(saved)
      setDraft(next)
      setBaseline(next)
      onSaved?.(saved)
      void message.success('Environment saved')
    },
  })

  useEffect(() => {
    onSaveReady?.(async () => {
      await saveMutation.mutateAsync()
    })
    return () => onSaveReady?.(undefined)
  }, [onSaveReady, saveMutation])

  return (
    <div className="asset-editor-page">
      <header className="asset-editor-header">
        <div>
          <h1>{draft.name || 'New Environment'}</h1>
          <Text type="secondary">Environment</Text>
        </div>
        <Space>
          <Text className={dirty ? 'scenario-draft-state dirty' : 'scenario-draft-state'}>
            {dirty ? 'Unsaved' : 'Saved'}
          </Text>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            disabled={!dirty || Boolean(variablesError) || !draft.name.trim() || !draft.baseUrl.trim()}
            loading={saveMutation.isPending}
            onClick={() => void saveMutation.mutateAsync()}
          >
            Save
          </Button>
        </Space>
      </header>
      {saveMutation.isError ? <Alert type="error" showIcon message="Failed to save environment." /> : null}
      <div className="asset-editor-scroll">
        <Form className="asset-editor-form" layout="vertical">
          <Form.Item label="Name">
            <Input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
          </Form.Item>
          <Form.Item label="Default Base URL">
            <Input value={draft.baseUrl} onChange={(event) => setDraft({ ...draft, baseUrl: event.target.value })} />
          </Form.Item>
          <Form.Item label="Status">
            <Select
              value={draft.status}
              options={[
                { value: 'ACTIVE', label: 'ACTIVE' },
                { value: 'INACTIVE', label: 'INACTIVE' },
              ]}
              onChange={(status) => setDraft({ ...draft, status })}
            />
          </Form.Item>
          <section className="asset-editor-section">
            <h2>API Endpoint Bindings</h2>
            <div className="endpoint-binding-list">
              {(apiSpecsQuery.data ?? []).map((apiSpec) => (
                <div className="endpoint-binding-row" key={apiSpec.id}>
                  <div className="endpoint-binding-name">
                    <strong>{apiSpec.title || apiSpec.name}</strong>
                    <Text type="secondary">{apiSpec.name}</Text>
                  </div>
                  <Input
                    aria-label={`${apiSpec.name} base URL`}
                    placeholder={draft.baseUrl || 'https://api.example.com'}
                    value={draft.endpoints[apiSpec.id] ?? ''}
                    onChange={(event) => {
                      const endpoints = { ...draft.endpoints }
                      if (event.target.value) {
                        endpoints[apiSpec.id] = event.target.value
                      } else {
                        delete endpoints[apiSpec.id]
                      }
                      setDraft({ ...draft, endpoints })
                    }}
                  />
                </div>
              ))}
            </div>
          </section>
          <section className="asset-editor-section">
            <h2>Variables</h2>
            <JsonObjectEditor
              ariaLabel="Environment variables JSON editor"
              value={draft.variables}
              onValidationChange={setVariablesError}
              onCommit={(variables) => setDraft({
                ...draft,
                variables: variables as Record<string, string>,
              })}
            />
          </section>
        </Form>
      </div>
    </div>
  )
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

export function ProfileAssetEditor({
  projectId,
  profile,
  onDirtyChange,
  onTitleChange,
  onSaveReady,
  onSaved,
}: EditorCallbacks<Profile> & { profile?: Profile }) {
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const initialValue = useMemo(() => profileDraft(profile), [profile])
  const [draft, setDraft] = useState<SaveProfileInput>(initialValue)
  const [baseline, setBaseline] = useState<SaveProfileInput>(initialValue)

  const environmentsQuery = useQuery({
    queryKey: ['environments', projectId],
    queryFn: () => platformApi.listEnvironments(projectId),
  })

  useEffect(() => {
    setDraft(initialValue)
    setBaseline(initialValue)
  }, [initialValue])

  const dirty = JSON.stringify(draft) !== JSON.stringify(baseline)

  useEffect(() => {
    onDirtyChange?.(dirty)
  }, [dirty, onDirtyChange])

  useEffect(() => {
    onTitleChange?.(draft.name || 'New Profile')
  }, [draft.name, onTitleChange])

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!draft.name.trim() || draft.environmentIds.length === 0) {
        throw new Error('Profile name and at least one environment are required.')
      }
      return profile
        ? platformApi.updateProfile(projectId, profile.id, draft)
        : platformApi.createProfile(projectId, draft)
    },
    onSuccess: async (saved) => {
      await queryClient.invalidateQueries({ queryKey: ['profiles', projectId] })
      const next = profileDraft(saved)
      setDraft(next)
      setBaseline(next)
      onSaved?.(saved)
      void message.success('Profile saved')
    },
  })

  useEffect(() => {
    onSaveReady?.(async () => {
      await saveMutation.mutateAsync()
    })
    return () => onSaveReady?.(undefined)
  }, [onSaveReady, saveMutation])

  return (
    <div className="asset-editor-page">
      <header className="asset-editor-header">
        <div>
          <h1>{draft.name || 'New Profile'}</h1>
          <Text type="secondary">Profile</Text>
        </div>
        <Space>
          <Text className={dirty ? 'scenario-draft-state dirty' : 'scenario-draft-state'}>
            {dirty ? 'Unsaved' : 'Saved'}
          </Text>
          <Button
            type="primary"
            icon={<SaveOutlined />}
            disabled={!dirty || !draft.name.trim() || draft.environmentIds.length === 0}
            loading={saveMutation.isPending}
            onClick={() => void saveMutation.mutateAsync()}
          >
            Save
          </Button>
        </Space>
      </header>
      {saveMutation.isError ? <Alert type="error" showIcon message="Failed to save profile." /> : null}
      <div className="asset-editor-scroll">
        <Form className="asset-editor-form" layout="vertical">
          <Form.Item label="Name">
            <Input value={draft.name} onChange={(event) => setDraft({ ...draft, name: event.target.value })} />
          </Form.Item>
          <Form.Item label="Type">
            <Select value={draft.type} options={[{ value: 'PGP', label: 'PGP' }]} onChange={(type) => setDraft({ ...draft, type })} />
          </Form.Item>
          <Form.Item label="Available Environments">
            <Select
              mode="multiple"
              aria-label="Available environments"
              value={draft.environmentIds}
              options={(environmentsQuery.data ?? []).map((environment) => ({
                label: environment.name,
                value: environment.id,
                disabled: environment.status !== 'ACTIVE',
              }))}
              onChange={(environmentIds) => setDraft({ ...draft, environmentIds })}
            />
          </Form.Item>
          <Form.Item label="Enabled">
            <Switch checked={draft.enabled} onChange={(enabled) => setDraft({ ...draft, enabled })} />
          </Form.Item>
          <section className="asset-editor-section">
            <h2>Sender Keys</h2>
            <Form.Item label="Public Key">
              <Input.TextArea rows={5} value={draft.senderPublicKey} onChange={(event) => setDraft({ ...draft, senderPublicKey: event.target.value })} />
            </Form.Item>
            <Form.Item label="Private Key">
              <Input.TextArea rows={5} value={draft.senderPrivateKey} onChange={(event) => setDraft({ ...draft, senderPrivateKey: event.target.value })} />
            </Form.Item>
            <Form.Item label="Passphrase">
              <Input.Password value={draft.senderPrivateKeyPassphrase} onChange={(event) => setDraft({ ...draft, senderPrivateKeyPassphrase: event.target.value })} />
            </Form.Item>
          </section>
          <section className="asset-editor-section">
            <h2>Recipient Keys</h2>
            <Form.Item label="Public Key">
              <Input.TextArea rows={5} value={draft.recipientPublicKey} onChange={(event) => setDraft({ ...draft, recipientPublicKey: event.target.value })} />
            </Form.Item>
            <Form.Item label="Private Key">
              <Input.TextArea rows={5} value={draft.recipientPrivateKey} onChange={(event) => setDraft({ ...draft, recipientPrivateKey: event.target.value })} />
            </Form.Item>
            <Form.Item label="Passphrase">
              <Input.Password value={draft.recipientPrivateKeyPassphrase} onChange={(event) => setDraft({ ...draft, recipientPrivateKeyPassphrase: event.target.value })} />
            </Form.Item>
          </section>
        </Form>
      </div>
    </div>
  )
}

function environmentDraft(environment?: Environment): SaveEnvironmentInput {
  return environment
    ? {
        name: environment.name,
        baseUrl: environment.baseUrl,
        endpoints: environment.endpoints ?? {},
        variables: environment.variables ?? {},
        status: environment.status,
      }
    : structuredClone(emptyEnvironment)
}

function profileDraft(profile?: Profile): SaveProfileInput {
  return profile
    ? {
        name: profile.name,
        type: profile.type,
        senderPublicKey: profile.senderPublicKey ?? '',
        senderPrivateKey: profile.senderPrivateKey ?? '',
        senderPrivateKeyPassphrase: profile.senderPrivateKeyPassphrase ?? '',
        recipientPublicKey: profile.recipientPublicKey ?? '',
        recipientPrivateKey: profile.recipientPrivateKey ?? '',
        recipientPrivateKeyPassphrase: profile.recipientPrivateKeyPassphrase ?? '',
        environmentIds: profile.environmentIds ?? [],
        enabled: profile.enabled,
      }
    : structuredClone(emptyProfile)
}
