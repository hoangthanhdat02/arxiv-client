'use client';

import React, { useState } from 'react';
import { Paper } from '../lib/api';
import { X, Trash2, Download, Copy, Check, FileText } from 'lucide-react';

interface LibraryModalProps {
  isOpen: boolean;
  onClose: () => void;
  bookmarks: Paper[];
  onRemoveBookmark: (paperId: string) => void;
}

export const LibraryModal: React.FC<LibraryModalProps> = ({
  isOpen,
  onClose,
  bookmarks,
  onRemoveBookmark,
}) => {
  const [copiedId, setCopiedId] = useState<string | null>(null);

  if (!isOpen) return null;

  const handleCopyBibtex = async (paper: Paper) => {
    const year = new Date(paper.published_date).getFullYear();
    const bibtex = `@article{${paper.id},\n  title = {${paper.title}},\n  author = {${paper.authors.join(' and ')}},\n  journal = {arXiv preprint arXiv:${paper.id}},\n  year = {${year}},\n  url = {${paper.pdf_url}}\n}`;

    if (navigator.clipboard) {
      await navigator.clipboard.writeText(bibtex);
      setCopiedId(paper.id);
      setTimeout(() => setCopiedId(null), 2000);
    }
  };

  const handleExportAllBib = () => {
    if (bookmarks.length === 0) return;

    const allBibtex = bookmarks
      .map((paper) => {
        const year = new Date(paper.published_date).getFullYear();
        return `@article{${paper.id},\n  title = {${paper.title}},\n  author = {${paper.authors.join(' and ')}},\n  journal = {arXiv preprint arXiv:${paper.id}},\n  year = {${year}},\n  url = {${paper.pdf_url}}\n}`;
      })
      .join('\n\n');

    const blob = new Blob([allBibtex], { type: 'text/plain;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `arxiv_library_${Date.now()}.bib`;
    link.click();
    URL.revokeObjectURL(url);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-in fade-in duration-200">
      <div className="w-full max-w-xl max-h-[85vh] flex flex-col rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-2xl overflow-hidden">
        {/* Header */}
        <div className="p-4 border-b border-slate-100 dark:border-slate-800 flex items-center justify-between">
          <div>
            <h3 className="font-bold text-slate-900 dark:text-slate-100 text-base">
              Personal Reading Library
            </h3>
            <p className="text-xs text-slate-500 dark:text-slate-400">
              {bookmarks.length} {bookmarks.length === 1 ? 'paper' : 'papers'} saved
            </p>
          </div>

          <div className="flex items-center gap-2">
            {bookmarks.length > 0 && (
              <button
                onClick={handleExportAllBib}
                className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-emerald-600 hover:bg-emerald-700 text-white text-xs font-semibold transition"
              >
                <Download className="w-3.5 h-3.5" />
                <span>Export .bib</span>
              </button>
            )}
            <button
              onClick={onClose}
              className="p-1 rounded-lg text-slate-400 hover:text-slate-600 dark:hover:text-slate-200"
            >
              <X className="w-5 h-5" />
            </button>
          </div>
        </div>

        {/* Content list */}
        <div className="flex-1 overflow-y-auto p-4 space-y-3">
          {bookmarks.length === 0 ? (
            <div className="text-center py-12">
              <FileText className="w-10 h-10 mx-auto text-slate-300 dark:text-slate-600 mb-2" />
              <p className="text-sm font-semibold text-slate-700 dark:text-slate-300">
                Your library is empty
              </p>
              <p className="text-xs text-slate-400 dark:text-slate-500 mt-1">
                Save papers from the feed to read offline or export citations.
              </p>
            </div>
          ) : (
            bookmarks.map((paper) => (
              <div
                key={paper.id}
                className="p-3.5 rounded-xl border border-slate-100 dark:border-slate-800 bg-slate-50/50 dark:bg-slate-800/50 flex items-start justify-between gap-3"
              >
                <div className="space-y-1">
                  <span className="text-[10px] font-bold px-2 py-0.5 rounded-full bg-rose-100 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400">
                    {paper.primary_category}
                  </span>
                  <h4 className="text-xs font-bold text-slate-900 dark:text-slate-100 line-clamp-2">
                    {paper.title}
                  </h4>
                  <p className="text-[11px] text-slate-500 dark:text-slate-400 line-clamp-1">
                    {paper.authors.join(', ')}
                  </p>
                </div>

                <div className="flex items-center gap-1 shrink-0">
                  <button
                    onClick={() => handleCopyBibtex(paper)}
                    title="Copy BibTeX Citation"
                    className="p-1.5 rounded-lg text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200 hover:bg-slate-200 dark:hover:bg-slate-700 transition"
                  >
                    {copiedId === paper.id ? (
                      <Check className="w-4 h-4 text-emerald-500" />
                    ) : (
                      <Copy className="w-4 h-4" />
                    )}
                  </button>

                  <button
                    onClick={() => onRemoveBookmark(paper.id)}
                    title="Remove Bookmark"
                    className="p-1.5 rounded-lg text-rose-500 hover:text-rose-700 hover:bg-rose-50 dark:hover:bg-rose-950/40 transition"
                  >
                    <Trash2 className="w-4 h-4" />
                  </button>
                </div>
              </div>
            ))
          )}
        </div>
      </div>
    </div>
  );
};
