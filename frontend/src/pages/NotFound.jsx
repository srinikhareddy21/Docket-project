import { Link } from 'react-router-dom';
import { Button } from '../components/ui/Button';
export default function NotFound() {
  return (
    <div className="flex min-h-[60vh] flex-col items-center justify-center gap-3 p-8 text-center">
      <h1 className="text-3xl font-semibold">Page not found</h1>
      <p className="text-sm text-fg-muted">The page you are looking for does not exist or has moved.</p>
      <Link to="/"><Button>Go to home</Button></Link>
    </div>
  );
}
