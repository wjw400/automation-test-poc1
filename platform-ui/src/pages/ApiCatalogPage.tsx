import { InboxOutlined, ReloadOutlined, UploadOutlined } from '@ant-design/icons'
import { Alert, App, Button, Modal, Select, Space, Table, Tag, Typography, Upload } from 'antd'
import type { ColumnsType } from 'antd/es/table'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { useTranslation } from 'react-i18next'
import { platformApi } from '../api/platformApi'
import { StoplightApi } from '../components/StoplightApi'
import type { ApiSpecRecord } from '../types/platform'

const { Text } = Typography

export function ApiCatalogPage() {
  const { t } = useTranslation(['common', 'api'])
  const { message } = App.useApp()
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const [selectedSpecId, setSelectedSpecId] = useState<string>()
  const [isImportModalOpen, setIsImportModalOpen] = useState(false)
  const specColumns: ColumnsType<ApiSpecRecord> = [
    {
      title: t('common:fields.name'),
      dataIndex: 'name',
      render: (_value, record) => <Text strong>{record.title || record.name}</Text>,
    },
  ]

  const projectsQuery = useQuery({
    queryKey: ['projects'],
    queryFn: platformApi.listProjects,
  })

  const selectedProjectId = searchParams.get('projectId') ?? projectsQuery.data?.[0]?.id

  const specsQuery = useQuery({
    queryKey: ['api-specs', selectedProjectId],
    queryFn: () => platformApi.listApiSpecs(selectedProjectId!),
    enabled: Boolean(selectedProjectId),
  })

  const importMutation = useMutation({
    mutationFn: (file: File) => platformApi.importApiSpec(selectedProjectId!, file),
    onSuccess: async () => {
      await queryClient.invalidateQueries({ queryKey: ['api-specs', selectedProjectId] })
      setIsImportModalOpen(false)
      void message.success(t('api:imported'))
    },
  })

  useEffect(() => {
    const specs = specsQuery.data ?? []
    if (!specs.length) {
      setSelectedSpecId(undefined)
      return
    }

    setSelectedSpecId((current) => {
      if (current && specs.some((spec) => spec.id === current)) {
        return current
      }
      return specs[0].id
    })
  }, [specsQuery.data])

  const selectedSpec = useMemo(
    () => (specsQuery.data ?? []).find((spec) => spec.id === selectedSpecId),
    [selectedSpecId, specsQuery.data],
  )

  return (
    <div className="page-shell">
      <section className="hero-band">
        <div>
          <h1>{t('api:catalog')}</h1>
          <p>{t('api:heroDescription')}</p>
        </div>
        <Space wrap>
          <Select
            className="project-picker"
            placeholder={t('api:selectProject')}
            value={selectedProjectId}
            options={(projectsQuery.data ?? []).map((project) => ({
              label: project.name,
              value: project.id,
            }))}
            onChange={(value) => setSearchParams({ projectId: value })}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={() => {
              void specsQuery.refetch()
            }}
          >
            {t('api:refresh')}
          </Button>
        </Space>
      </section>

      <div className="workspace-grid">
        <section className="panel-section list-panel">
          <div className="panel-title-row compact">
            <h2 className="panel-title">{t('api:apiSpecs')}</h2>
            <Button
              type="primary"
              icon={<UploadOutlined />}
              disabled={!selectedProjectId}
              onClick={() => setIsImportModalOpen(true)}
            >
              {t('api:addSpec')}
            </Button>
          </div>

          {specsQuery.isError ? (
            <Alert className="section-alert" type="warning" showIcon message={t('api:loadError')} />
          ) : null}
          {importMutation.isError ? (
            <Alert className="section-alert" type="error" showIcon message={t('api:importError')} />
          ) : null}

          <Table<ApiSpecRecord>
            rowKey="id"
            loading={specsQuery.isLoading}
            pagination={false}
            columns={specColumns}
            dataSource={specsQuery.data ?? []}
            rowClassName={(record) => (record.id === selectedSpecId ? 'spec-list-row active' : 'spec-list-row')}
            onRow={(record) => ({
              onClick: () => setSelectedSpecId(record.id),
            })}
            locale={{ emptyText: t('api:emptyImported') }}
          />
        </section>

        <section className="panel-section editor-panel">
          <div className="panel-title-row compact">
            <div>
              <h2 className="panel-title">{t('api:reference')}</h2>
              {selectedSpec ? (
                <Space wrap size={[8, 8]}>
                  <Text type="secondary">{selectedSpec.title || selectedSpec.name}</Text>
                  {selectedSpec.specVersion ? <Tag>{selectedSpec.specVersion}</Tag> : null}
                  <Tag color="blue">{t('api:operationCount', { count: selectedSpec.operationCount })}</Tag>
                </Space>
              ) : (
                <Text type="secondary">{t('api:selectReference')}</Text>
              )}
            </div>
          </div>

          {selectedProjectId && selectedSpec ? (
            <div className="stoplight-shell">
              <StoplightApi
                apiDescriptionUrl={platformApi.getApiSpecContentUrl(selectedProjectId, selectedSpec.id)}
              />
            </div>
          ) : (
            <div className="panel-empty">{t('api:selectInspect')}</div>
          )}
        </section>
      </div>

      <Modal
        title={t('api:addSpec')}
        open={isImportModalOpen}
        footer={null}
        destroyOnHidden
        onCancel={() => {
          if (!importMutation.isPending) {
            setIsImportModalOpen(false)
          }
        }}
      >
        <div className="api-import-stack">
          <Text type="secondary">
            {t('api:importDescription')}
          </Text>

          {importMutation.isError ? (
            <Alert type="error" showIcon message={t('api:importError')} />
          ) : null}

          <Upload.Dragger
            accept=".yaml,.yml"
            multiple={false}
            showUploadList={false}
            disabled={!selectedProjectId || importMutation.isPending}
            beforeUpload={(file) => {
              void importMutation.mutateAsync(file as File)
              return false
            }}
          >
            <p className="ant-upload-drag-icon">
              <InboxOutlined />
            </p>
            <p className="ant-upload-text">{t('api:dropYaml')}</p>
            <p className="ant-upload-hint">{t('api:importHint')}</p>
          </Upload.Dragger>
        </div>
      </Modal>
    </div>
  )
}
