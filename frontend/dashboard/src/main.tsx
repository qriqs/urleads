/**
 * @license
 * SPDX-License-Identifier: Apache-2.0
 */

// Aurora Weather Dashboard
// Primary UI and liquid glass styles are self-contained in index.html as requested.

const rootEl = document.getElementById('root');
if (rootEl) {
  // harmless fallback
}

// Interactive navigation state micro-handler
document.querySelectorAll('.nav a').forEach((link) => {
  link.addEventListener('click', (e) => {
    e.preventDefault();
    document.querySelectorAll('.nav a').forEach((l) => l.removeAttribute('aria-current'));
    link.setAttribute('aria-current', 'page');
  });
});
