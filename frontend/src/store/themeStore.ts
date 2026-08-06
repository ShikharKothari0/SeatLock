// Theme Store - Zustand store for dark/light theme with localStorage persistence

import { create } from 'zustand';
import { persist } from 'zustand/middleware';

export type Theme = 'dark' | 'light';

interface ThemeState {
  theme: Theme;
  toggle: () => void;
  setTheme: (theme: Theme) => void;
}

export const useThemeStore = create<ThemeState>()(
  persist(
    (set) => ({
      theme: 'dark',
      toggle: () => set((state) => ({ theme: state.theme === 'dark' ? 'light' : 'dark' })),
      setTheme: (theme: Theme) => set({ theme }),
    }),
    {
      name: 'seatlock:theme',
      // Only persist the theme field
      partialize: (state) => ({ theme: state.theme }),
    }
  )
);