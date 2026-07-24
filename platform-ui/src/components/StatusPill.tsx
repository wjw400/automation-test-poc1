import { Tag } from 'antd'
import { useTranslation } from 'react-i18next'

const colorMap: Record<string, string> = {
  PASSED: 'success',
  FAILED: 'error',
  ACTIVE: 'processing',
}

type StatusPillProps = {
  status: string
}

export function StatusPill({ status }: StatusPillProps) {
  const { t } = useTranslation('common')
  const normalizedStatus = status.toUpperCase()
  const labels: Record<string, string> = {
    ACTIVE: t('status.active'),
    FAILED: t('status.failed'),
    INACTIVE: t('status.inactive'),
    PASSED: t('status.passed'),
    PENDING: t('status.pending'),
    RUNNING: t('status.running'),
    SKIPPED: t('status.skipped'),
  }

  return <Tag color={colorMap[normalizedStatus] ?? 'default'}>{labels[normalizedStatus] ?? status}</Tag>
}
