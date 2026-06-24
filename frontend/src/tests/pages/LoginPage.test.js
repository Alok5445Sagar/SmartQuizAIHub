import React from 'react';
import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { BrowserRouter } from 'react-router-dom';
import { AuthContext } from '../../context/AuthContext';
import LoginPage from '../../pages/LoginPage';

const mockNavigate = jest.fn();

jest.mock('react-router-dom', () => ({
  ...jest.requireActual('react-router-dom'),
  useNavigate: () => mockNavigate,
}));

// Mock react-hot-toast to avoid issues in test environment
jest.mock('react-hot-toast', () => ({
  __esModule: true,
  default: { success: jest.fn(), error: jest.fn() },
  Toaster: () => null,
}));

const renderLogin = (loginFn = jest.fn()) => {
  return render(
    <BrowserRouter>
      <AuthContext.Provider value={{ login: loginFn }}>
        <LoginPage />
      </AuthContext.Provider>
    </BrowserRouter>
  );
};

describe('LoginPage', () => {
  beforeEach(() => {
    mockNavigate.mockClear();
  });

  test('renders enterprise ID and password fields', () => {
    renderLogin();
    expect(screen.getByPlaceholderText(/e\.g\. gaurav/i)).toBeInTheDocument();
    expect(screen.getByPlaceholderText(/enter password/i)).toBeInTheDocument();
  });

  test('renders Sign In button', () => {
    renderLogin();
    expect(screen.getByRole('button', { name: /sign in/i })).toBeInTheDocument();
  });

  test('fills enterprise ID on Admin demo button click', () => {
    renderLogin();
    fireEvent.click(screen.getByText(/👑 Admin/i));
    expect(screen.getByPlaceholderText(/e\.g\. gaurav/i)).toHaveValue('admin');
  });

  test('fills enterprise ID on SME Swati demo button click', () => {
    renderLogin();
    fireEvent.click(screen.getByText(/👤 SME \(Swati\)/i));
    expect(screen.getByPlaceholderText(/e\.g\. gaurav/i)).toHaveValue('swati.avinash.nikam');
  });

  test('fills enterprise ID on SME Indugu demo button click', () => {
    renderLogin();
    fireEvent.click(screen.getByText(/👤 SME \(Indugu\)/i));
    expect(screen.getByPlaceholderText(/e\.g\. gaurav/i)).toHaveValue('indugu.hari.prasad');
  });

  test('shows error message on failed login', async () => {
    const loginFn = jest.fn().mockRejectedValue({
      response: { data: { message: 'Invalid credentials. Please try again.' } }
    });
    renderLogin(loginFn);

    fireEvent.change(screen.getByPlaceholderText(/e\.g\. gaurav/i), {
      target: { value: 'wrong.user' },
    });
    fireEvent.change(screen.getByPlaceholderText(/enter password/i), {
      target: { value: 'wrongpass' },
    });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(screen.getByText(/invalid credentials/i)).toBeInTheDocument();
    });
  });

  test('navigates to admin question-bank after admin login', async () => {
    const loginFn = jest.fn().mockResolvedValue({ role: 'ADMIN' });
    renderLogin(loginFn);

    fireEvent.change(screen.getByPlaceholderText(/e\.g\. gaurav/i), {
      target: { value: 'admin' },
    });
    fireEvent.change(screen.getByPlaceholderText(/enter password/i), {
      target: { value: 'admin123' },
    });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/admin/question-bank');
    });
  });

  test('navigates to my-questions after SME login', async () => {
    const loginFn = jest.fn().mockResolvedValue({ role: 'SME' });
    renderLogin(loginFn);

    fireEvent.change(screen.getByPlaceholderText(/e\.g\. gaurav/i), {
      target: { value: 'gaurav.a.bhola' },
    });
    fireEvent.change(screen.getByPlaceholderText(/enter password/i), {
      target: { value: 'password123' },
    });
    fireEvent.click(screen.getByRole('button', { name: /sign in/i }));

    await waitFor(() => {
      expect(mockNavigate).toHaveBeenCalledWith('/my-questions');
    });
  });
});