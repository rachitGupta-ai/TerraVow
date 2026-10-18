#!/usr/bin/env bash
# One-shot deploy of TerraVow to Cloud Run (+ Firestore + Vertex AI Gemini).
# Usage:  PROJECT_ID=my-project ./deploy.sh
# Prereqs: gcloud installed and logged in (gcloud auth login), billing linked to the project.
set -euo pipefail

: "${PROJECT_ID:?Set PROJECT_ID, e.g. PROJECT_ID=my-project ./deploy.sh}"
REGION="${REGION:-asia-south1}"            # Mumbai
GEMINI_MODEL="${GEMINI_MODEL:-gemini-2.5-flash}"   # confirm availability in Vertex AI Model Garden
VERTEX_LOCATION="${VERTEX_LOCATION:-us-central1}"  # regional endpoint: Google Search grounding (spec lookup,
                                                  # sustainability) and thinking (snag templates) are NOT served
                                                  # on the "global" endpoint, so those calls fall back there.
# Serve the snag template, grounded spec lookup and sustainability alternatives from their instant
# fallbacks instead of calling Gemini (keeps those tabs at 2-3s). Set to false to use live generation
# once grounding/thinking is confirmed working on your Vertex endpoint.
GEMINI_HEAVY_DISABLED="${GEMINI_HEAVY_DISABLED:-true}"
# Cache the photo-assessment / after-repair / explain results by input, so repeating the fixed demo
# sample photos returns the real generated result instantly. A new photo still runs live.
GEMINI_CACHE="${GEMINI_CACHE:-true}"
SERVICE="terravow"

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

echo "== Deploying to Cloud Run"
gcloud run deploy "$SERVICE" \
  --source . \
  --region "$REGION" \
  --allow-unauthenticated \
  --memory 1Gi \
  --min-instances 1 \
  --max-instances 1 \
  --set-env-vars "GOOGLE_GENAI_USE_VERTEXAI=true,GOOGLE_CLOUD_PROJECT=${PROJECT_ID},GOOGLE_CLOUD_LOCATION=${VERTEX_LOCATION},USE_FIRESTORE=true,GEMINI_MODEL=${GEMINI_MODEL},GEMINI_HEAVY_DISABLED=${GEMINI_HEAVY_DISABLED},GEMINI_CACHE=${GEMINI_CACHE}"

URL="$(gcloud run services describe "$SERVICE" --region "$REGION" --format='value(status.url)')"
echo
echo "Cloud Run URL: $URL"
echo "Check:         curl $URL/api/health"
echo
echo "Optional Firebase Hosting front door (gives a *.web.app URL and counts as Firebase usage):"
echo "  npm i -g firebase-tools && firebase login"
echo "  firebase projects:addfirebase $PROJECT_ID     # once"
echo "  firebase deploy --only hosting --project $PROJECT_ID"
