import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Check, Circle } from 'lucide-react';
import { Input, PasswordInput, Checkbox } from '../../components/ui/Field';
import { Button } from '../../components/ui/Button';
import { registerSchema, passwordRules } from '../../schemas/auth';
import { useAuth } from '../../context/AuthContext';
import { useToast } from '../../context/ToastContext';
import { apiError } from '../../services/api';

export default function Register() {
  const { register: signUp } = useAuth(); const navigate = useNavigate(); const toast = useToast();
  const [formError, setFormError] = useState('');
  const { register, handleSubmit, watch, setError, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(registerSchema), defaultValues: { acceptTerms: false } });
  const pw = watch('password') || '';

  const onSubmit = async ({ confirmPassword, ...data }) => {
    setFormError('');
    try { await signUp(data); toast.success('Account created. Log in to get started.'); navigate('/login'); }
    catch (e) {
      const err = apiError(e);
      if (err.status === 409) setError('email', { message: 'An account with this email already exists.' });
      else { Object.entries(err.fieldErrors).forEach(([f, m]) => setError(f, { message: m })); setFormError(err.message); }
    }
  };

  return (
    <>
      <h1 className="text-2xl font-semibold">Create your account</h1>
      <p className="mt-1 text-sm text-fg-muted">Start with an empty workspace. Add only what is real.</p>
      <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4" noValidate>
        {formError && <p role="alert" className="rounded-control bg-danger/10 px-3 py-2 text-sm text-danger">{formError}</p>}
        <Input label="Full name" autoComplete="name" error={errors.name?.message} {...register('name')} />
        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <div>
          <PasswordInput label="Password" autoComplete="new-password" error={errors.password?.message} {...register('password')} />
          <ul className="mt-2 grid grid-cols-1 gap-1 sm:grid-cols-2" aria-label="Password requirements">
            {passwordRules.map((r) => { const ok = r.test(pw); return (
              <li key={r.id} className={`flex items-center gap-1.5 text-xs ${ok ? 'text-success' : 'text-fg-subtle'}`}>{ok ? <Check size={12} /> : <Circle size={12} />}{r.label}</li>); })}
          </ul>
        </div>
        <PasswordInput label="Confirm password" autoComplete="new-password" error={errors.confirmPassword?.message} {...register('confirmPassword')} />
        <Checkbox label="I understand that DOCKET stores the career data I enter and that I can delete my account at any time." error={errors.acceptTerms?.message} {...register('acceptTerms')} />
        <Button type="submit" loading={isSubmitting} className="w-full">Create account</Button>
      </form>
      <p className="mt-6 text-center text-sm text-fg-muted">Already have an account? <Link to="/login" className="font-medium text-fg underline-offset-2 hover:underline">Log in</Link></p>
    </>
  );
}
