import { App as AntApp } from 'antd'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { parseDsl } from '../utils/caseDsl'
import type { SaveCaseInput } from '../types/platform'
import { CaseDesigner } from './CaseDesigner'

describe('CaseDesigner', () => {
  it('shows and edits the required HTTP status rule', async () => {
    const user = userEvent.setup()
    const onChange = vi.fn()
    render(
      <AntApp>
        <CaseDesignerHarness onChange={onChange} />
      </AntApp>,
    )

    await user.click(screen.getByRole('tab', { name: 'Tests' }))
    expect(screen.getByText('200')).toBeInTheDocument()
    expect(screen.getByText('202')).toBeInTheDocument()
    expect(screen.getByText('The response status code is always evaluated as the first assertion.')).toBeInTheDocument()

    await user.click(screen.getByText('Range'))
    const statusInputs = screen.getAllByRole('spinbutton')
    await user.clear(statusInputs[0])
    await user.type(statusInputs[0], '201')
    await user.clear(statusInputs[1])
    await user.type(statusInputs[1], '299')

    const latestDraft = onChange.mock.calls.at(-1)?.[0] as SaveCaseInput
    const parsed = parseDsl(latestDraft.content).data
    expect(parsed).toBeDefined()
    expect((parsed!.steps as Array<any>)[0].response.status).toEqual({
      operator: 'range',
      min: 201,
      max: 299,
    })
  })
})

function CaseDesignerHarness({ onChange }: { onChange: (draft: SaveCaseInput) => void }) {
  const [draft, setDraft] = useState(() => scenarioDraft([200, 202]))
  return (
    <CaseDesigner
      draft={draft}
      onChange={(next) => {
        setDraft(next)
        onChange(next)
      }}
      operations={[]}
      view="step"
      activeStepIndex={0}
      onSelectCaseOverview={() => undefined}
      onSelectStep={() => undefined}
      onOpenRaw={() => undefined}
    />
  )
}

function scenarioDraft(expectedStatuses: number[]): SaveCaseInput {
  return {
    name: 'Create order',
    code: 'create-order',
    description: '',
    tags: [],
    dslVersion: 'v1',
    status: 'ACTIVE',
    content: `dslVersion: v1
name: Create order
description: ""
tags: []
runtime: {}
variables: {}
steps:
  - name: Create order
    type: http
    operation:
      method: POST
      path: /orders
    runtime: {}
    request:
      path: {}
      query: {}
      headers: {}
      body: {}
    actions:
      beforeSend: []
      afterReceive: []
    response:
      status:
        operator: oneOf
        expected: [${expectedStatuses.join(', ')}]
      assertions: []
      extract: {}
`,
  }
}
