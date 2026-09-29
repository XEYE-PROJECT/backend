# Contratos entre servicios (fixtures canónicas)

Ejemplos JSON de cada mensaje que cruza un servicio. **Este directorio es la copia canónica**;
`search-service/tests/contracts/` y `training-service/tests/contracts/` llevan copias byte a byte
(`bash contracts-check.sh` en `xeye-infra` comprueba que las tres coinciden). Cada servicio tiene
un test que produce o consume estas fixtures con su código real, así un cambio de campo rompe en
CI el lado que no se actualizó, no en producción.

| Fixture | Sentido | Productor / consumidor |
|---|---|---|
| `training-job.json` | backend → worker | `TrainingLaunchCommand` / `job_loader.parse_job` |
| `training-webhook-{phase,completed,failed}.json` | worker → backend | `WebhookReporter`, `completion_payload` / `TrainingWebhookRequest` |
| `search-index-push.json` | backend → search | `SearchIndexCommand` / `IndexPushRequest` |
| `search-bootstrap.json`, `search-bootstrap-page.json` | backend → search | `BootstrapResponse`, `KeysetPage` / `BackendClient.fetch_bootstrap` |
| `search-list-data.json` | backend → search | `ListSearchDataResponse` / `BackendClient.fetch_list_data` |

`embeddingsData` es una matriz 2×4 float32 real (`base64(np.save)`), `model` el string opaco que
emite el worker y `keyHash` un SHA-256 de un valor ficticio. Para cambiar un contrato: edita aquí,
copia a los otros dos repos y arregla los tests que fallen en cada lado.

`webhook_token` del job es `WebhookTokens.issue(secreto, 42)` con el secreto de los tests de
integración (`AbstractIntegrationTest.WEBHOOK_SECRET`): `<trainingId>.<hex(HMAC-SHA256(secreto,
"training-webhook:" + trainingId))>`. El worker lo devuelve tal cual en `X-Webhook-Token`.
