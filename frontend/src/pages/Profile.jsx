import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import { z } from 'zod';
import { profileService } from '../services/workspace';
import { apiError } from '../services/api';
import { useAuth } from '../context/AuthContext';
import { useToast } from '../context/ToastContext';
import { Button } from '../components/ui/Button';
import { Input, PasswordInput } from '../components/ui/Field';
import { Card, PageHeader } from '../components/ui/Display';
import { Modal } from '../components/ui/Modal';
import { changePasswordSchema } from '../schemas/auth';

export default function Profile() {
  const { user, setUser, logout } = useAuth(); const toast = useToast(); const navigate = useNavigate();
  const [delOpen, setDelOpen] = useState(false); const [delPw, setDelPw] = useState(''); const [delBusy, setDelBusy] = useState(false);

  const info = useForm({ resolver: zodResolver(z.object({ name: z.string().trim().min(2, 'Enter your full name').max(100) })), defaultValues: { name: user?.name || '' } });
  const email = useForm({ resolver: zodResolver(z.object({ email: z.string().email('Enter a valid email address') })), defaultValues: { email: '' } });
  const pw = useForm({ resolver: zodResolver(changePasswordSchema) });

  const saveInfo = async (v) => { try { const u = await profileService.update(v); setUser((c) => ({ ...c, ...u })); toast.success('Profile saved.'); } catch (e) { toast.error(apiError(e).message); } };
  const saveEmail = async (v) => { try { await profileService.requestEmailChange(v.email); email.reset(); toast.success('If that address can be used, we sent it a verification link. Your email changes only after you open it.'); } catch (e) { toast.error(apiError(e).message); } };
  const savePw = async ({ currentPassword, password }) => { try { await profileService.changePassword({ currentPassword, newPassword: password }); pw.reset(); toast.success('Password changed. Your other devices were signed out.'); } catch (e) { const er = apiError(e); if (er.fieldErrors?.currentPassword) pw.setError('currentPassword', { message: er.fieldErrors.currentPassword }); else if (er.fieldErrors?.newPassword) pw.setError('password', { message: er.fieldErrors.newPassword }); else toast.error(er.message); } };
  const del = async () => { setDelBusy(true); try { await profileService.requestDeletion(delPw); toast.success('Your account was deleted.'); await logout(); navigate('/'); } catch (e) { toast.error(apiError(e).message); setDelBusy(false); } };

  return (
    <>
      <PageHeader title="Profile" description={user?.email} />
      <div className="grid gap-4 lg:grid-cols-2">
        <Card title="Your details"><form onSubmit={info.handleSubmit(saveInfo)} className="space-y-3" noValidate><Input label="Full name" error={info.formState.errors.name?.message} {...info.register('name')} /><Button type="submit" loading={info.formState.isSubmitting}>Save profile</Button></form></Card>
        <Card title="Change email"><form onSubmit={email.handleSubmit(saveEmail)} className="space-y-3" noValidate><Input label="New email" type="email" hint="We email a verification link to the new address before anything changes." error={email.formState.errors.email?.message} {...email.register('email')} /><Button type="submit" variant="secondary" loading={email.formState.isSubmitting}>Send verification</Button></form></Card>
        <Card title="Change password"><form onSubmit={pw.handleSubmit(savePw)} className="space-y-3" noValidate>
          <PasswordInput label="Current password" autoComplete="current-password" error={pw.formState.errors.currentPassword?.message} {...pw.register('currentPassword')} />
          <PasswordInput label="New password" autoComplete="new-password" hint="10+ characters with upper and lower case, a number and a symbol." error={pw.formState.errors.password?.message} {...pw.register('password')} />
          <PasswordInput label="Confirm new password" autoComplete="new-password" error={pw.formState.errors.confirmPassword?.message} {...pw.register('confirmPassword')} />
          <Button type="submit" loading={pw.formState.isSubmitting}>Update password</Button></form></Card>
        <Card title="Delete account"><p className="mb-3 text-sm text-fg-muted">This permanently removes your account and every application, interview and document in it.</p><Button variant="danger" onClick={() => setDelOpen(true)}>Delete my account</Button></Card>
      </div>
      <Modal open={delOpen} onClose={() => setDelOpen(false)} title="Delete your account?" size="sm" footer={<><Button variant="secondary" onClick={() => setDelOpen(false)}>Cancel</Button><Button variant="danger" loading={delBusy} disabled={!delPw} onClick={del}>Delete account</Button></>}>
        <p className="mb-3 text-sm text-fg-muted">Enter your password to confirm. Your account, applications, interviews and uploaded files are deleted immediately and cannot be recovered.</p><PasswordInput label="Password" value={delPw} onChange={(e) => setDelPw(e.target.value)} />
      </Modal>
    </>
  );
}
