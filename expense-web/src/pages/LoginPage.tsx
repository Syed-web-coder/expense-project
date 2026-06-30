import { useNavigate } from 'react-router-dom';

export function LoginPage() {
  const navigate = useNavigate();

  function handleSignIn() {
    localStorage.setItem('uc:jwt', 'dev-fake-jwt-token');
    void navigate('/merchants');
  }

  return (
    <div>
      <h1>Sign in</h1>
      <button onClick={handleSignIn}>Sign in (stub)</button>
    </div>
  );
}
