# Automan — Client Transactions Requirements

**Document purpose:** Define how buyer accounts and the ledger should work in Automan.  
**Audience:** Business owner / operations & finance staff.  
**Status:** Draft — pending client sign-off  
**Version:** 1.1 · May 2026 *(Phase 2b: auto-create client on invoice)*

---

## 1. What this module is for

**Client Transactions** is the **money ledger for each overseas buyer** (business client).

It answers three questions at a glance:

1. How much has this client **paid** us?
2. How much have we **invoiced** them?
3. What is their **current balance** (prepaid credit or amount owed)?

It is **not** a duplicate of Shipping History or Invoice pages. Shipment details live there; **only money** lives here.

---

## 2. Recommended model (default proposal)

### Balance meaning — Option A (recommended)

| Balance shown | Meaning |
|---------------|---------|
| **+¥500,000** | Client has **prepaid credit** (paid ahead of invoices) |
| **¥0** | Settled |
| **−¥500,000** | Client **owes** us (invoices exceed payments) |

**Formula:**

```
New balance = Previous balance + Payment received − Invoice amount
```

> **Client decision required:** Confirm Option A (prepaid wallet) or Option B (positive = debt owed).  
> Option B uses the opposite sign on screen. Automan can support either once confirmed.

---

## 3. What posts to the ledger automatically

| When | System action | Ledger type |
|------|---------------|-------------|
| Invoice **saved** or **confirmed** in Automan | One line per invoice | **INVOICE_ISSUED** (charge) |
| Invoice **deleted** *(Phase 2)* | Reverse the charge | **INVOICE_REVERSAL** |

**Staff rule:** Do **not** manually re-enter invoice totals if the invoice was created in Automan.

**Client on invoice (agreed May 2026):** If the buyer name on the invoice is **not yet** in **Master → Clients** (Client Transactions), Automan **creates that client automatically** when the invoice is saved or confirmed, then posts the ledger line. Staff do **not** need to add the client manually first, and the system must **not** block the ledger with a message like *“Client is not in Client Management; ledger entry was not posted.”*

If the name matches **more than one** existing client (duplicate names), ledger posting is still skipped and staff must resolve the duplicate in Master → Clients.

---

## 4. What staff enter manually

Only **finance / admin** users add manual lines.

| Type | When to use | Required fields |
|------|-------------|-----------------|
| **PAYMENT_RECEIVED** | Bank TT, cash, wire received | Date, amount, reference (TT slip / bank ref), optional note |
| **ADJUSTMENT** | Refund, write-off, bank fee, correction | Date, amount ±, reason (required) |
| **OPENING_BALANCE** *(one-time migration)* | Import starting balance from old Excel | Date, amount, note “Opening balance as of …” |

**Removed from daily use:** Vessel names as “Event” and manual “Shipment Price” rows (those come from **Invoice**).

---

## 5. Credit limit & alerts

| Setting | Purpose | Example |
|---------|---------|---------|
| **Credit limit** | Maximum the client may owe beyond prepaid | ¥2,000,000 |
| **Alert threshold** | Warn before limit is reached | Alert at 90% of limit used |

**Available credit (Option A):** `Credit limit + current balance`

| Alert | Condition (Option A) | Proposed action |
|-------|----------------------|-----------------|
| Near limit | Balance ≤ −(90% × credit limit) | Yellow warning on client list |
| Over limit | Balance < −credit limit | Red alert; optionally block new invoices |

> **Client decision required:** Warn only, or **block** new invoices/bookings when over limit?

---

## 6. Client name linking (one name everywhere)

Every buyer should use the **same name** on:

- Purchase form → **Client Name**
- Invoice → **Client**
- Client Map (if used)

**On invoice save/confirm:** If that name is not in **Master → Clients** yet, Automan **adds a new client row** automatically (then links purchases and posts the ledger). Staff can fill in credit limit and other details later in Client Transactions.

If names differ only by spelling or spacing across old data, finance may still need a one-time cleanup; new invoices should not require a separate “add client first” step.

---

## 7. Screen layout (target)

### Client list

- Client #, name, **current balance**, credit limit, **available credit**
- Alert badge (near / over limit)
- Link to detail ledger

### Client detail — ledger table

| Date | Type | Reference | Description | Credit (+) | Debit (−) | Balance |
|------|------|-----------|-------------|------------|-----------|---------|
| 2026-05-10 | Payment | TT-88421 | TT received | ¥1,000,000 | | +¥1,000,000 |
| 2026-05-15 | Invoice | INV-2026-041 | Vessel XYZ · 3 cars | | ¥850,000 | +¥150,000 |

**Actions:** Add Payment · Add Adjustment · Export statement *(Phase 4)*

---

## 8. Sign-off questions

Please answer so development can proceed:

| # | Question | Your answer |
|---|----------|-------------|
| 1 | Balance: Option A (positive = prepaid) or Option B (positive = owes)? | |
| 2 | When is the client charged — only on **invoice confirm**, or earlier (booking)? | |
| 3 | Do clients usually pay **before** or **after** shipment? | |
| 4 | Stop using manual “shipment price” rows — **yes / no**? | |
| 5 | Credit limit: **warn only** or **block** new invoices when exceeded? | |
| 6 | Currency: all **JPY**, or some clients in USD? | |
| 7 | Need **opening balance import** from current Excel? | |
| 8 | Who uses this screen — finance only, or operations too? | |
| 9 | If invoice is deleted/edited, should ledger **auto-reverse**? | |
| 10 | Reports needed: client statement PDF, aging (30/60/90 days)? | |

**Approved by:** _________________________ **Date:** _____________

---

## 9. Rollout phases (summary)

| Phase | Deliverable |
|-------|-------------|
| **1** | Clean event types, balance labels, remove vessel dropdown, unify balance math |
| **2** | Invoice ledger sync/reversal, `purchases.client_id` linking, **auto-create client on invoice** when name is new |
| **2b** *(revision)* | Replace “warn and skip ledger” with auto-create; remove *“not in Client Management”* constraint |
| **3** | Credit limit alerts on list page |
| **4** | Client statement PDF + unpaid invoice report |

*Technical detail: Phase 1 → `CLIENT_TRANSACTIONS_PHASE1_PLAN.md` · Phase 2 / 2b → `CLIENT_TRANSACTIONS_PHASE2_PLAN.md`.*

---

*Automan Car Purchase Management · Internal requirements draft*
