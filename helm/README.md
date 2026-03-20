# Helm infrastructure for kind

This folder contains Helm values for infrastructure services in the `ecommerce` namespace.

## What Helm manages

- MySQL (`mysql` service)
- Kafka (`kafka` service)
- Redis (`redis-master` service)

## Prerequisites

```bash
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update
kubectl create namespace ecommerce --dry-run=client -o yaml | kubectl apply -f -
```

## Install / upgrade dependencies

```bash
helm upgrade --install mysql bitnami/mysql \
  -n ecommerce \
  -f helm/values/mysql-kind.yaml

helm upgrade --install kafka bitnami/kafka \
  -n ecommerce \
  -f helm/values/kafka-kind.yaml

helm upgrade --install redis bitnami/redis \
  -n ecommerce \
  -f helm/values/redis-kind.yaml
```

## Verify

```bash
kubectl get pods -n ecommerce
kubectl get svc -n ecommerce
```

If any pod shows `ImagePullBackOff`, pre-pull and load the exact image into kind:

```bash
docker pull <image>
kind load docker-image <image> --name personal-k8s
```

## Remove dependencies

```bash
helm uninstall mysql -n ecommerce
helm uninstall kafka -n ecommerce
helm uninstall redis -n ecommerce
```

