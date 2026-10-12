import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { PasswordInput } from '../../components/ui/Field';
import { Button } from '../../components/ui/Button';
import { resetSchema, passwordRules } from '../../schemas/auth';
import { authService } from '../../services/auth';
import { useToast } from '../../context/ToastContext';
import { apiError } from '../../services/api';

export default function ResetPassword() {
  const [params] = useSearchParams(); const token = params.get('token'); const navigate = useNavigate(); const toast = useToast();
  const [formError, setFormError] = useState('');
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(resetSchema) });
  if (!token) return (<div className="text-center"><h1 className="text-xl font-semibold">Reset link is missing</h1><p className="mt-2 text-sm text-fg-muted">Open the link from your email, or request a new one.</p><Link to="/forgot-password" className="mt-4 inline-block text-sm font-medium underline">Request a new link</Link></div>);
  const onSubmit = async ({ password }) => {
    setFormError('');
    try { await authService.resetPassword({ token, password }); toast.success('Password updated. Log in with your new password.'); navigate('/login'); }
    catch (e) { setFormError(apiError(e).status === 400 ? 'This reset link is invalid or has expired. Request a new one.' : apiError(e).message); }
  };
  return (
    <>
      <h1 className="text-2xl font-semibold">Choose a new password</h1>
      <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4" noValidate>
        {formError && <p role="alert" className="rounded-control bg-danger/10 px-3 py-2 text-sm text-danger">{formError}</p>}
        <PasswordInput label="New password" autoComplete="new-password" hint={passwordRules.map((r) => r.label).join(' · ')} error={errors.password?.message} {...register('password')} />
        <PasswordInput label="Confirm new password" autoComplete="new-password" error={errors.confirmPassword?.message} {...register('confirmPassword')} />
        <Button type="submit" loading={isSubmitting} className="w-full">Update password</Button>
      </form>
    </>
  );
}
