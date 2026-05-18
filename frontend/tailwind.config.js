/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,jsx,ts,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        'dark-base': '#020408',
        'dark-surface': '#0a0f1b',
        'dark-raised': '#0d1424',
        'dark-panel': 'rgba(13, 20, 36, 0.85)',
      },
      fontFamily: {
        'tech': ['JetBrains Mono', 'monospace'],
        'sans': ['DM Sans', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}
