module.exports = {
  content: [
    './src/**/*',
  ],
  theme: {
    extend: {
      colors: {
        misty: {
          500: '#6c5a9d',
          800: '#433a5c',
        },
      },
      fontFamily: {
        'sans': ["Switzer", "ui-sans-serif", "system-ui", "sans-serif", "Apple Color Emoji", "Segoe UI Emoji", "Segoe UI Symbol", "Noto Color Emoji"],
      },
    },
  },
  plugins: [
    require('@tailwindcss/forms'),
  ],
}
