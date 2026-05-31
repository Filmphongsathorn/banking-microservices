import React, { useState, useContext } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { Landmark, ArrowRight, Loader2 } from 'lucide-react';
import Swal from 'sweetalert2';

const Login = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useContext(AuthContext);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');
    setIsLoading(true);

    const result = await login(username, password);
    if (result.success) {
      Swal.fire({
        icon: 'success',
        title: 'Welcome Back!',
        text: 'Login successful.',
        timer: 1500,
        showConfirmButton: false
      });
      navigate('/');
    } else {
      setError(result.message);
      Swal.fire({
        icon: 'error',
        title: 'Login Failed',
        text: result.message || 'Invalid credentials'
      });
      setIsLoading(false);
    }
  };

  return (
    <div className="auth-layout">
      <div className="card auth-card animate-fade-in">
        <div className="text-center mb-8">
          <div className="flex justify-center items-center gap-2 mb-2">
            <Landmark size={32} className="text-primary" />
            <h1 style={{ fontSize: '1.75rem', fontWeight: '700' }}>Nexus<span className="text-primary">Bank</span></h1>
          </div>
          <p className="text-muted">Welcome back! Please enter your details.</p>
        </div>

        <form onSubmit={handleSubmit}>
          {error && <div className="form-group"><div className="badge badge-danger text-center w-full" style={{ padding: '0.75rem', borderRadius: '0.5rem', display: 'block' }}>{error}</div></div>}
          
          <div className="form-group">
            <label className="form-label">Username</label>
            <input
              type="text"
              className="form-control"
              placeholder="Enter your username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
            />
          </div>
          
          <div className="form-group mb-6">
            <label className="form-label">Password</label>
            <input
              type="password"
              className="form-control"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
            />
          </div>

          <button type="submit" className="btn btn-primary btn-block" disabled={isLoading}>
            {isLoading ? <Loader2 className="animate-spin" size={20} /> : 'Sign In'}
            {!isLoading && <ArrowRight size={18} />}
          </button>
        </form>

        <div className="text-center mt-6">
          <p className="text-muted" style={{ fontSize: '0.9rem' }}>
            Don't have an account? <Link to="/register" className="font-medium">Sign up</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Login;
