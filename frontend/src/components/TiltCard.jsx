import { useRef } from 'react';

/** Card that tilts in 3D towards the mouse (pure CSS transforms, no library). */
export default function TiltCard({ children, className = '', max = 10, as: Tag = 'div', ...rest }) {
  const ref = useRef(null);
  const move = e => {
    const el = ref.current; if (!el) return;
    const r = el.getBoundingClientRect();
    const px = (e.clientX - r.left) / r.width - 0.5, py = (e.clientY - r.top) / r.height - 0.5;
    el.style.transform = `perspective(900px) rotateY(${px * max}deg) rotateX(${-py * max}deg) translateZ(8px)`;
    el.style.setProperty('--gx', `${(px + 0.5) * 100}%`);
    el.style.setProperty('--gy', `${(py + 0.5) * 100}%`);
  };
  const leave = () => { if (ref.current) ref.current.style.transform = ''; };
  return <Tag ref={ref} className={`tilt ${className}`} onMouseMove={move} onMouseLeave={leave} {...rest}>{children}</Tag>;
}
