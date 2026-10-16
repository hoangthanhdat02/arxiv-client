'use client';

import React, { useState } from 'react';
import { Paper } from '../lib/api';
import { Bookmark, FileText, Share2, Quote, ExternalLink, ChevronDown, ChevronUp, Check } from 'lucide-react';

interface PaperCardProps {
  paper: Paper;
  isBookmarked: boolean;
  onToggleBookmark: (paper: Paper) => void;
  onShowCitation: (paper: Paper) => void;
}

export const PaperCard: React.FC<PaperCardProps> = ({
  paper,
  isBookmarked,
  onToggleBookmark,
  onShowCitation,
}) => {
  const [isExpanded, setIsExpanded] = useState(false);
  const [copiedLink, setCopiedLink] = useState(false);

  const handleShare = async () => {
    const url = paper.pdf_url;
    if (navigator.clipboard) {
      await navigator.clipboard.writeText(url);
      setCopiedLink(true);
      setTimeout(() => setCopiedLink(false), 2000);
    }
  };

  const formattedDate = new Date(paper.published_date).toLocaleDateString('en-US', {
    month: 'short',
    day: 'numeric',
    year: 'numeric',
  });

  return (
    <article className="p-5 rounded-2xl bg-white dark:bg-slate-900 border border-slate-200 dark:border-slate-800 shadow-sm hover:shadow-md transition-shadow">
      {/* Category & Date Header */}
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <span className="px-2.5 py-0.5 rounded-full text-xs font-semibold bg-rose-50 dark:bg-rose-950/40 text-rose-600 dark:text-rose-400 border border-rose-200 dark:border-rose-900">
            {paper.primary_category}
          </span>
          <span className="text-xs text-slate-500 dark:text-slate-400">
            {formattedDate}
          </span>
        </div>
        <span className="text-xs font-mono text-slate-400 dark:text-slate-500">
          arXiv:{paper.id}
        </span>
      </div>

      {/* Paper Title */}
      <h2 className="text-base sm:text-lg font-bold text-slate-900 dark:text-slate-100 leading-snug mb-2">
        {paper.title}
      </h2>

      {/* Authors */}
      <p className="text-xs text-slate-600 dark:text-slate-400 mb-3 line-clamp-1">
        <span className="font-semibold text-slate-700 dark:text-slate-300">Authors:</span>{' '}
        {paper.authors.join(', ')}
      </p>

      {/* Abstract with toggle */}
      <div className="text-sm text-slate-600 dark:text-slate-300 leading-relaxed mb-4">
        <p className={isExpanded ? '' : 'line-clamp-3'}>
          {paper.abstract}
        </p>
        <button
          onClick={() => setIsExpanded(!isExpanded)}
          className="text-xs font-medium text-rose-600 dark:text-rose-400 hover:underline mt-1 inline-flex items-center gap-1"
        >
          {isExpanded ? (
            <>
              Show less <ChevronUp className="w-3.5 h-3.5" />
            </>
          ) : (
            <>
              Read full abstract <ChevronDown className="w-3.5 h-3.5" />
            </>
          )}
        </button>
      </div>

      {/* Action Footer */}
      <div className="pt-3 border-t border-slate-100 dark:border-slate-800 flex items-center justify-between">
        {/* PDF Link Button */}
        <a
          href={paper.pdf_url}
          target="_blank"
          rel="noopener noreferrer"
          className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-rose-600 hover:bg-rose-700 text-white text-xs font-semibold transition shadow-sm"
        >
          <FileText className="w-3.5 h-3.5" />
          <span>Read PDF</span>
          <ExternalLink className="w-3 h-3 opacity-80" />
        </a>

        {/* Secondary Actions */}
        <div className="flex items-center gap-1">
          {/* Cite Button */}
          <button
            onClick={() => onShowCitation(paper)}
            title="Export BibTeX / APA Citation"
            className="p-2 rounded-lg text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition"
          >
            <Quote className="w-4 h-4" />
          </button>

          {/* Share Button */}
          <button
            onClick={handleShare}
            title="Copy PDF URL to Clipboard"
            className="p-2 rounded-lg text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800 transition relative"
          >
            {copiedLink ? <Check className="w-4 h-4 text-emerald-500" /> : <Share2 className="w-4 h-4" />}
          </button>

          {/* Bookmark Button */}
          <button
            onClick={() => onToggleBookmark(paper)}
            title={isBookmarked ? 'Remove from Library' : 'Save to Library'}
            className={`p-2 rounded-lg transition ${
              isBookmarked
                ? 'bg-rose-100 dark:bg-rose-950/60 text-rose-600 dark:text-rose-400'
                : 'text-slate-500 hover:text-slate-800 dark:text-slate-400 dark:hover:text-slate-200 hover:bg-slate-100 dark:hover:bg-slate-800'
            }`}
          >
            <Bookmark className={`w-4 h-4 ${isBookmarked ? 'fill-current' : ''}`} />
          </button>
        </div>
      </div>
    </article>
  );
};
