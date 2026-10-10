# CareTrack on Azure — step by step (portal, student account)

Everything here is done in the Azure portal (https://portal.azure.com) with
clicks. It takes about 30 minutes. Use the **same region** for everything:
**North Europe** (Ireland). If your student subscription doesn't allow it,
try **West Europe** or **UK South** — but keep both resources in one region.

You will create two things:

1. **Azure Database for PostgreSQL – Flexible Server** — the database
2. **App Service (Web App)** — runs CareTrack (screens + API in one app)

---

## Part 1 — Create the database

1. In the top search bar type **Azure Database for PostgreSQL flexible servers** → **Create**.
2. **Basics** tab:
   - Subscription: *Azure for Students*
   - Resource group: **Create new** → `caretrack-rg`
   - Server name: `caretrack-db-<your initials>` (must be unique)
   - Region: **North Europe**
   - PostgreSQL version: **16**
   - Workload type: **Development** (this picks the free-tier size)
   - Compute + storage: click **Configure server** → **Burstable**, **Standard_B1ms**, storage **32 GiB** → Save.
     Look for the *free tier* note; B1ms with 32 GiB is covered by the 12-month free services.
   - Authentication: **PostgreSQL authentication only**
   - Admin username: `caretrack`
   - Password: make a strong one and **write it down**
3. **Networking** tab:
   - Connectivity method: **Public access (allowed IP addresses)**
   - Tick **Allow public access from any Azure service within Azure to this server** (this lets the Web App connect)
   - Click **+ Add current client IP address** (lets *you* connect with DBeaver)
4. **Review + create** → **Create**. Wait about 5–10 minutes.
5. Open the new server → left menu **Databases** → **+ Add** → name `caretrack` → **Save**.
6. On the server's **Overview** page, copy the **Server name**, e.g.
   `caretrack-db-ps.postgres.database.azure.com`.

Your connection details are now:

| | |
|---|---|
| Host | `caretrack-db-ps.postgres.database.azure.com` (yours) |
| Port | `5432` |
| Database | `caretrack` |
| Username | `caretrack` |
| Password | the one you wrote down |
| JDBC URL for the app | `jdbc:postgresql://<host>:5432/caretrack?sslmode=require` |

## Part 2 — Create the Web App

1. Search **App Services** → **+ Create** → **Web App**.
2. **Basics** tab:
   - Resource group: `caretrack-rg`
   - Name: `caretrack-<your initials>` → your address will be `https://caretrack-<initials>.azurewebsites.net`
   - Publish: **Code**
   - Runtime stack: **Java 21**
   - Java web server stack: **Java SE (Embedded Web Server)**
   - Operating system: **Linux**
   - Region: **North Europe** (same as the database)
   - Pricing plan: **Basic B1** (recommended; small charge from your student credit) or **Free F1**
     (free, but it sleeps when idle and has a daily CPU limit — fine for trying it out)
3. **Review + create** → **Create**.

## Part 3 — Tell the app how to reach the database

1. Open the Web App → left menu **Settings → Environment variables** → **App settings** tab.
2. Click **+ Add** for each row below, then **Apply** at the bottom (the app restarts).

| Name | Value |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod,demo` (demo with sample data) — or `prod` for real use |
| `DB_URL` | `jdbc:postgresql://<your host>:5432/caretrack?sslmode=require` |
| `DB_USERNAME` | `caretrack` |
| `DB_PASSWORD` | your database password |
| `JWT_SECRET` | a long random string (see below) |
| `MRN_HASH_KEY` | another long random string (see below) |
| `APP_TIMEZONE` | `Europe/Dublin` |

**For a demo** (`prod,demo`) also add the sample logins' passwords:

| Name | Value |
|---|---|
| `DEV_MANAGER_PASSWORD` | e.g. `manager123` → logins `alex.morgan`, `jordan.lee` |
| `DEV_STAFF_PASSWORD` | e.g. `staff123` → logins `sam.patel`, `riley.chen` |

**For real use** (`prod`) add the first manager instead (remove the password after first sign-in):

| Name | Value |
|---|---|
| `INITIAL_MANAGER_USERNAME` | e.g. `pat.murphy` |
| `INITIAL_MANAGER_NAME` | e.g. `Pat Murphy` |
| `INITIAL_MANAGER_PASSWORD` | a temporary password, 8+ characters |

**Making the two random strings** — in a terminal run `openssl rand -base64 48`
twice (Git Bash has `openssl`), or use any password generator for 40+ characters.
Keep them safe. **Never change `MRN_HASH_KEY` after real patients are added.**

3. Left menu **Settings → Configuration → General settings**: turn **Always on** to *On* (B1 only),
   then **Save**. Under **Monitoring → Health check**, enable it with path `/api/actuator/health`.

## Part 4 — Put CareTrack on the Web App

**Option A — GitHub Actions (automatic on every push).** Follow section 3 of
[DEPLOYMENT.md](DEPLOYMENT.md) once; after that every push to `main` deploys.

**Option B — by hand from your computer** (quickest the first time):

```bash
# in the project folder
npm ci
npm run build
cd backend
./mvnw -B package          # runs the tests (Docker must be running) and builds target/equiptrack.jar
az login
az webapp deploy --resource-group caretrack-rg --name caretrack-<initials> --src-path target/equiptrack.jar --type jar
```

## Part 5 — Check it works

1. Open `https://caretrack-<initials>.azurewebsites.net/api/actuator/health` → it should say `{"status":"UP"}`.
   (The first start takes a minute: the app creates all the tables.)
2. Open `https://caretrack-<initials>.azurewebsites.net` → sign in
   (demo: `alex.morgan` / your `DEV_MANAGER_PASSWORD`).
3. On a phone, open the same address → Scan → allow the camera.

**If it doesn't start:** Web App → **Monitoring → Log stream**. Common messages:

| You see | Fix |
|---|---|
| `Connection ... refused` / timeout | Database **Networking**: tick "Allow public access from any Azure service" |
| `password authentication failed` | `DB_USERNAME` / `DB_PASSWORD` wrong (username is just `caretrack`) |
| `SSL` / `no pg_hba.conf entry` | Add `?sslmode=require` to the end of `DB_URL` |
| `database "caretrack" does not exist` | Part 1 step 5 — add the database |
| `JWT_SECRET must be set` | Add the missing app setting |

## Look at the Azure database with DBeaver

New connection → PostgreSQL → Host = your server name, Port `5432`, Database
`caretrack`, Username `caretrack`, Password → **SSL** tab → SSL mode **require**
→ Test connection. If it times out, add your current IP again in the database's
**Networking** page (your IP changes between networks).

## Moving from demo to real use

The demo data and logins live in the database. For real use, create a **new,
empty database** (Part 1 step 5, e.g. `caretrack_live`), point `DB_URL` at it,
set `SPRING_PROFILES_ACTIVE=prod`, remove `DEV_*_PASSWORD`, and add the
`INITIAL_MANAGER_*` settings.
