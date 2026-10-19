const messages = {
  press: 'A press for a little light.',
  double: 'Two presses. Ready for the photo.',
  hold: 'Hold the key. Take a music break.',
};

const result = document.querySelector('.demo-result');
const mappings = document.querySelectorAll('[data-gesture]');

for (const mapping of mappings) {
  mapping.addEventListener('click', () => {
    for (const other of mappings) {
      other.setAttribute('aria-pressed', String(other === mapping));
    }
    result.textContent = messages[mapping.dataset.gesture];
    result.classList.remove('pulse');
    requestAnimationFrame(() => requestAnimationFrame(() => result.classList.add('pulse')));
  });
}

const copyButton = document.querySelector('#copy-command');
const copyStatus = document.querySelector('#copy-status');

if (navigator.clipboard && window.isSecureContext) {
  copyButton.hidden = false;
  copyButton.addEventListener('click', async () => {
    try {
      await navigator.clipboard.writeText(document.querySelector('#adb-command').textContent);
      copyStatus.textContent = 'Command copied. Paste it into your computer’s terminal.';
    } catch {
      copyStatus.textContent = 'Could not copy automatically. Select the command above and copy it manually.';
    }
  });
}
