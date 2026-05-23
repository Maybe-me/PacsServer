import axios from 'axios'

const configuredApiBaseUrl = ''

const http = axios.create({
  baseURL: configuredApiBaseUrl,
  timeout: 15000,
})

function normalizeBaseUrl(baseUrl) {
  if (!baseUrl) {
    return window.location.origin
  }
  if (/^https?:\/\//i.test(baseUrl)) {
    return baseUrl.replace(/\/$/, '')
  }
  return new URL(baseUrl, window.location.origin).toString().replace(/\/$/, '')
}

export function resolveBackendUrl(path) {
  if (!path) {
    return normalizeBaseUrl(configuredApiBaseUrl)
  }
  if (/^https?:\/\//i.test(path)) {
    return path
  }
  return `${normalizeBaseUrl(configuredApiBaseUrl)}${path.startsWith('/') ? path : `/${path}`}`
}

export async function get(url, config = {}) {
  const response = await http.get(url, config)
  return response.data
}

export async function post(url, data) {
  const response = await http.post(url, data)
  return response.data
}

export async function put(url, data) {
  const response = await http.put(url, data)
  return response.data
}

export async function remove(url) {
  const response = await http.delete(url)
  return response.data
}
