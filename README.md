# wareX Backend

This repository contains the Spring Boot microservices and backend deployment assets.

## What is included

- Service source code under the repo root
- Dockerfiles for each service
- `docker-compose.ec2.yml` for EC2 deployment
- GitHub Actions workflow at `.github/workflows/deploy.yml`

## Required GitHub secrets

- `BACKEND_EC2_HOST`
- `BACKEND_EC2_USER`
- `BACKEND_EC2_SSH_KEY`
- `BACKEND_EC2_ENV_FILE`
- `GHCR_TOKEN`

`GHCR_TOKEN` should be a GitHub personal access token with package read access for deployment on EC2.

`BACKEND_EC2_ENV_FILE` should use `https://` values for both `FRONTEND_PUBLIC_BASE_URL` and `BACKEND_PUBLIC_BASE_URL` when the frontend is served through Caddy.

The `api-gateway` service remains published on host port `8080` so the frontend Caddy proxy on the same EC2 host can forward `/api` and docs traffic to it.

## Required EC2 preparation

Run `ec2-bootstrap.sh` once on the backend EC2 instance, then place the deployment files under `/opt/warex-backend`.

## First push

```powershell
git init -b main
git add .
git commit -m "Prepare backend for GitHub Actions and EC2 deployment"
git remote add origin https://github.com/<your-user>/<your-backend-repo>.git
git push -u origin main
```

## Branching strategy

- `main` is the production branch
- `dev` must be created from `main`
- every backend microservice branch must be created from `dev`

Recommended backend branches:

- `feature/eureka-server`
- `feature/auth-service`
- `feature/product-service`
- `feature/warehouse-service`
- `feature/supplier-service`
- `feature/purchase-order-service`
- `feature/stock-movement-service`
- `feature/payment-service`
- `feature/alert-service`
- `feature/report-service`
- `feature/admin-server`
- `feature/api-gateway`

Merge flow:

1. create `dev` from `main`
2. create a service branch from `dev`
3. merge the service branch into `dev`
4. merge `dev` into `main`

## Commit message format

Use this exact pattern:

```text
[Gautam] : added auth-service
```

The commit must start with `[Gautam] : added ` and then the service or feature name.
