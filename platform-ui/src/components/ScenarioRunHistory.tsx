import { useEffect, useMemo, useState } from 'react'
import { Typography } from 'antd'
import { useTranslation } from 'react-i18next'
import { formatDateTime } from '../i18n'
import type { RunRecord } from '../types/platform'
import { RunDetail } from './RunDetail'
import { StatusPill } from './StatusPill'

const { Text } = Typography

type ScenarioRunHistoryProps = {
  runs: RunRecord[]
  selectedRunId?: string
  suiteNamesById?: Map<string, string>
  onSelect: (runId: string) => void
}

export function ScenarioRunHistory({
  runs,
  selectedRunId,
  suiteNamesById = new Map(),
  onSelect,
}: ScenarioRunHistoryProps) {
  const { t, i18n } = useTranslation(['execution', 'scenario'])
  const selectedRun = useMemo(
    () => runs.find((run) => run.id === selectedRunId) ?? runs[0],
    [runs, selectedRunId],
  )
  const [selectedStepIndex, setSelectedStepIndex] = useState(1)

  useEffect(() => {
    setSelectedStepIndex(selectedRun?.steps[0]?.index ?? 1)
  }, [selectedRun?.id, selectedRun?.steps])

  if (!runs.length) {
    return (
      <div className="context-runs-empty">
        <Text strong>{t('scenario:noExecutions')}</Text>
        <Text type="secondary">{t('scenario:noExecutionsDescription')}</Text>
      </div>
    )
  }

  return (
    <div className="context-runs-shell">
      <aside className="context-run-list">
        <div className="context-run-list-head">
          <Text strong>{t('execution:title')}</Text>
          <Text type="secondary">{runs.length}</Text>
        </div>
        <div className="context-run-list-scroll">
          {runs.map((run) => {
            const suiteName = run.suiteRunId ? suiteNamesById.get(run.suiteRunId) : undefined
            return (
              <button
                key={run.id}
                type="button"
                className={run.id === selectedRun?.id ? 'context-run-row active' : 'context-run-row'}
                onClick={() => onSelect(run.id)}
              >
                <span className="context-run-row-top">
                  <span>{formatDateTime(run.startedAt, i18n.resolvedLanguage)}</span>
                  <StatusPill status={run.status} />
                </span>
                <span className="context-run-source">
                  {suiteName ? t('execution:suiteSource', { name: suiteName }) : t('execution:standalone')}
                </span>
                <span className="context-run-meta">
                  {[run.environmentName, run.profileName].filter(Boolean).join(' / ') || t('execution:noContext')}
                  <span>{t('execution:duration', { duration: run.durationMs })}</span>
                </span>
              </button>
            )
          })}
        </div>
      </aside>
      <main className="context-run-detail">
        <RunDetail
          run={selectedRun}
          selectedStepIndex={selectedStepIndex}
          onSelectStep={setSelectedStepIndex}
          summaryColumns={2}
        />
      </main>
    </div>
  )
}
