import { create } from 'zustand';
export interface Account { id: string; email: string; displayName: string; role: 'CUSTOMER' | 'ADMIN' }
export const useAuthStore = create<{ user: Account | null; setUser: (user: Account | null) => void }>(set => ({
  user: null, setUser: user => set({ user }),
}));
