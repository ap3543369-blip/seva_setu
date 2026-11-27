import { useEffect, useRef, useState } from 'react';
import * as THREE from 'three';

/** Pseudo-random lat/lng from an id so every donation gets a stable pin on the globe. */
const pinPos = (seed, r) => {
  const a = Math.sin(seed * 12.9898) * 43758.5453, b = Math.sin(seed * 78.233) * 12543.1234;
  const lat = ((a - Math.floor(a)) - 0.5) * Math.PI * 0.9, lng = (b - Math.floor(b)) * Math.PI * 2;
  return new THREE.Vector3(r * Math.cos(lat) * Math.cos(lng), r * Math.sin(lat), r * Math.cos(lat) * Math.sin(lng));
};

const foodMesh = kind => {
  const g = new THREE.Group();
  const mat = c => new THREE.MeshStandardMaterial({ color: c, roughness: 0.45, metalness: 0.05 });
  if (kind === 'apple') {
    const body = new THREE.Mesh(new THREE.SphereGeometry(0.2, 24, 20), mat(0xe5483c));
    const leaf = new THREE.Mesh(new THREE.SphereGeometry(0.06, 12, 10), mat(0x2f9e55));
    leaf.scale.set(1.8, 0.5, 1); leaf.position.set(0.07, 0.22, 0);
    g.add(body, leaf);
  } else if (kind === 'bread') {
    const loaf = new THREE.Mesh(new THREE.CapsuleGeometry(0.14, 0.3, 8, 16), mat(0xd9964b));
    loaf.rotation.z = Math.PI / 2;
    g.add(loaf);
  } else if (kind === 'carrot') {
    const c = new THREE.Mesh(new THREE.ConeGeometry(0.1, 0.42, 16), mat(0xf28c28));
    c.rotation.z = Math.PI;
    const top = new THREE.Mesh(new THREE.ConeGeometry(0.05, 0.14, 8), mat(0x2f9e55));
    top.position.y = 0.26;
    g.add(c, top);
  } else if (kind === 'bowl') {
    const bowl = new THREE.Mesh(new THREE.SphereGeometry(0.22, 24, 16, 0, Math.PI * 2, Math.PI / 2, Math.PI / 2), mat(0xf4f1ea));
    const rice = new THREE.Mesh(new THREE.SphereGeometry(0.2, 24, 12, 0, Math.PI * 2, 0, Math.PI / 2), mat(0xffd36b));
    rice.position.y = 0.01;
    g.add(bowl, rice);
  } else {
    g.add(new THREE.Mesh(new THREE.IcosahedronGeometry(0.18, 1), mat(0x5aa9e6)));
  }
  return g;
};

/** Interactive 3D globe with one pin per live donation and food items orbiting around it. */
export default function Hero3D({ donations = [] }) {
  const box = useRef(null);
  const state = useRef({});
  const [failed, setFailed] = useState(false);

  useEffect(() => {
    const el = box.current;
    let renderer;
    try {
      renderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    } catch (e) { setFailed(true); return undefined; }
    renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));
    el.appendChild(renderer.domElement);

    const scene = new THREE.Scene();
    const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 100);
    camera.position.set(0, 0.2, 6);

    scene.add(new THREE.AmbientLight(0xffffff, 0.9));
    const sun = new THREE.DirectionalLight(0xffffff, 2.2); sun.position.set(4, 5, 5); scene.add(sun);
    const glow = new THREE.PointLight(0x35d07f, 18, 14); glow.position.set(-3, -1, 3); scene.add(glow);

    const world = new THREE.Group(); scene.add(world);
    const globe = new THREE.Mesh(new THREE.SphereGeometry(1.7, 64, 48), new THREE.MeshStandardMaterial({ color: 0x0f6b38, roughness: 0.6, metalness: 0.1, emissive: 0x053d1f, emissiveIntensity: 0.5 }));
    const grid = new THREE.Mesh(new THREE.SphereGeometry(1.72, 28, 20), new THREE.MeshBasicMaterial({ color: 0x6ff0a8, wireframe: true, transparent: true, opacity: 0.16 }));
    world.add(globe, grid);

    // "land" dots
    const dots = new Float32Array(900 * 3);
    for (let i = 0; i < 900; i++) { const p = pinPos(i + 1, 1.74); dots.set([p.x, p.y, p.z], i * 3); }
    const dg = new THREE.BufferGeometry(); dg.setAttribute('position', new THREE.BufferAttribute(dots, 3));
    world.add(new THREE.Points(dg, new THREE.PointsMaterial({ color: 0xbff7d6, size: 0.022 })));

    // pins (one per live donation)
    const pins = new THREE.Group(); world.add(pins);
    const pinMat = new THREE.MeshStandardMaterial({ color: 0xffb020, emissive: 0xff8a00, emissiveIntensity: 0.8 });
    const makePin = (seed) => {
      const p = pinPos(seed, 1.74);
      const m = new THREE.Mesh(new THREE.ConeGeometry(0.06, 0.26, 12), pinMat);
      m.position.copy(p.clone().multiplyScalar(1.05));
      m.quaternion.setFromUnitVectors(new THREE.Vector3(0, -1, 0), p.clone().normalize());
      pins.add(m);
    };
    (donations.length ? donations.map(d => d.id) : [3, 8, 11, 15, 21, 27, 34]).slice(0, 14).forEach(makePin);

    // orbiting food
    const orbit = new THREE.Group(); scene.add(orbit);
    const kinds = ['apple', 'bread', 'carrot', 'bowl', 'apple', 'bread'];
    const items = kinds.map((k, i) => {
      const m = foodMesh(k); m.userData = { a: (i / kinds.length) * Math.PI * 2, r: 2.55 + (i % 2) * 0.3, s: 0.35 + (i % 3) * 0.08, y: (i % 3 - 1) * 0.55 };
      orbit.add(m); return m;
    });
    const ring = new THREE.Mesh(new THREE.TorusGeometry(2.7, 0.008, 8, 120), new THREE.MeshBasicMaterial({ color: 0xffffff, transparent: true, opacity: 0.25 }));
    ring.rotation.x = Math.PI / 2.3; scene.add(ring);

    const st = state.current; st.drag = false; st.vx = 0.0035; st.rx = 0; st.ry = 0; st.mx = 0; st.my = 0;
    const resize = () => { const w = el.clientWidth, h = el.clientHeight; renderer.setSize(w, h, false); renderer.domElement.style.width = '100%'; renderer.domElement.style.height = '100%'; camera.aspect = w / h; camera.updateProjectionMatrix(); };
    resize();
    const ro = new ResizeObserver(resize); ro.observe(el);

    const down = e => { st.drag = true; st.lx = e.clientX; st.ly = e.clientY; el.setPointerCapture?.(e.pointerId); };
    const up = () => { st.drag = false; };
    const move = e => {
      const r = el.getBoundingClientRect(); st.mx = (e.clientX - r.left) / r.width - 0.5; st.my = (e.clientY - r.top) / r.height - 0.5;
      if (st.drag) { world.rotation.y += (e.clientX - st.lx) * 0.01; world.rotation.x += (e.clientY - st.ly) * 0.006; st.lx = e.clientX; st.ly = e.clientY; }
    };
    el.addEventListener('pointerdown', down); el.addEventListener('pointerup', up); el.addEventListener('pointerleave', up); el.addEventListener('pointermove', move);

    let raf, t = 0;
    const reduce = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
    const tick = () => {
      raf = requestAnimationFrame(tick); t += 0.01;
      if (!st.drag && !reduce) world.rotation.y += st.vx;
      world.rotation.x = THREE.MathUtils.clamp(world.rotation.x, -0.6, 0.6);
      items.forEach(m => {
        const u = m.userData; const a = u.a + t * u.s;
        m.position.set(Math.cos(a) * u.r, u.y + Math.sin(t * 2 + u.a) * 0.12, Math.sin(a) * u.r * 0.7);
        m.rotation.y += 0.02; m.rotation.x += 0.008;
      });
      camera.position.x += (st.mx * 0.8 - camera.position.x) * 0.05;
      camera.position.y += (0.2 - st.my * 0.6 - camera.position.y) * 0.05;
      camera.lookAt(0, 0, 0);
      renderer.render(scene, camera);
    };
    tick();

    return () => {
      cancelAnimationFrame(raf); ro.disconnect();
      el.removeEventListener('pointerdown', down); el.removeEventListener('pointerup', up); el.removeEventListener('pointerleave', up); el.removeEventListener('pointermove', move);
      scene.traverse(o => { if (o.geometry) o.geometry.dispose(); if (o.material) [].concat(o.material).forEach(m => m.dispose()); });
      renderer.dispose(); renderer.domElement.remove();
    };
  }, [donations.length]); // eslint-disable-line react-hooks/exhaustive-deps

  if (failed) return <img className="hero3d-fallback" src="/assets/images/hero-food.jpg" alt="A box of fresh food ready to be shared" />;
  return <div className="hero3d" ref={box} aria-label="Interactive 3D globe. Drag to rotate." role="img" />;
}
