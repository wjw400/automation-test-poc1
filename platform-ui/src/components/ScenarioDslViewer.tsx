import { CheckOutlined, CloseOutlined, CopyOutlined, EditOutlined, ExclamationCircleOutlined } from '@ant-design/icons'
import Editor from '@monaco-editor/react'
import { App, Button, Typography } from 'antd'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { DslValidationIssue } from '../utils/caseDslValidation'

const { Text } = Typography

type ScenarioDslViewerProps = {
  content: string
  issues: DslValidationIssue[]
  parseError: string | null
  onChange: (content: string) => void
}

export function ScenarioDslViewer({ content, issues, parseError, onChange }: ScenarioDslViewerProps) {
  const { message } = App.useApp()
  const { t } = useTranslation(['common', 'scenario'])
  const [editing, setEditing] = useState(false)
  const contentBeforeEditRef = useRef(content)
  const issueCount = parseError ? 1 : issues.length

  const copyDsl = async () => {
    await navigator.clipboard.writeText(content)
    void message.success(t('scenario:dslCopied'))
  }

  const startEditing = () => {
    contentBeforeEditRef.current = content
    setEditing(true)
  }

  const cancelEditing = () => {
    onChange(contentBeforeEditRef.current)
    setEditing(false)
  }

  return (
    <section className="scenario-dsl-viewer">
      <header className="scenario-dsl-toolbar">
        <div className="scenario-dsl-title">
          <Text strong>{t('scenario:dslSource')}</Text>
          {issueCount ? (
            <span className="scenario-dsl-issue-count"><ExclamationCircleOutlined /> {issueCount}</span>
          ) : null}
        </div>
        <div className="scenario-dsl-actions">
          <Button
            aria-label={t('scenario:copyDsl')}
            icon={<CopyOutlined />}
            size="small"
            title={t('scenario:copyDsl')}
            type="text"
            onClick={() => void copyDsl()}
          />
          {editing ? (
            <>
              <Button
                aria-label={t('scenario:cancelDslEdit')}
                icon={<CloseOutlined />}
                size="small"
                title={t('scenario:cancelDslEdit')}
                type="text"
                onClick={cancelEditing}
              />
              <Button
                icon={<CheckOutlined />}
                size="small"
                type="primary"
                disabled={Boolean(parseError)}
                title={parseError ? t('scenario:validation.invalidSyntax') : t('scenario:applyDsl')}
                onClick={() => setEditing(false)}
              >
                {t('scenario:applyDsl')}
              </Button>
            </>
          ) : (
            <Button
              aria-label={t('scenario:editDsl')}
              icon={<EditOutlined />}
              size="small"
              title={t('scenario:editDsl')}
              type="text"
              onClick={startEditing}
            />
          )}
        </div>
      </header>
      {issueCount ? (
        <details className="scenario-dsl-issues" open={Boolean(parseError)}>
          <summary>{t('scenario:validation.issueCount', { count: issueCount })}</summary>
          <div className="scenario-dsl-issue-list">
            {parseError ? (
              <div className="scenario-dsl-issue-row">
                <code>YAML</code>
                <span>{parseError}</span>
              </div>
            ) : issues.map((issue, index) => (
              <div className="scenario-dsl-issue-row" key={`${issue.code}-${issue.path}-${index}`}>
                <code>{issue.path}</code>
                <span>{issue.message}</span>
              </div>
            ))}
          </div>
        </details>
      ) : null}
      <div className="scenario-dsl-editor">
        <Editor
          height="100%"
          language="yaml"
          theme="vs-light"
          value={content}
          options={{
            ariaLabel: t('scenario:rawDslAriaLabel'),
            automaticLayout: true,
            folding: true,
            fontSize: 13,
            glyphMargin: false,
            lineDecorationsWidth: 8,
            lineNumbers: 'on',
            minimap: { enabled: false },
            overviewRulerLanes: 0,
            padding: { top: 12, bottom: 12 },
            readOnly: !editing,
            renderLineHighlight: editing ? 'line' : 'none',
            scrollBeyondLastLine: false,
            tabSize: 2,
            wordWrap: 'off',
          }}
          onChange={(value) => {
            if (editing) {
              onChange(value ?? '')
            }
          }}
        />
      </div>
    </section>
  )
}
