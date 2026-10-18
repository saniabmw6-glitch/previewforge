# PreviewForge

Every GitHub pull request gets its own live preview environment, built and deployed by Jenkins.
When the PR closes, the environment is removed automatically.

**What it demonstrates:** dynamic Docker agents, GitHub webhooks, multibranch pipelines,
parallel quality gates, Trivy security scanning, Traefik routing, Jenkins Configuration as Code.

## Architecture

```
Developer -> GitHub PR -> webhook -> tunnel -> Jenkins controller (JCasC)
                                                   |
                                                   | Docker cloud plugin
                                                   v
                                     Ephemeral Docker agent (one per build)
                                     lint + tests | build | Trivy | deploy
                                                   |
                                                   v
                                     Traefik  pr-<n>.preview.localhost -> app-pr-<n>
```

## What you will see (expected output)

1. **Jenkins** (`http://localhost:8080`): a `previewforge` multibranch job with one entry per open PR
   (`PR-12`, `PR-15`...). Each build shows the stages: Prepare, Set up Python, Quality (Lint and
   Unit tests in parallel), Build image, Security scan, Deploy preview, Smoke test, Comment on PR.
   While a build runs, `docker ps` shows an extra agent container that disappears afterwards.
2. **The PR on GitHub:** a bot comment such as
   `Preview ready: http://pr-12.preview.localhost (commit a1b2c3d)` plus a green or red status check.
3. **The preview app** (`http://pr-12.preview.localhost/`):
   ```json
   {"commit":"a1b2c3d","flags":3,"pr":"12","service":"previewforge-demo"}
   ```
   `/health` returns `{"status":"ok"}` and `/flags` lists the feature flags.
4. **On a new commit:** the same URL updates with the new commit hash.
5. **On PR close:** the `preview-cleanup` job runs and `docker ps` no longer lists `app-pr-12`.
6. **Nightly:** `preview-expiry` removes previews older than 48 hours.

## Quick start

Prerequisites: Docker with Compose v2, a GitHub account, and a fork or new repo named `previewforge`.

1. Create a GitHub personal access token with scopes `repo` and `admin:repo_hook`.
2. `cp .env.example .env` and fill in `GITHUB_OWNER`, `GITHUB_TOKEN`, `JENKINS_ADMIN_PASSWORD`.
3. Push this code to your `previewforge` repo (see below).
4. `./scripts/up.sh` builds the agent image and starts Jenkins and Traefik.
5. Expose Jenkins: `cloudflared tunnel --url http://localhost:8080` (or ngrok) and copy the public URL.
6. In the GitHub repo, go to Settings, Webhooks and add two webhooks, both with content type
   `application/json`:
   - `https://<tunnel>/github-webhook/` for Pull requests and Pushes
   - `https://<tunnel>/generic-webhook-trigger/invoke?token=previewforge-cleanup` for Pull requests
7. In Jenkins, open the `previewforge` job and click *Scan Repository Now*.
8. Open a pull request and watch it work.

Stop everything with `./scripts/down.sh`.

## Push to GitHub

```bash
git init
git add .
git commit -m "Initial commit: PreviewForge"
git branch -M main
git remote add origin https://github.com/<you>/previewforge.git
git push -u origin main
```

## Repo layout

| Path | Purpose |
|------|---------|
| `Jenkinsfile` | Main pipeline run for every branch and PR |
| `ci/preview.groovy` | Helper functions: deploy, smoke test, PR comment |
| `jenkins/casc/casc.yaml` | Jenkins configuration as code: security, Docker cloud, credentials |
| `jenkins/casc/jobs.groovy` | Job DSL: multibranch job, cleanup job, expiry job |
| `jenkins/Dockerfile`, `plugins.txt` | Controller image with plugins baked in |
| `jenkins/agent/Dockerfile` | Agent image with Docker CLI, Python and Trivy |
| `docker-compose.yml` | Jenkins and Traefik on the shared `preview` network |
| `app/` | Flask feature-flag service with tests and a multi-stage Dockerfile |
| `scripts/` | `up.sh` and `down.sh` helpers |

## Troubleshooting

- **Agent cannot start:** make sure `previewforge-agent:latest` exists (`docker images`) and that the
  Docker cloud template uses `PULL_NEVER`.
- **Webhook shows 403 or 404:** the tunnel URL changes on restart; update the webhook.
- **Preview URL does not load:** check Traefik with `docker logs traefik`; make sure port 80 is free.
  `*.localhost` resolves automatically in Chrome and Firefox. For curl use
  `curl -H "Host: pr-12.preview.localhost" http://localhost/`.
- **Job DSL error on startup:** the GitHub source syntax varies between plugin versions. Check
  `docker logs jenkins`, or create the multibranch job in the UI (New Item, Multibranch Pipeline,
  GitHub source, credential `github-pat`, discover pull requests).
- **Trivy is slow the first time:** it downloads its vulnerability database; later runs are faster.

## Production notes (what I would change)

The agents and controller run as root with the Docker socket mounted, which is fine for a lab but
gives full host control. In production I would use Kubernetes agents or rootless Docker, a container
registry, wildcard DNS with TLS, and a secrets manager instead of environment variables.
