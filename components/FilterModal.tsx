'use client';

import React from 'react';
import { X, Check } from 'lucide-react';

interface FilterModalProps {
  isOpen: boolean;
  onClose: () => void;
  sortBy: 'relevance' | 'date';
  onSelectSort: (sort: 'relevance' | 'date') => void;
}

export const FilterModal: React.FC<FilterModalProps> = ({
  isOpen,
  onClose,
  sortBy,
  onSelectSort,
}) => {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="w-full max-w-sm rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-5 shadow-xl">
        <div className="flex items-center justify-between pb-3 border-b border-slate-100 dark:border-slate-800">
          <h3 className="font-bold text-slate-900 dark:text-slate-100 text-sm">
            Search & Filter Options
          </h3>
          <button
            onClick={onClose}
            className="p-1 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
          >
            <X className="w-4 h-4" />
          </button>
        </div>

        <div className="py-4 space-y-4">
          <div>
            <label className="text-xs font-semibold text-slate-500 dark:text-slate-400 uppercase tracking-wider block mb-2">
              Sort Order
            </label>
            <div className="space-y-1.5">
              <button
                onClick={() => onSelectSort('relevance')}
                className={`w-full flex items-center justify-between px-3.5 py-2 rounded-xl text-xs font-medium transition ${
                  sortBy === 'relevance'
                    ? 'bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 font-bold'
                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800'
                }`}
              >
                <span>Most Relevant</span>
                {sortBy === 'relevance' && <Check className="w-4 h-4" />}
              </button>
              <button
                onClick={() => onSelectSort('date')}
                className={`w-full flex items-center justify-between px-3.5 py-2 rounded-xl text-xs font-medium transition ${
                  sortBy === 'date'
                    ? 'bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 font-bold'
                    : 'text-slate-700 dark:text-slate-300 hover:bg-slate-50 dark:hover:bg-slate-800'
                }`}
              >
                <span>Latest Submission Date</span>
                {sortBy === 'date' && <Check className="w-4 h-4" />}
              </button>
            </div>
          </div>
        </div>

        <button
          onClick={onClose}
          className="w-full py-2 rounded-xl bg-slate-900 dark:bg-slate-100 text-white dark:text-slate-900 text-xs font-bold transition hover:opacity-90"
        >
          Apply Filters
        </button>
      </div>
    </div>
  );
};
