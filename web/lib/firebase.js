// SafeGuard AI — Firebase configuration
// Uses NEXT_PUBLIC_* environment variables (see .env.local.example)

import { initializeApp } from 'firebase/app';
import { getDatabase } from 'firebase/database';

const firebaseConfig = {
  apiKey: process.env.NEXT_PUBLIC_FIREBASE_API_KEY,
  authDomain: process.env.NEXT_PUBLIC_FIREBASE_AUTH_DOMAIN,
  databaseURL: process.env.NEXT_PUBLIC_FIREBASE_DATABASE_URL,
  projectId: process.env.NEXT_PUBLIC_FIREBASE_PROJECT_ID,
  storageBucket: process.env.NEXT_PUBLIC_FIREBASE_STORAGE_BUCKET,
  messagingSenderId: process.env.NEXT_PUBLIC_FIREBASE_MESSAGING_SENDER_ID,
  appId: process.env.NEXT_PUBLIC_FIREBASE_APP_ID,
};

// Detect the placeholder values shipped in .env.local.example — connecting
// with them only produces "Firebase error ... https://test.firebaseio.com/"
// noise in the console. When credentials are not real, export null so pages
// can render their empty states instead of crashing.
const PLACEHOLDER_MARKERS = ['test_key', 'test.firebaseio.com', 'test-project', 'test.firebaseapp.com'];
const isPlaceholder = (value) =>
  !value || PLACEHOLDER_MARKERS.some((marker) => String(value).includes(marker));

const isConfigured = ![
  firebaseConfig.apiKey,
  firebaseConfig.databaseURL,
  firebaseConfig.projectId,
].some(isPlaceholder);

const app = isConfigured ? initializeApp(firebaseConfig) : null;

export const database = isConfigured ? getDatabase(app) : null;

if (!isConfigured) {
  console.warn(
    '[SafeGuard AI] Firebase is not configured (placeholder values in .env.local). ' +
      'Live data features are disabled until real credentials are provided.'
  );
}

export default app;
