---
name: secure-payload
description: Compose encrypted, signed, encoded, and mixed clear/encrypted API payload flows.
version: 1.1.0
riskLevel: high
requiredTools:
  - getScenario
  - getSupportedActions
  - searchPublishedKnowledge
  - createScenarioDraft
  - applyScenarioCommands
  - validateScenarioDraft
  - getScenarioDraftDiff
matchTerms:
  - encrypt
  - decrypt
  - pgp
  - signature
  - signed
  - cipher
  - 加密
  - 解密
  - 签名
  - 密文
enabled: true
---
# Secure Payload

Read the effective `processingSettings` and resolved `authoring` contract from `getApiOperationSchema` first. When API processing already protects the request and decodes the response, write the documented business request fields and use `business.response.body` for business assertions and extraction. Do not add duplicate PGP Actions or manually construct a ciphertext envelope in automatic mode.

Only when manual processing is intentionally needed, use supported Case DSL v1 Actions and preserve their execution order.

- `crypto.pgp.signAndEncrypt` signs with the resolved Profile and encrypts for the recipient.
- `crypto.pgp.encrypt` encrypts without signing.
- `data.encodeBase64` is explicit and is used only when the wire protocol requires another Base64 layer.
- `body.buildJson` composes a final JSON wire body from values and step artifacts.
- `crypto.pgp.decryptAndVerify` reads a response value, decrypts it, and verifies its signature.
- `crypto.pgp.decrypt` decrypts without requiring a signature.

Actions cannot select a Profile. They inherit the Step or Scenario runtime Profile. Never request or expose key material, passphrases, credentials, or Environment/Profile secrets. If protocol-specific details are missing from published Knowledge, state the assumption instead of fabricating it.
