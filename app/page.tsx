'use client';

import React, { useState, useEffect, useCallback } from 'react';
import { Navbar } from '../components/Navbar';
import { SearchBar } from '../components/SearchBar';
import { PaperCard } from '../components/PaperCard';
import { FilterModal } from '../components/FilterModal';
import { LibraryModal } from '../components/LibraryModal';
import { Paper, fetchPapers, searchPapers, getCitation } from '../lib/api';
import { Sparkles, Smartphone, Monitor, BookMarked, Quote, X, Copy, Check } from 'lucide-react';

export default function HomePage() {
  const [papers, setPapers] = useState<Paper[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedCategory, setSelectedCategory] = useState('cs.AI');
  const [sortBy, setSortBy] = useState<'relevance' | 'date'>('relevance');
  const [isFilterOpen, setIsFilterOpen] = useState(false);
  const [isLibraryOpen, setIsLibraryOpen] = useState(false);
  const [bookmarks, setBookmarks] = useState<Paper[]>([]);
  const [isDark, setIsDark] = useState(false);
  const [isMobilePreview, setIsMobilePreview] = useState(false);

  // Citation modal state
  const [activeCitation, setActiveCitation] = useState<{ paper: Paper; bibtex: string; apa: string } | null>(null);
  const [copiedType, setCopiedType] = useState<string | null>(null);

  // Load bookmarks from local storage
  useEffect(() => {
    try {
      const saved = localStorage.getItem('arxiv_bookmarks');
      if (saved) setBookmarks(JSON.parse(saved));
      const theme = localStorage.getItem('arxiv_theme');
      if (theme === 'dark') {
        setIsDark(true);
        document.documentElement.classList.add('dark');
      }
    } catch (_) {}
  }, []);

  const toggleTheme = () => {
    const next = !isDark;
    setIsDark(next);
    if (next) {
      document.documentElement.classList.add('dark');
      localStorage.setItem('arxiv_theme', 'dark');
    } else {
      document.documentElement.classList.remove('dark');
      localStorage.setItem('arxiv_theme', 'light');
    }
  };

  const handleToggleBookmark = (paper: Paper) => {
    setBookmarks((prev) => {
      const exists = prev.some((p) => p.id === paper.id);
      let updated: Paper[];
      if (exists) {
        updated = prev.filter((p) => p.id !== paper.id);
      } else {
        updated = [paper, ...prev];
      }
      try {
        localStorage.setItem('arxiv_bookmarks', JSON.stringify(updated));
      } catch (_) {}
      return updated;
    });
  };

  const handleRemoveBookmark = (paperId: string) => {
    setBookmarks((prev) => {
      const updated = prev.filter((p) => p.id !== paperId);
      try {
        localStorage.setItem('arxiv_bookmarks', JSON.stringify(updated));
      } catch (_) {}
      return updated;
    });
  };

  const loadPapers = useCallback(async (cat: string) => {
    setLoading(true);
    try {
      const res = await fetchPapers(cat, 1, 10);
      setPapers(res.data);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadPapers(selectedCategory);
  }, [selectedCategory, loadPapers]);

  const handleSearch = useCallback(async (query: string, category: string) => {
    if (!query.trim()) {
      loadPapers(category);
      return;
    }
    setLoading(true);
    try {
      const res = await searchPapers(query, category);
      setPapers(res.data);
    } finally {
      setLoading(false);
    }
  }, [loadPapers]);

  const handleShowCitation = async (paper: Paper) => {
    const cite = await getCitation(paper.id);
    setActiveCitation({ paper, ...cite });
  };

  const copyCitation = async (text: string, type: string) => {
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(text);
      setCopiedType(type);
      setTimeout(() => setCopiedType(null), 2000);
    }
  };

  return (
    <div className={`min-h-screen bg-slate-50 dark:bg-slate-950 transition-colors pb-16 ${isDark ? 'dark' : ''}`}>
      {/* Top Navigation */}
      <Navbar
        bookmarkCount={bookmarks.length}
        onOpenLibrary={() => setIsLibraryOpen(true)}
        isDark={isDark}
        onToggleTheme={toggleTheme}
      />

      {/* Main Container */}
      <main className={`mx-auto px-4 py-6 transition-all duration-300 ${
        isMobilePreview ? 'max-w-[420px] border-x border-slate-200 dark:border-slate-800 shadow-2xl bg-white dark:bg-slate-900 min-h-screen my-4 rounded-3xl' : 'max-w-4xl'
      }`}>
        {/* Device Viewport Toggle Banner */}
        <div className="flex items-center justify-between mb-6 pb-3 border-b border-slate-200/60 dark:border-slate-800/60">
          <div className="flex items-center gap-2 text-xs text-slate-500 dark:text-slate-400">
            <Sparkles className="w-4 h-4 text-rose-500" />
            <span>Latest preprints in <b>{selectedCategory}</b></span>
          </div>

          <div className="flex items-center gap-1 bg-slate-200/60 dark:bg-slate-800/60 p-1 rounded-xl">
            <button
              onClick={() => setIsMobilePreview(false)}
              className={`p-1.5 rounded-lg text-xs font-semibold flex items-center gap-1 transition ${
                !isMobilePreview ? 'bg-white dark:bg-slate-700 text-slate-900 dark:text-slate-100 shadow-xs' : 'text-slate-500'
              }`}
              title="Responsive Desktop View"
            >
              <Monitor className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Desktop</span>
            </button>
            <button
              onClick={() => setIsMobilePreview(true)}
              className={`p-1.5 rounded-lg text-xs font-semibold flex items-center gap-1 transition ${
                isMobilePreview ? 'bg-white dark:bg-slate-700 text-slate-900 dark:text-slate-100 shadow-xs' : 'text-slate-500'
              }`}
              title="Preview in 375px Mobile Frame"
            >
              <Smartphone className="w-3.5 h-3.5" />
              <span className="hidden sm:inline">Mobile Frame</span>
            </button>
          </div>
        </div>

        {/* Search & Categories */}
        <div className="mb-6">
          <SearchBar
            onSearch={handleSearch}
            selectedCategory={selectedCategory}
            onSelectCategory={setSelectedCategory}
            onOpenFilter={() => setIsFilterOpen(true)}
          />
        </div>

        {/* Feed List */}
        <div className="space-y-4">
          {loading ? (
            // Shimmer Loading Skeleton
            Array.from({ length: 3 }).map((_, i) => (
              <div
                key={i}
                className="p-5 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 animate-pulse space-y-3"
              >
                <div className="h-4 bg-slate-200 dark:bg-slate-800 rounded w-1/4" />
                <div className="h-6 bg-slate-200 dark:bg-slate-800 rounded w-3/4" />
                <div className="h-4 bg-slate-200 dark:bg-slate-800 rounded w-1/2" />
                <div className="h-16 bg-slate-100 dark:bg-slate-800/60 rounded" />
              </div>
            ))
          ) : papers.length === 0 ? (
            <div className="text-center py-16 bg-white dark:bg-slate-900 rounded-2xl border border-slate-200 dark:border-slate-800 p-8">
              <BookMarked className="w-12 h-12 text-slate-300 dark:text-slate-600 mx-auto mb-3" />
              <h3 className="font-bold text-slate-800 dark:text-slate-200 text-base">
                No matching papers found
              </h3>
              <p className="text-xs text-slate-500 dark:text-slate-400 mt-1">
                Try searching for another keyword or selecting a different subject category.
              </p>
            </div>
          ) : (
            papers.map((paper) => (
              <PaperCard
                key={paper.id}
                paper={paper}
                isBookmarked={bookmarks.some((b) => b.id === paper.id)}
                onToggleBookmark={handleToggleBookmark}
                onShowCitation={handleShowCitation}
              />
            ))
          )}
        </div>
      </main>

      {/* Filter Modal */}
      <FilterModal
        isOpen={isFilterOpen}
        onClose={() => setIsFilterOpen(false)}
        sortBy={sortBy}
        onSelectSort={(s) => {
          setSortBy(s);
          setIsFilterOpen(false);
        }}
      />

      {/* Library Modal */}
      <LibraryModal
        isOpen={isLibraryOpen}
        onClose={() => setIsLibraryOpen(false)}
        bookmarks={bookmarks}
        onRemoveBookmark={handleRemoveBookmark}
      />

      {/* Citation Modal */}
      {activeCitation && (
        <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-200">
          <div className="w-full max-w-lg rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 p-5 shadow-2xl space-y-4">
            <div className="flex items-center justify-between pb-2 border-b border-slate-100 dark:border-slate-800">
              <div className="flex items-center gap-2">
                <Quote className="w-5 h-5 text-rose-600" />
                <h3 className="font-bold text-slate-900 dark:text-slate-100 text-sm">
                  Cite This Paper
                </h3>
              </div>
              <button
                onClick={() => setActiveCitation(null)}
                className="p-1 rounded-lg text-slate-400 hover:text-slate-600"
              >
                <X className="w-4 h-4" />
              </button>
            </div>

            {/* BibTeX */}
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-700 dark:text-slate-300">BibTeX Format</span>
                <button
                  onClick={() => copyCitation(activeCitation.bibtex, 'bibtex')}
                  className="inline-flex items-center gap-1 text-xs text-rose-600 hover:underline font-semibold"
                >
                  {copiedType === 'bibtex' ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copiedType === 'bibtex' ? 'Copied!' : 'Copy BibTeX'}</span>
                </button>
              </div>
              <pre className="p-3 rounded-xl bg-slate-100 dark:bg-slate-800 text-[11px] font-mono text-slate-800 dark:text-slate-200 overflow-x-auto">
                {activeCitation.bibtex}
              </pre>
            </div>

            {/* APA */}
            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <span className="text-xs font-bold text-slate-700 dark:text-slate-300">APA Format</span>
                <button
                  onClick={() => copyCitation(activeCitation.apa, 'apa')}
                  className="inline-flex items-center gap-1 text-xs text-rose-600 hover:underline font-semibold"
                >
                  {copiedType === 'apa' ? <Check className="w-3.5 h-3.5 text-emerald-500" /> : <Copy className="w-3.5 h-3.5" />}
                  <span>{copiedType === 'apa' ? 'Copied!' : 'Copy APA'}</span>
                </button>
              </div>
              <p className="p-3 rounded-xl bg-slate-100 dark:bg-slate-800 text-xs text-slate-700 dark:text-slate-300">
                {activeCitation.apa}
              </p>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
