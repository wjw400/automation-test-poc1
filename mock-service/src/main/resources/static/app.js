const state = {
  view: 'orders',
  summary: {},
  products: [],
  orders: [],
  shipments: [],
  activity: [],
  serviceKey: null,
  partnerKeys: [],
  lineSequence: 0,
}

const elements = {
  toast: document.querySelector('#toast'),
  ordersBody: document.querySelector('#orders-body'),
  shipmentsBody: document.querySelector('#shipments-body'),
  activityBody: document.querySelector('#activity-body'),
  productGrid: document.querySelector('#product-grid'),
  orderMetrics: document.querySelector('#order-metrics'),
  orderDialog: document.querySelector('#order-dialog'),
  orderDialogTitle: document.querySelector('#order-dialog-title'),
  orderDialogContent: document.querySelector('#order-dialog-content'),
  activityDialog: document.querySelector('#activity-dialog'),
  activityDialogTitle: document.querySelector('#activity-dialog-title'),
  activityDialogContent: document.querySelector('#activity-dialog-content'),
  createOrderDialog: document.querySelector('#create-order-dialog'),
  orderLines: document.querySelector('#order-lines'),
  serviceKeyMetadata: document.querySelector('#service-key-metadata'),
  servicePublicKey: document.querySelector('#service-public-key'),
  partnerKeyBody: document.querySelector('#partner-key-body'),
  partnerKeyDialog: document.querySelector('#partner-key-dialog'),
  partnerKeyDialogTitle: document.querySelector('#partner-key-dialog-title'),
  partnerKeyForm: document.querySelector('#partner-key-form'),
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: options.body ? { 'Content-Type': 'application/json', ...options.headers } : options.headers,
    ...options,
  })
  if (!response.ok) {
    const errorBody = await response.json().catch(() => ({}))
    throw new Error(errorBody.message || `Request failed with status ${response.status}`)
  }
  if (response.status === 204) {
    return undefined
  }
  return response.json()
}

async function refreshAll() {
  try {
    const [summary, products, orders, shipments, activity] = await Promise.all([
      api('/web-api/summary'),
      api('/web-api/products'),
      api('/web-api/orders'),
      api('/web-api/shipments'),
      api('/web-api/activity'),
    ])
    Object.assign(state, { summary, products, orders, shipments, activity })
    render()
  } catch (error) {
    showToast(error.message, true)
  }
}

async function refreshSecurity() {
  try {
    const [serviceKey, partnerKeys] = await Promise.all([
      api('/web-api/security/service-key'),
      api('/web-api/security/partners'),
    ])
    Object.assign(state, { serviceKey, partnerKeys })
    renderSecurity()
  } catch (error) {
    showToast(error.message, true)
  }
}

function render() {
  renderMetrics()
  renderOrders()
  renderProducts()
  renderShipments()
  renderActivity()
  document.querySelector('#orders-count').textContent = state.orders.length
  document.querySelector('#products-count').textContent = state.products.length
  document.querySelector('#shipments-count').textContent = state.shipments.length
  document.querySelector('#activity-count').textContent = state.activity.length
}

function renderMetrics() {
  const statusCounts = state.orders.reduce((result, order) => {
    result[order.status] = (result[order.status] || 0) + 1
    return result
  }, {})
  elements.orderMetrics.innerHTML = [
    metric(state.orders.length, 'Total orders'),
    metric(statusCounts.CREATED || 0, 'Awaiting reservation'),
    metric(statusCounts.RESERVED || 0, 'Ready to dispatch'),
    metric(statusCounts.DISPATCHED || 0, 'Dispatched'),
  ].join('')
}

function metric(value, label) {
  return `<div class="metric"><strong>${value}</strong><span>${label}</span></div>`
}

function renderOrders() {
  document.querySelector('#orders-empty').hidden = state.orders.length > 0
  elements.ordersBody.innerHTML = state.orders.map((order) => `
    <tr>
      <td><span class="cell-primary">${escapeHtml(order.id)}</span><span class="cell-secondary">${escapeHtml(order.customerReference)}</span></td>
      <td>${escapeHtml(order.customerName)}</td>
      <td>${order.items.reduce((total, item) => total + item.quantity, 0)}</td>
      <td>${formatCurrency(order.totalAmount)}</td>
      <td>${statusBadge(order.status)}</td>
      <td>${formatDate(order.updatedAt)}</td>
      <td><button class="row-action" type="button" data-order-id="${escapeHtml(order.id)}">View</button></td>
    </tr>
  `).join('')
}

function renderProducts() {
  elements.productGrid.innerHTML = state.products.map((product) => `
    <article class="product-card">
      <div class="product-image ${escapeHtml(product.imageKey)}" role="img" aria-label="${escapeHtml(product.name)}"></div>
      <div class="product-copy">
        <div class="product-meta"><span>${escapeHtml(product.category)}</span><span>${escapeHtml(product.sku)}</span></div>
        <h2>${escapeHtml(product.name)}</h2>
        <div class="product-meta"><strong>${formatCurrency(product.unitPrice)}</strong>${statusBadge(product.status)}</div>
        <div class="inventory-row"><span>Available inventory</span><strong>${product.availableQuantity}</strong></div>
      </div>
    </article>
  `).join('')
}

function renderShipments() {
  document.querySelector('#shipments-empty').hidden = state.shipments.length > 0
  elements.shipmentsBody.innerHTML = state.shipments.map((shipment) => `
    <tr>
      <td><span class="cell-primary">${escapeHtml(shipment.id)}</span></td>
      <td>${escapeHtml(shipment.orderId)}</td>
      <td>${escapeHtml(shipment.carrier)}</td>
      <td><span class="channel">${escapeHtml(shipment.trackingNumber)}</span></td>
      <td>${statusBadge(shipment.status)}</td>
      <td>${formatDate(shipment.createdAt)}</td>
    </tr>
  `).join('')
}

function renderActivity() {
  document.querySelector('#activity-empty').hidden = state.activity.length > 0
  elements.activityBody.innerHTML = state.activity.map((activity) => `
    <tr>
      <td class="activity-time">${formatDate(activity.startedAt)}</td>
      <td class="activity-operation"><span class="cell-primary">${escapeHtml(activity.operation)}</span><span class="cell-secondary">${escapeHtml(activity.path)}</span></td>
      <td class="activity-channel"><span class="channel">${escapeHtml(activity.channel)}</span></td>
      <td class="activity-profile">${escapeHtml(activity.profile || '—')}</td>
      <td class="activity-result">${statusBadge(activity.outcome)}</td>
      <td class="activity-duration">${activity.durationMs} ms</td>
      <td class="activity-action"><button class="row-action" type="button" data-activity-id="${escapeHtml(activity.id)}">Inspect</button></td>
    </tr>
  `).join('')
}

function renderSecurity() {
  if (!state.serviceKey) return
  elements.serviceKeyMetadata.innerHTML = [
    keyMetadata('Key ID', state.serviceKey.keyId),
    keyMetadata('Fingerprint', state.serviceKey.fingerprint),
    keyMetadata('Algorithm', state.serviceKey.algorithm),
    keyMetadata('Expires', state.serviceKey.expiresAt ? formatDate(state.serviceKey.expiresAt) : 'Never'),
  ].join('')
  elements.servicePublicKey.value = state.serviceKey.publicKey

  document.querySelector('#partner-key-empty').hidden = state.partnerKeys.length > 0
  elements.partnerKeyBody.innerHTML = state.partnerKeys.map((partner) => `
    <tr>
      <td>
        <span class="cell-primary">${escapeHtml(partner.profileName)}</span>
        ${partner.defaultProfile ? '<span class="default-profile">Default</span>' : ''}
      </td>
      <td><span class="cell-primary">${escapeHtml(partner.displayName)}</span><span class="cell-secondary">${escapeHtml(partner.userId || 'No user ID')}</span></td>
      <td><span class="channel">${escapeHtml(partner.keyId)}</span></td>
      <td><span class="fingerprint">${escapeHtml(partner.fingerprint)}</span></td>
      <td>${formatDate(partner.updatedAt)}</td>
      <td>
        <div class="partner-actions">
          <button class="row-action" type="button" data-partner-action="replace" data-profile="${escapeHtml(partner.profileName)}">Replace</button>
          <button class="row-action danger-text" type="button" data-partner-action="delete" data-profile="${escapeHtml(partner.profileName)}">Delete</button>
        </div>
      </td>
    </tr>
  `).join('')
}

function keyMetadata(label, value) {
  return `<div class="key-metadata-item"><span>${label}</span><strong>${escapeHtml(value || '—')}</strong></div>`
}

function setView(view) {
  state.view = view
  document.querySelectorAll('.view').forEach((element) => {
    element.classList.toggle('active', element.id === `${view}-view`)
  })
  document.querySelectorAll('.nav-item').forEach((element) => {
    element.classList.toggle('active', element.dataset.view === view)
  })
  document.querySelector('#partner-access').classList.toggle('active', view === 'security')
}

function openPartnerKeyDialog(profileName) {
  const partner = state.partnerKeys.find((item) => item.profileName === profileName)
  elements.partnerKeyForm.reset()
  elements.partnerKeyForm.dataset.mode = partner ? 'replace' : 'create'
  elements.partnerKeyForm.elements.profileName.readOnly = Boolean(partner)
  elements.partnerKeyForm.elements.profileName.value = partner?.profileName || ''
  elements.partnerKeyForm.elements.displayName.value = partner?.displayName || ''
  elements.partnerKeyDialogTitle.textContent = partner ? 'Replace partner key' : 'Add partner key'
  elements.partnerKeyDialog.showModal()
}

async function submitPartnerKey(event) {
  event.preventDefault()
  const form = event.currentTarget
  const profileName = form.elements.profileName.value.trim()
  const request = {
    profileName,
    displayName: form.elements.displayName.value.trim(),
    publicKey: form.elements.publicKey.value.trim(),
  }
  try {
    const replacing = form.dataset.mode === 'replace'
    await api(replacing
      ? `/web-api/security/partners/${encodeURIComponent(profileName)}`
      : '/web-api/security/partners', {
      method: replacing ? 'PUT' : 'POST',
      body: JSON.stringify(request),
    })
    elements.partnerKeyDialog.close()
    showToast(replacing ? 'Partner key replaced' : 'Partner key added')
    await refreshSecurity()
  } catch (error) {
    showToast(error.message, true)
  }
}

async function deletePartnerKey(profileName) {
  if (!window.confirm(`Delete partner profile "${profileName}"?`)) return
  try {
    await api(`/web-api/security/partners/${encodeURIComponent(profileName)}`, { method: 'DELETE' })
    showToast('Partner key deleted')
    await refreshSecurity()
  } catch (error) {
    showToast(error.message, true)
  }
}

async function openOrder(orderId) {
  try {
    const order = await api(`/web-api/orders/${encodeURIComponent(orderId)}`)
    elements.orderDialogTitle.textContent = order.id
    const availableActions = []
    if (order.status === 'CREATED') {
      availableActions.push(`<button class="button primary" type="button" data-order-action="reserve" data-order-id="${escapeHtml(order.id)}">Reserve inventory</button>`)
    }
    if (order.status === 'RESERVED') {
      availableActions.push(`<button class="button primary" type="button" data-order-action="fulfill" data-order-id="${escapeHtml(order.id)}">Dispatch shipment</button>`)
    }
    if (order.status === 'CREATED' || order.status === 'RESERVED') {
      availableActions.push(`<button class="button danger" type="button" data-order-action="cancel" data-order-id="${escapeHtml(order.id)}">Cancel order</button>`)
    }
    elements.orderDialogContent.innerHTML = `
      <div class="detail-block detail-grid">
        ${detailField('Customer', order.customerName)}
        ${detailField('Customer reference', order.customerReference)}
        ${detailField('Status', statusBadge(order.status))}
        ${detailField('Created', formatDate(order.createdAt))}
        ${detailField('Last updated', formatDate(order.updatedAt))}
        ${detailField('Order total', formatCurrency(order.totalAmount))}
      </div>
      <div class="detail-block">
        <div class="section-heading"><strong>Items</strong><span>${order.items.length} line${order.items.length === 1 ? '' : 's'}</span></div>
        <div class="line-list">
          ${order.items.map((item) => `
            <div class="line-item">
              <strong>${escapeHtml(item.productName)}</strong>
              <span>${item.quantity} × ${formatCurrency(item.unitPrice)}</span>
              <strong>${formatCurrency(item.quantity * Number(item.unitPrice))}</strong>
            </div>
          `).join('')}
        </div>
      </div>
      ${availableActions.length ? `<div class="detail-block order-actions">${availableActions.join('')}</div>` : ''}
    `
    elements.orderDialog.showModal()
  } catch (error) {
    showToast(error.message, true)
  }
}

async function runOrderAction(orderId, action) {
  try {
    await api(`/web-api/orders/${encodeURIComponent(orderId)}/${action}`, {
      method: 'POST',
      body: action === 'fulfill' ? JSON.stringify({ carrier: 'Northstar Logistics' }) : undefined,
    })
    elements.orderDialog.close()
    showToast(action === 'fulfill' ? 'Shipment dispatched' : `Order ${action}d`)
    await refreshAll()
  } catch (error) {
    showToast(error.message, true)
  }
}

function openCreateOrder() {
  document.querySelector('#create-order-form').reset()
  elements.orderLines.innerHTML = ''
  state.lineSequence = 0
  addOrderLine()
  elements.createOrderDialog.showModal()
}

function addOrderLine() {
  const lineId = `line-${++state.lineSequence}`
  const line = document.createElement('div')
  line.className = 'edit-line'
  line.dataset.lineId = lineId
  line.innerHTML = `
    <select aria-label="Product" required>
      <option value="">Select a product</option>
      ${state.products.map((product) => `<option value="${escapeHtml(product.id)}">${escapeHtml(product.name)} · ${product.availableQuantity} available</option>`).join('')}
    </select>
    <input aria-label="Quantity" type="number" min="1" value="1" required>
    <button class="remove-line" type="button" aria-label="Remove item">×</button>
  `
  elements.orderLines.append(line)
}

async function submitOrder(event) {
  event.preventDefault()
  const form = event.currentTarget
  const lines = [...elements.orderLines.querySelectorAll('.edit-line')].map((line) => ({
    productId: line.querySelector('select').value,
    quantity: Number(line.querySelector('input').value),
  })).filter((line) => line.productId)
  if (!lines.length) {
    showToast('Add at least one product', true)
    return
  }
  try {
    await api('/web-api/orders', {
      method: 'POST',
      body: JSON.stringify({
        customerName: form.elements.customerName.value,
        customerReference: form.elements.customerReference.value,
        items: lines,
      }),
    })
    elements.createOrderDialog.close()
    showToast('Order created')
    await refreshAll()
  } catch (error) {
    showToast(error.message, true)
  }
}

async function inspectActivity(activityId) {
  try {
    const activity = await api(`/web-api/activity/${encodeURIComponent(activityId)}`)
    elements.activityDialogTitle.textContent = activity.operation
    elements.activityDialogContent.innerHTML = `
      <div class="activity-meta">
        <span>${formatDate(activity.startedAt)}</span>
        <span>${escapeHtml(activity.method)} ${escapeHtml(activity.path)}</span>
        <span>${escapeHtml(activity.channel)}</span>
        <span>${escapeHtml(activity.profile || 'No profile')}</span>
        <span>${activity.durationMs} ms</span>
        ${statusBadge(activity.outcome)}
      </div>
      ${activity.errorMessage ? `<div class="detail-block"><span class="status failed">${escapeHtml(activity.errorMessage)}</span></div>` : ''}
      <div class="evidence-grid">
        ${evidence('Wire request', activity.wireRequest)}
        ${evidence('Decrypted request', activity.decryptedRequest)}
        ${evidence('Business result', activity.businessResult)}
        ${evidence('Wire response', activity.wireResponse)}
      </div>
    `
    elements.activityDialog.showModal()
  } catch (error) {
    showToast(error.message, true)
  }
}

function detailField(label, value) {
  return `<div class="detail-field"><span>${label}</span><strong>${value}</strong></div>`
}

function evidence(title, value) {
  return `<section class="evidence"><h3>${title}</h3><pre>${escapeHtml(formatJson(value))}</pre></section>`
}

function formatJson(value) {
  return value == null ? 'No data' : JSON.stringify(value, null, 2)
}

function statusBadge(status) {
  const normalized = String(status || 'UNKNOWN').toLowerCase()
  return `<span class="status ${escapeHtml(normalized)}">${escapeHtml(String(status || 'UNKNOWN'))}</span>`
}

function formatCurrency(value) {
  return new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD' }).format(Number(value))
}

function formatDate(value) {
  return new Intl.DateTimeFormat('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(value))
}

function escapeHtml(value) {
  return String(value ?? '')
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#039;')
}

let toastTimer
function showToast(message, error = false) {
  clearTimeout(toastTimer)
  elements.toast.textContent = message
  elements.toast.classList.toggle('error', error)
  elements.toast.classList.add('visible')
  toastTimer = setTimeout(() => elements.toast.classList.remove('visible'), 2600)
}

document.querySelectorAll('.nav-item').forEach((button) => {
  button.addEventListener('click', () => setView(button.dataset.view))
})

document.querySelector('#partner-access').addEventListener('click', async () => {
  setView('security')
  await refreshSecurity()
})

document.querySelector('#new-order').addEventListener('click', openCreateOrder)
document.querySelector('#add-line').addEventListener('click', addOrderLine)
document.querySelector('#create-order-form').addEventListener('submit', submitOrder)
document.querySelector('#add-partner-key').addEventListener('click', () => openPartnerKeyDialog())
elements.partnerKeyForm.addEventListener('submit', submitPartnerKey)

document.querySelector('#copy-service-key').addEventListener('click', async () => {
  try {
    await navigator.clipboard.writeText(state.serviceKey.publicKey)
    showToast('Service public key copied')
  } catch {
    showToast('Unable to copy the public key', true)
  }
})

document.querySelector('#partner-key-file').addEventListener('change', async (event) => {
  const [file] = event.target.files
  if (file) {
    elements.partnerKeyForm.elements.publicKey.value = await file.text()
  }
})

elements.orderLines.addEventListener('click', (event) => {
  if (!event.target.matches('.remove-line')) return
  if (elements.orderLines.children.length === 1) {
    showToast('An order needs at least one item', true)
    return
  }
  event.target.closest('.edit-line').remove()
})

elements.ordersBody.addEventListener('click', (event) => {
  const button = event.target.closest('[data-order-id]')
  if (button) openOrder(button.dataset.orderId)
})

elements.orderDialogContent.addEventListener('click', (event) => {
  const button = event.target.closest('[data-order-action]')
  if (button) runOrderAction(button.dataset.orderId, button.dataset.orderAction)
})

elements.activityBody.addEventListener('click', (event) => {
  const button = event.target.closest('[data-activity-id]')
  if (button) inspectActivity(button.dataset.activityId)
})

elements.partnerKeyBody.addEventListener('click', (event) => {
  const button = event.target.closest('[data-partner-action]')
  if (!button) return
  if (button.dataset.partnerAction === 'replace') {
    openPartnerKeyDialog(button.dataset.profile)
  } else {
    deletePartnerKey(button.dataset.profile)
  }
})

document.querySelectorAll('[data-close-dialog]').forEach((button) => {
  button.addEventListener('click', () => document.querySelector(`#${button.dataset.closeDialog}`).close())
})

document.querySelector('#reset-demo').addEventListener('click', async () => {
  if (!window.confirm('Reset all orders, shipments, inventory, and API activity?')) return
  try {
    await api('/web-api/admin/reset', { method: 'POST' })
    showToast('Demo data restored')
    await refreshAll()
  } catch (error) {
    showToast(error.message, true)
  }
})

document.querySelector('#clear-activity').addEventListener('click', async () => {
  try {
    await api('/web-api/activity', { method: 'DELETE' })
    await refreshAll()
    showToast('API activity cleared')
  } catch (error) {
    showToast(error.message, true)
  }
})

refreshAll()
setInterval(() => {
  if (state.view === 'activity') refreshAll()
}, 5000)
