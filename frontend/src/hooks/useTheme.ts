// useTheme - Applies 'dark' class to document.documentElement when theme is 'dark'

import { useEffect } from 'react';
import { useThemeStore } from '../store/themeStore';

export function useTheme(): void {
  const theme = useThemeStore((state) => state.theme);

  useEffect(() => {
    const root = document.documentElement;
    if (theme === 'dark') {
      root.classList.add('dark');
    } else {
      root.classList.remove('dark');
    }
  }, [theme]);
}