import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { MailCheck } from 'lucide-react';
import { Input } from '../../components/ui/Field';
import { Button } from '../../components/ui/Button';
import { forgotSchema } from '../../schemas/auth';
import { authService } from '../../services/auth';
import { apiError } from '../../services/api';

export default function ForgotPassword() {
  const [sent, setSent] = useState(false); const [formError, setFormError] = useState('');
  const { register, handleSubmit, formState: { errors, isSubmitting } } = useForm({ resolver: zodResolver(forgotSchema) });
  const onSubmit = async ({ email }) => {
    setFormError('');
    try { await authService.forgotPassword(email); setSent(true); } catch (e) { setFormError(apiError(e).message); }
  };
  if (sent) return (
    <div className="text-center">
      <MailCheck className="mx-auto text-primary" size={32} />
      <h1 className="mt-3 text-2xl font-semibold">Check your email</h1>
      <p className="mt-2 text-sm text-fg-muted">If an account exists for that address, a reset link is on its way. The link expires soon, so use it promptly.</p>
      <Link to="/login" className="mt-6 inline-block text-sm font-medium underline-offset-2 hover:underline">Back to log in</Link>
    </div>
  );
  return (
    <>
      <h1 className="text-2xl font-semibold">Reset your password</h1>
      <p className="mt-1 text-sm text-fg-muted">Enter your email and we will send a reset link.</p>
      <form onSubmit={handleSubmit(onSubmit)} className="mt-6 space-y-4" noValidate>
        {formError && <p role="alert" className="rounded-control bg-danger/10 px-3 py-2 text-sm text-danger">{formError}</p>}
        <Input label="Email" type="email" autoComplete="email" error={errors.email?.message} {...register('email')} />
        <Button type="submit" loading={isSubmitting} className="w-full">Send reset link</Button>
      </form>
      <p className="mt-6 text-center text-sm"><Link to="/login" className="text-fg-muted underline-offset-2 hover:underline">Back to log in</Link></p>
    </>
  );
}
