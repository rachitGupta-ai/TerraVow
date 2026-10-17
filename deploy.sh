#!/usr/bin/env bash
# One-shot deploy of the combined TerraVow service to Cloud Run.
# One Cloud Run service = one container = Python front door ($PORT) + Java engine (internal 8081).
# Builds from the repo root via the multi-stage Dockerfile.
# Usage:  PROJECT_ID=my-project ./deploy.sh
set -euo pipefail

: "${PROJECT_ID:?Set PROJECT_ID, e.g. PROJECT_ID=my-project ./deploy.sh}"
REGION="${REGION:-asia-south1}"
GEMINI_MODEL="${GEMINI_MODEL:-gemini-2.5-flash}"
VERTEX_LOCATION="${VERTEX_LOCATION:-global}"
SERVICE="terravow"
MEMORY="${MEMORY:-2Gi}"           # JVM + Python in one container needs more than either alone
CPU="${CPU:-2}"

gcloud config set project "$PROJECT_ID"

echo "== Enabling APIs"
gcloud services enable run.googleapis.com cloudbuild.googleapis.com artifactregistry.googleapis.com \
  firestore.googleapis.com aiplatform.googleapis.com

echo "== Firestore (default database; skipped if it already exists)"
gcloud firestore databases create --location="$REGION" --type=firestore-native 2>/dev/null \
  || echo "   Firestore database already exists, continuing."

echo "== Granting the Cloud Run service account access to Vertex AI and Firestore"
PROJECT_NUMBER="$(gcloud projects describe "$PROJECT_ID" --format='value(projectNumber)')"
SA="${PROJECT_NUMBER}-compute@developer.gserviceaccount.com"
for ROLE in roles/aiplatform.user roles/datastore.user; do
  gcloud projects add-iam-policy-binding "$PROJECT_ID" --member="serviceAccount:${SA}" --role="$ROLE" --quiet >/dev/null
done

echo "== Deploying the combined service to Cloud Run (builds the root Dockerfile)"
gcloud run deploy "$SERVICE" \
  --source . \
  --region "$REGION" \
  --allow-unauthenticated \
  --memory "$MEMORY" \
  --cpu "$CPU" \
  --timeout 360 \
  --min-instances 1 \
  --max-instances 1 \
  --set-env-vars "GOOGLE_GENAI_USE_VERTEXAI=true,GOOGLE_CLOUD_PROJECT=${PROJECT_ID},GOOGLE_CLOUD_LOCATION=${VERTEX_LOCATION},USE_FIRESTORE=true,GEMINI_MODEL=${GEMINI_MODEL},JAVA_PORT=8081,JAVA_ENGINE_URL=http://127.0.0.1:8081"

URL="$(gcloud run services describe "$SERVICE" --region "$REGION" --format='value(status.url)')"
echo
echo "Cloud Run URL: $URL"
echo "Front door health:  curl $URL/api/health"
echo "Java engine (proxied): curl $URL/api/java/health"
echo "Gate round-trip:    curl -X POST $URL/api/java/gate -H 'Content-Type: application/json' -d '{\"deltas_kg[\"air\":-120},\"confidence\":0.9,\"data_trust\":1.0}'"
