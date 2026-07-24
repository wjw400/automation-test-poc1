# OrderFlow Demo System

## Purpose

`mock-service` is a self-contained order fulfillment application used to demonstrate the automation platform against a visible business system.

It has two API surfaces that share the same H2 in-memory domain model:

- `/web-api/**` supports the embedded business UI.
- `/secure/fulfillment/**` accepts signed and encrypted partner requests.

Only the OrderFlow partner endpoints under `/secure/fulfillment/**` are published by the mock service.

## Domain

The first version contains:

- Product and available inventory
- Order and order lines
- Shipment
- Order lifecycle: `CREATED -> RESERVED -> DISPATCHED`
- Cancellation before dispatch

H2 uses `create-drop` and the application seeds four products, two orders, and one shipment on startup. `POST /web-api/admin/reset` restores this dataset.

## Partner Access

OrderFlow owns one Service Identity configured outside the database:

- Service public key is available to copy or download from the UI.
- Service private key and passphrase are never returned by an API.

Trusted Partner Profiles are stored in H2 and contain the partner public key plus parsed metadata such as Key ID and Fingerprint. The default `secureEcho` profile is seeded at startup. Resetting demo business data does not remove Partner Profiles.

For an inbound request, OrderFlow decrypts with its private key and verifies the signature with the selected Partner Profile public key. For an outbound response, it signs with its private key and encrypts with that same partner public key.

## Observability

Business mutations are retained in a 200-entry in-memory activity buffer.

Each secure exchange records:

- wire request
- decrypted request
- business result
- encrypted wire response
- profile, result, duration, and error

The embedded UI exposes this evidence under **API Activity**. Key material and passphrases are never recorded.

## Demonstration Flow

The reusable scenario `mock/secure-order-fulfillment.yaml` performs:

1. Create partner order
2. Extract `orderId`
3. Reserve inventory
4. Dispatch shipment
5. Extract `trackingNumber`
6. Query and validate the dispatched order

After execution, the resulting order and shipment are visible in OrderFlow and the secure exchanges are visible under API Activity.
