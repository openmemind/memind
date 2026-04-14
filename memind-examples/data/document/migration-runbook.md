# Ledger Migration Runbook

## Approved Window

- Start no earlier than Saturday 2026-04-18 01:00 UTC.
- Finish validation or rollback decision by 03:00 UTC.
- If prechecks are incomplete by 01:15 UTC, postpone the change instead of compressing the schedule.

## Required Prechecks

1. Create a fresh production snapshot within 30 minutes of migration start.
2. Pause batch backfills and bulk invoice imports.
3. Confirm `billing_ledger_v2` shadow writes are healthy on 5% canary traffic.
4. Verify no finance operator is running manual balance corrections.
5. Confirm the release commander and the primary database on-call are both present in `#release-billing`.

## Execution Steps

1. Freeze manual billing adjustments.
2. Pause ledger-worker backfills.
3. Run schema migration `2026_04_ledger_expand`.
4. Verify row counts on `invoice_ledger_shadow`.
5. Raise `billing_ledger_v2` to 25% traffic.
6. Run validation query pack and compare account-level balances.
7. Raise `billing_ledger_v2` to 100% traffic.
8. Resume backfills only after 20 minutes of stable metrics.

## Rollback Path

- If step 6 validation fails, stop the rollout immediately and disable `billing_ledger_v2`.
- Do not attempt destructive schema rollback during the window.
- Route writes back to the legacy ledger and keep the expanded shadow tables for forensic inspection.
- If rollback is triggered after step 7, finance operations must be notified before replay jobs resume.
