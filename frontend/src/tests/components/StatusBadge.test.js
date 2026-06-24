import React from 'react';
import { render, screen } from '@testing-library/react';
import StatusBadge, { DifficultyBadge } from '../../components/StatusBadge';

describe('StatusBadge', () => {
  test('renders DRAFT status', () => {
    render(<StatusBadge status="DRAFT" />);
    expect(screen.getByText(/Draft/i)).toBeInTheDocument();
  });

  test('renders APPROVED status', () => {
    render(<StatusBadge status="APPROVED" />);
    expect(screen.getByText(/Approved/i)).toBeInTheDocument();
  });

  test('renders REJECTED status', () => {
    render(<StatusBadge status="REJECTED" />);
    expect(screen.getByText(/Rejected/i)).toBeInTheDocument();
  });

  test('renders READY_FOR_REVIEW status', () => {
    render(<StatusBadge status="READY_FOR_REVIEW" />);
    expect(screen.getByText(/Ready for Review/i)).toBeInTheDocument();
  });

  test('renders UNDER_REVIEW status', () => {
    render(<StatusBadge status="UNDER_REVIEW" />);
    expect(screen.getByText(/Under Review/i)).toBeInTheDocument();
  });
});

describe('DifficultyBadge', () => {
  test('renders Easy difficulty', () => {
    render(<DifficultyBadge difficulty="Easy" />);
    expect(screen.getByText('Easy')).toBeInTheDocument();
  });

  test('renders Medium difficulty', () => {
    render(<DifficultyBadge difficulty="Medium" />);
    expect(screen.getByText('Medium')).toBeInTheDocument();
  });

  test('renders Hard difficulty', () => {
    render(<DifficultyBadge difficulty="Hard" />);
    expect(screen.getByText('Hard')).toBeInTheDocument();
  });
});