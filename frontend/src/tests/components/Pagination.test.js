import React from 'react';
import { render, screen, fireEvent } from '@testing-library/react';
import Pagination from '../../components/Pagination';

describe('Pagination', () => {
  test('does not render when totalPages is 1', () => {
    const { container } = render(
      <Pagination page={0} totalPages={1} onPageChange={() => {}} />
    );
    expect(container.firstChild).toBeNull();
  });

  test('renders correct number of page buttons', () => {
    render(<Pagination page={0} totalPages={4} onPageChange={() => {}} />);
    expect(screen.getByText('1')).toBeInTheDocument();
    expect(screen.getByText('2')).toBeInTheDocument();
    expect(screen.getByText('3')).toBeInTheDocument();
    expect(screen.getByText('4')).toBeInTheDocument();
  });

  test('calls onPageChange with correct page when clicked', () => {
    const onPageChange = jest.fn();
    render(<Pagination page={0} totalPages={3} onPageChange={onPageChange} />);
    fireEvent.click(screen.getByText('2'));
    expect(onPageChange).toHaveBeenCalledWith(1);
  });

  test('Prev button is disabled on first page', () => {
    render(<Pagination page={0} totalPages={3} onPageChange={() => {}} />);
    expect(screen.getByText(/Prev/i)).toBeDisabled();
  });

  test('Next button is disabled on last page', () => {
    render(<Pagination page={2} totalPages={3} onPageChange={() => {}} />);
    expect(screen.getByText(/Next/i)).toBeDisabled();
  });

  test('active page button has active class', () => {
    render(<Pagination page={1} totalPages={3} onPageChange={() => {}} />);
    expect(screen.getByText('2')).toHaveClass('active');
  });
});