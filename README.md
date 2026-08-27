# Coin AI

This repository is the main source for the Coin AI Space application.

Quick start (local):

1. Backend

```bash
cd server
npm install
# set environment variables in .env (see .env.example)
npm start
```

2. Frontend

Open `client/index.html` in your browser (or serve it from a static server).

Environment variables (server/.env):

AZURE_OPENAI_ENDPOINT=<your-resource-name>.openai.azure.com
AZURE_OPENAI_KEY=<your-azure-openai-key>
AZURE_DEPLOYMENT_NAME=<your-o4-mini-deployment-name>
AZURE_API_VERSION=2024-09-01

Notes:
- This scaffold provides a minimal backend proxy that calls Azure OpenAI chat completions for the `o4-mini` deployment.
- Do not put secrets in the client or commit them to the repo. Use server-side environment variables or the Space secrets UI.
