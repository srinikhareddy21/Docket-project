import { useEffect, useRef, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { authService } from '../../services/auth';
import { apiError } from '../../services/api';
import { Button } from '../../components/ui/Button';

/** Opened from the verification email. Works whether or not the browser is logged in; the token is the proof. */
export default function ConfirmEmail() {
  const [params] = useSearchParams(); const token = params.get('token');
  const [state, setState] = useState(token ? 'working' : 'missing'); const [message, setMessage] = useState('');
  const started = useRef(false);
  useEffect(() => {
    if (!token || started.current) return;
    started.current = true;
    authService.confirmEmailChange(token).then(() => setState('done')).catch((e) => { setMessage(apiError(e).message); setState('error'); });
  }, [token]);

  if (state === 'working') return <p role="status" className="text-center text-sm text-fg-muted">Confirming your new email…</p>;
  if (state === 'done') return (
    <div className="text-center"><h1 className="text-xl font-semibold">Email address updated</h1>
      <p className="mt-2 text-sm text-fg-muted">Your login email has changed and every device was signed out. Log in with the new address.</p>
      <Link to="/login" className="mt-4 inline-block"><Button>Go to log in</Button></Link></div>
  );
  return (
    <div className="text-center"><h1 className="text-xl font-semibold">{state === 'missing' ? 'Link is missing' : 'We could not confirm this'}</h1>
      <p role="alert" className="mt-2 text-sm text-fg-muted">{state === 'missing' ? 'Open the link from the verification email.' : message}</p>
      <Link to="/app/profile" className="mt-4 inline-block text-sm font-medium underline">Back to your profile</Link></div>
  );
}
