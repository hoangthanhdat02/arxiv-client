import type { Metadata, Viewport } from 'next';
import './globals.css';

export const metadata: Metadata = {
  title: 'arXiv Discovery Client - Academic Research Explorer',
  description: 'Fast, responsive, and distraction-free mobile & web client for exploring arXiv AI and Machine Learning papers.',
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 5,
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body className="antialiased selection:bg-rose-500 selection:text-white">
        {children}
      </body>
    </html>
  );
}
