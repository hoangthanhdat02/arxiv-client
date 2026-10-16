<div align="center">

# 🌐 arXiv Client - Frontend Application
### Topic 28: Next.js 15 App Router & Responsive Mobile Client

[![Next.js](https://img.shields.io/badge/Next.js-15.1-black?style=flat-square&logo=next.js)](https://nextjs.org)
[![React](https://img.shields.io/badge/React-19.0-61dafb?style=flat-square&logo=react)](https://react.dev)
[![Tailwind CSS](https://img.shields.io/badge/Tailwind-3.4-38bdf8?style=flat-square&logo=tailwindcss)](https://tailwindcss.com)
[![TypeScript](https://img.shields.io/badge/TypeScript-5.5-blue?style=flat-square&logo=typescript)](https://www.typescriptlang.org)
[![License](https://img.shields.io/badge/License-MIT-amber?style=flat-square)](LICENSE)

*Fast, responsive, and distraction-free mobile-first client for exploring, reading, and citing scientific papers from arXiv.*

</div>

---

## 🎯 1. Tính năng Phía Frontend

* **Khám phá bài báo (Home Feed):** Lọc theo các danh mục `cs.AI`, `cs.LG`, `cs.CV` với hiệu ứng khung xương Shimmer Loading.
* **Tìm kiếm toàn văn (Search Bar):** Tích hợp cơ chế **Debounce 350ms** chống spam API.
* **Khung hiển thị Di động (Mobile Frame Preview):** Nút chuyển đổi nhanh chế độ xem trên khung điện thoại **375px** để phục vụ demo trực quan môn di động.
* **Trình đọc PDF Native:** Nút "Read PDF" mở tài liệu trực tiếp từ nguồn arXiv.
* **Trích dẫn khoa học 1 chạm:** Tự động sinh mã trích dẫn **BibTeX / APA** sao chép vào Clipboard.
* **Thư viện cá nhân (Bookmarks):** Lưu bài đọc offline bằng LocalStorage và tính năng **Export toàn bộ thư viện thành file `.bib`** (tương thích Overleaf / Zotero).
* **Dark Mode chuẩn OLED:** Nút chuyển đổi giao diện sáng/tối lưu cấu hình vào máy.

---

## ⚡ 2. Cài đặt & Khởi chạy Nhanh

```bash
# 1. Cài đặt dependencies
npm install

# 2. Khởi chạy client ở chế độ phát triển (port 3000)
npm run dev
```

* Mở trình duyệt tại: [http://localhost:3000](http://localhost:3000)
* Giao tiếp với Backend tại: `http://localhost:3001` (Cấu hình qua file `.env`)

---

## 👥 3. Phân công Thành viên Team Frontend

* **TV 5 (Home Feed & Cards):** Lập trình component `PaperCard.tsx`, `Navbar.tsx` và hiệu ứng Shimmer loading.
* **TV 6 (Search & Mobile UI):** Lập trình `SearchBar.tsx`, `FilterModal.tsx` và tối ưu responsive view 375px.
* **TV 7 (Library & Citations UI):** Lập trình `LibraryModal.tsx`, modal trích dẫn BibTeX và xuất file `.bib`.
* **TV 1 (Lead):** Review code, kiểm tra giao diện và quản lý Git.
