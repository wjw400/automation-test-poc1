import { Select, Typography } from 'antd'
import { useEffect, useMemo, useState } from 'react'
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
        <Text strong>Execution not found</Text>
        <Text type="secondary">Select another execution from the history.</Text>
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
            aria-label="Select suite execution"
            value={run.id}
            options={executions.map((execution) => ({
              value: execution.id,
              label: formatExecutionOption(execution),
            }))}
            onChange={onSelectExecution}
          />
        </div>
        <div className="suite-execution-summary-stats">
          <StatusPill status={run.status} />
          <span>{passedCount}/{run.runs.length} passed</span>
          <span>{run.durationMs} ms</span>
          <span>{run.triggeredBy}</span>
        </div>
      </header>

      {run.errorMessage && !hasScenarioError ? (
        <div className="suite-execution-error">{run.errorMessage}</div>
      ) : null}

      <div className="suite-execution-main">
        <aside className="suite-scenario-nav" aria-label="Scenario results">
          <div className="suite-scenario-nav-head">
            <Text strong>Scenarios</Text>
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
                    <span>{context || 'Runtime context unavailable'}</span>
                  </span>
                  <span className="suite-scenario-row-result">
                    <StatusPill status={child.status} />
                    <span>{child.durationMs} ms</span>
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
              <Text strong>Select a scenario</Text>
              <Text type="secondary">Inspect its steps, payloads, and validations here.</Text>
            </div>
          )}
        </section>
      </div>
    </div>
  )
}

function formatExecutionOption(run: SuiteRunRecord) {
  const passedCount = run.runs.filter((child) => child.status === 'PASSED').length
  return `${new Date(run.startedAt).toLocaleString()} · ${run.status} · ${passedCount}/${run.runs.length} passed`
}
