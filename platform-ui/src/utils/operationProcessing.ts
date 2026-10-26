import type { OperationProcessingConfiguration } from '../types/platform'

export function processingStages(configuration: OperationProcessingConfiguration): string[] {
  const request = configuration.request
  const location = request.ciphertext?.location === 'body' ? 'HTTP body' : request.ciphertext?.pointer || '（需要设置路径）'
  return [
    '解析请求变量，执行用例的请求前 Actions',
    ...(request.protection === 'none' ? [] : [request.protection === 'pgp-sign-encrypt' ? 'PGP 签名并加密业务 JSON' : 'PGP 加密业务 JSON', `将密文写入 ${location}`]),
    '执行用例的传输报文 Actions（如有）',
    '发送 HTTP 请求并保存原始响应',
    ...Object.entries(configuration.responses).map(([status, rule]) => {
      if (rule.protection === 'none') return `响应 ${status}：直接读取普通 JSON`
      const position = rule.ciphertext?.location === 'body' ? 'HTTP body' : rule.ciphertext?.pointer || '（需要设置路径）'
      return `响应 ${status}：从 ${position} 提取密文，${rule.protection === 'pgp-decrypt-verify' ? 'PGP 解密并验签' : 'PGP 解密'}`
    }),
    '执行用例的响应后 Actions，然后进行断言和变量提取',
  ]
}
