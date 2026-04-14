# Billing Platform Release Readiness Packet

- Release scope: enable `billing_ledger_v2`, switch invoice writes to the new ledger, and tighten `payment-api` retry handling.
- Change lead: Maya Chen
- Executive approver: Jordan Patel
- Release channel: `#release-billing`
- Maintenance window: Saturday 2026-04-18 01:00-03:00 UTC

## Go / No-Go Criteria

1. Billing replica lag stays below 5 seconds for the last 30 minutes before start.
2. Payment webhook queue remains below 500 pending messages.
3. Canary traffic at 5% has no sustained 5xx regression and billing write latency stays below 250 ms p95.
4. Database snapshot created within 30 minutes of the migration start.
5. Finance operations confirms no manual credit adjustments are in progress.

## Rollback Triggers

- `payment-api` 429 rate above 2% for 10 consecutive minutes after feature flag increase.
- Billing write latency above 400 ms p95 for 15 minutes.
- Migration validation query reports ledger mismatches for more than 25 accounts.
- Migration step 6 exceeds 20 minutes with no steady row-count progress.

## Communication Expectations

- Use `#release-billing` for all operator updates.
- Open an incident bridge if customer impact lasts longer than 10 minutes.
- Notify finance operations and customer support immediately if rollback is triggered.
- Keep the weekend handoff operator informed before each feature-flag increase.
