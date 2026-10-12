import { z } from 'zod';

export const passwordRules = [
  { id: 'len', label: 'At least 10 characters', test: (v) => v.length >= 10 },
  { id: 'upper', label: 'An uppercase letter', test: (v) => /[A-Z]/.test(v) },
  { id: 'lower', label: 'A lowercase letter', test: (v) => /[a-z]/.test(v) },
  { id: 'num', label: 'A number', test: (v) => /\d/.test(v) },
  { id: 'sym', label: 'A symbol', test: (v) => /[^A-Za-z0-9]/.test(v) },
];
const password = z.string().min(1, 'Enter a password').refine((v) => passwordRules.every((r) => r.test(v)), 'Password does not meet all requirements');

export const loginSchema = z.object({
  email: z.string().min(1, 'Enter your email').email('Enter a valid email address'),
  password: z.string().min(1, 'Enter your password'),
  remember: z.boolean().optional(),
});
export const registerSchema = z.object({
  name: z.string().trim().min(2, 'Enter your full name').max(100),
  email: z.string().min(1, 'Enter your email').email('Enter a valid email address'),
  password,
  confirmPassword: z.string().min(1, 'Confirm your password'),
  acceptTerms: z.literal(true, { errorMap: () => ({ message: 'Accept the terms and privacy policy to continue' }) }),
}).refine((d) => d.password === d.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' });
export const forgotSchema = z.object({ email: z.string().min(1, 'Enter your email').email('Enter a valid email address') });
export const resetSchema = z.object({ password, confirmPassword: z.string().min(1, 'Confirm your password') })
  .refine((d) => d.password === d.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' });
export const changePasswordSchema = z.object({ currentPassword: z.string().min(1, 'Enter your current password'), password, confirmPassword: z.string().min(1, 'Confirm your password') })
  .refine((d) => d.password === d.confirmPassword, { path: ['confirmPassword'], message: 'Passwords do not match' });
