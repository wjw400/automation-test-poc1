import {
  ArrowRightOutlined,
  ExperimentOutlined,
  LockOutlined,
  PlusOutlined,
  SearchOutlined,
} from '@ant-design/icons'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, App, Button, Input, Modal, Typography } from 'antd'
import { useMemo, useState } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { platformApi } from '../api/platformApi'
import { LanguageSwitcher } from '../components/LanguageSwitcher'

const { Text } = Typography

export function ProjectsPage() {
  const { t } = useTranslation(['common', 'workspace'])
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { message } = App.useApp()
  const [searchParams, setSearchParams] = useSearchParams()
  const [filter, setFilter] = useState('')
  const [name, setName] = useState('')
  const [description, setDescription] = useState('')
  const createOpen = searchParams.get('create') === '1'

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const createMutation = useMutation({
    mutationFn: () => platformApi.createProject({
      name: name.trim(),
      description: description.trim(),
    }),
    onSuccess: async (project) => {
      await queryClient.invalidateQueries({ queryKey: ['projects'] })
      void message.success(t('workspace:home.created'))
      navigate(`/workspace?projectId=${encodeURIComponent(project.id)}&view=scenarios`)
    },
  })

  const filteredProjects = useMemo(() => {
    const keyword = filter.trim().toLowerCase()
    return keyword
      ? (projectsQuery.data ?? []).filter((project) => `${project.name} ${project.code}`.toLowerCase().includes(keyword))
      : projectsQuery.data ?? []
  }, [filter, projectsQuery.data])

  const openCreate = () => {
    setName('')
    setDescription('')
    setSearchParams({ create: '1' })
  }

  const closeCreate = () => {
    if (!createMutation.isPending) {
      setSearchParams({})
    }
  }

  return (
    <div className="workspace-home-page">
      <header className="workspace-home-header">
        <div className="workspace-home-brand">
          <ExperimentOutlined />
          <Text strong>{t('workspace:home.brand')}</Text>
        </div>
        <div className="workspace-home-actions">
          <LanguageSwitcher />
          <Button type="primary" icon={<PlusOutlined />} onClick={openCreate}>
            {t('workspace:home.create')}
          </Button>
        </div>
      </header>

      <main className="workspace-home-main">
        <div className="workspace-home-title">
          <div>
            <h1>{t('workspace:home.title')}</h1>
            <Text type="secondary">{t('workspace:home.description')}</Text>
          </div>
          <Input
            allowClear
            className="workspace-home-search"
            prefix={<SearchOutlined />}
            placeholder={t('workspace:home.search')}
            value={filter}
            onChange={(event) => setFilter(event.target.value)}
          />
        </div>

        {projectsQuery.isError ? (
          <Alert
            className="section-alert"
            type="warning"
            showIcon
            message={t('workspace:home.loadError')}
          />
        ) : null}

        <div className="workspace-home-list" aria-label={t('workspace:home.title')}>
          {filteredProjects.map((project) => (
            <button
              key={project.id}
              type="button"
              className="workspace-home-row"
              onClick={() => navigate(`/workspace?projectId=${encodeURIComponent(project.id)}&view=scenarios`)}
            >
              <span className="workspace-home-row-icon"><LockOutlined /></span>
              <span className="workspace-home-row-copy">
                <strong>{project.name}</strong>
                <span>{project.code}</span>
              </span>
              <ArrowRightOutlined />
            </button>
          ))}
          {!projectsQuery.isLoading && !filteredProjects.length ? (
            <div className="workspace-home-empty">
              <Text strong>{t('workspace:home.empty')}</Text>
              <Button icon={<PlusOutlined />} onClick={openCreate}>{t('workspace:home.create')}</Button>
            </div>
          ) : null}
        </div>
      </main>

      <Modal
        title={t('workspace:home.create')}
        open={createOpen}
        okText={t('actions.create')}
        confirmLoading={createMutation.isPending}
        okButtonProps={{ disabled: !name.trim() }}
        onCancel={closeCreate}
        onOk={() => void createMutation.mutateAsync()}
      >
        <div className="workspace-create-form">
          <label>
            <span>{t('fields.name')}</span>
            <Input
              autoFocus
              placeholder={t('workspace:home.namePlaceholder')}
              value={name}
              onChange={(event) => setName(event.target.value)}
              onPressEnter={() => {
                if (name.trim()) {
                  void createMutation.mutateAsync()
                }
              }}
            />
          </label>
          <label>
            <span>{t('fields.description')}</span>
            <Input.TextArea
              autoSize={{ minRows: 3, maxRows: 6 }}
              placeholder={t('optional')}
              value={description}
              onChange={(event) => setDescription(event.target.value)}
            />
          </label>
          {createMutation.isError ? (
            <Alert type="error" showIcon message={t('workspace:home.createError')} />
          ) : null}
        </div>
      </Modal>
    </div>
  )
}
