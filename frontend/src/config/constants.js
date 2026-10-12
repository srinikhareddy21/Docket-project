export const STATUSES = ['Applied', 'Shortlisted', 'Assessment', 'Interview', 'Selected', 'Rejected', 'Withdrawn'];
export const STATUS_TOKEN = {
  Applied: 'status-applied', Shortlisted: 'status-shortlisted', Assessment: 'status-assessment',
  Interview: 'status-interview', Selected: 'status-selected', Rejected: 'status-rejected', Withdrawn: 'status-withdrawn',
};
export const JOB_TYPES = ['Internship', 'Full-time', 'Part-time', 'Other'];
export const WORK_MODES = ['Remote', 'Hybrid', 'Onsite'];
export const SOURCES = ['Referral', 'LinkedIn', 'Other job portal', 'Company career page', 'College placement cell', 'Other'];
export const ROUND_TYPES = ['Technical', 'HR', 'Managerial', 'Assessment', 'Other'];
export const INTERVIEW_MODES = ['Video call', 'Phone', 'Onsite'];
export const INTERVIEW_RESULTS = ['Pending', 'Passed', 'Failed', 'Awaiting outcome'];
export const SKILL_CATEGORIES = [
  'Programming languages', 'Web development', 'Databases', 'Tools and platforms',
  'Core computer science', 'Communication and professional',
];
export const REQUIREMENT_CATEGORIES = [
  'Skills I have', 'Skills to improve', 'Documents to prepare', 'Topics to revise', 'Other',
];
export const DOCUMENT_TYPES = ['Resume', 'Cover letter', 'Certificate', 'Other'];

export const NAV = [
  { to: '/app', label: 'Dashboard', icon: 'LayoutDashboard', end: true },
  { to: '/app/applications', label: 'Applications', icon: 'Briefcase' },
  { to: '/app/board', label: 'Kanban board', icon: 'Columns3' },
  { to: '/app/applications/new', label: 'Add application', icon: 'PlusCircle' },
  { to: '/app/interviews', label: 'Interviews', icon: 'CalendarClock' },
  { to: '/app/calendar', label: 'Calendar & reminders', icon: 'Bell' },
  { to: '/app/analytics', label: 'Analytics', icon: 'BarChart3' },
  { to: '/app/skills', label: 'My skills', icon: 'Sparkles' },
  { to: '/app/goals', label: 'Weekly goals', icon: 'Target' },
  { to: '/app/templates', label: 'Message templates', icon: 'Mail' },
  { to: '/app/share', label: 'Share progress', icon: 'Share2' },
  { to: '/app/profile', label: 'Profile', icon: 'User' },
  { to: '/app/settings', label: 'Settings', icon: 'Settings' },
];
