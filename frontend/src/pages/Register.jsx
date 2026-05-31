import React, { useState, useContext } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { AuthContext } from '../context/AuthContext';
import { Landmark, UserPlus, Loader2 } from 'lucide-react';
import Swal from 'sweetalert2';

const Register = () => {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState('');
  const [isLoading, setIsLoading] = useState(false);
  
  const { register } = useContext(AuthContext);
  const navigate = useNavigate();

  const handleSubmit = async (e) => {
    e.preventDefault();
    setError('');

    if (password !== confirmPassword) {
      setError('Passwords do not match');
      Swal.fire({
        icon: 'error',
        title: 'Registration Failed',
        text: 'Passwords do not match'
      });
      return;
    }

    setIsLoading(true);

    const result = await register(username, password);
    if (result.success) {
      Swal.fire({
        icon: 'success',
        title: 'Registration Successful',
        text: 'You can now log in with your credentials.',
        timer: 2000,
        showConfirmButton: false
      });
      navigate('/login');
    } else {
      setError(result.message);
      Swal.fire({
        icon: 'error',
        title: 'Registration Failed',
        text: result.message || 'Something went wrong'
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
          <p className="text-muted">Create an account to get started.</p>
        </div>

        <form onSubmit={handleSubmit}>
          {error && <div className="form-group"><div className="badge badge-danger text-center w-full" style={{ padding: '0.75rem', borderRadius: '0.5rem', display: 'block' }}>{error}</div></div>}
          
          <div className="form-group">
            <label className="form-label">Username</label>
            <input
              type="text"
              className="form-control"
              placeholder="Choose a username"
              value={username}
              onChange={(e) => setUsername(e.target.value)}
              required
              minLength="3"
            />
          </div>
          
          <div className="form-group">
            <label className="form-label">Password</label>
            <input
              type="password"
              className="form-control"
              placeholder="Create a password (min. 6 chars)"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength="6"
            />
          </div>

          <div className="form-group mb-6">
            <label className="form-label">Confirm Password</label>
            <input
              type="password"
              className="form-control"
              placeholder="Confirm your password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
            />
          </div>

          <button type="submit" className="btn btn-primary btn-block" disabled={isLoading}>
            {isLoading ? <Loader2 className="animate-spin" size={20} /> : 'Create Account'}
            {!isLoading && <UserPlus size={18} />}
          </button>
        </form>

        <div className="text-center mt-6">
          <p className="text-muted" style={{ fontSize: '0.9rem' }}>
            Already have an account? <Link to="/login" className="font-medium">Sign in</Link>
          </p>
        </div>
      </div>
    </div>
  );
};

export default Register;
