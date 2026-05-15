// Nav scroll effect
const nav = document.getElementById('nav');
window.addEventListener('scroll', () => {
  nav.style.boxShadow = window.scrollY > 40
    ? '0 4px 40px rgba(0,0,0,0.4)'
    : 'none';
});

// Intersection Observer — fade-in cards
const observer = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        entry.target.classList.add('visible');
        observer.unobserve(entry.target);
      }
    });
  },
  { threshold: 0.12 }
);

const animTargets = document.querySelectorAll(
  '.feature-card, .step, .tech-card, .screen-card, .offline-badge, .arch-diagram'
);

animTargets.forEach((el, i) => {
  el.style.opacity = '0';
  el.style.transform = 'translateY(28px)';
  el.style.transition = `opacity 0.55s ease ${i * 0.06}s, transform 0.55s ease ${i * 0.06}s`;
  observer.observe(el);
});

document.addEventListener('DOMContentLoaded', () => {
  document.querySelectorAll('.feature-card, .step, .tech-card, .screen-card').forEach((el) => {
    el.style.willChange = 'opacity, transform';
  });
});

// Add visible class styles via JS
const style = document.createElement('style');
style.textContent = '.visible { opacity: 1 !important; transform: translateY(0) !important; }';
document.head.appendChild(style);

// Smooth active nav link
const sections = document.querySelectorAll('section[id]');
const navLinks = document.querySelectorAll('.nav-links a');

const sectionObserver = new IntersectionObserver(
  (entries) => {
    entries.forEach((entry) => {
      if (entry.isIntersecting) {
        const id = entry.target.getAttribute('id');
        navLinks.forEach((link) => {
          link.style.color = link.getAttribute('href') === `#${id}`
            ? '#F0F0F5'
            : '';
        });
      }
    });
  },
  { threshold: 0.5 }
);

sections.forEach((s) => sectionObserver.observe(s));
