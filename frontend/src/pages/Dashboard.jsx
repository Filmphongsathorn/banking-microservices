import React, { useState, useEffect, useContext } from 'react';
import { AuthContext } from '../context/AuthContext';
import api from '../api';
import { Plus, ArrowDownToLine, ArrowUpFromLine, ArrowRightLeft, CreditCard, Activity, RefreshCw } from 'lucide-react';
import Swal from 'sweetalert2';

const Dashboard = () => {
  const { user } = useContext(AuthContext);
  const [accounts, setAccounts] = useState([]);
  const [transactions, setTransactions] = useState([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [error, setError] = useState('');

  // Modals state
  const [activeModal, setActiveModal] = useState(null); // 'create', 'deposit', 'withdraw', 'transfer'
  const [modalForm, setModalForm] = useState({});

  const fetchData = async () => {
    try {
      setRefreshing(true);
      const userId = user.userId;
      
      const accountsRes = await api.get(`/api/v1/accounts/user/${userId}`);
      setAccounts(accountsRes.data.data || []);

      try {
        const txRes = await api.get(`/api/v1/transactions/user/${userId}`);
        setTransactions(txRes.data.data.content || []);
      } catch(txErr) {
        console.error("Failed to load transactions", txErr);
      }
      
      setError('');
    } catch (err) {
      console.error(err);
      setError('Failed to load dashboard data');
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  useEffect(() => {
    fetchData();
  }, []);

  const totalBalance = accounts.reduce((acc, account) => acc + account.balance, 0);

  const handleAction = async (actionType) => {
    try {
      const userId = user.userId;
      
      let res;
      if (actionType === 'create') {
        // Validate 10 digits
        if (!/^\d{10}$/.test(modalForm.accountNo)) {
          Swal.fire({ icon: 'warning', title: 'Invalid Input', text: 'Account number must be exactly 10 digits.' });
          return;
        }
        res = await api.post('/api/v1/accounts', {
          accountNo: modalForm.accountNo,
          userId,
          initialBalance: 0
        });
        Swal.fire({ icon: 'success', title: 'Success!', text: 'Account created successfully.' });
      } else if (actionType === 'deposit') {
        if (!modalForm.accountId || !modalForm.amount || parseFloat(modalForm.amount) <= 0) {
          Swal.fire({ icon: 'warning', title: 'Invalid Input', text: 'Please select an account and enter a valid amount.' });
          return;
        }
        res = await api.post('/api/v1/transactions/deposit', {
          accountId: modalForm.accountId,
          amount: parseFloat(modalForm.amount),
          userId,
          idempotencyKey: `dep-${Date.now()}`
        });
        Swal.fire({ icon: 'success', title: 'Deposit Successful', text: `Deposited $${modalForm.amount} to account ${modalForm.accountId}.` });
      } else if (actionType === 'withdraw') {
        if (!modalForm.accountId || !modalForm.amount || parseFloat(modalForm.amount) <= 0) {
          Swal.fire({ icon: 'warning', title: 'Invalid Input', text: 'Please select an account and enter a valid amount.' });
          return;
        }
        res = await api.post('/api/v1/transactions/withdraw', {
          accountId: modalForm.accountId,
          amount: parseFloat(modalForm.amount),
          userId,
          idempotencyKey: `wd-${Date.now()}`
        });
        Swal.fire({ icon: 'success', title: 'Withdrawal Successful', text: `Withdrew $${modalForm.amount} from account ${modalForm.accountId}.` });
      } else if (actionType === 'transfer') {
        if (!modalForm.fromAccountId || !modalForm.toAccountId || !modalForm.amount || parseFloat(modalForm.amount) <= 0) {
          Swal.fire({ icon: 'warning', title: 'Invalid Input', text: 'Please fill in all fields correctly.' });
          return;
        }
        if (modalForm.fromAccountId === modalForm.toAccountId) {
          Swal.fire({ icon: 'warning', title: 'Invalid Transfer', text: 'Cannot transfer to the same account.' });
          return;
        }
        res = await api.post('/api/v1/transactions/transfer', {
          fromAccountId: modalForm.fromAccountId,
          toAccountId: modalForm.toAccountId,
          amount: parseFloat(modalForm.amount),
          userId,
          idempotencyKey: `tx-${Date.now()}`
        });
        Swal.fire({ icon: 'success', title: 'Transfer Initiated', text: `Transfer of $${modalForm.amount} to ${modalForm.toAccountId} has been initiated.` });
      }
      
      setActiveModal(null);
      setModalForm({});
      fetchData();
    } catch (err) {
      const errorMsg = err.response?.data?.message || err.message || 'Operation failed';
      Swal.fire({ icon: 'error', title: 'Error', text: errorMsg });
    }
  };

  const renderModal = () => {
    if (!activeModal) return null;

    const titles = {
      create: 'Open New Account',
      deposit: 'Deposit Funds',
      withdraw: 'Withdraw Funds',
      transfer: 'Transfer Money'
    };

    return (
      <div className="modal-overlay">
        <div className="modal-content">
          <div className="modal-header">
            <h3 className="modal-title">{titles[activeModal]}</h3>
            <button className="modal-close" onClick={() => setActiveModal(null)}>×</button>
          </div>
          <div className="modal-body">
            {activeModal === 'create' && (
              <div className="form-group">
                <label className="form-label">Account Number (10 digits)</label>
                <input type="text" className="form-control" value={modalForm.accountNo || ''} onChange={e => setModalForm({...modalForm, accountNo: e.target.value})} placeholder="e.g. 1111111111" />
              </div>
            )}
            
            {['deposit', 'withdraw'].includes(activeModal) && (
              <>
                <div className="form-group">
                  <label className="form-label">Select Account</label>
                  <select className="form-control" value={modalForm.accountId || ''} onChange={e => setModalForm({...modalForm, accountId: e.target.value})}>
                    <option value="">-- Select --</option>
                    {accounts.map(acc => (
                      <option key={acc.accountNo} value={acc.accountNo}>{acc.accountNo} (Bal: ${acc.balance.toFixed(2)})</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">Amount</label>
                  <input type="number" className="form-control" value={modalForm.amount || ''} onChange={e => setModalForm({...modalForm, amount: e.target.value})} placeholder="0.00" min="0.01" step="0.01" />
                </div>
              </>
            )}

            {activeModal === 'transfer' && (
              <>
                <div className="form-group">
                  <label className="form-label">From Account</label>
                  <select className="form-control" value={modalForm.fromAccountId || ''} onChange={e => setModalForm({...modalForm, fromAccountId: e.target.value})}>
                    <option value="">-- Select --</option>
                    {accounts.map(acc => (
                      <option key={acc.accountNo} value={acc.accountNo}>{acc.accountNo} (Bal: ${acc.balance.toFixed(2)})</option>
                    ))}
                  </select>
                </div>
                <div className="form-group">
                  <label className="form-label">To Account Number</label>
                  <input type="text" className="form-control" value={modalForm.toAccountId || ''} onChange={e => setModalForm({...modalForm, toAccountId: e.target.value})} placeholder="Destination account" />
                </div>
                <div className="form-group">
                  <label className="form-label">Amount</label>
                  <input type="number" className="form-control" value={modalForm.amount || ''} onChange={e => setModalForm({...modalForm, amount: e.target.value})} placeholder="0.00" min="0.01" step="0.01" />
                </div>
              </>
            )}
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setActiveModal(null)}>Cancel</button>
            <button className="btn btn-primary" onClick={() => handleAction(activeModal)}>Confirm</button>
          </div>
        </div>
      </div>
    );
  };

  if (loading) {
    return <div className="flex justify-center items-center" style={{ height: '60vh' }}><RefreshCw className="animate-spin text-primary" size={32} /></div>;
  }

  return (
    <div className="container animate-fade-in">
      <div className="flex justify-between items-center mb-6">
        <div>
          <h1 style={{ fontSize: '1.75rem', fontWeight: '700' }}>Overview</h1>
          <p className="text-muted">Welcome back, {user.username}</p>
        </div>
        <button onClick={fetchData} className="btn btn-outline" disabled={refreshing}>
          <RefreshCw size={16} className={refreshing ? "animate-spin" : ""} />
          Refresh
        </button>
      </div>

      {error && <div className="badge badge-danger mb-4" style={{ padding: '0.75rem', borderRadius: '0.5rem', display: 'block' }}>{error}</div>}

      {/* Top row: Total Balance and Actions */}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '1.5rem', marginBottom: '2rem' }}>
        
        {/* Total Balance Card */}
        <div className="card" style={{ background: 'linear-gradient(135deg, var(--primary) 0%, #002277 100%)', color: 'white' }}>
          <div className="flex items-center gap-2 mb-4" style={{ opacity: 0.9 }}>
            <CreditCard size={20} />
            <h3 style={{ fontWeight: '500' }}>Total Balance</h3>
          </div>
          <h2 style={{ fontSize: '2.5rem', fontWeight: '700', marginBottom: '0.5rem' }}>
            ${totalBalance.toFixed(2)}
          </h2>
          <p style={{ opacity: 0.8, fontSize: '0.9rem' }}>Across {accounts.length} account{accounts.length !== 1 ? 's' : ''}</p>
        </div>

        {/* Quick Actions Card */}
        <div className="card">
          <h3 style={{ fontWeight: '600', marginBottom: '1.25rem', display: 'flex', alignItems: 'center', gap: '0.5rem' }}>
            <Activity size={18} className="text-primary" />
            Quick Actions
          </h3>
          <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
            <button className="btn btn-outline" onClick={() => setActiveModal('deposit')}>
              <ArrowDownToLine size={16} /> Deposit
            </button>
            <button className="btn btn-outline" onClick={() => setActiveModal('withdraw')}>
              <ArrowUpFromLine size={16} /> Withdraw
            </button>
            <button className="btn btn-outline" style={{ gridColumn: 'span 2' }} onClick={() => setActiveModal('transfer')}>
              <ArrowRightLeft size={16} /> Transfer Money
            </button>
          </div>
        </div>
      </div>

      {/* Accounts List */}
      <div className="flex justify-between items-center mb-4 mt-8">
        <h2 style={{ fontSize: '1.25rem', fontWeight: '600' }}>Your Accounts</h2>
        <button className="btn btn-primary" style={{ padding: '0.5rem 1rem', fontSize: '0.9rem' }} onClick={() => setActiveModal('create')}>
          <Plus size={16} /> Open Account
        </button>
      </div>

      {accounts.length === 0 ? (
        <div className="card text-center" style={{ padding: '3rem' }}>
          <CreditCard size={48} className="text-light mx-auto mb-4" style={{ margin: '0 auto', color: 'var(--text-light)' }} />
          <h3 style={{ fontWeight: '600', marginBottom: '0.5rem' }}>No accounts found</h3>
          <p className="text-muted mb-4">Open your first bank account to get started.</p>
          <button className="btn btn-primary" onClick={() => setActiveModal('create')}><Plus size={16} /> Open Account</button>
        </div>
      ) : (
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(300px, 1fr))', gap: '1.5rem' }}>
          {accounts.map(acc => (
            <div key={acc.accountNo} className="card">
              <div className="flex justify-between items-start mb-4">
                <div>
                  <div className="badge badge-success mb-2">{acc.status}</div>
                  <h4 style={{ fontWeight: '600', color: 'var(--text-muted)' }}>Account Number</h4>
                  <p style={{ fontFamily: 'monospace', fontSize: '1.1rem' }}>{acc.accountNo}</p>
                </div>
              </div>
              <div style={{ borderTop: '1px solid var(--border)', paddingTop: '1rem', marginTop: '1rem' }}>
                <h4 style={{ fontWeight: '500', color: 'var(--text-muted)', fontSize: '0.9rem', marginBottom: '0.25rem' }}>Available Balance</h4>
                <p style={{ fontSize: '1.5rem', fontWeight: '700', color: 'var(--text-main)' }}>${acc.balance.toFixed(2)}</p>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* Transactions List */}
      <div className="flex justify-between items-center mb-4 mt-12">
        <h2 style={{ fontSize: '1.25rem', fontWeight: '600' }}>Recent Transactions</h2>
      </div>
      
      <div className="card mb-8">
        {transactions.length === 0 ? (
          <div className="text-center" style={{ padding: '2rem 0' }}>
            <p className="text-muted">No recent transactions</p>
          </div>
        ) : (
          <ul className="transaction-list">
            {transactions.map(tx => {
              const isCredit = tx.fromAccountId !== tx.toAccountId && (tx.toAccountId === accounts[0]?.accountNo || tx.fromAccountId === 'SYSTEM_DEPOSIT');
              // Just a simple heuristic for demo: if fromAccountId doesn't exist in our accounts, it's a deposit
              
              return (
                <li key={tx.txId} className="transaction-item">
                  <div className="flex items-center">
                    <div className={`transaction-icon ${isCredit ? 'credit' : 'debit'}`}>
                      {isCredit ? <ArrowDownToLine size={20} /> : <ArrowUpFromLine size={20} />}
                    </div>
                    <div>
                      <h4 style={{ fontWeight: '500' }}>
                        {tx.description || (isCredit ? 'Deposit / Received' : 'Withdrawal / Sent')}
                      </h4>
                      <p className="text-muted" style={{ fontSize: '0.85rem' }}>
                        {new Date(tx.createdAt).toLocaleString()} • Ref: {tx.referenceNumber}
                      </p>
                    </div>
                  </div>
                  <div className="text-right">
                    <p style={{ fontWeight: '600', fontSize: '1.1rem' }} className={isCredit ? 'text-success' : ''}>
                      {isCredit ? '+' : '-'}${tx.amount.toFixed(2)}
                    </p>
                    <span className={`badge ${tx.status === 'COMPLETED' ? 'badge-success' : 'badge-warning'}`}>
                      {tx.status}
                    </span>
                  </div>
                </li>
              );
            })}
          </ul>
        )}
      </div>

      {renderModal()}
    </div>
  );
};

export default Dashboard;
