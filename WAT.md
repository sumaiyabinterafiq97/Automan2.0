# Automan Car Purchase Management - WAT Architecture

This document outlines the **WAT (Workflows, Agents, Tools)** architecture of the Automan Car Purchase Management System. It provides a high-level conceptual map of how business processes are executed, who/what executes them, and the specific technical tools they use.

---

## 🌊 W - Workflows
*The core business processes that move data from raw input to final business value.*

1. **Purchase Acquisition & Data Entry (W1)**
   - **Goal:** Record a new vehicle purchase from an auction or local supplier.
   - **Steps:** Input chassis/specs → Assign Supplier/Venue (Tree Routing) → Add Rixo/Auction prices → Calculate Total Cost (Before/After Tax) → Save to DB.
   
2. **Car Booking & Logistics (W2)**
   - **Goal:** Group purchased cars into shipments.
   - **Steps:** Filter unshipped cars by POL/Country → Select target cars → Define Vessel, ETD, POD, and Consignee (auto-mapped) → Submit booking request.
   
3. **Shipping Charge & Freight Calculation (W3)**
   - **Goal:** Accurately distribute container shipping costs across individual cars.
   - **Steps:** Trigger C&F or FOB calculator → System fetches "Shipping Charge Map" tiers based on Stock Location → User inputs container count → Cars are grouped into containers → Per-car shipping cost is calculated and applied.

4. **Invoicing & Documentation (W4)**
   - **Goal:** Generate official financial documents for clients.
   - **Steps:** Select Client/Consignee and Vessel → System auto-fetches matching shipped cars → User adds banking/LC details → Generate PDF Invoice / Shipping Schedule.

5. **Client Financial Tracking (W5)**
   - **Goal:** Maintain accurate ledgers for client accounts.
   - **Steps:** Monitor running balances → Apply credit limits → Log payments/adjustments via the Transactions system → Trigger alerts for low balance or limit breaches.

6. **Master Data Management (W6)**
   - **Goal:** Maintain system reference data to ensure UI consistency.
   - **Steps:** Add/Edit/Duplicate base entities (Suppliers, Car Brands, Clients, Consignees) → Changes immediately reflect in application dropdowns.

---

## 🤖 A - Agents
*The active entities (human or systemic) that execute the workflows.*

1. **System Admin / Operational Staff (A1)**
   - **Role:** The human operator.
   - **Responsibilities:** Enters purchase data, orchestrates container packing, manages client ledgers, and approves new user signups.

2. **Kotlin JS / Compose Frontend (A2)**
   - **Role:** The client-side orchestration agent.
   - **Responsibilities:** Manages complex state (e.g., real-time math for C&F/FOB), renders dynamic cascading dropdowns, handles global event delegation, and generates client-side PDFs via `jsPDF`.

3. **Spring Boot / Kotlin Backend (A3)**
   - **Role:** The server-side business logic and validation agent.
   - **Responsibilities:** Secures API endpoints, parses CSV/Text file imports for Rixo data, orchestrates cascading deletes (e.g., batch deleting invoices), and manages authentication tokens.

4. **MySQL Database (A4)**
   - **Role:** The persistence and integrity agent.
   - **Responsibilities:** Stores relational data (Purchases, Mappings, Clients), enforces foreign key constraints, and manages schema versions via Flyway.

5. **Resend Email Service (A5)**
   - **Role:** Third-party communication agent.
   - **Responsibilities:** Delivers signup verification tokens and admin approval notifications asynchronously.

---

## 🛠️ T - Tools
*The specific features, algorithms, or UI elements used by the Agents to complete the Workflows.*

1. **Cascading Tree Router (T1)**
   - **Use:** Enforces hierarchical data logic (e.g., `Supplier` → `Stock Location` → `POL` & `Rixo Company`).
   - **Workflow:** W1 (Purchase Acquisition).

2. **Shipping Tier Allocation Engine (T2)**
   - **Use:** Reads `freight_scm_tiers` to determine how many cars fit in a container from a specific Stock Location, and auto-calculates the exact Yen `¥` rate per car.
   - **Workflow:** W3 (Shipping Charge Calculation).

3. **PDF Generation Modules (T3)**
   - **Use:** Transforms HTML/DOM tabular data into downloadable, formatted PDF documents (Invoices, Rixo Notes, Shipping Schedules).
   - **Workflow:** W4 (Invoicing & Documentation).

4. **Batch Operation Checkboxes (T4)**
   - **Use:** UI tool allowing the Admin to select multiple rows (e.g., in Invoice History) and fire a single atomic `DELETE /batch-delete` request.
   - **Workflow:** W4 (Invoicing), W2 (Booking).

5. **Strict Masked Inputs (T5)**
   - **Use:** Formats and validates input in real-time (e.g., forcing dates into `YYYY-MM-DD` behind the scenes while showing `MM/DD/YYYY` to the user, or formatting large numbers with commas).
   - **Workflow:** W1, W2, W4, W5.

6. **CSV Import Parser (T6)**
   - **Use:** Backend tool that digests raw text/CSV strings from auction houses (Rixo) and normalizes them into structured database rows.
   - **Workflow:** W1 (Purchase Acquisition).
