export interface Paper {
  id: string;
  title: string;
  authors: string[];
  abstract: string;
  published_date: string;
  updated_date?: string;
  primary_category: string;
  categories: string[];
  pdf_url: string;
  doi?: string;
}

export interface Bookmark {
  id: string;
  paperId: string;
  paper: Paper;
  note: string;
  tags: string[];
  savedAt: string;
}

const API_BASE = process.env.NEXT_PUBLIC_API_URL || 'http://localhost:3001';

export async function fetchPapers(category = 'cs.AI', page = 1, limit = 10): Promise<{ data: Paper[]; total: number }> {
  try {
    const res = await fetch(`${API_BASE}/api/papers?category=${encodeURIComponent(category)}&page=${page}&limit=${limit}`, {
      next: { revalidate: 60 },
    });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (err) {
    console.warn('[API Client] Backend unreachable, using fallback dataset:', err);
    // Fallback static sample papers
    return {
      total: 3,
      data: [
        {
          id: '2401.00001',
          title: 'Attention Is All You Need for Multimodal Reasoning: A Comprehensive Survey',
          authors: ['Hoang Nguyen', 'Ashish Vaswani', 'Noam Shazeer'],
          abstract: 'The dominant sequence transduction models are based on complex recurrent or convolutional neural networks...',
          published_date: '2026-01-02T10:00:00Z',
          primary_category: 'cs.AI',
          categories: ['cs.AI', 'cs.LG'],
          pdf_url: 'https://arxiv.org/pdf/1706.03762.pdf',
        },
        {
          id: '2401.00002',
          title: 'LoRA-XS: Low-Rank Adaptation with Extremely Small Parameter Footprint',
          authors: ['Elena Rostova', 'Marcus Vance', 'David Silver'],
          abstract: 'Parameter-Efficient Fine-Tuning (PEFT) has become the gold standard for adapting large foundation models...',
          published_date: '2026-01-03T14:30:00Z',
          primary_category: 'cs.LG',
          categories: ['cs.LG', 'cs.AI'],
          pdf_url: 'https://arxiv.org/pdf/2106.09685.pdf',
        },
        {
          id: '2401.00004',
          title: 'Vision-Language State Space Models for Long-Horizon Video Understanding',
          authors: ['Albert Gu', 'Tri Dao', 'Kaiming He'],
          abstract: 'Transformer architectures struggle with quadratic complexity when scaling to hour-long video contexts...',
          published_date: '2026-01-05T16:20:00Z',
          primary_category: 'cs.CV',
          categories: ['cs.CV', 'cs.LG'],
          pdf_url: 'https://arxiv.org/pdf/2312.00752.pdf',
        },
      ],
    };
  }
}

export async function searchPapers(q: string, category?: string): Promise<{ data: Paper[]; total: number }> {
  try {
    let url = `${API_BASE}/api/search?q=${encodeURIComponent(q)}`;
    if (category && category !== 'ALL') url += `&category=${encodeURIComponent(category)}`;
    const res = await fetch(url);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (err) {
    const fallback = await fetchPapers(category || 'cs.AI');
    const filtered = fallback.data.filter(
      (p) =>
        p.title.toLowerCase().includes(q.toLowerCase()) ||
        p.abstract.toLowerCase().includes(q.toLowerCase()),
    );
    return { data: filtered, total: filtered.length };
  }
}

export async function getCitation(paperId: string): Promise<{ bibtex: string; apa: string }> {
  try {
    const res = await fetch(`${API_BASE}/api/papers/${paperId}/cite`);
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    return await res.json();
  } catch (err) {
    return {
      bibtex: `@article{${paperId},\n  title={Paper ${paperId}},\n  journal={arXiv preprint},\n  year={2026}\n}`,
      apa: `Author et al. (2026). Paper ${paperId}. arXiv preprint.`,
    };
  }
}
