# Deploy to K3s (single node)

This folder is now ready for a K3s server node workflow.

## Layout

- `k8s/kustomization.yaml`: root entrypoint (`kubectl apply -k k8s`)
- `k8s/hybrid`: app manifests + DB init job (no cross-folder references)
- `k8s/kind`: previous kind-focused manifests
- `helm/values`: Helm values for MySQL, Kafka, Redis

## 1) Install infra with Helm

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

kubectl create namespace ecommerce --dry-run=client -o yaml | kubectl apply -f -

helm upgrade --install mysql bitnami/mysql -n ecommerce -f helm/values/mysql-kind.yaml
helm upgrade --install kafka bitnami/kafka -n ecommerce -f helm/values/kafka-kind.yaml
helm upgrade --install redis bitnami/redis -n ecommerce -f helm/values/redis-kind.yaml
```

## 2) Make app images available to K3s

Current app deployments pull directly from Docker Hub:

- `nguyenle1002/auth-service:dev`
- `nguyenle1002/product-service:dev`
- `nguyenle1002/inventory-service:dev`
- `nguyenle1002/order-service:dev`
- `nguyenle1002/api-gateway:dev`

Since images are public on Docker Hub, K3s will pull them automatically (ImagePullPolicy: Always).

If you need to update images:

```bash
# On your laptop
docker build -t nguyenle1002/auth-service:dev -f auth-service/Dockerfile .
docker push nguyenle1002/auth-service:dev
# Repeat for other services
```

## 3) Apply app manifests

```bash
kubectl apply -k k8s
```

## 4) Re-run DB grants if needed

If app logs show `Access denied for user 'auth'`, re-run the DB init job:

```bash
kubectl delete job mysql-init-databases -n ecommerce --ignore-not-found
kubectl apply -f k8s/hybrid/03-init-databases-job.yaml
kubectl wait --for=condition=complete job/mysql-init-databases -n ecommerce --timeout=180s
```

## 5) Verify

```bash
kubectl get pods -n ecommerce -w
kubectl get svc -n ecommerce
```

Gateway service is `api-gateway` on NodePort `30099`.

