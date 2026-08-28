# Metronome Video Gallery

Upload these files to a Node.js web host:

- index.html
- server.js
- package.json

Run:

    npm start

The app stores up to four source URLs in the browser and lets the user load a gallery URL. The server extracts only publicly exposed HTML media metadata, video/source URLs, common OG video metadata, direct .mp4/.webm/.ogg links, and iframe URLs.

It does not bypass authentication, DRM, paywalls, anti-bot controls, or other access controls.

Important: a browser cannot freely fetch arbitrary websites with JavaScript because of same-origin/CORS rules. The small server component is therefore required for page retrieval. Direct video playback can still be blocked by the source site or browser policies.
