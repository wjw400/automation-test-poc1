import { Tag } from 'antd'

const colorMap: Record<string, string> = {
  PASSED: 'success',
  FAILED: 'error',
  ACTIVE: 'processing',
}

type StatusPillProps = {
  status: string
}

export function StatusPill({ status }: StatusPillProps) {
  return <Tag color={colorMap[status] ?? 'default'}>{status}</Tag>
}
