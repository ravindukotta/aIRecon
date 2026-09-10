# aireconsile Helm chart

This shared chart renders the existing Deployment, Service, and output PVC. Resource
names and selectors remain stable for migration: `aireconsile` and `aireconsile-output`.
Run one instance per namespace. The namespace comes from the Helm release namespace
(Argo CD's destination namespace); the chart does not create a Namespace resource.

The Deployment retains one replica, the `Recreate` strategy, health probes, non-root
security context, persistent output, and ephemeral logs. Both environments run the
scheduled batch job independently with separate PVCs.

`values.yaml` supplies the image repository, tag, pull policy, PVC size, container
resources, and an `env` list accepting Kubernetes `value` or `valueFrom` entries.
`values-dev.yaml` inherits those defaults. CI updates the image repository and tag in
`values-prod.yaml` after publishing the production image to GHCR.

Validate and preview from the repository root:

```bash
helm lint charts/aireconsile --strict -f charts/aireconsile/values-dev.yaml
helm lint charts/aireconsile --strict -f charts/aireconsile/values-prod.yaml
helm template aireconsile charts/aireconsile --namespace aireconsile -f charts/aireconsile/values-dev.yaml
helm template aireconsile charts/aireconsile --namespace aireconsile -f charts/aireconsile/values-prod.yaml
```

The `aireconsile` Argo CD Application tracks this chart on `master`, using
`values-prod.yaml`, release name `aireconsile`, and namespace `aireconsile`.
After committing the chart and workflow changes, apply `argocd/aireconsile.yaml`
to switch the existing Application to Helm. Compare rendered resources before syncing
so the PVC and Deployment selector remain unchanged. Development deployment in CI
is still simulated.
