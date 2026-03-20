# Kubernetes manifests for kind

This folder supports two deployment modes.

## Mode A: All Kubernetes manifests

- Infrastructure and apps from manifests
- Uses `k8s/kind/kustomization.yaml`
- Includes `04-mysql.yaml`

```bash
kubectl apply -k k8s/kind
kubectl get pods -n ecommerce -w
```

## Mode B: Hybrid (Helm infra + Kubernetes apps)

- Install infra with Helm (see `helm/README.md`)
- Deploy apps with Kubernetes manifests
- Uses `k8s/hybrid/kustomization.yaml`
- Does not apply `04-mysql.yaml`

```bash
kubectl kustomize --load-restrictor=LoadRestrictionsNone k8s/hybrid | kubectl apply -f -
kubectl get pods -n ecommerce -w
```

## Build and load app images into kind

```bash
docker build -t ecommerce/auth-service:dev -f auth-service/Dockerfile .
docker build -t ecommerce/product-service:dev -f product-service/Dockerfile .
docker build -t ecommerce/inventory-service:dev -f inventory-service/Dockerfile .
docker build -t ecommerce/order-service:dev -f order-service/Dockerfile .
docker build -t ecommerce/api-gateway:dev -f api-gateway/Dockerfile .

kind load docker-image ecommerce/auth-service:dev --name personal-k8s
kind load docker-image ecommerce/product-service:dev --name personal-k8s
kind load docker-image ecommerce/inventory-service:dev --name personal-k8s
kind load docker-image ecommerce/order-service:dev --name personal-k8s
kind load docker-image ecommerce/api-gateway:dev --name personal-k8s
```

## Recreate DB grants

Run this when DB credentials change or when app pods show `Access denied`.

```bash
kubectl delete job mysql-init-databases -n ecommerce --ignore-not-found
kubectl apply -f k8s/kind/03-init-databases-job.yaml
kubectl wait --for=condition=complete job/mysql-init-databases -n ecommerce --timeout=180s
```

## Access API Gateway

```bash
kubectl port-forward -n ecommerce svc/api-gateway 7999:7999
```

Gateway URL: `http://localhost:7999`

## Notes

- `03-init-databases-job.yaml` creates `auth_db`, `product_db`, `inventory_db`, `order_db`.
- The init job also creates/updates the app DB user from `AUTH_DB_USERNAME`/`AUTH_DB_PASSWORD` in `02-secret.yaml` and grants permissions.
- For local dev convenience, gateway is exposed with NodePort `30099`.
