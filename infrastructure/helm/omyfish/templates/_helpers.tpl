{{/*
Common labels applied to every resource.
*/}}
{{- define "omyfish.labels" -}}
app.kubernetes.io/part-of: omyfish
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end }}

{{/*
Default Spring Boot readiness probe — used for Java services that don't override it.
*/}}
{{- define "omyfish.defaultReadinessProbe" -}}
httpGet:
  path: /actuator/health/readiness
  port: 8080
initialDelaySeconds: 30
periodSeconds: 10
failureThreshold: 3
{{- end }}

{{/*
Default Spring Boot liveness probe — used for Java services that don't override it.
*/}}
{{- define "omyfish.defaultLivenessProbe" -}}
httpGet:
  path: /actuator/health/liveness
  port: 8080
initialDelaySeconds: 60
periodSeconds: 15
failureThreshold: 3
{{- end }}
