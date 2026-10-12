/** All colors come from CSS variables defined in src/styles/tokens.css. */
const token = (name) => `rgb(var(--${name}) / <alpha-value>)`;

export default {
  content: ['./index.html', './src/**/*.{js,jsx}'],
  theme: {
    extend: {
      colors: {
        bg: token('color-bg'),
        surface: token('color-surface'),
        'surface-muted': token('color-surface-muted'),
        border: token('color-border'),
        'border-strong': token('color-border-strong'),
        fg: token('color-text'),
        'fg-muted': token('color-text-muted'),
        'fg-subtle': token('color-text-subtle'),
        primary: token('color-primary'),
        'primary-hover': token('color-primary-hover'),
        'on-primary': token('color-on-primary'),
        'primary-soft': token('color-primary-soft'),
        focus: token('color-focus'),
        success: token('color-success'),
        warning: token('color-warning'),
        danger: token('color-danger'),
        info: token('color-info'),
        'status-applied': token('status-applied'),
        'status-shortlisted': token('status-shortlisted'),
        'status-assessment': token('status-assessment'),
        'status-interview': token('status-interview'),
        'status-selected': token('status-selected'),
        'status-rejected': token('status-rejected'),
        'status-withdrawn': token('status-withdrawn'),
      },
      fontFamily: {
        display: 'var(--font-display)',
        sans: 'var(--font-body)',
      },
      borderRadius: {
        card: 'var(--radius-card)',
        control: 'var(--radius-control)',
      },
      boxShadow: {
        card: 'var(--shadow-card)',
        pop: 'var(--shadow-pop)',
      },
    },
  },
  plugins: [],
};
