import { UndoOutlined } from '@ant-design/icons'
import Editor from '@monaco-editor/react'
import { Button, Typography } from 'antd'
import { useEffect, useMemo, useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'

const { Text } = Typography

type JsonObjectEditorProps = {
  value: unknown
  onCommit: (next: Record<string, unknown>) => void
  onValidationChange?: (error: string | null) => void
  ariaLabel?: string
  height?: string
  resetLabel?: string
  resetValue?: Record<string, unknown>
}

export function JsonObjectEditor({
  value,
  onCommit,
  onValidationChange,
  ariaLabel,
  height = '180px',
  resetLabel,
  resetValue = {},
}: JsonObjectEditorProps) {
  const { t } = useTranslation(['common'])
  const editorAriaLabel = ariaLabel ?? t('common:jsonEditor.ariaLabel')
  const validationMessages = useMemo(() => ({
    invalid: (detail: string) => t('common:jsonEditor.invalid', { detail }),
    invalidSyntax: t('common:jsonEditor.invalidSyntax'),
    objectRequired: t('common:jsonEditor.objectRequired'),
    propertyNameRequired: t('common:jsonEditor.propertyNameRequired'),
  }), [t])
  const initialText = stringifyJsonObject(value)
  const initialValidation = validateJsonObject(initialText, validationMessages)
  const [text, setText] = useState(initialText)
  const [error, setError] = useState<string | null>(initialValidation.error)
  const committedValueRef = useRef(jsonFingerprint(value))
  const incomingText = useMemo(() => stringifyJsonObject(value), [value])
  const incomingFingerprint = useMemo(() => jsonFingerprint(value), [value])

  useEffect(() => {
    onValidationChange?.(error)
  }, [error, onValidationChange])

  useEffect(() => {
    if (incomingFingerprint === committedValueRef.current) {
      return
    }
    const validation = validateJsonObject(incomingText, validationMessages)
    committedValueRef.current = incomingFingerprint
    setText(incomingText)
    setError(validation.error)
  }, [incomingFingerprint, incomingText, validationMessages])

  const commitText = (nextText: string) => {
    const validation = validateJsonObject(nextText, validationMessages)
    setText(nextText)
    setError(validation.error)
    if (validation.error || !validation.value) {
      return
    }
    committedValueRef.current = jsonFingerprint(validation.value)
    onCommit(validation.value)
  }

  const restoreDefault = () => {
    const defaultText = stringifyJsonObject(resetValue)
    committedValueRef.current = jsonFingerprint(resetValue)
    setText(defaultText)
    setError(null)
    onCommit(resetValue)
  }

  return (
    <div className="json-object-editor-field">
      {resetLabel ? (
        <div className="json-object-editor-toolbar">
          <Button
            icon={<UndoOutlined />}
            size="small"
            type="text"
            onClick={restoreDefault}
          >
            {resetLabel}
          </Button>
        </div>
      ) : null}
      <div className={error ? 'json-object-editor-frame has-error' : 'json-object-editor-frame'}>
        <Editor
          height={height}
          language="json"
          theme="vs-light"
          value={text}
          options={{
            ariaLabel: editorAriaLabel,
            automaticLayout: true,
            folding: false,
            fontSize: 13,
            formatOnPaste: true,
            formatOnType: true,
            glyphMargin: false,
            lineDecorationsWidth: 8,
            lineNumbers: 'on',
            minimap: { enabled: false },
            overviewRulerLanes: 0,
            padding: { top: 8, bottom: 8 },
            renderLineHighlight: 'line',
            scrollBeyondLastLine: false,
            tabSize: 2,
            wordWrap: 'on',
          }}
          onChange={(nextValue) => commitText(nextValue ?? '')}
        />
      </div>
      {error ? <Text className="json-object-editor-error" type="danger">{error}</Text> : null}
    </div>
  )
}

function stringifyJsonObject(value: unknown) {
  try {
    return JSON.stringify(value ?? {}, null, 2) ?? '{}'
  } catch {
    return '{}'
  }
}

function jsonFingerprint(value: unknown) {
  try {
    return JSON.stringify(value ?? {})
  } catch {
    return ''
  }
}

function validateJsonObject(
  text: string,
  messages: {
    invalid: (detail: string) => string
    invalidSyntax: string
    objectRequired: string
    propertyNameRequired: string
  },
): {
  value?: Record<string, unknown>
  error: string | null
} {
  if (!text.trim()) {
    return { error: messages.objectRequired }
  }
  try {
    const value: unknown = JSON.parse(text)
    if (!value || typeof value !== 'object' || Array.isArray(value)) {
      return { error: messages.objectRequired }
    }
    if (Object.keys(value).some((key) => !key.trim())) {
      return { error: messages.propertyNameRequired }
    }
    return { value: value as Record<string, unknown>, error: null }
  } catch (parseError) {
    const detail = parseError instanceof Error ? parseError.message : messages.invalidSyntax
    return { error: messages.invalid(detail) }
  }
}
