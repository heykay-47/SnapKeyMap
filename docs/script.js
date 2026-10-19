const stage = document.querySelector('.phone-stage');
const resultText = document.querySelector('.demo-result');
const status = document.querySelector('#demo-status');
const lensLabel = document.querySelector('.lens-label');
const mappings = document.querySelectorAll('[data-gesture]');
const scene = { torch: false, screen: 'home', music: false };

// Each gesture changes the illustrated phone the way a mapped Snap Key would, and describes what happened.
const actions = {
  press() {
    scene.torch = !scene.torch;
    return scene.torch ? 'Press once. The flashlight turns on.' : 'Press again. The flashlight turns off.';
  },
  double() {
    scene.screen = scene.screen === 'camera' ? 'home' : 'camera';
    return scene.screen === 'camera' ? 'Double press. The camera opens.' : 'Double press again. The camera closes.';
  },
  hold() {
    scene.music = !scene.music;
    scene.screen = 'music';
    return scene.music ? 'Press and hold. Your music plays.' : 'Hold again. Your music pauses.';
  },
};
const labels = { press: 'Press', double: 'Press, press', hold: 'Press & hold' };

function renderScene() {
  stage.dataset.torch = scene.torch ? 'on' : 'off';
  stage.dataset.screen = scene.screen;
  stage.dataset.music = scene.music ? 'playing' : 'paused';
}

function runAction(gesture, announce) {
  const text = actions[gesture]();
  renderScene();
  resultText.textContent = text;
  if (announce) status.textContent = text;
}

// Replaced with the GSAP version below when the library loads.
let perform = (gesture, announce) => runAction(gesture, announce);
let stopAutoplay = () => {};

for (const mapping of mappings) {
  mapping.addEventListener('click', () => {
    stopAutoplay();
    perform(mapping.dataset.gesture, true);
  });
}

if (window.gsap && window.ScrollTrigger) {
  const { gsap, ScrollTrigger } = window;
  gsap.registerPlugin(ScrollTrigger);

  gsap.context((context) => {
    const intro = gsap.timeline({ defaults: { duration: 0.65, ease: 'power3.out' } });
    intro
      .from('.headline-line > span', { yPercent: 110, stagger: 0.1 }, 0)
      .from('.hero-copy .intro, .hero-copy .button, .download-note', {
        y: 14, opacity: 0.5, stagger: 0.07, clearProps: 'transform,opacity',
      }, 0.15)
      .from('.key-demo', { y: 20, scale: 0.98, clearProps: 'transform' }, 0.1)
      .from('.phone-rig', { y: 40, rotation: 4, opacity: 0, duration: 0.9, clearProps: 'all' }, 0.2)
      .from('.lens, .lens-label', { scale: 0.7, opacity: 0, stagger: 0.08, duration: 0.6, ease: 'back.out(1.6)', clearProps: 'all' }, 0.55)
      .from('.lens-link', { opacity: 0, duration: 0.4, clearProps: 'opacity' }, 0.9)
      .from('.mapping', { y: 10, opacity: 0.5, stagger: 0.06, clearProps: 'transform,opacity' }, 0.4);

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

    const ripple = document.querySelector('.ripple');
    const holdRing = document.querySelector('.hold-ring circle');
    const lens = document.querySelector('.lens');
    const key = document.querySelector('.phone-key');
    const link = document.querySelector('.lens-link');
    let current;

    const tap = (tl, at) => tl
      .to(stage, { '--press': 1, duration: 0.08, ease: 'power2.in' }, at)
      .fromTo(ripple, { scale: 0.5, opacity: 0.8 }, { scale: 1.7, opacity: 0, duration: 0.5, ease: 'power2.out' }, at + 0.06)
      .to(stage, { '--press': 0, duration: 0.16, ease: 'power2.out' }, at + 0.12);

    // A gesture is the finger reaching the key, the presses themselves, then the phone reacting.
    perform = (gesture, announce) => {
      if (current) current.progress(1);
      const button = document.querySelector(`[data-gesture="${gesture}"]`);
      const tl = gsap.timeline({ onComplete: () => button.classList.remove('is-active') });
      button.classList.add('is-active');
      tl.call(() => { lensLabel.textContent = labels[gesture]; })
        .fromTo(lensLabel, { y: 6, opacity: 0 }, { y: 0, opacity: 1, duration: 0.3, ease: 'power3.out' }, 0)
        .to(stage, { '--finger': 1, duration: 0.3, ease: 'power3.out' }, 0);

      if (gesture === 'press') {
        tap(tl, 0.32);
        // With a double press assigned, the app waits 350 ms to rule out a second press.
        tl.call(() => runAction(gesture, announce), null, 0.32 + 0.35);
      } else if (gesture === 'double') {
        tap(tl, 0.32);
        tap(tl, 0.6);
        tl.call(() => runAction(gesture, announce), null, 0.82);
      } else {
        tl.to(stage, { '--press': 1, duration: 0.1, ease: 'power2.in' }, 0.32)
          .fromTo(holdRing, { strokeDashoffset: 1, opacity: 1 }, { strokeDashoffset: 0, duration: 0.6, ease: 'none' }, 0.36)
          .call(() => runAction(gesture, announce), null, 0.96)
          .fromTo(ripple, { scale: 0.5, opacity: 0.8 }, { scale: 1.7, opacity: 0, duration: 0.5, ease: 'power2.out' }, 0.96)
          .to(stage, { '--press': 0, duration: 0.18, ease: 'power2.out' }, 1.3)
          .to(holdRing, { opacity: 0, duration: 0.3 }, 1.3);
      }
      tl.to(stage, { '--finger': 0, duration: 0.4, ease: 'power2.inOut' }, '+=0.15');
      current = tl;
      return tl;
    };

    // Until the visitor takes over, cycle through each gesture twice so every action is seen turning on and off.
    const loop = [['press', 1.4], ['press', 1.1], ['double', 1.8], ['double', 1.1], ['hold', 2], ['hold', 1.3]];
    let step = 0;
    let next;
    let autoplay = true;
    let visible = false;
    let waiting = true;
    const playStep = () => {
      if (!autoplay) return;
      if (!visible) { waiting = true; return; }
      waiting = false;
      const [gesture, pause] = loop[step];
      step = (step + 1) % loop.length;
      perform(gesture, false).call(() => {
        if (!autoplay) return;
        if (step === 0) { scene.screen = 'home'; renderScene(); }
        next = gsap.delayedCall(pause, playStep);
      });
    };
    const observer = new IntersectionObserver(([entry]) => {
      visible = entry.isIntersecting;
      if (visible && waiting) {
        waiting = false;
        next = gsap.delayedCall(0.6, playStep);
      }
    }, { threshold: 0.4 });
    stopAutoplay = () => {
      autoplay = false;
      next?.kill();
      observer.disconnect();
    };
    intro.call(() => observer.observe(stage));

    // Dashed line from the close-up to the key on the illustrated phone.
    const placeLink = () => {
      const s = stage.getBoundingClientRect();
      const l = lens.getBoundingClientRect();
      const k = key.getBoundingClientRect();
      const r = l.width / 2;
      const cx = l.left - s.left + r;
      const cy = l.top - s.top + r;
      const kx = k.left - s.left + k.width / 2;
      const ky = k.top - s.top + k.height / 2;
      const d = Math.hypot(kx - cx, ky - cy) || 1;
      const edge = r + s.width * 0.02;
      link.setAttribute('viewBox', `0 0 ${s.width} ${s.height}`);
      const line = link.querySelector('line');
      line.setAttribute('x1', cx + (kx - cx) * edge / d);
      line.setAttribute('y1', cy + (ky - cy) * edge / d);
      line.setAttribute('x2', kx - (kx - cx) * 7 / d);
      line.setAttribute('y2', ky - (ky - cy) * 7 / d);
      link.querySelector('circle').setAttribute('cx', kx);
      link.querySelector('circle').setAttribute('cy', ky);
    };
    const resizeObserver = new ResizeObserver(placeLink);
    resizeObserver.observe(stage);
    intro.eventCallback('onComplete', placeLink);

    const refresh = () => ScrollTrigger.refresh();
    const finishIntro = () => intro.progress(1);
    document.querySelector('.hero').addEventListener('focusin', finishIntro);
    for (const details of document.querySelectorAll('details')) {
      details.addEventListener('toggle', refresh);
    }

    return () => {
      stopAutoplay();
      resizeObserver.disconnect();
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
