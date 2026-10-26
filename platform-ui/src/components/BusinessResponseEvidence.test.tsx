import { cleanup, render, screen } from '@testing-library/react'
import { afterEach, beforeEach, expect, it } from 'vitest'
import i18n from '../i18n'
import { BusinessResponseEvidence } from './BusinessResponseEvidence'

beforeEach(async () => { await i18n.changeLanguage('zh-CN') })
afterEach(cleanup)

it('shows the assertion source value without adding the report result name to the JSON', () => {
  const business = { id: 'O3011', status: 'CREATED', items: [{ productId: 'P102', quantity: 2 }] }
  const { container } = render(<BusinessResponseEvidence step={{
    decryptedPayloads: { business }, response: null,
  }} />)
  expect(JSON.parse(container.querySelector('pre')!.textContent!)).toEqual(business)
  expect(screen.getByText('business.response.body')).toBeInTheDocument()
  expect(screen.getByText(/JSONPath 以此对象为根/)).toBeInTheDocument()
  expect(screen.queryByText('decrypted.business')).not.toBeInTheDocument()
})

it('preserves a real business field inside the decrypted response', () => {
  const business = { business: { id: 'O3011', status: 'CREATED' }, metadata: { version: 1 } }
  const { container } = render(<BusinessResponseEvidence step={{
    decryptedPayloads: { business }, response: null,
  }} />)
  expect(JSON.parse(container.querySelector('pre')!.textContent!)).toEqual(business)
})

it('shows additional named results separately with their selectable data sources', () => {
  const { container } = render(<BusinessResponseEvidence step={{
    decryptedPayloads: { business: { id: 'O3011' }, order: { status: 'CREATED' } }, response: null,
  }} />)
  expect([...container.querySelectorAll('pre')].map((item) => JSON.parse(item.textContent!)))
    .toEqual([{ id: 'O3011' }, { status: 'CREATED' }])
  expect(screen.getByText('decrypted.order')).toBeInTheDocument()
})

it('uses the raw response only when no business result exists, preserving explicit null', () => {
  const response = { statusCode: 200, headers: {}, body: '{"id":"O3011"}' }
  const { container, rerender } = render(<BusinessResponseEvidence step={{ decryptedPayloads: {}, response }} />)
  expect(JSON.parse(container.querySelector('pre')!.textContent!)).toEqual({ id: 'O3011' })
  rerender(<BusinessResponseEvidence step={{ decryptedPayloads: { business: null }, response }} />)
  expect(screen.getByText('无业务响应值')).toBeInTheDocument()
  expect(container.querySelector('pre')).not.toBeInTheDocument()
})
