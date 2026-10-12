import { describe, it, expect } from 'vitest';
import { registerSchema, loginSchema, passwordRules } from '../schemas/auth';

const ok = { name: 'Asha Rao', email: 'asha@example.com', password: 'Str0ng!Passw0rd', confirmPassword: 'Str0ng!Passw0rd', acceptTerms: true };
const err = (data) => { const r = registerSchema.safeParse(data); return r.success ? [] : r.error.issues.map((i) => i.message); };

describe('registration validation', () => {
  it('accepts a valid registration', () => expect(registerSchema.safeParse(ok).success).toBe(true));
  it('rejects bad email, weak password, mismatch and missing consent', () => {
    expect(err({ ...ok, email: 'nope' })).toContain('Enter a valid email address');
    expect(err({ ...ok, password: 'short1!A', confirmPassword: 'short1!A' })).toContain('Password does not meet all requirements');
    expect(err({ ...ok, confirmPassword: 'Different1!Pass' })).toContain('Passwords do not match');
    expect(err({ ...ok, acceptTerms: false }).join()).toMatch(/terms/i);
  });
  it('each password rule is individually enforced', () => {
    const fails = (pw) => passwordRules.filter((r) => !r.test(pw)).map((r) => r.id);
    expect(fails('alllowercase1!')).toEqual(['upper']);
    expect(fails('ALLUPPERCASE1!')).toEqual(['lower']);
    expect(fails('NoNumbersHere!')).toEqual(['num']);
    expect(fails('NoSymbols12345A')).toEqual(['sym']);
    expect(fails('Sh0rt!a')).toEqual(['len']);
  });
});

describe('login validation', () => {
  it('requires email and password', () => {
    expect(loginSchema.safeParse({ email: '', password: '' }).success).toBe(false);
    expect(loginSchema.safeParse({ email: 'a@b.co', password: 'x' }).success).toBe(true);
  });
});
