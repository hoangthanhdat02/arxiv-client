/** @type {import('tailwindcss').Config} */
module.exports = {
  darkMode: 'class',
  content: [
    './app/**/*.{js,ts,jsx,tsx,mdx}',
    './components/**/*.{js,ts,jsx,tsx,mdx}',
  ],
  theme: {
    extend: {
      colors: {
        arxiv: {
          red: '#b31b1b',
          dark: '#1e293b',
          accent: '#3b82f6',
        },
      },
    },
  },
  plugins: [],
};
