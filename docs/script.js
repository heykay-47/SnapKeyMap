const messages = {
  press: 'A press for a little light.',
  double: 'Two presses. Ready for the photo.',
  hold: 'Hold the key. Take a music break.',
};

const result = document.querySelector('.demo-result');
const mappings = document.querySelectorAll('[data-gesture]');
let animateGesture = () => {};

for (const mapping of mappings) {
  mapping.addEventListener('click', () => {
    for (const other of mappings) {
      other.setAttribute('aria-pressed', String(other === mapping));
    }
    result.textContent = messages[mapping.dataset.gesture];
    animateGesture(mapping);
  });
}

if (window.gsap && window.ScrollTrigger) {
  const { gsap, ScrollTrigger } = window;
  gsap.registerPlugin(ScrollTrigger);

  gsap.matchMedia().add('(prefers-reduced-motion: no-preference)', (context) => {
    const intro = gsap.timeline({ defaults: { duration: 0.65, ease: 'power3.out' } });
    intro
      .from('.headline-line > span', { yPercent: 110, stagger: 0.1 }, 0)
      .from('.hero-copy .intro, .hero-copy .button, .download-note', {
        y: 14, opacity: 0.5, stagger: 0.07, clearProps: 'transform,opacity',
      }, 0.15)
      .from('.key-demo', { y: 20, scale: 0.98, clearProps: 'transform' }, 0.1)
      .from('.mapping .gesture-shape', {
        scaleY: 0.4, stagger: 0.1, transformOrigin: 'center', clearProps: 'transform',
      }, 0.3);

    for (const section of document.querySelectorAll('.possibilities-inner, .section-heading, .privacy-inner')) {
      gsap.from(section.children, {
        y: 22, opacity: 0.65, duration: 0.65, stagger: 0.1, ease: 'power3.out',
        immediateRender: false, clearProps: 'transform,opacity',
        scrollTrigger: { trigger: section, start: 'top 94%', once: true },
      });
    }

    for (const step of document.querySelectorAll('.steps li')) {
      gsap.from(step, {
        y: 16, opacity: 0.7, duration: 0.5, ease: 'power3.out',
        immediateRender: false, clearProps: 'transform,opacity',
        scrollTrigger: { trigger: step, start: 'top 96%', once: true },
      });
    }

    gsap.from('.questions h2, .site-footer > div', {
      y: 12, duration: 0.55, ease: 'power3.out', immediateRender: false,
      clearProps: 'transform',
      scrollTrigger: { trigger: '.questions', start: 'top 94%', once: true },
    });

    context.add('gesture', (mapping) => {
      const marks = mapping.querySelectorAll('.gesture-shape i');
      const icon = mapping.querySelector('svg');
      gsap.killTweensOf([result, icon, ...marks]);
      const feedback = gsap.timeline({ defaults: { ease: 'power3.out' } });
      feedback
        .fromTo(marks, { scale: 0.6 }, {
          scale: 1, duration: mapping.dataset.gesture === 'hold' ? 0.55 : 0.24,
          stagger: 0.12, clearProps: 'transform',
        }, 0)
        .fromTo(icon, { scale: 0.85 }, {
          scale: 1, duration: 0.4, clearProps: 'transform',
        }, 0)
        .fromTo(result, { y: 5, opacity: 0.6 }, {
          y: 0, opacity: 1, duration: 0.3, clearProps: 'transform,opacity',
        }, 0);
    });
    animateGesture = context.gesture;

    const refresh = () => ScrollTrigger.refresh();
    const finishIntro = () => intro.progress(1);
    document.querySelector('.hero').addEventListener('focusin', finishIntro);
    for (const details of document.querySelectorAll('details')) {
      details.addEventListener('toggle', refresh);
    }

    return () => {
      animateGesture = () => {};
      document.querySelector('.hero').removeEventListener('focusin', finishIntro);
      for (const details of document.querySelectorAll('details')) {
        details.removeEventListener('toggle', refresh);
      }
    };
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
