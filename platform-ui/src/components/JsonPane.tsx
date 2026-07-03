import { Typography } from 'antd'

const { Text } = Typography

type JsonPaneProps = {
  value: unknown
  emptyLabel?: string
}

export function JsonPane({ value, emptyLabel = 'No data' }: JsonPaneProps) {
  const content =
    value === null || value === undefined || value === ''
      ? ''
      : typeof value === 'string'
        ? value
        : JSON.stringify(value, null, 2)

  return (
    <div className="code-pane">
      {content ? <pre>{content}</pre> : <Text type="secondary">{emptyLabel}</Text>}
    </div>
  )
}
