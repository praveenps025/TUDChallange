# CareTrack demo (sample data, no database)

The demo is the full CareTrack app running on **built-in sample data** in the
browser. There is no backend and no database: it is a plain static website, so
it can be hosted for free and opened on any phone or laptop.

- Every patient, MRN, serial number and name is **fictional**.
- What you do is **saved in your browser**, so a refresh doesn't undo it.
  Each browser has its own copy. **Account → Reset demo data** starts over.
- To show a hand-over between roles (staff asks → manager approves), use the
  **same browser**: sign out and sign in as the other account.

## Run it on your computer

```bash
npm ci
npm run build:demo     # builds the demo into dist/
npx vite preview       # opens it at http://localhost:4173
```

(`npm run dev` with no `VITE_API_URL` set also runs on the sample data.)

## Put it online — Azure Web App "equipment" (set up)

The repository deploys the demo to the Azure Web App **equipment** (Java 21,
Linux) on every push to `main`, through `.github/workflows/main_equipment.yml`.
The demo is packaged with a tiny JDK-only web server
(`demo-server/DemoServer.java`) into `caretrack-demo.jar`, so it runs on App
Service's Java runtime with **no database and no settings needed**.
The address is on the Web App's Overview page, e.g. `https://equipment-….azurewebsites.net`.

To try that jar on your computer:

```bash
npm run build:demo
javac -d demo-build demo-server/DemoServer.java && cp -r dist demo-build/site
jar --create --file caretrack-demo.jar --main-class DemoServer -C demo-build .
PORT=8095 java -jar caretrack-demo.jar      # http://localhost:8095
```

## Or: Azure Static Web Apps (free)

1. Push this repository to GitHub (done once).
2. Azure portal → search **Static Web Apps** → **Create**:
   - Resource group: `caretrack-rg` · Name: `caretrack-demo`
   - Plan type: **Free**
   - Deployment source: **Other** (the repository already has its own workflow)
   - **Review + create** → **Create**
3. Open the new Static Web App → **Manage deployment token** → copy the token.
4. GitHub repository → **Settings → Secrets and variables → Actions → New repository secret**:
   name `AZURE_STATIC_WEB_APPS_API_TOKEN`, value: the token.
5. GitHub → **Actions → Demo site (Azure Static Web Apps) → Run workflow**
   (it also runs on every push to `main`).
6. Open the address shown on the Static Web App's **Overview** page
   (`https://<name>.azurestaticapps.net`). It is HTTPS, so phone cameras work.

## Sign in

The sign-in screen has two buttons:

- **Sign in as OT staff (Sam)**
- **Sign in as Manager (Alex)**

Other demo logins (any password): `riley.chen` (staff), `jordan.lee` (manager).

## What's in the sample data

| | |
|---|---|
| Equipment | 18 items: wheelchairs (incl. bariatric), cushions, sliding boards, hoists — in the OT store, workshop and on wards; some issued, some needing inspection, three missing |
| Loans | One **overdue** (WC-035), two **due today** (WC-026, CU-024) |
| Patients | **4821** (Ward 4C): a sliding board to send home + a lost wheelchair · **7731** (Ward 6A): home loan waiting for a manager · **5502** (Ward 5B): override waiting for a manager · **6190** (Ward 2A): ready for discharge |
| Waitlist | 7 requests, Urgent / Soon / Routine, most with a patient MRN |
| Audit walk | In progress round Ward 3 and the OT store (5 of 9 found) |

## Demo script

### Part 1 — OT staff (Sam)

1. **Sign in as OT staff.** Home shows Sam's tasks: items to allocate, the overdue
   loan, loans due today, missing items and today's discharge checks. Note the
   bottom tabs: Home · Waitlist · Scan · Discharge · **Audit** (no Manage, no Insights).
2. **Find equipment** (top of Home): Category *Wheelchairs*, Patient weight
   *Bariatric* → **Find** → WC-040 in the OT store. Then *Sliding boards* +
   *Bariatric* → "Oops, nothing available" → **Add to waitlist**.
3. **New waitlist request:** item, ward, patient MRN (e.g. `8880001122` for a new
   patient), priority **Urgent** → it jumps near the top of the Waitlist.
4. **Return and allocate:** Scan (or type **CU-024**) → *Return and check* →
   *Clean* → **Allocate to Ward 3A** — returned and handed to the next ward in one step.
5. **Inspect:** open **SB-011** → *Mark inspected* → available again.
6. **Discharge check:** Discharge → search **4821**:
   - **Send home on loan** → keep SB-014 switched on → *Request home loan*.
   - The wheelchair WC-018 is missing → *Item lost? Ask a manager* → reason → *Request override*.
   - The patient now shows "Waiting for a manager".
7. **Lend for home use** from an item: open **WC-033** → *Lend for home use* →
   MRN `6190` → *Request home loan*.
8. **Audit walk:** Audit tab → *Scan next item* → type **CU-031** → found →
   *Finish walk* (items not found are marked missing).
9. **Camera:** on a phone, Scan → allow the camera → point at a printed QR label
   (see Part 2, step 4).

### Part 2 — Manager (Alex)

1. **Account → Sign out → Sign in as Manager.** Bottom tabs now end in **Manage**;
   Home shows **Approve home loan** and **Approve discharge** tasks.
2. **Approvals:** Manage → *Approvals* → open each request → enter a reason →
   **Approve** (or Reject). Patient 4821's sliding board now shows
   "Home loan · MRN ••••4821" and the discharge is ready.
3. **Register equipment:** Manage → *Register equipment* → type *Hoist*, pool,
   location, name, weight category → **Register and print label** → the item
   gets the next ID (**HO-003**) and its QR label opens.
4. **Print / reprint a label:** Manage → *Inventory* → search → open an item →
   *Print label* (50 × 25 mm; on screen it can be scanned by a phone).
5. **New equipment type:** on Register, *New type* (e.g. *Commode*, prefix *CM*).
6. **Insights:** Manage → *Insights* → switch *This month / Last month / This year*.
7. **Users:** Manage → *Users* → add a staff member, change a role, reset a password.

Finish with **Account → Reset demo data** to put everything back for the next demo.
