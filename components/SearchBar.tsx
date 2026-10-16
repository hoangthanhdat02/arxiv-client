'use client';

import React, { useState, useEffect } from 'react';
import { Search, X, SlidersHorizontal } from 'lucide-react';

interface SearchBarProps {
  onSearch: (query: string, category: string) => void;
  selectedCategory: string;
  onSelectCategory: (cat: string) => void;
  onOpenFilter: () => void;
}

const CATEGORIES = [
  { code: 'cs.AI', label: 'AI' },
  { code: 'cs.LG', label: 'Machine Learning' },
  { code: 'cs.CV', label: 'Computer Vision' },
  { code: 'ALL', label: 'All Subjects' },
];

export const SearchBar: React.FC<SearchBarProps> = ({
  onSearch,
  selectedCategory,
  onSelectCategory,
  onOpenFilter,
}) => {
  const [query, setQuery] = useState('');

  // Debounced search trigger (350ms)
  useEffect(() => {
    const handler = setTimeout(() => {
      onSearch(query, selectedCategory);
    }, 350);

    return () => clearTimeout(handler);
  }, [query, selectedCategory, onSearch]);

  return (
    <div className="w-full space-y-3">
      {/* Search Input Box */}
      <div className="relative flex items-center">
        <Search className="absolute left-3.5 w-4 h-4 text-slate-400" />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search papers by title, abstract or author..."
          className="w-full pl-10 pr-20 py-2.5 rounded-xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 text-sm text-slate-900 dark:text-slate-100 placeholder-slate-400 focus:outline-none focus:ring-2 focus:ring-rose-500/30 focus:border-rose-500 shadow-sm transition"
        />

        <div className="absolute right-2 flex items-center gap-1">
          {query && (
            <button
              onClick={() => setQuery('')}
              className="p-1 rounded-md text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
            >
              <X className="w-4 h-4" />
            </button>
          )}
          <button
            onClick={onOpenFilter}
            title="Advanced Filters"
            className="p-1.5 rounded-lg text-slate-500 hover:text-slate-700 dark:text-slate-400 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800"
          >
            <SlidersHorizontal className="w-4 h-4" />
          </button>
        </div>
      </div>

      {/* Category Filter Pills */}
      <div className="flex items-center gap-2 overflow-x-auto pb-1 scrollbar-none">
        {CATEGORIES.map((cat) => {
          const isActive = selectedCategory === cat.code;
          return (
            <button
              key={cat.code}
              onClick={() => onSelectCategory(cat.code)}
              className={`px-3 py-1.5 rounded-xl text-xs font-semibold whitespace-nowrap transition shadow-xs ${
                isActive
                  ? 'bg-rose-600 text-white shadow-rose-600/20'
                  : 'bg-white dark:bg-slate-900 text-slate-600 dark:text-slate-400 border border-slate-200 dark:border-slate-800 hover:bg-slate-50 dark:hover:bg-slate-800'
              }`}
            >
              {cat.label}
            </button>
          );
        })}
      </div>
    </div>
  );
};
