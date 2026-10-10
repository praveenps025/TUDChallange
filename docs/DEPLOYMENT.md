# Deploying CareTrack to Azure

CareTrack runs as **one Azure App Service** (Java 21, deployed as code) with
**Azure Database for PostgreSQL – Flexible Server**. The React app is built into
the backend jar, so the screens (`/`) and the API (`/api`) share one HTTPS
address — which the phone camera and sign-in rely on.

```
 Phone / PC browser ──HTTPS──▶ App Service (Spring Boot jar)
                                 ├── /        React app
                                 ├── /api/... REST API
                                 └──────────▶ Azure Database for PostgreSQL
```

GitHub Actions (`.github/workflows/build-and-deploy.yml`) builds the app, runs
every test, and deploys to App Service on each push to `main`.

**New to Azure or on a student account?** Use the click-by-click portal guide
[AZURE-STEP-BY-STEP.md](AZURE-STEP-BY-STEP.md). It also covers *demo mode*
(`SPRING_PROFILES_ACTIVE=prod,demo`), which loads the sample equipment, patient
and logins on an empty database.

---

## 1. Create the Azure resources

Run in a terminal with the [Azure CLI](https://learn.microsoft.com/cli/azure/install-azure-cli)
(`az login` first). Change the names: web app and server names must be unique
across Azure.

```bash
RG=equiptrack-rg
LOCATION=northeurope            # Ireland
PG=equiptrack-db-<unique>
APP=equiptrack-<unique>
PLAN=equiptrack-plan

az group create -n $RG -l $LOCATION

# Database (smallest tier; scale up later without changing the app)
az postgres flexible-server create -g $RG -n $PG -l $LOCATION \
  --tier Burstable --sku-name Standard_B1ms --version 16 \
  --admin-user equiptrack --admin-password '<a strong password>' \
  --public-access 0.0.0.0        # allows Azure services, not the internet
az postgres flexible-server db create -g $RG -s $PG -d equiptrack

# App Service (Linux, Java 21)
az appservice plan create -g $RG -n $PLAN --is-linux --sku B1
az webapp create -g $RG -p $PLAN -n $APP --runtime "JAVA:21-java21"
az webapp update -g $RG -n $APP --https-only true
az webapp config set -g $RG -n $APP --always-on true \
  --generic-configurations '{"healthCheckPath": "/api/actuator/health"}'
```

For real patient use, consider private networking (VNet integration and a
private endpoint for the database) instead of `--public-access 0.0.0.0`.

## 2. Configure the app

Generate the two secrets (each at least 32 characters) and keep them safe —
changing `JWT_SECRET` signs everyone out; **changing `MRN_HASH_KEY` makes
existing patients unfindable**, so never change it after go-live.

```bash
openssl rand -base64 48   # JWT_SECRET
openssl rand -base64 48   # MRN_HASH_KEY
```

```bash
az webapp config appsettings set -g $RG -n $APP --settings \
  SPRING_PROFILES_ACTIVE=prod \
  APP_TIMEZONE=Europe/Dublin \
  DB_URL="jdbc:postgresql://$PG.postgres.database.azure.com:5432/equiptrack?sslmode=require" \
  DB_USERNAME=equiptrack \
  DB_PASSWORD='<the database password>' \
  JWT_SECRET='<generated>' \
  MRN_HASH_KEY='<generated>' \
  INITIAL_MANAGER_USERNAME=<first manager's username> \
  INITIAL_MANAGER_NAME='<their full name>' \
  INITIAL_MANAGER_PASSWORD='<a temporary password>'
```

| Setting | Purpose |
|---|---|
| `SPRING_PROFILES_ACTIVE=prod` | **Required.** Without it the app starts in dev mode. `prod,demo` adds sample data and the demo logins (`DEV_MANAGER_PASSWORD`, `DEV_STAFF_PASSWORD`). |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | The database. Azure Postgres needs `?sslmode=require`. |
| `JWT_SECRET` | Signs sign-in tokens (8-hour sessions). |
| `MRN_HASH_KEY` | Keyed hash of patient MRNs. Never change after go-live. |
| `APP_TIMEZONE` | "Today", due dates and times shown to staff. |
| `INITIAL_MANAGER_*` | Creates the first manager **only while the database has no users**. |
| `SWAGGER_ENABLED` | Optional. `true` turns on API docs at `/api/swagger-ui.html` (off in prod). |

Better still, store the secrets in **Azure Key Vault** and reference them from
the app settings (`@Microsoft.KeyVault(SecretUri=...)`), with the web app's
managed identity allowed to read them.

On first start Flyway creates all tables and the reference data (categories,
types, pools, wards, weight categories). No sample data is loaded in prod.

## 3. Connect GitHub Actions to Azure (no passwords stored)

The workflow signs in with OpenID Connect. Create an identity that may deploy
to the web app, and trust your repository's `production` environment:

```bash
REPO=<github-org>/<repo>

az identity create -g $RG -n equiptrack-deployer
CLIENT_ID=$(az identity show -g $RG -n equiptrack-deployer --query clientId -o tsv)
PRINCIPAL_ID=$(az identity show -g $RG -n equiptrack-deployer --query principalId -o tsv)
APP_ID=$(az webapp show -g $RG -n $APP --query id -o tsv)

az role assignment create --assignee-object-id $PRINCIPAL_ID \
  --assignee-principal-type ServicePrincipal --role "Website Contributor" --scope $APP_ID

az identity federated-credential create -g $RG --identity-name equiptrack-deployer \
  -n github-production --issuer https://token.actions.githubusercontent.com \
  --subject "repo:$REPO:environment:production" --audiences api://AzureADTokenExchange

echo "AZURE_CLIENT_ID=$CLIENT_ID"
echo "AZURE_TENANT_ID=$(az account show --query tenantId -o tsv)"
echo "AZURE_SUBSCRIPTION_ID=$(az account show --query id -o tsv)"
```

In GitHub, **Settings → Environments → New environment** `production`
(optionally require a reviewer before each deploy), then:

| Where | Name | Value |
|---|---|---|
| Settings → Secrets and variables → Actions → **Secrets** | `AZURE_CLIENT_ID` | from above |
| | `AZURE_TENANT_ID` | from above |
| | `AZURE_SUBSCRIPTION_ID` | from above |
| Settings → Secrets and variables → Actions → **Variables** | `AZURE_WEBAPP_NAME` | `$APP` |

Until `AZURE_WEBAPP_NAME` is set the workflow only builds and tests.

## 4. Deploy and sign in

Push to `main` (or run the workflow by hand from the **Actions** tab). When it
finishes, open `https://<APP>.azurewebsites.net`:

1. Sign in as the initial manager and change the password (Account).
2. **Remove `INITIAL_MANAGER_PASSWORD`** from the app settings.
3. Add staff and managers under **Manage → Users**.
4. Register equipment under **Manage → Register equipment** and print labels.

Staff can now use the app — including camera scanning — on their phones at the
same address.

## Running it by hand (without GitHub)

```bash
npm ci && npm run build                 # React app → dist/
cd backend && ./mvnw -B package         # tests + jar with the app inside
az webapp deploy -g $RG -n $APP --src-path target/equiptrack.jar --type jar
```

## Troubleshooting

- **Logs:** `az webapp log tail -g $RG -n $APP` (or Log stream in the portal).
- **"Application Error" page:** usually a missing setting (startup fails with
  e.g. `JWT_SECRET must be set`) or the database firewall.
- **Health:** `https://<APP>.azurewebsites.net/api/actuator/health` should say `UP`.
- **Camera doesn't open on a phone:** the page must be opened over `https://`
  and camera permission allowed for the site in the browser settings.
