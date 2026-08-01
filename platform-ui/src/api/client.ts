import axios from 'axios'

export const apiClient = axios.create({
  baseURL: '/api',
  timeout: 20000,
  headers: { 'X-Platform-User': 'local-user' },
})
