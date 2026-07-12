# Stage 5 — Admin Dashboard (sidebar shell + accounts table)

**Project:** Bank Management System & ATM Simulator
**Stage:** 5 (post-MVP enhancement — admin UI redesign)
**Date:** 2026-07-12
**Status:** Approved design

---

## 1. Context & Scope

Stages 1–4 delivered the DB, service layer, customer ATM GUI, and admin GUI;
recent work added theming, back buttons, delete, copy-number, and unblock/reopen.
Stage 5 redesigns the **admin side** into a **dashboard**: a persistent left
**sidebar** + a swappable **content area**, with a real **accounts table** and a
**search** box — inspired by (not a pixel copy of) a classic desktop bank admin
UI.

### In scope
- A **dashboard shell** shown after admin login: left sidebar (bank name + menu),
  right content region that swaps between sections.
- Sidebar menu (mapped to real features): **Home**, **All Accounts**,
  **Open Account**, **Log Out**.
- **Home** section: a summary panel (total accounts + active/blocked/closed counts).
- **All Accounts** section: a `TableView` (No, Account #, Holder, Type, Balance,
  Status) with a live **search by holder name** and a **Refresh** button.
- Selecting a row opens the existing **Manage Account** panel (details, history,
  Block/Close/Unblock/Reopen/Delete) rendered in the content area.
- Dashboard styling (blue sidebar, active-item highlight, styled table header).

### Out of scope (deferred, per discussion)
- **Save-to-PDF** export and **date-range filters** (add later).
- The **customer** ATM flow, **Role Select**, and **Admin Login** screens —
  unchanged (still full-screen). No customer-side changes at all.
- No new domain (no "employees"); the table shows bank accounts.

### Environment
Java 17, Maven, JavaFX 21, MySQL, JUnit 5. No new dependencies (no PDF lib).

---

## 2. Architecture — dashboard shell

Today every screen replaces the whole scene root (`App.setRoot`). The dashboard
keeps the sidebar fixed and swaps only the center.

- **`App` gains a dashboard shell**: a `BorderPane` with the **sidebar** on the
  left and a **content region** (`StackPane`) in the center. `App` keeps a
  reference to that content region.
- **Two placement methods in `App`:**
  - `setRoot(Parent)` (existing) — full-screen screens: Role Select, Admin Login,
    and the whole customer flow. Unchanged.
  - `setContent(Parent)` (new) — renders a node into the dashboard's center region
    (sidebar stays). Used by the admin sections.
- **Navigation changes (admin only):**
  - `showAdminMenu()` is repurposed to **build + show the dashboard shell** as the
    scene root, wire the sidebar buttons, then load the default section (**Home**)
    into the content region. (`AdminLoginPresenter` still calls
    `navigator.showAdminMenu()` on success — no presenter change needed.)
  - `showAdminHome()` (new), `showAllAccounts()`, `showAdminOpenAccount()`,
    `showManageAccount(long)` → build view+presenter and call **`setContent(...)`**
    (not `setRoot`). The sidebar persists across these.
  - **Log Out** (sidebar) → clear `AdminSession`, `showRoleSelect()` (`setRoot`,
    leaves the dashboard).
- `AdminNavigator` gains **`showAdminHome()`**. The MVP boundary holds: presenters
  still depend only on `AdminNavigator`; only `App` knows about the shell.

The customer `Navigator` and all customer screens are untouched.

---

## 3. Sidebar

A `SidebarFx` view: a styled `VBox` with the bank name ("Sky Bank") at the top and
menu buttons: **Home**, **All Accounts**, **Open Account**, **Log Out**. Buttons
expose `setOnHome/​setOnAllAccounts/​setOnOpenAccount/​setOnLogout(Runnable)`
(wired by `App` to `showAdminHome`, `showAllAccounts`, `showAdminOpenAccount`, and
logout). The active section is visually highlighted (a style class toggled by
`App` when a section is shown). No business logic — pure view; App wires it.

---

## 4. Home section

`AdminHomeView` (interface): `showSummary(String)`, and the JavaFX `AdminHomeViewFx`.
`AdminHomePresenter(AdminHomeView, AccountService)`: `load()` calls
`accountService.listAllAccounts()`, computes total + counts per status
(ACTIVE/BLOCKED/CLOSED), and calls `view.showSummary(...)` with a readable
summary. Unit-tested headless against `bank_test` (seed accounts of each status,
assert the summary contains the right totals).

---

## 5. All Accounts — table + search

- **`AccountRow` expands** from `(long accountNumber, String text)` to structured
  columns: `AccountRow(long accountNumber, String holderName, String accountType,
  String balance, String status)`.
- **`AllAccountsView`** gains nothing removed but its `showAccounts(List<AccountRow>)`
  now feeds a `TableView` (`AllAccountsViewFx`) with columns **No** (row index),
  **Account #**, **Holder**, **Type**, **Balance**, **Status**; plus a search
  `TextField` and a **Refresh** button. `getSelected()` returns the selected
  `AccountRow` (unchanged contract). New: `setOnSearch(java.util.function.Consumer<String>)`
  — fired with the query text as the user types.
- **`AllAccountsPresenter`** gains `search(String query)`: filters
  `listAllAccounts()` by holder name (case-insensitive `contains`; blank = all),
  maps to `AccountRow`s, and calls `view.showAccounts(...)`. `load()` = `search("")`.
  `manage()` unchanged (reads `getSelected()` → `selectAccount` + `showManageAccount`).
  Presenter tests: `load` renders all rows with the right columns; `search`
  narrows to matching holders; `manage` still navigates + stores the selection.

---

## 6. Manage panel

The existing `ManageAccount` view/presenter are reused as-is; `App.showManageAccount`
just renders them via `setContent(...)` so they appear in the dashboard content
region with the sidebar still visible. Its **Back** already calls
`showAllAccounts()`, which now returns to the table within the dashboard. No
presenter/logic change — only the placement (`setContent` vs `setRoot`).

---

## 7. Styling

Extend `app.css`:
- `.sidebar` — blue gradient background, fixed width (~200px), padding.
- `.sidebar .label.brand` — bank name, large/bold, white.
- `.sidebar-item` — full-width flat menu buttons, left-aligned, white text, hover
  highlight; `.sidebar-item.active` — highlighted (e.g. green/lighter) for the
  current section.
- `.table-view` — styled header (blue), readable rows, selection highlight.
- The content region keeps the light theme; the dashboard root is NOT wrapped in
  the centered "card" (the shell fills the window). `setContent` sections may use
  a light padded panel.

The customer screens keep their existing card look.

---

## 8. Testing

- **Presenter/logic tests (headless, `bank_test`):**
  - `AdminHomePresenter.load` computes the correct total + per-status counts.
  - `AllAccountsPresenter.load` renders one structured `AccountRow` per account
    (correct holder/type/balance/status); `search` filters by holder name;
    `manage` stores the selection + navigates.
  - `ManageAccountPresenter` tests unchanged (still pass).
- **View/shell code** (`SidebarFx`, `AllAccountsViewFx` table, `AdminHomeViewFx`,
  `App` dashboard wiring) is **compile-verified** (`mvn -q compile`) and validated
  by running the app; not unit-tested (UI wiring), consistent with prior stages.
- **No customer regressions:** the customer flow and its tests are untouched; the
  full suite (currently 102) stays green, plus the new admin-dashboard presenter
  tests.

---

## 9. Success Criteria

1. `mvn test` green (existing 102 + new Home/search presenter tests) on `bank_test`.
2. `mvn javafx:run` → Role Select → **Admin** → login → lands on the **dashboard**:
   sidebar (Home/All Accounts/Open Account/Log Out) + content area.
3. **All Accounts** shows a real **table** (No/Account #/Holder/Type/Balance/Status);
   typing in **Search** filters by holder name; **Refresh** reloads; selecting a
   row opens **Manage** (block/close/unblock/reopen/delete/history) in the content
   area; **Back** returns to the table.
4. **Home** shows account totals; **Log Out** returns to Role Select.
5. The **customer** flow and **Admin Login** are visually and behaviorally
   unchanged.
6. MVP boundary intact: admin presenters import no JavaFX; only `App`/`*ViewFx`
   know about the shell/table widgets.
