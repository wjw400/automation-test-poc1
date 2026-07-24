import { Select, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
import { useTranslation } from 'react-i18next'
import { formatDateTime } from '../i18n'
import type { SuiteRunRecord } from '../types/platform'
import { RunDetail } from './RunDetail'
import { StatusPill } from './StatusPill'

const { Text } = Typography

type SuiteExecutionWorkspaceProps = {
  run?: SuiteRunRecord
  executions: SuiteRunRecord[]
  onSelectExecution: (runId: string) => void
  selectedRunId?: string
  onSelectRun: (runId: string) => void
}

export function SuiteExecutionWorkspace({
  run,
  executions,
  onSelectExecution,
  selectedRunId,
  onSelectRun,
}: SuiteExecutionWorkspaceProps) {
  const { t, i18n } = useTranslation(['execution', 'scenario'])
  const selectedRun = useMemo(
    () => run?.runs.find((child) => child.id === selectedRunId),
    [run, selectedRunId],
  )
  const [selectedStepIndex, setSelectedStepIndex] = useState(1)

  useEffect(() => {
    setSelectedStepIndex(selectedRun?.steps[0]?.index ?? 1)
  }, [selectedRun?.id, selectedRun?.steps])

  if (!run) {
    return (
      <div className="context-runs-empty">
        <Text strong>{t('execution:executionNotFound')}</Text>
        <Text type="secondary">{t('execution:executionNotFoundDescription')}</Text>
      </div>
    )
  }

  const passedCount = run.runs.filter((child) => child.status === 'PASSED').length
  const hasScenarioError = run.runs.some((child) => child.errorMessage)

  return (
    <div className="suite-execution-workspace">
      <header className="suite-execution-summary">
        <div className="suite-execution-picker">
          <Select
            aria-label={t('execution:selectExecution')}
            value={run.id}
            options={executions.map((execution) => ({
              value: execution.id,
              label: formatExecutionOption(
                execution,
                formatDateTime(execution.startedAt, i18n.resolvedLanguage),
                t('execution:passedCount', {
                  passed: execution.runs.filter((child) => child.status === 'PASSED').length,
                  total: execution.runs.length,
                }),
              ),
            }))}
            onChange={onSelectExecution}
          />
        </div>
        <div className="suite-execution-summary-stats">
          <StatusPill status={run.status} />
          <span>{t('execution:passedCount', { passed: passedCount, total: run.runs.length })}</span>
          <span>{t('execution:duration', { duration: run.durationMs })}</span>
          <span>{run.triggeredBy}</span>
        </div>
      </header>

      {run.errorMessage && !hasScenarioError ? (
        <div className="suite-execution-error">{run.errorMessage}</div>
      ) : null}

      <div className="suite-execution-main">
        <aside className="suite-scenario-nav" aria-label={t('execution:scenarioResults')}>
          <div className="suite-scenario-nav-head">
            <Text strong>{t('execution:scenarios')}</Text>
            <Text type="secondary">{run.runs.length}</Text>
          </div>
          <div className="suite-scenario-nav-scroll">
            {run.runs.map((child, index) => {
              const context = [child.environmentName, child.profileName].filter(Boolean).join(' / ')
              return (
                <button
                  key={child.id}
                  type="button"
                  className={child.id === selectedRun?.id ? 'suite-scenario-row active' : 'suite-scenario-row'}
                  title={child.suiteDisplayName || child.caseId}
                  onClick={() => onSelectRun(child.id)}
                >
                  <span className="suite-scenario-order">
                    {String(child.suiteCaseOrder ?? index + 1).padStart(2, '0')}
                  </span>
                  <span className="suite-scenario-row-copy">
                    <strong>{child.suiteDisplayName || child.caseId}</strong>
                    <span>{context || t('scenario:runtimeContextUnavailable')}</span>
                  </span>
                  <span className="suite-scenario-row-result">
                    <StatusPill status={child.status} />
                    <span>{t('execution:duration', { duration: child.durationMs })}</span>
                  </span>
                </button>
              )
            })}
          </div>
        </aside>

        <section className="suite-scenario-evidence">
          {selectedRun ? (
            <>
              {selectedRun.errorMessage ? (
                <div className="suite-scenario-error">{selectedRun.errorMessage}</div>
              ) : null}
              <div className="suite-scenario-evidence-body">
                <RunDetail
                  run={selectedRun}
                  selectedStepIndex={selectedStepIndex}
                  onSelectStep={setSelectedStepIndex}
                  showSummary={false}
                />
              </div>
            </>
          ) : (
            <div className="context-runs-empty">
              <Text strong>{t('scenario:selectScenario')}</Text>
              <Text type="secondary">{t('scenario:selectScenarioDescription')}</Text>
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

function formatExecutionOption(run: SuiteRunRecord, startedAt: string, passedLabel: string) {
  return `${startedAt} · ${run.status} · ${passedLabel}`
}
