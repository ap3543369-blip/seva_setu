import { createContext, useContext, useState, useCallback } from 'react';

const Ctx = createContext(() => {});
export const useToast = () => useContext(Ctx);

export function ToastProvider({ children }) {
  const [items, setItems] = useState([]);
  const toast = useCallback((message, type = 'ok') => {
    const id = Math.random().toString(36).slice(2);
    setItems(l => [...l, { id, message, type }]);
    setTimeout(() => setItems(l => l.filter(t => t.id !== id)), 3600);
  }, []);
  return (
    <Ctx.Provider value={toast}>
      {children}
      <div className="toast-box" aria-live="polite">
        {items.map(t => (
          <div key={t.id} className={`toast ${t.type}`} role="alert">{t.message}</div>
        ))}
      </div>
    </Ctx.Provider>
  );
}
