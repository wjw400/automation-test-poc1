import { RightOutlined } from '@ant-design/icons'
import { Button, Space, Typography } from 'antd'
import { useTranslation } from 'react-i18next'
import { formatDateTime } from '../i18n'
import type { RunRecord, SuiteRunRecord } from '../types/platform'
import { StatusPill } from './StatusPill'

const { Text } = Typography

type SuiteRunDetailProps = {
  run?: SuiteRunRecord
  onOpenRun?: (run: RunRecord) => void
  onOpenSuite?: () => void
}

export function SuiteRunDetail({
  run,
  onOpenRun,
  onOpenSuite,
}: SuiteRunDetailProps) {
  const { t, i18n } = useTranslation(['execution', 'scenario'])

  if (!run) {
    return <div className="workbench-empty"><Text type="secondary">{t('execution:suiteRunNotFound')}</Text></div>
  }

  const passedCount = run.runs.filter((child) => child.status === 'PASSED').length

  return (
    <div className="suite-run-tab-pane">
      <header className="suite-run-header">
        <div>
          <Text type="secondary">{t('execution:suiteExecution')}</Text>
          <h1>{run.suiteName}</h1>
        </div>
        <Space size={16}>
          <div className="suite-run-summary">
            <StatusPill status={run.status} />
            <span>{t('execution:passedCount', { passed: passedCount, total: run.runs.length })}</span>
            <span>{t('execution:duration', { duration: run.durationMs })}</span>
          </div>
          {onOpenSuite ? <Button size="small" onClick={onOpenSuite}>{t('execution:openSuite')}</Button> : null}
        </Space>
      </header>
      <div className="suite-run-body">
        {run.errorMessage ? <div className="suite-run-error">{run.errorMessage}</div> : null}
        <div className="suite-run-meta">
          <span>{t('execution:started', { time: formatDateTime(run.startedAt, i18n.resolvedLanguage) })}</span>
          <span>{t('execution:triggeredBy', { name: run.triggeredBy })}</span>
        </div>
        <section className="suite-run-results">
          <h2>{t('execution:scenarioResults')}</h2>
          <div className="suite-run-list">
            {run.runs.map((child, index) => (
              <button
                key={child.id}
                type="button"
                className="suite-run-row"
                disabled={!onOpenRun}
                onClick={() => onOpenRun?.(child)}
              >
                <span className="suite-run-order">{String(child.suiteCaseOrder ?? index + 1).padStart(2, '0')}</span>
                <span className="suite-run-row-copy">
                  <strong>{child.suiteDisplayName || child.caseId}</strong>
                  <span>
                    {[child.environmentName, child.profileName].filter(Boolean).join(' / ')
                      || t('scenario:runtimeContextUnavailable')}
                  </span>
                </span>
                <StatusPill status={child.status} />
                <span className="suite-run-duration">{t('execution:duration', { duration: child.durationMs })}</span>
                {onOpenRun ? <RightOutlined /> : <span />}
              </button>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}
