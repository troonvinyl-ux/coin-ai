const express = require('express');
const fetch = require('node-fetch');
const cors = require('cors');
require('dotenv').config();

const app = express();
app.use(cors());
app.use(express.json());

app.post('/api/chat', async (req, res) => {
  // Accept either `message` (string) or `messages` (OpenAI Chat format)
  const messages = req.body.messages || [{ role: 'user', content: req.body.message }];

  const endpoint = `https://${process.env.AZURE_OPENAI_ENDPOINT}/openai/deployments/${process.env.AZURE_DEPLOYMENT_NAME}/chat/completions?api-version=${process.env.AZURE_API_VERSION}`;

  try {
    const r = await fetch(endpoint, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'api-key': process.env.AZURE_OPENAI_KEY
      },
      body: JSON.stringify({ messages, max_tokens: 512 })
    });

    const data = await r.json();
    res.json(data);
  } catch (err) {
    console.error('Azure request failed', err);
    res.status(500).json({ error: 'request failed' });
  }
});

const port = process.env.PORT || 3000;
app.listen(port, () => console.log('Server listening on', port));
