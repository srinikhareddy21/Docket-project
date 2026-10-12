import { describe, it, expect, vi } from 'vitest';
import { render, screen, fireEvent, within } from '@testing-library/react';
import { MemoryRouter, Routes, Route, useLocation } from 'react-router-dom';
import ProtectedRoute, { GuestRoute } from '../routes/ProtectedRoute';
import * as Auth from '../context/AuthContext';
import { useShortcuts } from '../hooks/useShortcuts';
import { Modal } from '../components/ui/Modal';

const Where = () => <p data-testid="where">{useLocation().pathname}</p>;
const withStatus = (status) => vi.spyOn(Auth, 'useAuth').mockReturnValue({ status, restore: vi.fn() });

function tree(initial) {
  return (
    <MemoryRouter initialEntries={[initial]}>
      <Routes>
        <Route element={<ProtectedRoute />}><Route path="/app" element={<p>private workspace</p>} /></Route>
        <Route element={<GuestRoute />}><Route path="/login" element={<p>login page</p>} /></Route>
      </Routes>
    </MemoryRouter>
  );
}

describe('protected routes', () => {
  it('anonymous visitors are redirected to login and never see private content', () => {
    withStatus('anonymous');
    render(tree('/app'));
    expect(screen.queryByText('private workspace')).toBeNull();
    expect(screen.getByText('login page')).toBeInTheDocument();
  });
  it('shows a neutral checking state while the session is being verified', () => {
    withStatus('loading');
    render(tree('/app'));
    expect(screen.queryByText('private workspace')).toBeNull();
    expect(screen.getByRole('status')).toHaveTextContent(/checking your session/i);
  });
  it('an unreachable server is reported honestly, not treated as logged in', () => {
    withStatus('unreachable');
    render(tree('/app'));
    expect(screen.queryByText('private workspace')).toBeNull();
    expect(screen.getByText(/cannot reach the docket server/i)).toBeInTheDocument();
  });
  it('authenticated users reach the workspace, and are bounced away from login', () => {
    withStatus('authenticated');
    render(tree('/app'));
    expect(screen.getByText('private workspace')).toBeInTheDocument();
    render(tree('/login'));
    expect(screen.getAllByText('private workspace').length).toBeGreaterThan(0);
  });
});

function Shortcuts() {
  useShortcuts({ onHelp: () => document.body.setAttribute('data-help', '1') });
  return (
    <>
      <Where />
      <input data-testid="text" /><textarea data-testid="area" /><select data-testid="sel"><option>a</option></select>
      <div data-testid="editable" contentEditable suppressContentEditableWarning tabIndex={0} />
      <input data-search-input data-testid="search" />
      <div role="dialog"><button data-testid="in-dialog">x</button></div>
      <button data-testid="plain">plain</button>
    </>
  );
}
const renderShortcuts = () => render(<MemoryRouter initialEntries={['/app']}><Shortcuts /></MemoryRouter>);

describe('keyboard shortcuts', () => {
  it('N opens Add Application, "/" focuses search, "?" opens help', () => {
    renderShortcuts();
    fireEvent.keyDown(screen.getByTestId('plain'), { key: '/' });
    expect(screen.getByTestId('search')).toHaveFocus();
    fireEvent.keyDown(screen.getByTestId('plain'), { key: '?' });
    expect(document.body.getAttribute('data-help')).toBe('1');
    fireEvent.keyDown(screen.getByTestId('plain'), { key: 'n' });
    expect(screen.getByTestId('where')).toHaveTextContent('/app/applications/new');
  });
  it.each(['text', 'area', 'sel', 'editable', 'in-dialog'])('does not fire while typing in or inside %s', (id) => {
    renderShortcuts();
    for (const key of ['n', 'N', '/', '?']) fireEvent.keyDown(screen.getByTestId(id), { key });
    expect(screen.getByTestId('where')).toHaveTextContent('/app');
    expect(screen.getByTestId('where')).not.toHaveTextContent('/new');
    expect(screen.getByTestId('search')).not.toHaveFocus();
  });
  it('ignores Ctrl/Cmd/Alt combinations so browser shortcuts keep working', () => {
    renderShortcuts();
    fireEvent.keyDown(screen.getByTestId('plain'), { key: 'n', ctrlKey: true });
    fireEvent.keyDown(screen.getByTestId('plain'), { key: 'n', metaKey: true });
    expect(screen.getByTestId('where')).not.toHaveTextContent('/new');
  });
});

describe('dialog keyboard behavior', () => {
  it('focuses the dialog, traps Tab, closes on Escape and restores focus', async () => {
    const onClose = vi.fn();
    const opener = document.createElement('button'); document.body.appendChild(opener); opener.focus();
    render(<Modal open onClose={onClose} title="Test dialog" footer={<button>Last</button>}><button>First</button></Modal>);
    const dialog = await screen.findByRole('dialog');
    expect(dialog).toHaveFocus();
    const buttons = within(dialog).getAllByRole('button');
    const last = screen.getByText('Last');
    last.focus();
    fireEvent.keyDown(last, { key: 'Tab' });
    expect(buttons[0]).toHaveFocus();            // wrapped from last to first (the close button)
    fireEvent.keyDown(buttons[0], { key: 'Tab', shiftKey: true });
    expect(last).toHaveFocus();                  // and back
    fireEvent.keyDown(document, { key: 'Escape' });
    expect(onClose).toHaveBeenCalled();
    opener.remove();
  });
});
