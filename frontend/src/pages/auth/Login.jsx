import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Input, PasswordInput, Checkbox } from '../../components/ui/Field';
import { Button } from '../../components/ui/Button';
import { loginSchema } from '../../schemas/auth';
import { useAuth } from '../../context/AuthContext';
import { apiError } from '../../services/api';

export default function Login() {
  const { login } = useAuth(); const navigate = useNavigate(); const loc = useLocation();
  const [formError, setFormError] = useState('');
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(loginSchema), defaultValues: { remember: false } });

  const onSubmit = async (v) => {
    setFormError('');
    try { await login(v); navigate(loc.state?.from || '/app', { replace: true }); }
    catch (e) { const err = apiError(e); setFormError(err.status === 401 ? 'Email or password is incorrect.' : err.message); }
  };

  return (
    <>
      <h1 className="text-2xl font-semibold">Log in</h1>
      <p className="mt-1 text-sm text-fg-muted">Pick up where your search left off.</p>
      <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4" noValidate>
        {formError && <p role="alert" className="rounded-control bg-danger/10 px-3 py-2 text-sm text-danger">{formError}</p>}
        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <PasswordInput label="Password" autoComplete="current-password" error={errors.password?.message} {...register('password')} />
        <div className="flex items-center justify-between">
          <Checkbox label="Keep me logged in" {...register('remember')} />
          <Link to="/forgot-password" className="text-sm text-fg-muted underline-offset-2 hover:underline">Forgot password?</Link>
        </div>
        <Button type="submit" loading={isSubmitting} className="w-full">Log in</Button>
      </form>
      <p className="mt-6 text-center text-sm text-fg-muted">New to DOCKET? <Link to="/register" className="font-medium text-fg underline-offset-2 hover:underline">Create an account</Link></p>
    </>
  );
}
