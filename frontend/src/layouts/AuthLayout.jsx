import { Link, Outlet } from 'react-router-dom';
import { motion } from 'motion/react';
import { Logo } from '../components/ui/Logo';

export default function AuthLayout() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center px-4 py-10">
      <Link to="/" aria-label="DOCKET home" className="mb-8"><Logo size={32} /></Link>
      <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }} className="card w-full max-w-md p-6 sm:p-8">
        <Outlet />
      </motion.div>
    </div>
  );
}
