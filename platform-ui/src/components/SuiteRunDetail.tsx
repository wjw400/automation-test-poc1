import { RightOutlined } from '@ant-design/icons'
import { Button, Space, Typography } from 'antd'
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
  if (!run) {
    return <div className="workbench-empty"><Text type="secondary">Suite run not found.</Text></div>
  }

  const passedCount = run.runs.filter((child) => child.status === 'PASSED').length

  return (
    <div className="suite-run-tab-pane">
      <header className="suite-run-header">
        <div>
          <Text type="secondary">Suite Execution</Text>
          <h1>{run.suiteName}</h1>
        </div>
        <Space size={16}>
          <div className="suite-run-summary">
            <StatusPill status={run.status} />
            <span>{passedCount}/{run.runs.length} passed</span>
            <span>{run.durationMs} ms</span>
          </div>
          {onOpenSuite ? <Button size="small" onClick={onOpenSuite}>Open Suite</Button> : null}
        </Space>
      </header>
      <div className="suite-run-body">
        {run.errorMessage ? <div className="suite-run-error">{run.errorMessage}</div> : null}
        <div className="suite-run-meta">
          <span>Started {new Date(run.startedAt).toLocaleString()}</span>
          <span>Triggered by {run.triggeredBy}</span>
        </div>
        <section className="suite-run-results">
          <h2>Scenario Results</h2>
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
                  <span>{[child.environmentName, child.profileName].filter(Boolean).join(' / ') || 'Runtime context unavailable'}</span>
                </span>
                <StatusPill status={child.status} />
                <span className="suite-run-duration">{child.durationMs} ms</span>
                {onOpenRun ? <RightOutlined /> : <span />}
              </button>
            ))}
          </div>
        </section>
      </div>
    </div>
  )
}
